package com.example.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.example.model.AutoFetchConfig
import com.example.model.AutoFetchInterval
import com.example.model.AutoFetchRunLog
import com.example.model.LeadRecord
import com.example.model.LinkedInCandidate
import com.example.model.PeriodicSyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AutoFetchPreferences {
  private const val PREFS_NAME = "leadx_autofetch_prefs"
  private const val KEY_ENABLED = "enabled"
  private const val KEY_INTERVAL_HOURS = "interval_hours"
  private const val KEY_INTERVAL_MINUTES = "interval_minutes"
  private const val KEY_MAX_LEADS = "max_leads"
  private const val KEY_AUTO_SAVE = "auto_save"
  private const val KEY_AUTO_EXPORT_CSV = "auto_export_separate_csv"
  private const val KEY_CONTINUOUS_24H = "continuous_24h_loop"
  private const val KEY_TARGET_CLIENT = "target_client_name"
  private const val KEY_ROLE_TITLE = "role_title"
  private const val KEY_COMPANY_NAME = "company_name"
  private const val KEY_INDUSTRY = "industry"
  private const val KEY_CITY = "city"
  private const val KEY_COUNTRY_CODE = "country_code"
  private const val KEY_PROFILE_URLS = "profile_urls"
  private const val KEY_ACTIVE_LOGIN_ID = "active_login_id"
  private const val KEY_OWNER_ACCOUNT_ID = "owner_account_id"
  private const val KEY_LAST_RUN_AT = "last_run_at"
  private const val KEY_LAST_RUN_STATUS = "last_run_status"
  private const val KEY_LAST_FETCHED_COUNT = "last_fetched_count"
  private const val KEY_TOTAL_FETCHED_COUNT = "total_fetched_count"
  private const val KEY_LOGS_JSON = "run_logs_json"
  private const val KEY_STAGED_JSON = "staged_candidates_json"
  private const val KEY_PERIODIC_SYNC_ENABLED = "periodic_sync_enabled"
  private const val KEY_PERIODIC_SYNC_LAST_AT = "periodic_sync_last_at"
  private const val KEY_PERIODIC_SYNC_STATUS = "periodic_sync_status"
  private const val KEY_PERIODIC_SYNC_FIRESTORE_UPDATED = "periodic_sync_firestore_updated"
  private const val KEY_PERIODIC_SYNC_RUN_COUNT = "periodic_sync_run_count"

  private val _configFlow = MutableStateFlow(AutoFetchConfig())
  val configFlow: StateFlow<AutoFetchConfig> = _configFlow.asStateFlow()

  private val _logsFlow = MutableStateFlow<List<AutoFetchRunLog>>(emptyList())
  val logsFlow: StateFlow<List<AutoFetchRunLog>> = _logsFlow.asStateFlow()

  private val _periodicSyncFlow = MutableStateFlow(PeriodicSyncState())
  val periodicSyncFlow: StateFlow<PeriodicSyncState> = _periodicSyncFlow.asStateFlow()

  @Volatile private var initialized = false

  fun init(context: Context) {
    if (initialized) return
    synchronized(this) {
      if (!initialized) {
        _configFlow.value = readConfig(context)
        _logsFlow.value = readLogs(context)
        _periodicSyncFlow.value = readPeriodicSyncState(context)
        initialized = true
      }
    }
  }

  fun getConfig(context: Context): AutoFetchConfig {
    val cfg = readConfig(context)
    _configFlow.value = cfg
    return cfg
  }

  fun saveConfig(context: Context, config: AutoFetchConfig) {
    val resolvedMins = config.resolvedIntervalMinutes()
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
      .putBoolean(KEY_ENABLED, config.enabled)
      .putLong(KEY_INTERVAL_HOURS, (resolvedMins / 60L).coerceAtLeast(1L))
      .putLong(KEY_INTERVAL_MINUTES, resolvedMins)
      .putInt(KEY_MAX_LEADS, config.maxLeadsPerRun.coerceIn(1, 20))
      .putBoolean(KEY_AUTO_SAVE, config.autoSaveToWallet)
      .putBoolean(KEY_AUTO_EXPORT_CSV, config.autoExportSeparateCsv)
      .putBoolean(KEY_CONTINUOUS_24H, config.continuous24hLoop)
      .putString(KEY_TARGET_CLIENT, config.targetClientName.trim())
      .putString(KEY_ROLE_TITLE, config.roleTitle.trim())
      .putString(KEY_COMPANY_NAME, config.companyName.trim())
      .putString(KEY_INDUSTRY, config.industry.trim())
      .putString(KEY_CITY, config.city.trim())
      .putString(KEY_COUNTRY_CODE, config.countryCode.trim().uppercase().ifBlank { "IN" })
      .putString(KEY_PROFILE_URLS, config.profileUrlsText.trim())
      .putString(KEY_ACTIVE_LOGIN_ID, config.activeLoginId.trim())
      .putString(KEY_OWNER_ACCOUNT_ID, config.ownerAccountId.trim())
      .putLong(KEY_LAST_RUN_AT, config.lastRunAtMillis)
      .putString(KEY_LAST_RUN_STATUS, config.lastRunStatus)
      .putInt(KEY_LAST_FETCHED_COUNT, config.lastFetchedCount)
      .putInt(KEY_TOTAL_FETCHED_COUNT, config.totalAutoFetchedCount)
      .apply()
    _configFlow.value =
      config.copy(
        intervalMinutes = resolvedMins,
        intervalHours = (resolvedMins / 60L).coerceAtLeast(1L),
      )
  }

  fun appendRunLog(context: Context, log: AutoFetchRunLog) {
    val updated = (listOf(log) + readLogs(context)).take(15)
    val array = JSONArray()
    updated.forEach { entry ->
      array.put(
        JSONObject()
          .put("id", entry.id)
          .put("timestampMillis", entry.timestampMillis)
          .put("fetchedCount", entry.fetchedCount)
          .put("savedCount", entry.savedCount)
          .put("statusMessage", entry.statusMessage)
          .put("isError", entry.isError)
      )
    }
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_LOGS_JSON, array.toString())
      .apply()
    _logsFlow.value = updated
  }

  fun getLogs(context: Context): List<AutoFetchRunLog> {
    val logs = readLogs(context)
    _logsFlow.value = logs
    return logs
  }

  fun saveStagedCandidates(context: Context, candidates: List<LinkedInCandidate>) {
    val array = JSONArray()
    candidates.forEach { c ->
      array.put(
        JSONObject()
          .put("id", c.id)
          .put("name", c.name)
          .put("title", c.title)
          .put("company", c.company)
          .put("email", c.email)
          .put("phone", c.phone)
          .put("industry", c.industry)
          .put("location", c.location)
          .put("profileUrl", c.profileUrl)
          .put("captureMethod", c.captureMethod)
          .put("bio", c.bio)
          .put("selected", c.selected)
      )
    }
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_STAGED_JSON, array.toString())
      .apply()
  }

  fun consumeStagedCandidates(context: Context): List<LinkedInCandidate> {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_STAGED_JSON, "").orEmpty()
    if (raw.isBlank()) return emptyList()
    prefs.edit().remove(KEY_STAGED_JSON).apply()
    return runCatching {
      val array = JSONArray(raw)
      buildList {
        for (i in 0 until array.length()) {
          val obj = array.optJSONObject(i) ?: continue
          val name = obj.optString("name").trim()
          if (name.isBlank()) continue
          add(
            LinkedInCandidate(
              id = obj.optString("id"),
              name = name,
              title = obj.optString("title"),
              company = obj.optString("company"),
              email = obj.optString("email"),
              phone = obj.optString("phone"),
              industry = obj.optString("industry"),
              location = obj.optString("location"),
              profileUrl = obj.optString("profileUrl"),
              captureMethod = obj.optString("captureMethod"),
              bio = obj.optString("bio"),
              selected = obj.optBoolean("selected", true),
            )
          )
        }
      }
    }.getOrDefault(emptyList())
  }

  private fun readConfig(context: Context): AutoFetchConfig {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val savedHours = prefs.getLong(KEY_INTERVAL_HOURS, AutoFetchInterval.HOURLY.hours)
    val savedMinutes =
      prefs.getLong(KEY_INTERVAL_MINUTES, (savedHours * 60L).coerceAtLeast(15L)).coerceAtLeast(15L)
    return AutoFetchConfig(
      enabled = prefs.getBoolean(KEY_ENABLED, false),
      intervalHours = (savedMinutes / 60L).coerceAtLeast(1L),
      intervalMinutes = savedMinutes,
      maxLeadsPerRun = prefs.getInt(KEY_MAX_LEADS, 5).coerceIn(1, 20),
      autoSaveToWallet = prefs.getBoolean(KEY_AUTO_SAVE, true),
      autoExportSeparateCsv = prefs.getBoolean(KEY_AUTO_EXPORT_CSV, true),
      continuous24hLoop = prefs.getBoolean(KEY_CONTINUOUS_24H, true),
      targetClientName = prefs.getString(KEY_TARGET_CLIENT, "").orEmpty(),
      roleTitle = prefs.getString(KEY_ROLE_TITLE, "").orEmpty(),
      companyName = prefs.getString(KEY_COMPANY_NAME, "").orEmpty(),
      industry = prefs.getString(KEY_INDUSTRY, "").orEmpty(),
      city = prefs.getString(KEY_CITY, "").orEmpty(),
      countryCode = prefs.getString(KEY_COUNTRY_CODE, "IN").orEmpty().ifBlank { "IN" },
      profileUrlsText = prefs.getString(KEY_PROFILE_URLS, "").orEmpty(),
      activeLoginId = prefs.getString(KEY_ACTIVE_LOGIN_ID, "").orEmpty(),
      ownerAccountId = prefs.getString(KEY_OWNER_ACCOUNT_ID, "").orEmpty(),
      lastRunAtMillis = prefs.getLong(KEY_LAST_RUN_AT, 0L),
      lastRunStatus = prefs.getString(KEY_LAST_RUN_STATUS, "Not run yet").orEmpty().ifBlank { "Not run yet" },
      lastFetchedCount = prefs.getInt(KEY_LAST_FETCHED_COUNT, 0),
      totalAutoFetchedCount = prefs.getInt(KEY_TOTAL_FETCHED_COUNT, 0),
    )
  }

  fun getPeriodicSyncState(context: Context): PeriodicSyncState {
    val state = readPeriodicSyncState(context)
    _periodicSyncFlow.value = state
    return state
  }

  fun setPeriodicSyncEnabled(context: Context, enabled: Boolean) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_PERIODIC_SYNC_ENABLED, enabled).apply()
    val current = readPeriodicSyncState(context)
    _periodicSyncFlow.value =
      current.copy(
        enabled = enabled,
        workerStateLabel = if (enabled) "ENQUEUED · Every 24h" else "PAUSED",
      )
  }

  fun recordPeriodicSyncOutcome(
    context: Context,
    statusMessage: String,
    firestoreUpdated: Boolean,
    timestampMillis: Long = System.currentTimeMillis(),
  ) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val nextCount = prefs.getInt(KEY_PERIODIC_SYNC_RUN_COUNT, 0) + 1
    prefs.edit()
      .putLong(KEY_PERIODIC_SYNC_LAST_AT, timestampMillis)
      .putString(KEY_PERIODIC_SYNC_STATUS, statusMessage)
      .putBoolean(KEY_PERIODIC_SYNC_FIRESTORE_UPDATED, firestoreUpdated)
      .putInt(KEY_PERIODIC_SYNC_RUN_COUNT, nextCount)
      .apply()
    _periodicSyncFlow.value = readPeriodicSyncState(context)
  }

  private fun readPeriodicSyncState(context: Context): PeriodicSyncState {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val enabled = prefs.getBoolean(KEY_PERIODIC_SYNC_ENABLED, true)
    return PeriodicSyncState(
      enabled = enabled,
      lastRunAtMillis = prefs.getLong(KEY_PERIODIC_SYNC_LAST_AT, 0L),
      lastStatusMessage =
        prefs.getString(KEY_PERIODIC_SYNC_STATUS, "Scheduled every 24h via WorkManager")
          .orEmpty()
          .ifBlank { "Scheduled every 24h via WorkManager" },
      firestoreUpdated = prefs.getBoolean(KEY_PERIODIC_SYNC_FIRESTORE_UPDATED, false),
      totalRuns = prefs.getInt(KEY_PERIODIC_SYNC_RUN_COUNT, 0),
      workerStateLabel = if (enabled) "ENQUEUED · Every 24h" else "PAUSED",
    )
  }

  private fun readLogs(context: Context): List<AutoFetchRunLog> {
    val raw =
      context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LOGS_JSON, "")
        .orEmpty()
    if (raw.isBlank()) return emptyList()
    return runCatching {
      val array = JSONArray(raw)
      buildList {
        for (i in 0 until array.length()) {
          val obj = array.optJSONObject(i) ?: continue
          add(
            AutoFetchRunLog(
              id = obj.optString("id"),
              timestampMillis = obj.optLong("timestampMillis", 0L),
              fetchedCount = obj.optInt("fetchedCount", 0),
              savedCount = obj.optInt("savedCount", 0),
              statusMessage = obj.optString("statusMessage"),
              isError = obj.optBoolean("isError", false),
            )
          )
        }
      }
    }.getOrDefault(emptyList())
  }

  fun buildAutoFetchDigestText(
    leads: List<LeadRecord>,
    stagedCandidates: List<LinkedInCandidate> = emptyList(),
  ): String {
    val dateLabel = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
    return buildString {
      appendLine("LeadX Pro · Automated Contact & Bio Digest")
      appendLine("Generated: $dateLabel")
      appendLine("============================================")

      if (leads.isEmpty() && stagedCandidates.isEmpty()) {
        appendLine("No captured leads available yet.")
        return@buildString
      }

      if (stagedCandidates.isNotEmpty()) {
        appendLine("\n[Queued / Live Preview Contacts (${stagedCandidates.size})]")
        stagedCandidates.forEachIndexed { idx, c ->
          appendLine("${idx + 1}. ${c.name}")
          if (c.title.isNotBlank() || c.company.isNotBlank()) {
            appendLine("   Role/Company: ${listOf(c.title, c.company).filter { it.isNotBlank() }.joinToString(" @ ")}")
          }
          if (c.email.isNotBlank()) appendLine("   Email: ${c.email}")
          if (c.phone.isNotBlank()) appendLine("   Phone: ${c.phone}")
          if (c.industry.isNotBlank()) appendLine("   Industry: ${c.industry}")
          if (c.location.isNotBlank()) appendLine("   Location: ${c.location}")
          if (c.bio.isNotBlank()) appendLine("   Bio Data: ${c.bio}")
          if (c.profileUrl.isNotBlank()) appendLine("   Profile: ${c.profileUrl}")
        }
      }

      if (leads.isNotEmpty()) {
        val recent = leads.take(25)
        appendLine("\n[Saved Lead Records (${recent.size})]")
        recent.forEachIndexed { idx, l ->
          appendLine("${idx + 1}. ${l.name} [${l.sourceEnum().label}]")
          if (l.title.isNotBlank() || l.company.isNotBlank()) {
            appendLine("   Role/Company: ${listOf(l.title, l.company).filter { it.isNotBlank() }.joinToString(" · ")}")
          }
          if (l.email.isNotBlank()) appendLine("   Email: ${l.email}")
          if (l.phone.isNotBlank()) appendLine("   Phone: ${l.phone}")
          if (l.industry.isNotBlank()) appendLine("   Industry/Info: ${l.industry}")
          if (l.location.isNotBlank()) appendLine("   Location: ${l.location}")
          if (l.vehicleDetails.isNotBlank()) appendLine("   Bio / Details: ${l.vehicleDetails}")
          if (l.plateNumber.isNotBlank()) appendLine("   Vehicle Plate: ${l.plateNumber}")
          if (l.profileUrl.isNotBlank()) appendLine("   URL: ${l.profileUrl}")
        }
      }
    }.trim()
  }

  fun copyDigestToClipboard(context: Context, digest: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("LeadX Pro Daily Digest", digest))
  }

  fun shareDigest(context: Context, digest: String): Boolean {
    return runCatching {
      val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_SUBJECT, "LeadX Pro · Daily Contact & Bio Digest")
          putExtra(Intent.EXTRA_TEXT, digest)
        }
      val chooser = Intent.createChooser(shareIntent, "Send Daily Lead & Bio Digest").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
      true
    }.getOrDefault(false)
  }
}
