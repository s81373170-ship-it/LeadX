package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.AutoFetchPreferences
import com.example.data.RealLeadExtractor
import com.example.model.AutoFetchConfig
import com.example.model.AutoFetchInterval
import com.example.model.AutoFetchRunLog
import com.example.model.LINKEDIN_COST_PER_LEAD
import com.example.model.LeadRecord
import com.example.model.LinkedInCandidate
import com.example.model.PeriodicSyncState
import com.example.ui.components.EditorialDivider
import com.example.ui.components.LeadXCard
import com.example.ui.components.MonoBadge
import com.example.ui.components.MonochromeBadgeTag
import com.example.ui.components.SectionLabel
import com.example.ui.components.StatusPill
import com.example.ui.theme.DangerInk
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkGraphite
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftAlabaster
import com.example.ui.theme.StrongBorder
import com.example.ui.theme.SuccessDot
import com.example.ui.theme.SuccessInk
import com.example.ui.theme.SuccessSurface
import com.example.ui.theme.WarningInk
import com.example.ui.theme.WarningSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoFetchSchedulerSection(
  config: AutoFetchConfig,
  runLogs: List<AutoFetchRunLog>,
  scopedLeads: List<LeadRecord>,
  stagedCandidates: List<LinkedInCandidate>,
  isBusy: Boolean,
  apiConfigVersion: Int,
  csvBatchesCount: Int = 0,
  continuousLoopActive: Boolean = false,
  periodicSyncState: PeriodicSyncState = PeriodicSyncState(),
  fieldColors: TextFieldColors,
  onSaveConfig: (AutoFetchConfig) -> Unit,
  onRunNow: (AutoFetchConfig) -> Unit,
  onTogglePeriodicFirestoreSync: (Boolean) -> Unit = {},
  onRunPeriodicFirestoreSyncNow: () -> Unit = {},
  onShowMessage: (String, Boolean) -> Unit,
  onOpenApiKeys: () -> Unit,
  onToggleCandidate: (String) -> Unit,
  onSelectAllCandidates: (Boolean) -> Unit,
  onClearCandidates: () -> Unit,
  onSaveSelectedLinkedIn: (String) -> Unit,
) {
  val context = LocalContext.current

  var enabled by rememberSaveable { mutableStateOf(config.enabled) }
  var intervalMinutes by rememberSaveable { mutableLongStateOf(config.resolvedIntervalMinutes()) }
  var maxLeads by rememberSaveable { mutableStateOf(config.maxLeadsPerRun) }
  var autoSave by rememberSaveable { mutableStateOf(config.autoSaveToWallet) }
  var autoExportCsv by rememberSaveable { mutableStateOf(config.autoExportSeparateCsv) }
  var continuous24h by rememberSaveable { mutableStateOf(config.continuous24hLoop) }
  var targetClientName by rememberSaveable { mutableStateOf(config.targetClientName) }
  var roleTitle by rememberSaveable { mutableStateOf(config.roleTitle) }
  var companyName by rememberSaveable { mutableStateOf(config.companyName) }
  var industry by rememberSaveable { mutableStateOf(config.industry) }
  var city by rememberSaveable { mutableStateOf(config.city) }
  var countryCode by rememberSaveable { mutableStateOf(config.countryCode) }
  var profileUrlsText by rememberSaveable { mutableStateOf(config.profileUrlsText) }

  LaunchedEffect(config.enabled, config.lastRunAtMillis) {
    enabled = config.enabled
  }

  val isInspection = LocalInspectionMode.current
  val hasActivityRegistry = LocalActivityResultRegistryOwner.current != null
  val notificationPermissionLauncher =
    if (!isInspection && hasActivityRegistry) {
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        // Background auto-fetch still runs cleanly even if notifications are denied
      }
    } else {
      null
    }

  fun buildDraftConfig(overrideEnabled: Boolean = enabled): AutoFetchConfig =
    config.copy(
      enabled = overrideEnabled,
      intervalHours = (intervalMinutes / 60L).coerceAtLeast(1L),
      intervalMinutes = intervalMinutes,
      maxLeadsPerRun = maxLeads,
      autoSaveToWallet = autoSave,
      autoExportSeparateCsv = autoExportCsv,
      continuous24hLoop = continuous24h,
      targetClientName = targetClientName.trim(),
      roleTitle = roleTitle.trim(),
      companyName = companyName.trim(),
      industry = industry.trim(),
      city = city.trim(),
      countryCode = countryCode.trim().uppercase().ifBlank { "IN" },
      profileUrlsText = profileUrlsText.trim(),
    )

  val requestNotificationPermissionIfNeeded = {
    if (!isInspection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notificationPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  // 1. Master 24h Continuous Auto-Fetch Engine Card
  LeadXCard(borderColor = StrongBorder) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Icon(
          Icons.Rounded.AutoMode,
          contentDescription = null,
          tint = InkBlack,
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
          SectionLabel("24h Continuous Auto-Fetch (No API)", color = InkBlack)
          Text(
            text =
              if (enabled) "${AutoFetchInterval.fromMinutes(intervalMinutes).label} · 24h Sync"
              else "24h Lead Generation Paused",
            style = MaterialTheme.typography.titleMedium,
            color = InkBlack,
          )
        }
      }
      StatusPill(
        text = if (enabled) (if (continuousLoopActive) "24H LIVE" else "ACTIVE") else "OFF",
        containerColor = if (enabled) SuccessSurface else SoftAlabaster,
        contentColor = if (enabled) SuccessInk else InkGraphite,
        dotColor = if (enabled) SuccessDot else null,
      )
    }

    Spacer(Modifier.height(10.dp))
    Text(
      text =
        "Continuously fetches real LinkedIn leads without any paid API key, saves them to your database, and auto-generates separate Client CSV files every cycle for 24 hours.",
      style = MaterialTheme.typography.bodyMedium,
      color = InkGraphite,
    )

    Spacer(Modifier.height(12.dp))
    Surface(
      shape = MaterialTheme.shapes.small,
      color = SoftAlabaster,
      border = BorderStroke(1.dp, HairlineBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Enable 24h Continuous Auto-Fetch",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = InkBlack,
          )
          Text(
            text = "Runs automatically every cycle on Wi-Fi/Data & saves separate CSVs",
            style = MaterialTheme.typography.bodyMedium,
            color = InkGraphite,
          )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
          checked = enabled,
          onCheckedChange = { next ->
            val draft = buildDraftConfig(overrideEnabled = next)
            if (next && !draft.hasValidTarget()) {
              onShowMessage(
                "Add a B2B search filter or LinkedIn URL below before enabling 24h Auto-Fetch.",
                true,
              )
              return@Switch
            }
            if (next) requestNotificationPermissionIfNeeded()
            enabled = next
            onSaveConfig(draft)
          },
          colors =
            SwitchDefaults.colors(
              checkedThumbColor = PureWhite,
              checkedTrackColor = InkBlack,
              uncheckedThumbColor = InkBlack,
              uncheckedTrackColor = PureWhite,
              uncheckedBorderColor = InkBlack,
            ),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("autofetch_enable_switch"),
        )
      }
    }

    Spacer(Modifier.height(14.dp))
    SectionLabel("24h Run Frequency", color = InkMuted)
    Spacer(Modifier.height(6.dp))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      AutoFetchInterval.entries.forEach { option ->
        val selected = intervalMinutes == option.minutes
        Surface(
          onClick = { intervalMinutes = option.minutes },
          shape = RoundedCornerShape(8.dp),
          color = if (selected) InkBlack else SoftAlabaster,
          border = BorderStroke(1.dp, if (selected) InkBlack else HairlineBorder),
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("autofetch_freq_${option.minutes}"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.Schedule,
              contentDescription = null,
              tint = if (selected) PureWhite else InkBlack,
              modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = option.label,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = if (selected) PureWhite else InkBlack,
            )
          }
        }
      }
    }

    Spacer(Modifier.height(12.dp))
    SectionLabel("Max New Leads / Cycle", color = InkMuted)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(3, 5, 10).forEach { count ->
        val selected = maxLeads == count
        Surface(
          onClick = { maxLeads = count },
          shape = RoundedCornerShape(8.dp),
          color = if (selected) InkBlack else SoftAlabaster,
          border = BorderStroke(1.dp, if (selected) InkBlack else HairlineBorder),
          modifier =
            Modifier.weight(1f).minimumInteractiveComponentSize().testTag("autofetch_max_$count"),
        ) {
          Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "$count Leads/Run",
              fontFamily = MonoFontFamily,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = if (selected) PureWhite else InkBlack,
            )
          }
        }
      }
    }

    Spacer(Modifier.height(12.dp))
    Surface(
      onClick = { autoSave = !autoSave },
      shape = MaterialTheme.shapes.small,
      color = PureWhite,
      border = BorderStroke(1.dp, HairlineBorder),
      modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("autofetch_autosave_toggle"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (autoSave) "Auto-Save to Database & Cloud" else "Stage in Preview Queue Only",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = InkBlack,
          )
          Text(
            text =
              if (autoSave) "Stores leads automatically (-$LINKEDIN_COST_PER_LEAD pts/new lead; keeps ≥5,000 reserve)"
              else "Stages fresh leads in preview list (0 pts deducted until you tap Save)",
            style = MaterialTheme.typography.bodyMedium,
            color = InkGraphite,
          )
        }
        Spacer(Modifier.width(8.dp))
        MonochromeBadgeTag(text = if (autoSave) "AUTO DB" else "PREVIEW ONLY", inverted = autoSave)
      }
    }

    Spacer(Modifier.height(8.dp))
    Surface(
      onClick = { autoExportCsv = !autoExportCsv },
      shape = MaterialTheme.shapes.small,
      color = PureWhite,
      border = BorderStroke(1.dp, HairlineBorder),
      modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("autofetch_auto_csv_toggle"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Auto-Generate Separate Client .csv",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = InkBlack,
          )
          Text(
            text = "Creates a separate downloadable CSV batch each cycle ready for your client",
            style = MaterialTheme.typography.bodyMedium,
            color = InkGraphite,
          )
        }
        Spacer(Modifier.width(8.dp))
        MonochromeBadgeTag(text = if (autoExportCsv) "AUTO CSV" else "CSV OFF", inverted = autoExportCsv)
      }
    }

    Spacer(Modifier.height(8.dp))
    Surface(
      onClick = { continuous24h = !continuous24h },
      shape = MaterialTheme.shapes.small,
      color = PureWhite,
      border = BorderStroke(1.dp, HairlineBorder),
      modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("autofetch_continuous_loop_toggle"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "24h Continuous Active Loop",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = InkBlack,
          )
          Text(
            text = "Keeps generating new leads automatically in both foreground loop & background WorkManager",
            style = MaterialTheme.typography.bodyMedium,
            color = InkGraphite,
          )
        }
        Spacer(Modifier.width(8.dp))
        MonochromeBadgeTag(text = if (continuous24h) "24H LOOP" else "BG ONLY", inverted = continuous24h)
      }
    }
  }

  Spacer(Modifier.height(14.dp))

  // 1B. Dedicated 24-Hour WorkManager Firestore Sync Job Card (Simulated Fetch + Cloud Update)
  LeadXCard(
    borderColor = StrongBorder,
    modifier = Modifier.testTag("workmanager_sync_card"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        SectionLabel("WorkManager · 24h Firestore Sync", color = InkBlack)
        Text(
          text = periodicSyncState.workerStateLabel,
          style = MaterialTheme.typography.titleMedium,
          color = InkBlack,
        )
      }
      StatusPill(
        text = if (periodicSyncState.enabled) "SIMULATION · 24H" else "PAUSED",
        containerColor = if (periodicSyncState.enabled) WarningSurface else SoftAlabaster,
        contentColor = if (periodicSyncState.enabled) WarningInk else InkGraphite,
      )
    }

    Spacer(Modifier.height(8.dp))
    Text(
      text =
        "Background WorkManager job runs every 24 hours on network connectivity to simulate periodic data fetching and update Cloud Firestore (users & leads collections).",
      style = MaterialTheme.typography.bodyMedium,
      color = InkGraphite,
    )

    Spacer(Modifier.height(10.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text =
            if (periodicSyncState.lastRunAtMillis > 0L) {
              "Last Run: ${formatTimestamp(periodicSyncState.lastRunAtMillis)} · Runs: ${periodicSyncState.totalRuns}"
            } else {
              "Runs: ${periodicSyncState.totalRuns} · Waiting next 24h cycle"
            },
          fontFamily = MonoFontFamily,
          style = MaterialTheme.typography.labelSmall,
          color = InkBlack,
        )
        Spacer(Modifier.height(2.dp))
        Text(
          text = periodicSyncState.lastStatusMessage,
          style = MaterialTheme.typography.bodyMedium,
          color = InkGraphite,
        )
      }
      Spacer(Modifier.width(10.dp))
      Switch(
        checked = periodicSyncState.enabled,
        onCheckedChange = onTogglePeriodicFirestoreSync,
        colors =
          SwitchDefaults.colors(
            checkedThumbColor = PureWhite,
            checkedTrackColor = InkBlack,
            uncheckedThumbColor = InkBlack,
            uncheckedTrackColor = PureWhite,
            uncheckedBorderColor = InkBlack,
          ),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("workmanager_24h_switch"),
      )
    }

    Spacer(Modifier.height(10.dp))
    Button(
      onClick = onRunPeriodicFirestoreSyncNow,
      enabled = !isBusy,
      colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("run_workmanager_sync_now_button"),
    ) {
      Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text("Run 24h Firestore Sync Job Now", fontWeight = FontWeight.Bold)
    }
  }

  Spacer(Modifier.height(14.dp))

  // 2. Client CSV & No-API Target Filters
  LeadXCard(borderColor = StrongBorder) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SectionLabel("Client Target Filters (No API)", color = InkBlack)
      StatusPill(
        text = "NO API NEEDED",
        containerColor = SuccessSurface,
        contentColor = SuccessInk,
        dotColor = SuccessDot,
      )
    }

    Spacer(Modifier.height(8.dp))
    Text(
      text =
        "Set your client name and B2B filters or LinkedIn URLs. LeadX fetches leads automatically without any API key and builds separate CSVs for the client.",
      style = MaterialTheme.typography.bodyMedium,
      color = InkGraphite,
    )

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = targetClientName,
      onValueChange = { targetClientName = it },
      label = { Text("Client / Customer Name for CSV (e.g. Acme Client)") },
      colors = fieldColors,
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("autofetch_client_input"),
    )

    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
      value = roleTitle,
      onValueChange = { roleTitle = it },
      label = { Text("Target Role / Title (e.g. Founder, VP Sales)") },
      leadingIcon = { Icon(Icons.Rounded.WorkOutline, contentDescription = null) },
      colors = fieldColors,
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("autofetch_role_input"),
    )

    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
      value = companyName,
      onValueChange = { companyName = it },
      label = { Text("Company Name (optional)") },
      leadingIcon = { Icon(Icons.Rounded.Business, contentDescription = null) },
      colors = fieldColors,
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("autofetch_company_input"),
    )

    Spacer(Modifier.height(10.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedTextField(
        value = industry,
        onValueChange = { industry = it },
        label = { Text("Industry") },
        colors = fieldColors,
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("autofetch_industry_input"),
      )
      OutlinedTextField(
        value = city,
        onValueChange = { city = it },
        label = { Text("City") },
        leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
        colors = fieldColors,
        singleLine = true,
        modifier = Modifier.weight(1f).testTag("autofetch_city_input"),
      )
    }

    Spacer(Modifier.height(10.dp))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      listOf("IN", "US", "GB", "SG", "AE").forEach { code ->
        val selected = countryCode == code
        Surface(
          onClick = { countryCode = code },
          shape = RoundedCornerShape(8.dp),
          color = if (selected) InkBlack else SoftAlabaster,
          border = BorderStroke(1.dp, if (selected) InkBlack else HairlineBorder),
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Text(
            text = code,
            fontFamily = MonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) PureWhite else InkBlack,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }
      }
    }

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = profileUrlsText,
      onValueChange = { profileUrlsText = it },
      label = { Text("Watchlist LinkedIn URLs (1 per line, optional)") },
      leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
      colors = fieldColors,
      minLines = 2,
      maxLines = 4,
      modifier = Modifier.fillMaxWidth().testTag("autofetch_urls_input"),
    )

    Spacer(Modifier.height(14.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedButton(
        onClick = {
          if (enabled) requestNotificationPermissionIfNeeded()
          onSaveConfig(buildDraftConfig())
        },
        enabled = !isBusy,
        border = BorderStroke(1.dp, InkBlack),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).height(50.dp).testTag("save_autofetch_config_button"),
      ) {
        Icon(Icons.Rounded.Save, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Save 24h Schedule", color = InkBlack, fontWeight = FontWeight.Bold)
      }
      Button(
        onClick = {
          requestNotificationPermissionIfNeeded()
          onRunNow(buildDraftConfig())
        },
        enabled = !isBusy,
        colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).height(50.dp).testTag("run_autofetch_now_button"),
      ) {
        if (isBusy) {
          CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
        } else {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
        }
        Text("Fetch Now", fontWeight = FontWeight.Bold)
      }
    }
  }

  Spacer(Modifier.height(14.dp))

  // 3. Automated Contact & Bio Digest Sender
  LeadXCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SectionLabel("Client Lead Digest ($csvBatchesCount CSVs)", color = InkBlack)
      MonochromeBadgeTag(text = "${scopedLeads.size + stagedCandidates.size} READY")
    }
    Spacer(Modifier.height(6.dp))
    Text(
      text =
        "Share or copy a structured report containing Name, Role, Company, Bio Data, Email, Phone, and Profile URLs, or open Team & Ledger -> Client CSVs to send separate .csv files directly.",
      style = MaterialTheme.typography.bodyMedium,
      color = InkGraphite,
    )
    Spacer(Modifier.height(12.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedButton(
        onClick = {
          val digest = AutoFetchPreferences.buildAutoFetchDigestText(scopedLeads, stagedCandidates)
          AutoFetchPreferences.copyDigestToClipboard(context, digest)
          onShowMessage("Client Contact & Bio Digest copied to clipboard.", false)
        },
        border = BorderStroke(1.dp, InkBlack),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).height(48.dp).testTag("copy_autofetch_digest_button"),
      ) {
        Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Copy Digest", color = InkBlack, fontWeight = FontWeight.Bold)
      }
      Button(
        onClick = {
          val digest = AutoFetchPreferences.buildAutoFetchDigestText(scopedLeads, stagedCandidates)
          val opened = AutoFetchPreferences.shareDigest(context, digest)
          if (!opened) {
            AutoFetchPreferences.copyDigestToClipboard(context, digest)
            onShowMessage("Share sheet unavailable — digest copied to clipboard.", false)
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).height(48.dp).testTag("share_autofetch_digest_button"),
      ) {
        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Send Digest", fontWeight = FontWeight.Bold)
      }
    }
  }

  if (stagedCandidates.isNotEmpty()) {
    Spacer(Modifier.height(14.dp))
    StructuredLeadResultsList(
      candidates = stagedCandidates,
      isBusy = isBusy,
      onToggleCandidate = onToggleCandidate,
      onSelectAll = onSelectAllCandidates,
      onClearCandidates = onClearCandidates,
      onSaveSelected = onSaveSelectedLinkedIn,
    )
  }

  Spacer(Modifier.height(14.dp))

  // 4. Auto-Fetch Run History
  LeadXCard {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.History, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        SectionLabel("Auto-Fetch Run Logs (${runLogs.size})", color = InkBlack)
      }
      MonoBadge("Total: ${config.totalAutoFetchedCount}", color = InkGraphite)
    }

    Spacer(Modifier.height(8.dp))
    Text(
      text =
        if (config.lastRunAtMillis == 0L) "No automated runs executed yet."
        else "Last run ${formatTimestamp(config.lastRunAtMillis)}: ${config.lastRunStatus}",
      style = MaterialTheme.typography.bodyMedium,
      color = InkBlack,
    )

    if (runLogs.isNotEmpty()) {
      Spacer(Modifier.height(10.dp))
      runLogs.take(6).forEachIndexed { idx, entry ->
        if (idx > 0) EditorialDivider()
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Icon(
            imageVector = if (entry.isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (entry.isError) DangerInk else SuccessInk,
            modifier = Modifier.size(16.dp).padding(top = 2.dp),
          )
          Spacer(Modifier.width(8.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = entry.statusMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = InkBlack,
            )
            Spacer(Modifier.height(2.dp))
            Text(
              text = "${formatTimestamp(entry.timestampMillis)} · Fetched ${entry.fetchedCount} · Saved ${entry.savedCount}",
              fontFamily = MonoFontFamily,
              style = MaterialTheme.typography.labelSmall,
              color = InkMuted,
            )
          }
        }
      }
    }
  }
}

private fun formatTimestamp(millis: Long): String {
  if (millis <= 0L) return "—"
  return SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(millis))
}

@Preview(showBackground = true, name = "24h Auto-Fetch Scheduler Preview")
@Composable
private fun AutoFetchSchedulerSectionPreview() {
  com.example.ui.theme.LeadXTheme {
    Column(modifier = Modifier.padding(16.dp)) {
      AutoFetchSchedulerSection(
        config = AutoFetchConfig(enabled = true, targetClientName = "Acme Corp", roleTitle = "VP Sales"),
        runLogs = emptyList(),
        scopedLeads = emptyList(),
        stagedCandidates = emptyList(),
        isBusy = false,
        apiConfigVersion = 0,
        csvBatchesCount = 2,
        continuousLoopActive = true,
        periodicSyncState = PeriodicSyncState(enabled = true, totalRuns = 3),
        fieldColors = OutlinedTextFieldDefaults.colors(),
        onSaveConfig = {},
        onRunNow = {},
        onShowMessage = { _, _ -> },
        onOpenApiKeys = {},
        onToggleCandidate = {},
        onSelectAllCandidates = {},
        onClearCandidates = {},
        onSaveSelectedLinkedIn = {},
      )
    }
  }
}
