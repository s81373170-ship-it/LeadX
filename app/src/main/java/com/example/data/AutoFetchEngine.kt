package com.example.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.LeadXDao
import com.example.model.AccountRole
import com.example.model.AutoFetchConfig
import com.example.model.AutoFetchRunLog
import com.example.model.LINKEDIN_COST_PER_LEAD
import com.example.model.LeadRecord
import com.example.model.LinkedInCandidate
import com.example.model.LinkedInSearchQuery
import com.example.model.MIN_WALLET_BALANCE
import kotlinx.coroutines.flow.first
import java.util.UUID

data class AutoFetchOutcome(
  val fetchedCount: Int,
  val savedCount: Int,
  val stagedCandidates: List<LinkedInCandidate>,
  val statusMessage: String,
  val isError: Boolean,
)

object AutoFetchNotifier {
  private const val CHANNEL_ID = "leadx_daily_autofetch"
  private const val NOTIFICATION_ID = 4102

  fun notifyRunResult(context: Context, title: String, message: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) return
    }

    runCatching {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.autofetch_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
          ).apply {
            description = context.getString(R.string.autofetch_channel_desc)
          }
        manager.createNotificationChannel(channel)
      }

      val openIntent =
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
      val pendingIntent =
        PendingIntent.getActivity(
          context,
          0,
          openIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

      val notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_notification)
          .setContentTitle(title)
          .setContentText(message)
          .setStyle(NotificationCompat.BigTextStyle().bigText(message))
          .setPriority(NotificationCompat.PRIORITY_DEFAULT)
          .setAutoCancel(true)
          .setContentIntent(pendingIntent)
          .build()

      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
  }
}

object AutoFetchEngine {

  /**
   * Deduplicates candidates against each other and already-saved workspace leads
   * so daily runs never charge duplicate points for previously captured contacts.
   */
  fun deduplicateNewCandidates(
    fetched: List<LinkedInCandidate>,
    existingLeads: List<LeadRecord>,
  ): List<LinkedInCandidate> {
    val existingUrls =
      existingLeads
        .map { it.profileUrl.trim().substringBefore("?").trimEnd('/').lowercase() }
        .filter { it.isNotBlank() }
        .toHashSet()
    val existingIdentity =
      existingLeads
        .map { "${it.name.trim().lowercase()}|${it.company.trim().lowercase()}" }
        .filter { !it.startsWith("|") }
        .toHashSet()

    val seenUrls = HashSet<String>(existingUrls)
    val seenIdentities = HashSet<String>(existingIdentity)

    return fetched.filter { candidate ->
      val normUrl = candidate.profileUrl.trim().substringBefore("?").trimEnd('/').lowercase()
      val identity = "${candidate.name.trim().lowercase()}|${candidate.company.trim().lowercase()}"
      val urlDup = normUrl.isNotBlank() && !seenUrls.add(normUrl)
      val idDup = candidate.name.isNotBlank() && !seenIdentities.add(identity)
      !urlDup && !idDup
    }
  }

  suspend fun executeAutoFetch(
    context: Context,
    dao: LeadXDao,
    repository: LeadXRepository,
    config: AutoFetchConfig,
    notifyUser: Boolean = true,
  ): AutoFetchOutcome {
    RealLeadExtractor.init(context)
    dao.seedInitialAccountsIfEmpty()
    val now = System.currentTimeMillis()

    if (!config.hasValidTarget()) {
      val msg = "Configure B2B filters or at least one LinkedIn URL before running Auto-Fetch."
      recordOutcome(context, config, 0, 0, msg, isError = true, now = now)
      return AutoFetchOutcome(0, 0, emptyList(), msg, isError = true)
    }

    val collected = mutableListOf<LinkedInCandidate>()
    var lastErrorMsg = ""

    // 1. Fetch configured watchlist LinkedIn URLs (Proxycurl / Public Meta / DuckDuckGo Web Index)
    val urls = config.parsedProfileUrls().take(config.maxLeadsPerRun)
    for (url in urls) {
      val res = RealLeadExtractor.fetchRealLinkedInProfile(url)
      res.onSuccess { collected.add(it) }
      res.onFailure { err ->
        if (lastErrorMsg.isBlank()) lastErrorMsg = err.message.orEmpty()
      }
    }

    // 2. Fetch Proxycurl B2B search leads when filters + API key are available
    val remainingCapacity = (config.maxLeadsPerRun - collected.size).coerceAtLeast(0)
    if (config.hasSearchFilters() && remainingCapacity > 0) {
      val query =
        LinkedInSearchQuery(
          roleTitle = config.roleTitle,
          companyName = config.companyName,
          industry = config.industry,
          city = config.city,
          countryCode = config.countryCode,
          pageSize = remainingCapacity,
        )
      val searchRes = RealLeadExtractor.searchB2BLeadsViaProxycurl(query)
      searchRes.onSuccess { list -> collected.addAll(list) }
      searchRes.onFailure { err ->
        if (lastErrorMsg.isBlank()) lastErrorMsg = err.message.orEmpty()
      }
    }

    if (collected.isEmpty()) {
      val msg =
        lastErrorMsg.ifBlank {
          "No real contact or bio leads could be fetched right now. Check Proxycurl API key or LinkedIn URLs."
        }
      recordOutcome(context, config, 0, 0, msg, isError = true, now = now)
      if (notifyUser) {
        AutoFetchNotifier.notifyRunResult(context, "LeadX Daily Auto-Fetch", msg)
      }
      return AutoFetchOutcome(0, 0, emptyList(), msg, isError = true)
    }

    val existingLeads = dao.observeLeads().first().map { it.toDomain() }
    val freshCandidates =
      deduplicateNewCandidates(collected, existingLeads).take(config.maxLeadsPerRun)

    if (freshCandidates.isEmpty()) {
      val msg =
        "Checked ${collected.size} profile(s) — all contact & bio records are already saved (0 duplicates charged)."
      recordOutcome(context, config, collected.size, 0, msg, isError = false, now = now)
      return AutoFetchOutcome(collected.size, 0, emptyList(), msg, isError = false)
    }

    var savedCount = 0
    val stagedRemaining = mutableListOf<LinkedInCandidate>()
    var finalMessage = ""
    var isError = false

    if (config.autoSaveToWallet) {
      val targetAccount =
        config.activeLoginId.takeIf { it.isNotBlank() }?.let { dao.getAccount(it) }
          ?: dao.observeAccounts().first().firstOrNull { it.role == AccountRole.OWNER.wireValue }
          ?: dao.observeAccounts().first().firstOrNull()

      if (targetAccount == null) {
        stagedRemaining.addAll(freshCandidates)
        AutoFetchPreferences.saveStagedCandidates(context, stagedRemaining)
        finalMessage = "Fetched ${freshCandidates.size} lead(s) to preview queue (no active wallet account found)."
      } else {
        val usablePoints = (targetAccount.balance - MIN_WALLET_BALANCE).coerceAtLeast(0L)
        val affordableCount = (usablePoints / LINKEDIN_COST_PER_LEAD).toInt().coerceAtMost(freshCandidates.size)
        if (affordableCount <= 0) {
          stagedRemaining.addAll(freshCandidates)
          AutoFetchPreferences.saveStagedCandidates(context, stagedRemaining)
          finalMessage =
            "Minimum 5000 balance required, please recharge — ${freshCandidates.size} lead(s) staged in preview."
          isError = true
        } else {
          val toSave = freshCandidates.take(affordableCount)
          val overflow = freshCandidates.drop(affordableCount)
          val saveRes =
            repository.captureLinkedInLeads(
              accountId = targetAccount.loginId,
              ownerAccountId = targetAccount.ownerAccountId,
              candidates = toSave,
            )
          saveRes.fold(
            onSuccess = { saved ->
              savedCount = saved
              if (overflow.isNotEmpty()) {
                stagedRemaining.addAll(overflow)
                AutoFetchPreferences.saveStagedCandidates(context, stagedRemaining)
              }
              finalMessage =
                "Auto-fetched & saved $saved contact/bio lead${if (saved > 1) "s" else ""} (-${saved * LINKEDIN_COST_PER_LEAD} pts)." +
                  if (overflow.isNotEmpty()) " ${overflow.size} staged (reserve limit)." else ""
            },
            onFailure = { err ->
              stagedRemaining.addAll(freshCandidates)
              AutoFetchPreferences.saveStagedCandidates(context, stagedRemaining)
              finalMessage = err.message ?: "Auto-save failed; staged ${freshCandidates.size} lead(s) in preview."
              isError = true
            },
          )
        }
      }
    } else {
      stagedRemaining.addAll(freshCandidates)
      AutoFetchPreferences.saveStagedCandidates(context, stagedRemaining)
      finalMessage =
        "Auto-fetched ${freshCandidates.size} fresh contact & bio lead(s) into your preview list."
    }

    recordOutcome(context, config, freshCandidates.size, savedCount, finalMessage, isError, now)
    if (notifyUser) {
      AutoFetchNotifier.notifyRunResult(
        context = context,
        title = "LeadX Pro · Daily Auto-Fetch",
        message = finalMessage,
      )
    }

    return AutoFetchOutcome(
      fetchedCount = freshCandidates.size,
      savedCount = savedCount,
      stagedCandidates = stagedRemaining,
      statusMessage = finalMessage,
      isError = isError,
    )
  }

  private fun recordOutcome(
    context: Context,
    config: AutoFetchConfig,
    fetchedCount: Int,
    savedCount: Int,
    message: String,
    isError: Boolean,
    now: Long,
  ) {
    val updatedConfig =
      config.copy(
        lastRunAtMillis = now,
        lastRunStatus = message,
        lastFetchedCount = fetchedCount,
        totalAutoFetchedCount = config.totalAutoFetchedCount + fetchedCount,
      )
    AutoFetchPreferences.saveConfig(context, updatedConfig)
    AutoFetchPreferences.appendRunLog(
      context,
      AutoFetchRunLog(
        id = "log_${UUID.randomUUID()}",
        timestampMillis = now,
        fetchedCount = fetchedCount,
        savedCount = savedCount,
        statusMessage = message,
        isError = isError,
      ),
    )
  }
}
