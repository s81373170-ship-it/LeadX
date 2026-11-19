package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppTab
import com.example.model.AutoFetchConfig
import com.example.model.AutoFetchInterval
import com.example.model.FlashBanner
import com.example.model.LINKEDIN_COST_PER_LEAD
import com.example.model.LeadRecord
import com.example.model.MIN_WALLET_BALANCE
import com.example.model.PointTransaction
import com.example.model.TeamAccount
import com.example.model.VEHICLE_CHECK_COST
import com.example.ui.components.EditorialDivider
import com.example.ui.components.FlashBannerView
import com.example.ui.components.LeadXCard
import com.example.ui.components.MinBalanceBadge
import com.example.ui.components.MonolithWalletCard
import com.example.ui.components.MonochromeBadgeTag
import com.example.ui.components.ReserveMeterBar
import com.example.ui.components.RolePill
import com.example.ui.components.SectionLabel
import com.example.ui.components.StatusPill
import com.example.ui.components.formatPoints
import com.example.ui.theme.SuccessDot
import com.example.ui.theme.SuccessSurface
import com.example.ui.theme.DangerInk
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkGraphite
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianMuted
import com.example.ui.theme.PaperBackground
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftAlabaster
import com.example.ui.theme.SuccessInk
import com.example.ui.theme.WarningInk
import com.example.ui.theme.WarningSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardHomeScreen(
  entryLabel: String,
  account: TeamAccount,
  allAccounts: List<TeamAccount>,
  allLeads: List<LeadRecord>,
  allTransactions: List<PointTransaction>,
  autoFetchConfig: AutoFetchConfig,
  flashBanner: FlashBanner?,
  isBusy: Boolean,
  isGoogleConnected: Boolean,
  onDismissFlash: () -> Unit,
  onSwitchTeamId: () -> Unit,
  onConnectGoogle: () -> Unit,
  onSignOutGoogle: () -> Unit,
  onSyncCloud: () -> Unit,
  onOpenApiKeys: () -> Unit,
  onTopUp: (String) -> Unit,
  onOpenFetchTab: (Int) -> Unit,
  onNavigateTab: (AppTab) -> Unit,
  onOpenTransferScreen: () -> Unit,
  onExportOwnerCsv: () -> Unit,
  onOpenShareApk: () -> Unit = {},
  onOpenHowToInstall: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var showTopUpSheet by rememberSaveable { mutableStateOf(false) }
  var topUpAmount by rememberSaveable { mutableStateOf("5000") }

  val scopedLeadsCount =
    remember(account, allLeads) {
      if (account.isOwnerRole()) {
        allLeads.count { it.ownerAccountId == account.loginId }
      } else {
        allLeads.count { it.fetchedByLoginId == account.loginId }
      }
    }

  val employeeCount =
    remember(account, allAccounts) {
      allAccounts.count { it.ownerAccountId == account.ownerAccountId && !it.isOwnerRole() }
    }

  val recentTxs =
    remember(account, allTransactions) {
      val scoped =
        if (account.isOwnerRole()) {
          allTransactions.filter { it.ownerAccountId == account.loginId }
        } else {
          allTransactions.filter { it.loginId == account.loginId }
        }
      scoped.take(6)
    }

  val fieldColors =
    OutlinedTextFieldDefaults.colors(
      focusedBorderColor = InkBlack,
      unfocusedBorderColor = HairlineBorder,
      focusedLabelColor = InkBlack,
      unfocusedLabelColor = InkMuted,
      cursorColor = InkBlack,
      focusedContainerColor = PureWhite,
      unfocusedContainerColor = PaperBackground,
    )

  if (showTopUpSheet) {
    ModalBottomSheet(
      onDismissRequest = { showTopUpSheet = false },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = PureWhite,
      contentColor = InkBlack,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            SectionLabel("Razorpay Instant Top-Up · ₹1 = 1 Pt", color = InkMuted)
            Spacer(Modifier.height(2.dp))
            Text("Recharge ${account.loginId}", style = MaterialTheme.typography.headlineMedium, color = InkBlack)
          }
          MonochromeBadgeTag(text = if (account.isOwnerRole()) "OWNER" else "EMPLOYEE")
        }
        Spacer(Modifier.height(14.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf("1000", "2500", "5000", "10000").forEach { preset ->
            val active = topUpAmount == preset
            Surface(
              onClick = { topUpAmount = preset },
              shape = RoundedCornerShape(8.dp),
              color = if (active) InkBlack else SoftAlabaster,
              border = BorderStroke(1.dp, if (active) InkBlack else HairlineBorder),
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("topup_preset_$preset"),
            ) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                  text = "₹${formatPoints(preset.toLong())}",
                  fontFamily = MonoFontFamily,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = if (active) PureWhite else InkBlack,
                )
              }
            }
          }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = topUpAmount,
          onValueChange = { topUpAmount = it.filter(Char::isDigit) },
          label = { Text("Recharge Amount (₹1 = 1 Point)") },
          colors = fieldColors,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("topup_amount_input"),
        )
        Spacer(Modifier.height(14.dp))
        Button(
          onClick = {
            onTopUp(topUpAmount)
            showTopUpSheet = false
          },
          enabled = !isBusy && topUpAmount.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("topup_submit_button"),
        ) {
          Icon(Icons.Rounded.AddCard, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = "Pay ₹${topUpAmount.ifBlank { "0" }} & Add Points",
            fontWeight = FontWeight.Bold,
          )
        }
        Spacer(Modifier.height(18.dp))
      }
    }
  }

  Box(
    modifier = modifier.fillMaxSize().background(PaperBackground),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 640.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
      // Classic Editorial Masthead with Role Entry Label, Cloud Sync, and Switch ID / Logout
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SectionLabel(entryLabel, color = InkMuted)
          Spacer(Modifier.height(2.dp))
          Text(
            text = account.fullName,
            style = MaterialTheme.typography.headlineMedium,
            color = InkBlack,
          )
          Spacer(Modifier.height(6.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            MonochromeBadgeTag(text = account.loginId, inverted = false)
            RolePill(isOwner = account.isOwnerRole(), ownerLoginId = account.ownerAccountId)
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Surface(
            onClick = onOpenApiKeys,
            enabled = !isBusy,
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.size(44.dp).minimumInteractiveComponentSize().testTag("api_keys_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = "Configure Surepass & Proxycurl API keys",
                tint = InkBlack,
                modifier = Modifier.size(20.dp),
              )
            }
          }
          Surface(
            onClick = if (isGoogleConnected) onSyncCloud else onConnectGoogle,
            enabled = !isBusy,
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.size(44.dp).minimumInteractiveComponentSize().testTag("sync_cloud_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = if (isGoogleConnected) "Sync workspace with Firestore" else "Connect Google Cloud Sync",
                tint = InkBlack,
                modifier = Modifier.size(20.dp),
              )
            }
          }
          Surface(
            onClick = onSwitchTeamId,
            enabled = !isBusy,
            shape = RoundedCornerShape(12.dp),
            color = InkBlack,
            border = BorderStroke(1.dp, InkBlack),
            modifier = Modifier.size(44.dp).minimumInteractiveComponentSize().testTag("switch_team_id_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.Badge,
                contentDescription = "Switch Owner or Employee ID",
                tint = PureWhite,
                modifier = Modifier.size(20.dp),
              )
            }
          }
          if (isGoogleConnected) {
            Surface(
              onClick = onSignOutGoogle,
              enabled = !isBusy,
              shape = RoundedCornerShape(12.dp),
              color = PureWhite,
              border = BorderStroke(1.dp, InkBlack),
              modifier = Modifier.size(44.dp).minimumInteractiveComponentSize().testTag("logout_button"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.Logout,
                  contentDescription = "Disconnect Google Cloud Sync",
                  tint = InkBlack,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        }
      }

      Spacer(Modifier.height(14.dp))
      FlashBannerView(banner = flashBanner, onDismiss = onDismissFlash)

      // Monolithic Matte-Black Wallet Card + Prominent Top-Up Button (Owner & Employee)
      MonolithWalletCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SectionLabel("Wallet Balance · ₹1 = 1 Pt", color = ObsidianMuted)
          MinBalanceBadge(meetsMin = account.meetsMinBalance())
        }
        Spacer(Modifier.height(14.dp))
        Text(
          text = "${formatPoints(account.balance)} pts",
          fontFamily = MonoFontFamily,
          style = MaterialTheme.typography.displaySmall,
          color = PureWhite,
          modifier = Modifier.testTag("wallet_balance_text"),
        )
        Spacer(Modifier.height(10.dp))
        ReserveMeterBar(balance = account.balance, minReserve = MIN_WALLET_BALANCE)
        Spacer(Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Min Reserve: ${formatPoints(MIN_WALLET_BALANCE)} pts",
            style = MaterialTheme.typography.bodyMedium,
            color = ObsidianMuted,
          )
          Text(
            text = "Usable: ${formatPoints(account.usablePoints())} pts",
            fontFamily = MonoFontFamily,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = PureWhite,
          )
        }
        Spacer(Modifier.height(16.dp))
        Button(
          onClick = { showTopUpSheet = true },
          enabled = !isBusy,
          colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = InkBlack),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("open_topup_sheet_button"),
        ) {
          Icon(Icons.Rounded.AddCard, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = if (account.isOwnerRole()) "Top-Up Wallet (Owner)" else "Top-Up Wallet (Employee)",
            fontWeight = FontWeight.Bold,
          )
        }
      }

      if (!account.meetsMinBalance()) {
        Spacer(Modifier.height(10.dp))
        Surface(
          onClick = { showTopUpSheet = true },
          shape = MaterialTheme.shapes.small,
          color = WarningSurface,
          border = BorderStroke(1.dp, WarningInk.copy(alpha = 0.3f)),
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("min_balance_warning"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = WarningInk, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
              text = "Minimum 5000 balance required, please recharge",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = WarningInk,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      Spacer(Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        MetricMiniCard(
          label = if (account.isOwnerRole()) "Team Leads" else "My Leads",
          value = scopedLeadsCount.toString(),
          caption = "Full Dossiers",
          modifier = Modifier.weight(1f),
        )
        MetricMiniCard(
          label = if (account.isOwnerRole()) "Team Employees" else "Owner ID",
          value = if (account.isOwnerRole()) employeeCount.toString() else account.ownerAccountId,
          caption = if (account.isOwnerRole()) "Separate IDs" else "Assigned Team",
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        DashboardFetchTile(
          title = "LinkedIn B2B",
          subtitle = "Proxycurl Search & URL",
          badge = "$LINKEDIN_COST_PER_LEAD PTS",
          icon = Icons.Rounded.Hub,
          testTag = "launch_linkedin_button",
          onClick = { onOpenFetchTab(0) },
          modifier = Modifier.weight(1f),
        )
        DashboardFetchTile(
          title = "Number Plate",
          subtitle = "Vahan RC Lead",
          badge = "$VEHICLE_CHECK_COST PTS",
          icon = Icons.Rounded.DirectionsCar,
          testTag = "launch_vehicle_button",
          onClick = { onOpenFetchTab(1) },
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(Modifier.height(10.dp))
      Surface(
        onClick = { onOpenFetchTab(2) },
        shape = MaterialTheme.shapes.medium,
        color = PureWhite,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("launch_autofetch_button"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = InkBlack,
              modifier = Modifier.size(36.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoMode, contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
              }
            }
            Spacer(Modifier.width(12.dp))
            Column {
              Text("Daily Auto-Fetch & Digest", style = MaterialTheme.typography.titleMedium, color = InkBlack)
              Text(
                text =
                  if (autoFetchConfig.enabled) {
                    "${AutoFetchInterval.fromHours(autoFetchConfig.intervalHours).label} · ${autoFetchConfig.totalAutoFetchedCount} fetched"
                  } else {
                    "Schedule everyday automatic contact & bio fetching"
                  },
                style = MaterialTheme.typography.bodyMedium,
                color = InkGraphite,
              )
            }
          }
          Spacer(Modifier.width(8.dp))
          StatusPill(
            text = if (autoFetchConfig.enabled) "ACTIVE" else "SETUP",
            containerColor = if (autoFetchConfig.enabled) SuccessSurface else SoftAlabaster,
            contentColor = if (autoFetchConfig.enabled) SuccessInk else InkBlack,
            dotColor = if (autoFetchConfig.enabled) SuccessDot else null,
          )
        }
      }

      if (account.isOwnerRole()) {
        Spacer(Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            onClick = onOpenTransferScreen,
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(50.dp).testTag("open_owner_transfer_button"),
          ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Transfer Points", fontWeight = FontWeight.Bold)
          }
          androidx.compose.material3.OutlinedButton(
            onClick = onExportOwnerCsv,
            enabled = !isBusy,
            border = BorderStroke(1.dp, InkBlack),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(50.dp).testTag("owner_export_csv_button"),
          ) {
            Icon(Icons.Rounded.Download, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Export CSV", color = InkBlack, fontWeight = FontWeight.Bold)
          }
        }
      }

      Spacer(Modifier.height(14.dp))

      LeadXCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            SectionLabel("Recent Transactions", color = InkBlack)
          }
          TextButton(
            onClick = { onNavigateTab(AppTab.TEAM) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("view_all_ledger_button"),
          ) {
            Text("Ledger & CSV", color = InkBlack, fontWeight = FontWeight.Bold)
          }
        }

        if (recentTxs.isEmpty()) {
          Spacer(Modifier.height(8.dp))
          Text(
            text = "No transactions recorded yet. Use Top-Up or fetch a lead to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkGraphite,
          )
        } else {
          Spacer(Modifier.height(6.dp))
          recentTxs.forEachIndexed { index, tx ->
            if (index > 0) EditorialDivider()
            DashboardTxRow(tx)
          }
        }
      }

      Spacer(Modifier.height(14.dp))

      Surface(
        shape = MaterialTheme.shapes.medium,
        color = PureWhite,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              SectionLabel(stringResource(R.string.app_build_verified_label), color = InkMuted)
              Spacer(Modifier.height(2.dp))
              Text(
                text = com.example.BuildConfig.APPLICATION_ID,
                fontFamily = MonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = InkGraphite,
              )
            }
            Spacer(Modifier.width(8.dp))
            com.example.ui.components.AppVersionBadge(
              inverted = false,
              testTag = "dashboard_app_version_badge",
            )
          }
          Spacer(Modifier.height(10.dp))
          androidx.compose.material3.OutlinedButton(
            onClick = onOpenShareApk,
            enabled = !isBusy,
            border = BorderStroke(1.dp, InkBlack),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("dashboard_share_apk_button"),
          ) {
            Icon(Icons.Rounded.Share, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Share Installable App (.apk) / Link", color = InkBlack, fontWeight = FontWeight.Bold)
          }
          Spacer(Modifier.height(6.dp))
          TextButton(
            onClick = onOpenHowToInstall,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("dashboard_how_to_install_button"),
          ) {
            Text("How to Install (.apk Unknown Sources Guide)", color = InkGraphite, fontWeight = FontWeight.SemiBold)
          }
        }
      }

      Spacer(Modifier.height(22.dp))
    }
  }
}

@Composable
private fun MetricMiniCard(
  label: String,
  value: String,
  caption: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = PureWhite,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      SectionLabel(label, color = InkMuted)
      Spacer(Modifier.height(6.dp))
      Text(
        text = value,
        fontFamily = MonoFontFamily,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = InkBlack,
      )
      Text(
        text = caption,
        style = MaterialTheme.typography.bodyMedium,
        color = InkGraphite,
      )
    }
  }
}

@Composable
private fun DashboardFetchTile(
  title: String,
  subtitle: String,
  badge: String,
  icon: ImageVector,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = MaterialTheme.shapes.medium,
    color = PureWhite,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = modifier.minimumInteractiveComponentSize().testTag(testTag),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = InkBlack,
          modifier = Modifier.size(36.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
          }
        }
        MonochromeBadgeTag(text = badge)
      }
      Spacer(Modifier.height(14.dp))
      Text(title, style = MaterialTheme.typography.titleMedium, color = InkBlack)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = InkGraphite)
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
          contentDescription = null,
          tint = InkBlack,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

@Composable
private fun DashboardTxRow(tx: PointTransaction) {
  val isCredit = tx.amount >= 0
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(tx.note, style = MaterialTheme.typography.labelLarge, color = InkBlack)
      Spacer(Modifier.height(2.dp))
      Text(
        text = tx.loginId + if (tx.counterpartyId.isNotBlank()) " · ${tx.counterpartyId}" else "",
        fontFamily = MonoFontFamily,
        style = MaterialTheme.typography.labelSmall,
        color = InkMuted,
      )
    }
    Spacer(Modifier.width(10.dp))
    Column(horizontalAlignment = Alignment.End) {
      Text(
        text = (if (isCredit) "+" else "") + formatPoints(tx.amount),
        fontFamily = MonoFontFamily,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = if (isCredit) SuccessInk else DangerInk,
      )
      Text(
        text = "Bal ${formatPoints(tx.balanceAfter)}",
        fontFamily = MonoFontFamily,
        style = MaterialTheme.typography.labelSmall,
        color = InkGraphite,
      )
    }
  }
}
