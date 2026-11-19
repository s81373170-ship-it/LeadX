package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.AutoFetchEngine
import com.example.data.AutoFetchPreferences
import com.example.data.LeadXRepository
import com.example.data.RealLeadExtractor
import com.example.data.local.LeadXDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class DailyAutoFetchWorker(
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

        val config = AutoFetchPreferences.getConfig(applicationContext)
        if (!config.enabled) {
          return@withContext Result.success()
        }

        val dao = LeadXDatabase.getInstance(applicationContext).leadXDao()
        val databaseId = applicationContext.getString(R.string.firestore_database_id)
        val firestore = if (hasFirebase) runCatching { FirebaseFirestore.getInstance(databaseId) }.getOrNull() else null
        val auth = if (hasFirebase) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null
        val repository = LeadXRepository(dao, firestore, auth)

        AutoFetchEngine.executeAutoFetch(
          context = applicationContext,
          dao = dao,
          repository = repository,
          config = config,
          notifyUser = true,
        )
        Result.success()
      } catch (_: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
      }
    }
}

object AutoFetchScheduler {
  private const val UNIQUE_WORK_NAME = "leadx_daily_auto_fetch"

  fun syncSchedule(context: Context, enabled: Boolean, intervalHours: Long) {
    if (enabled) {
      schedulePeriodic(context, intervalHours)
    } else {
      cancelPeriodic(context)
    }
  }

  fun schedulePeriodic(context: Context, intervalHours: Long) {
    runCatching {
      val clampedHours = intervalHours.coerceAtLeast(1L)
      val constraints =
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build()

      val request =
        PeriodicWorkRequestBuilder<DailyAutoFetchWorker>(clampedHours, TimeUnit.HOURS)
          .setConstraints(constraints)
          .addTag(UNIQUE_WORK_NAME)
          .build()

      WorkManager.getInstance(context.applicationContext)
        .enqueueUniquePeriodicWork(
          UNIQUE_WORK_NAME,
          ExistingPeriodicWorkPolicy.UPDATE,
          request,
        )
    }
  }

  fun cancelPeriodic(context: Context) {
    runCatching {
      WorkManager.getInstance(context.applicationContext)
        .cancelUniqueWork(UNIQUE_WORK_NAME)
    }
  }
}
