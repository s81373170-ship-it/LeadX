package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.model.FlashBanner
import com.example.model.MIN_WALLET_BALANCE
import com.example.model.PointTransaction
import com.example.model.TeamAccount
import com.example.model.TxType
import com.example.ui.components.EditorialDivider
import com.example.ui.components.FlashBannerView
import com.example.ui.components.LeadXCard
import com.example.ui.components.MonoBadge
import com.example.ui.components.MonolithWalletCard
import com.example.ui.components.MonochromeBadgeTag
import com.example.ui.components.SectionLabel
import com.example.ui.components.formatPoints
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
import com.example.ui.theme.StrongBorder
import com.example.ui.theme.SuccessInk
import com.example.ui.theme.WarningInk
import com.example.ui.theme.WarningSurface

@Composable
fun OwnerPointTransferScreen(
  ownerAccount: TeamAccount,
  teamEmployees: List<TeamAccount>,
  allTransactions: List<PointTransaction>,
  flashBanner: FlashBanner?,
  isBusy: Boolean,
  onTransferPoints: (String, String) -> Unit,
  onDismissFlash: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!LocalInspectionMode.current && LocalOnBackPressedDispatcherOwner.current != null) {
    BackHandler { onBack() }
  }

  var recipientId by rememberSaveable { mutableStateOf(teamEmployees.firstOrNull()?.loginId.orEmpty()) }
  var amountText by rememberSaveable { mutableStateOf("2000") }

  LaunchedEffect(teamEmployees) {
    if (recipientId.isBlank() && teamEmployees.isNotEmpty()) {
      recipientId = teamEmployees.first().loginId
    }
  }

  val transferLogs =
    remember(ownerAccount, allTransactions) {
      allTransactions.filter {
        it.ownerAccountId == ownerAccount.loginId &&
          (it.typeEnum() == TxType.TRANSFER_OUT || it.typeEnum() == TxType.TRANSFER_IN)
      }
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            onClick = onBack,
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.size(44.dp).minimumInteractiveComponentSize().testTag("transfer_back_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Owner Dashboard", tint = InkBlack)
            }
          }
          Spacer(Modifier.width(12.dp))
          Column {
            Text("Transfer Points", style = MaterialTheme.typography.headlineMedium, color = InkBlack)
            Text("Owner ID Sharing · Firestore Transactions", style = MaterialTheme.typography.bodyMedium, color = InkGraphite)
          }
        }
        MonochromeBadgeTag(text = ownerAccount.loginId, inverted = true)
      }

      Spacer(Modifier.height(14.dp))
      FlashBannerView(banner = flashBanner, onDismiss = onDismissFlash)

      MonolithWalletCard {
        SectionLabel("Available Owner Transfer Balance", color = ObsidianMuted)
        Spacer(Modifier.height(8.dp))
        Text(
          text = "${formatPoints(ownerAccount.balance)} pts",
          fontFamily = MonoFontFamily,
          style = MaterialTheme.typography.displaySmall,
          color = PureWhite,
        )
        Spacer(Modifier.height(6.dp))
        Text(
          text = "Shareable now: ${formatPoints(ownerAccount.usablePoints())} pts (${formatPoints(MIN_WALLET_BALANCE)} pts reserve locked)",
          style = MaterialTheme.typography.bodyMedium,
          color = ObsidianMuted,
        )
      }

      if (!ownerAccount.meetsMinBalance() || ownerAccount.usablePoints() <= 0L) {
        Spacer(Modifier.height(10.dp))
        Surface(
          shape = MaterialTheme.shapes.small,
          color = WarningSurface,
          border = BorderStroke(1.dp, WarningInk.copy(alpha = 0.3f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = WarningInk, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
              text = "Minimum 5000 balance required, please recharge before transferring points.",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = WarningInk,
            )
          }
        }
      }

      Spacer(Modifier.height(14.dp))

      LeadXCard(borderColor = StrongBorder) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.Share, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          SectionLabel("Select Registered Employee ID", color = InkBlack)
        }
        Spacer(Modifier.height(10.dp))

        if (teamEmployees.isNotEmpty()) {
          teamEmployees.forEachIndexed { idx, emp ->
            if (idx > 0) EditorialDivider()
            val selected = recipientId.equals(emp.loginId, ignoreCase = true)
            Surface(
              onClick = { recipientId = emp.loginId },
              shape = RoundedCornerShape(10.dp),
              color = if (selected) InkBlack else PureWhite,
              border = BorderStroke(1.dp, if (selected) InkBlack else HairlineBorder),
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("transfer_employee_row_${emp.loginId}"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  MonoBadge(emp.loginId, color = if (selected) PureWhite else InkBlack)
                  Text(
                    text = emp.fullName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) ObsidianMuted else InkGraphite,
                  )
                }
                Text(
                  text = "${formatPoints(emp.balance)} pts",
                  fontFamily = MonoFontFamily,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = if (selected) PureWhite else InkBlack,
                )
              }
            }
          }
          Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
          value = recipientId,
          onValueChange = { recipientId = it.uppercase() },
          label = { Text("Registered Employee ID (e.g. EMP001)") },
          leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
          colors = fieldColors,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("owner_transfer_employee_id_input"),
        )

        Spacer(Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf("500", "1000", "2000", "5000").forEach { preset ->
            val picked = amountText == preset
            Surface(
              onClick = { amountText = preset },
              shape = RoundedCornerShape(8.dp),
              color = if (picked) InkBlack else SoftAlabaster,
              border = BorderStroke(1.dp, if (picked) InkBlack else HairlineBorder),
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
            ) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                Text(
                  text = formatPoints(preset.toLong()),
                  fontFamily = MonoFontFamily,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = if (picked) PureWhite else InkBlack,
                )
              }
            }
          }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it.filter(Char::isDigit) },
          label = { Text("Points to Transfer") },
          colors = fieldColors,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("owner_transfer_amount_input"),
        )

        Spacer(Modifier.height(14.dp))

        Button(
          onClick = { onTransferPoints(recipientId, amountText) },
          enabled = !isBusy && recipientId.isNotBlank() && amountText.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("owner_transfer_submit_button"),
        ) {
          if (isBusy) {
            CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
          } else {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
          }
          Text("Transfer Points & Log Transaction", fontWeight = FontWeight.Bold)
        }
      }

      Spacer(Modifier.height(14.dp))

      LeadXCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          SectionLabel("Firestore 'transactions' Transfer Log (${transferLogs.size})", color = InkBlack)
        }
        Spacer(Modifier.height(8.dp))
        if (transferLogs.isEmpty()) {
          Text(
            text = "No point transfers recorded yet. Completed transfers appear here and sync to Firestore 'transactions'.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkGraphite,
          )
        } else {
          transferLogs.take(12).forEachIndexed { idx, tx ->
            if (idx > 0) EditorialDivider()
            val isCredit = tx.amount >= 0
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(tx.note, style = MaterialTheme.typography.labelLarge, color = InkBlack)
                Text(
                  text = "${tx.loginId} ↔ ${tx.counterpartyId}",
                  fontFamily = MonoFontFamily,
                  style = MaterialTheme.typography.labelSmall,
                  color = InkMuted,
                )
              }
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
        }
      }

      Spacer(Modifier.height(24.dp))
    }
  }
}

@Preview(showBackground = true, showSystemUi = true, name = "Owner Point Transfer Preview")
@Composable
private fun OwnerPointTransferScreenPreview() {
  com.example.ui.theme.LeadXTheme {
    OwnerPointTransferScreen(
      ownerAccount = TeamAccount(loginId = "OWN1001", fullName = "Aarav Mehta", balance = 25_000L),
      teamEmployees =
        listOf(
          TeamAccount(
            loginId = "EMP001",
            fullName = "Riya Sharma",
            role = "employee",
            ownerAccountId = "OWN1001",
            balance = 5_000L,
          ),
        ),
      allTransactions = emptyList(),
      flashBanner = null,
      isBusy = false,
      onTransferPoints = { _, _ -> },
      onDismissFlash = {},
      onBack = {},
    )
  }
}
