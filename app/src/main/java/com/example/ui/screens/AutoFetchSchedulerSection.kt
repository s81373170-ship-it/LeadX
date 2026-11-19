package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
  fieldColors: TextFieldColors,
  onSaveConfig: (AutoFetchConfig) -> Unit,
  onRunNow: (AutoFetchConfig) -> Unit,
  onShowMessage: (String, Boolean) -> Unit,
  onOpenApiKeys: () -> Unit,
  onToggleCandidate: (String) -> Unit,
  onSelectAllCandidates: (Boolean) -> Unit,
  onClearCandidates: () -> Unit,
  onSaveSelectedLinkedIn: () -> Unit,
) {
  val context = LocalContext.current
  val hasProxycurl = remember(apiConfigVersion) { RealLeadExtractor.isProxycurlConfigured() }

  var enabled by rememberSaveable { mutableStateOf(config.enabled) }
  var intervalHours by rememberSaveable { mutableLongStateOf(config.intervalHours) }
  var maxLeads by rememberSaveable { mutableStateOf(config.maxLeadsPerRun) }
  var autoSave by rememberSaveable { mutableStateOf(config.autoSaveToWallet) }
  var roleTitle by rememberSaveable { mutableStateOf(config.roleTitle) }
  var companyName by rememberSaveable { mutableStateOf(config.companyName) }
  var industry by rememberSaveable { mutableStateOf(config.industry) }
  var city by rememberSaveable { mutableStateOf(config.city) }
  var countryCode by rememberSaveable { mutableStateOf(config.countryCode) }
  var profileUrlsText by rememberSaveable { mutableStateOf(config.profileUrlsText) }

  LaunchedEffect(config.enabled, config.lastRunAtMillis) {
    enabled = config.enabled
  }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
      // Background auto-fetch still runs cleanly even if notifications are denied
    }

  fun buildDraftConfig(overrideEnabled: Boolean = enabled): AutoFetchConfig =
    config.copy(
      enabled = overrideEnabled,
      intervalHours = intervalHours,
      maxLeadsPerRun = maxLeads,
      autoSaveToWallet = autoSave,
      roleTitle = roleTitle.trim(),
      companyName = companyName.trim(),
      industry = industry.trim(),
      city = city.trim(),
      countryCode = countryCode.trim().uppercase().ifBlank { "IN" },
      profileUrlsText = profileUrlsText.trim(),
    )

  val requestNotificationPermissionIfNeeded = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  // 1. Master Auto-Fetch Engine Card
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
          SectionLabel("Daily Auto-Fetch Engine", color = InkBlack)
          Text(
            text =
              if (enabled) "${AutoFetchInterval.fromHours(intervalHours).label} · Background Sync"
              else "Scheduled Fetching Paused",
            style = MaterialTheme.typography.titleMedium,
            color = InkBlack,
          )
        }
      }
      StatusPill(
        text = if (enabled) "ACTIVE" else "OFF",
        containerColor = if (enabled) SuccessSurface else SoftAlabaster,
        contentColor = if (enabled) SuccessInk else InkGraphite,
        dotColor = if (enabled) SuccessDot else null,
      )
    }

    Spacer(Modifier.height(10.dp))
    Text(
      text =
        "Automatically fetches fresh contact details, company roles, and career bio data every day via background WorkManager. Duplicate leads are skipped automatically.",
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
            text = "Enable Everyday Auto-Fetch",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = InkBlack,
          )
          Text(
            text = "Runs automatically on Wi-Fi/Mobile Data & notifies you",
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
                "Add a B2B search filter or LinkedIn URL below before enabling Daily Auto-Fetch.",
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
    SectionLabel("Run Frequency", color = InkMuted)
    Spacer(Modifier.height(6.dp))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      AutoFetchInterval.entries.forEach { option ->
        val selected = intervalHours == option.hours
        Surface(
          onClick = { intervalHours = option.hours },
          shape = RoundedCornerShape(8.dp),
          color = if (selected) InkBlack else SoftAlabaster,
          border = BorderStroke(1.dp, if (selected) InkBlack else HairlineBorder),
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("autofetch_freq_${option.hours}"),
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
    SectionLabel("Max New Leads / Day", color = InkMuted)
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
            text = if (autoSave) "Auto-Save to Leads & Cloud" else "Stage in Preview Queue Only",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = InkBlack,
          )
          Text(
            text =
              if (autoSave) "Saves contact + bio directly (-$LINKEDIN_COST_PER_LEAD pts/new lead; keeps ≥5,000 reserve)"
              else "Stages fresh leads in preview list (0 pts deducted until you tap Save)",
            style = MaterialTheme.typography.bodyMedium,
            color = InkGraphite,
          )
        }
        Spacer(Modifier.width(8.dp))
        MonochromeBadgeTag(text = if (autoSave) "AUTO SAVE" else "PREVIEW ONLY", inverted = autoSave)
      }
    }
  }

  Spacer(Modifier.height(14.dp))

  // 2. Daily Target Filters & Profile URLs
  LeadXCard(borderColor = StrongBorder) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SectionLabel("Daily Fetch Targets (Contact & Bio)", color = InkBlack)
      StatusPill(
        text = if (hasProxycurl) "PROXYCURL READY" else "URL + WEB MODE",
        containerColor = if (hasProxycurl) SuccessSurface else WarningSurface,
        contentColor = if (hasProxycurl) SuccessInk else WarningInk,
        dotColor = if (hasProxycurl) SuccessDot else null,
      )
    }

    Spacer(Modifier.height(8.dp))
    Text(
      text =
        "Set B2B Role/Company filters for daily Proxycurl discovery and/or paste LinkedIn Profile URLs (one per line) to pull contact & bio updates automatically.",
      style = MaterialTheme.typography.bodyMedium,
      color = InkGraphite,
    )

    Spacer(Modifier.height(12.dp))
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

    if (!hasProxycurl) {
      Spacer(Modifier.height(8.dp))
      OutlinedButton(
        onClick = onOpenApiKeys,
        border = BorderStroke(1.dp, HairlineBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(46.dp),
      ) {
        Icon(Icons.Rounded.Key, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add Proxycurl API Key for Automated B2B Discovery", color = InkBlack, fontWeight = FontWeight.SemiBold)
      }
    }

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
        Text("Save Schedule", color = InkBlack, fontWeight = FontWeight.Bold)
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
        Text("Run Now", fontWeight = FontWeight.Bold)
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
      SectionLabel("Send Daily Contact & Bio Digest", color = InkBlack)
      MonochromeBadgeTag(text = "${scopedLeads.size + stagedCandidates.size} READY")
    }
    Spacer(Modifier.height(6.dp))
    Text(
      text =
        "Share or copy a structured daily report containing Name, Role, Company, Bio Data, Email, Phone, and Profile URLs via WhatsApp, Email, or Slack.",
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
          onShowMessage("Daily Contact & Bio Digest copied to clipboard.", false)
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
