package com.example.worker

import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.R
import com.example.data.AutoFetchEngine
import com.example.data.AutoFetchNotifier
import com.example.data.AutoFetchPreferences
import com.example.data.CsvBatchManager
import com.example.data.LeadXRepository
import com.example.data.RealLeadExtractor
import com.example.data.local.LeadXDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager CoroutineWorker scheduled every 24 hours to perform simulated periodic data fetching
 * and update the Cloud Firestore database (and local Room DB).
 */
class PeriodicFirestoreSyncWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result =
    withContext(Dispatchers.IO) {
      try {
        val hasFirebase =
          runCatching { FirebaseApp.getApps(applicationContext).isNotEmpty() }.getOrDefault(false) ||
            runCatching { FirebaseApp.initializeApp(applicationContext) != null }.getOrDefault(false)

        RealLeadExtractor.init(applicationContext)
        AutoFetchPreferences.init(applicationContext)
        CsvBatchManager.init(applicationContext)

        val dao = LeadXDatabase.getInstance(applicationContext).leadXDao()
        val databaseId = applicationContext.getString(R.string.firestore_database_id)
        val firestore =
          if (hasFirebase) runCatching { FirebaseFirestore.getInstance(databaseId) }.getOrNull()
          else null
        val auth = if (hasFirebase) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null
        val repository = LeadXRepository(dao, firestore, auth)

        val syncRes = repository.perform24hSimulatedFetchAndFirestoreSync()
        val outcome = syncRes.getOrThrow()

        // Also run configured real No-API LinkedIn auto-fetch if enabled
        val autoFetchConfig = AutoFetchPreferences.getConfig(applicationContext)
        if (autoFetchConfig.enabled && autoFetchConfig.hasValidTarget()) {
          AutoFetchEngine.executeAutoFetch(
            context = applicationContext,
            dao = dao,
            repository = repository,
            config = autoFetchConfig,
            notifyUser = false,
          )
        }

        AutoFetchPreferences.recordPeriodicSyncOutcome(
          context = applicationContext,
          statusMessage = outcome.statusMessage,
          firestoreUpdated = outcome.firestoreUpdated,
          timestampMillis = outcome.timestampMillis,
        )

        AutoFetchNotifier.notifyRunResult(
          context = applicationContext,
          title = "LeadX Pro · 24h WorkManager Sync",
          message = outcome.statusMessage,
        )

        Result.success(
          workDataOf(
            KEY_OUTPUT_BATCH to outcome.simulatedBatchLabel,
            KEY_OUTPUT_FIRESTORE_UPDATED to outcome.firestoreUpdated,
            KEY_OUTPUT_STATUS to outcome.statusMessage,
          )
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (runAttemptCount < 3) {
          Result.retry()
        } else {
          AutoFetchPreferences.recordPeriodicSyncOutcome(
            context = applicationContext,
            statusMessage = "24h WorkManager sync failed: ${e.message ?: "unknown error"}",
            firestoreUpdated = false,
          )
          Result.failure()
        }
      }
    }

  companion object {
    const val KEY_OUTPUT_BATCH = "output_batch"
    const val KEY_OUTPUT_FIRESTORE_UPDATED = "output_firestore_updated"
    const val KEY_OUTPUT_STATUS = "output_status"
  }
}

object PeriodicFirestoreSyncScheduler {
  const val UNIQUE_PERIODIC_SYNC_WORK = "leadx_24h_firestore_sync"
  const val UNIQUE_IMMEDIATE_SYNC_WORK = "leadx_24h_firestore_sync_now"
  const val REPEAT_INTERVAL_HOURS = 24L

  fun buildPeriodicWorkRequest(): PeriodicWorkRequest {
    val constraints =
      Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    return PeriodicWorkRequestBuilder<PeriodicFirestoreSyncWorker>(
      REPEAT_INTERVAL_HOURS,
      TimeUnit.HOURS,
    )
      .setConstraints(constraints)
      .addTag(UNIQUE_PERIODIC_SYNC_WORK)
      .build()
  }

  private fun safeWorkManager(context: Context): WorkManager? =
    runCatching { WorkManager.getInstance(context.applicationContext) }
      .recoverCatching {
        WorkManager.initialize(
          context.applicationContext,
          Configuration.Builder().build(),
        )
        WorkManager.getInstance(context.applicationContext)
      }
      .getOrNull()

  fun ensureScheduled24h(context: Context) {
    AutoFetchPreferences.init(context)
    val state = AutoFetchPreferences.getPeriodicSyncState(context)
    if (!state.enabled) return
    runCatching {
      safeWorkManager(context)
        ?.enqueueUniquePeriodicWork(
          UNIQUE_PERIODIC_SYNC_WORK,
          ExistingPeriodicWorkPolicy.KEEP,
          buildPeriodicWorkRequest(),
        )
    }
  }

  fun setPeriodicSyncEnabled(context: Context, enabled: Boolean) {
    AutoFetchPreferences.init(context)
    AutoFetchPreferences.setPeriodicSyncEnabled(context, enabled)
    runCatching {
      val wm = safeWorkManager(context) ?: return@runCatching
      if (enabled) {
        wm.enqueueUniquePeriodicWork(
          UNIQUE_PERIODIC_SYNC_WORK,
          ExistingPeriodicWorkPolicy.UPDATE,
          buildPeriodicWorkRequest(),
        )
      } else {
        wm.cancelUniqueWork(UNIQUE_PERIODIC_SYNC_WORK)
      }
    }
  }

  fun triggerImmediateSyncNow(context: Context) {
    runCatching {
      val wm = safeWorkManager(context) ?: return@runCatching
      val request =
        OneTimeWorkRequestBuilder<PeriodicFirestoreSyncWorker>()
          .addTag(UNIQUE_IMMEDIATE_SYNC_WORK)
          .build()
      wm.enqueueUniqueWork(
        UNIQUE_IMMEDIATE_SYNC_WORK,
        ExistingWorkPolicy.REPLACE,
        request,
      )
    }
  }

  fun observeWorkStateLabel(context: Context): Flow<String> {
    val wm = safeWorkManager(context) ?: return flowOf("ENQUEUED · Every 24h")
    return runCatching {
      wm.getWorkInfosForUniqueWorkFlow(UNIQUE_PERIODIC_SYNC_WORK)
        .map { infos ->
          val primary = infos.firstOrNull()
          when (primary?.state) {
            WorkInfo.State.ENQUEUED -> "ENQUEUED · Every 24h"
            WorkInfo.State.RUNNING -> "RUNNING NOW"
            WorkInfo.State.SUCCEEDED -> "SUCCEEDED · Every 24h"
            WorkInfo.State.BLOCKED -> "WAITING NETWORK · 24h"
            WorkInfo.State.FAILED -> "RETRY SCHEDULED · 24h"
            WorkInfo.State.CANCELLED, null -> "PAUSED"
          }
        }
        .catch { emit("ENQUEUED · Every 24h") }
    }.getOrElse { flowOf("ENQUEUED · Every 24h") }
  }
}
