package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CsvExporter
import com.example.model.FlashBanner
import com.example.model.LeadRecord
import com.example.model.PointTransaction
import com.example.model.TeamAccount
import com.example.ui.components.EditorialDivider
import com.example.ui.components.FlashBannerView
import com.example.ui.components.LeadXCard
import com.example.ui.components.MonoBadge
import com.example.ui.components.MonochromeBadgeTag
import com.example.ui.components.SectionLabel
import com.example.ui.components.SegmentedMonochromeTabs
import com.example.ui.components.StatusPill
import com.example.ui.components.formatPoints
import com.example.ui.theme.DangerInk
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkGraphite
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PaperBackground
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftAlabaster
import com.example.ui.theme.StrongBorder
import com.example.ui.theme.SuccessDot
import com.example.ui.theme.SuccessInk
import com.example.ui.theme.SuccessSurface

@Composable
fun TeamAndRecordsScreen(
  account: TeamAccount,
  allAccounts: List<TeamAccount>,
  allLeads: List<LeadRecord>,
  allTransactions: List<PointTransaction>,
  flashBanner: FlashBanner?,
  isBusy: Boolean,
  onSharePoints: (String, String) -> Unit,
  onCreateEmployee: (String, String, String, () -> Unit) -> Unit,
  onCreateOwner: (String, String, String, () -> Unit) -> Unit,
  onPreviewCsv: (String) -> Unit = {},
  onOpenShareApk: () -> Unit = {},
  onDismissFlash: () -> Unit,
  onBackDashboard: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler { onBackDashboard() }

  val context = LocalContext.current
  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var localBanner by remember { mutableStateOf<FlashBanner?>(null) }

  var shareTargetId by rememberSaveable { mutableStateOf("") }
  var shareAmount by rememberSaveable { mutableStateOf("") }
  var newId by rememberSaveable { mutableStateOf("") }
  var newName by rememberSaveable { mutableStateOf("") }
  var newPassword by rememberSaveable { mutableStateOf("") }
  var passwordVisible by rememberSaveable { mutableStateOf(false) }

  val teamTeammates =
    remember(account, allAccounts) {
      allAccounts.filter { it.ownerAccountId == account.ownerAccountId && it.loginId != account.loginId }
    }
  val teamEmployees =
    remember(account, allAccounts) {
      allAccounts.filter { it.ownerAccountId == account.loginId && !it.isOwnerRole() }
    }

  val scopedLeads =
    remember(account, allLeads, searchQuery) {
      val roleFiltered =
        if (account.isOwnerRole()) {
          allLeads.filter { it.ownerAccountId == account.loginId }
        } else {
          allLeads.filter { it.fetchedByLoginId == account.loginId }
        }
      val q = searchQuery.trim().lowercase()
      if (q.isEmpty()) roleFiltered
      else {
        roleFiltered.filter {
          it.name.lowercase().contains(q) ||
            it.company.lowercase().contains(q) ||
            it.email.lowercase().contains(q) ||
            it.phone.lowercase().contains(q) ||
            it.plateNumber.lowercase().contains(q) ||
            it.fetchedByLoginId.lowercase().contains(q)
        }
      }
    }

  val scopedTxs =
    remember(account, allTransactions) {
      if (account.isOwnerRole()) {
        allTransactions.filter { it.ownerAccountId == account.loginId }
      } else {
        allTransactions.filter { it.loginId == account.loginId }
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
      unfocusedContainerColor = PureWhite,
    )

  Box(
    modifier = modifier.fillMaxSize().background(PaperBackground),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            onClick = onBackDashboard,
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.size(44.dp).minimumInteractiveComponentSize().testTag("team_back_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Dashboard", tint = InkBlack)
            }
          }
          Spacer(Modifier.width(12.dp))
          Column {
            Text("Team & Ledger", style = MaterialTheme.typography.headlineMedium, color = InkBlack)
            Text(
              if (account.isOwnerRole()) "Owner Workspace · ${account.loginId}" else "Employee · ${account.loginId}",
              style = MaterialTheme.typography.bodyMedium,
              color = InkGraphite,
            )
          }
        }
        Button(
          onClick = {
            if (scopedLeads.isEmpty()) {
              localBanner = FlashBanner("No leads available to export yet.", isError = true)
            } else {
              val csv = CsvExporter.buildLeadsCsv(scopedLeads)
              CsvExporter.copyCsvToClipboard(context, csv)
              onPreviewCsv(csv)
              localBanner = FlashBanner("Prepared CSV (${scopedLeads.size} leads) & copied to clipboard.", isError = false)
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("export_csv_button"),
        ) {
          Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.cd_export_csv), modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("CSV", fontWeight = FontWeight.Bold)
        }
      }

      Spacer(Modifier.height(14.dp))
      FlashBannerView(
        banner = localBanner ?: flashBanner,
        onDismiss = {
          localBanner = null
          onDismissFlash()
        },
      )

      SegmentedMonochromeTabs(
        tabs = listOf("Team & Share", "Leads (${scopedLeads.size})", "Ledger (${scopedTxs.size})"),
        selectedIndex = selectedTab,
        onSelect = { selectedTab = it },
      )

      Spacer(Modifier.height(12.dp))

      when (selectedTab) {
        0 -> {
          Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // ID Sharing via Points Card
            LeadXCard {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Rounded.Share, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  SectionLabel("ID Sharing via Points", color = InkBlack)
                }
                MonoBadge("Max ${formatPoints(account.usablePoints())}", color = InkGraphite)
              }
              Spacer(Modifier.height(6.dp))
              Text(
                text = "5,000 pts minimum balance must remain in your wallet after sharing.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkGraphite,
              )
              if (teamTeammates.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  teamTeammates.take(4).forEach { mate ->
                    val picked = shareTargetId == mate.loginId
                    Surface(
                      onClick = { shareTargetId = mate.loginId },
                      shape = RoundedCornerShape(8.dp),
                      color = if (picked) InkBlack else SoftAlabaster,
                      border = BorderStroke(1.dp, if (picked) InkBlack else HairlineBorder),
                      modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                      Text(
                        text = mate.loginId,
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (picked) PureWhite else InkBlack,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                      )
                    }
                  }
                }
              }
              Spacer(Modifier.height(10.dp))
              OutlinedTextField(
                value = shareTargetId,
                onValueChange = { shareTargetId = it.uppercase() },
                label = { Text("Recipient ID (e.g. EMP001)") },
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("share_recipient_input"),
              )
              Spacer(Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedTextField(
                  value = shareAmount,
                  onValueChange = { shareAmount = it.filter(Char::isDigit) },
                  label = { Text("Points to Share") },
                  colors = fieldColors,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  singleLine = true,
                  modifier = Modifier.weight(1f).testTag("share_amount_input"),
                )
                Button(
                  onClick = { onSharePoints(shareTargetId, shareAmount) },
                  enabled = !isBusy,
                  colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.height(54.dp).minimumInteractiveComponentSize().testTag("share_submit_button"),
                ) {
                  Text("Share", fontWeight = FontWeight.Bold)
                }
              }
            }

            if (account.isOwnerRole()) {
              Spacer(Modifier.height(14.dp))
              LeadXCard(borderColor = StrongBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Rounded.GroupAdd, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  SectionLabel("Owner Team Directory (Set Login ID & Password)", color = InkBlack)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                  value = newId,
                  onValueChange = { newId = it.uppercase() },
                  label = { Text("New Login ID (e.g. EMP002 or OWN2001)") },
                  leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                  colors = fieldColors,
                  keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("new_employee_id_input"),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                  value = newName,
                  onValueChange = { newName = it },
                  label = { Text("Full Name") },
                  leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                  colors = fieldColors,
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("new_employee_name_input"),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                  value = newPassword,
                  onValueChange = { newPassword = it },
                  label = { Text("Set Login Password (min 6 chars)") },
                  leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                  trailingIcon = {
                    IconButton(
                      onClick = { passwordVisible = !passwordVisible },
                      modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                      Icon(
                        imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                      )
                    }
                  },
                  visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                  colors = fieldColors,
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("new_employee_password_input"),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Button(
                    onClick = {
                      onCreateEmployee(newId, newName, newPassword) {
                        newId = ""
                        newName = ""
                        newPassword = ""
                      }
                    },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("create_employee_button"),
                  ) {
                    Text("Add Employee", fontWeight = FontWeight.Bold)
                  }
                  OutlinedButton(
                    onClick = {
                      onCreateOwner(newId, newName, newPassword) {
                        newId = ""
                        newName = ""
                        newPassword = ""
                      }
                    },
                    enabled = !isBusy,
                    border = BorderStroke(1.dp, InkBlack),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("create_owner_button"),
                  ) {
                    Text("Add Owner", color = InkBlack, fontWeight = FontWeight.Bold)
                  }
                }

                Spacer(Modifier.height(16.dp))
                SectionLabel("Team Employees (${teamEmployees.size}) · Tap to Pre-fill Share")
                Spacer(Modifier.height(8.dp))
                if (teamEmployees.isEmpty()) {
                  Text(
                    "No employees added yet. Create an Employee ID & Password above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkGraphite,
                  )
                } else {
                  teamEmployees.forEachIndexed { index, emp ->
                    if (index > 0) EditorialDivider()
                    Surface(
                      onClick = { shareTargetId = emp.loginId },
                      color = PureWhite,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Column {
                          MonoBadge(emp.loginId, color = InkBlack)
                          Text(emp.fullName, style = MaterialTheme.typography.bodyMedium, color = InkGraphite)
                        }
                        Text(
                          text = "${formatPoints(emp.balance)} pts",
                          fontFamily = MonoFontFamily,
                          style = MaterialTheme.typography.bodyLarge,
                          fontWeight = FontWeight.Bold,
                          color = if (emp.meetsMinBalance()) SuccessInk else InkGraphite,
                        )
                      }
                    }
                  }
                }
              }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
              onClick = onOpenShareApk,
              enabled = !isBusy,
              border = BorderStroke(1.dp, InkBlack),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("team_share_apk_button"),
            ) {
              Icon(Icons.Rounded.Share, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(8.dp))
              Text("Send Installable App (.apk) to Teammates", color = InkBlack, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(22.dp))
          }
        }
        1 -> {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search name, email, phone, company, or plate") },
            colors = fieldColors,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("dashboard_search_input"),
          )
          Spacer(Modifier.height(10.dp))

          if (scopedLeads.isEmpty()) {
            EmptyRecordsCard(
              title = "No leads captured yet",
              subtitle = "Fetch B2B leads from LinkedIn URL or Number Plate lookup to populate this ledger.",
            )
          } else {
            LazyColumn(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(scopedLeads, key = { it.id }) { lead ->
                LeadItemCard(lead)
              }
            }
          }
        }
        else -> {
          if (scopedTxs.isEmpty()) {
            EmptyRecordsCard(
              title = "No wallet transactions yet",
              subtitle = "Top-ups, ID point shares, and lead deductions will appear here.",
            )
          } else {
            LazyColumn(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(scopedTxs, key = { it.id }) { tx ->
                TransactionItemCard(tx)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LeadItemCard(lead: LeadRecord) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = PureWhite,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(lead.name, style = MaterialTheme.typography.titleMedium, color = InkBlack, modifier = Modifier.weight(1f))
        MonochromeBadgeTag(text = lead.sourceEnum().label, inverted = true)
      }
      Spacer(Modifier.height(4.dp))
      Text("${lead.company} · ${lead.title}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = InkBlack)
      if (lead.industry.isNotBlank()) {
        Text(lead.industry, style = MaterialTheme.typography.bodyMedium, color = InkGraphite)
      }
      Text(
        buildString {
          append(lead.location)
          if (lead.plateNumber.isNotBlank()) append(" · ${lead.plateNumber}")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = InkGraphite,
      )
      if (lead.email.isNotBlank() || lead.phone.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        if (lead.email.isNotBlank()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(13.dp), tint = InkMuted)
            Spacer(Modifier.width(5.dp))
            Text(lead.email, style = MaterialTheme.typography.bodyMedium, color = InkBlack)
          }
        }
        if (lead.phone.isNotBlank()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(13.dp), tint = InkMuted)
            Spacer(Modifier.width(5.dp))
            Text(lead.phone, fontFamily = MonoFontFamily, style = MaterialTheme.typography.bodyMedium, color = InkBlack)
          }
        }
      }
      if (lead.vehicleDetails.isNotBlank()) {
        Spacer(Modifier.height(3.dp))
        Text(lead.vehicleDetails, style = MaterialTheme.typography.bodyMedium, color = InkGraphite)
      }
      if (lead.profileUrl.isNotBlank()) {
        Text(
          text = lead.profileUrl,
          fontFamily = MonoFontFamily,
          style = MaterialTheme.typography.labelSmall,
          color = InkMuted,
          maxLines = 1,
          modifier = Modifier.padding(top = 3.dp),
        )
      }
      Spacer(Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "BY ${lead.fetchedByLoginId}",
          fontFamily = MonoFontFamily,
          style = MaterialTheme.typography.labelSmall,
          color = InkGraphite,
        )
        if (lead.sourceEnum() == com.example.model.LeadSource.NUMBER_PLATE) {
          StatusPill(
            text = if (lead.consentGranted) "CONSENT VERIFIED" else "CONSENT PENDING",
            containerColor = if (lead.consentGranted) SuccessSurface else SoftAlabaster,
            contentColor = if (lead.consentGranted) SuccessInk else InkGraphite,
            dotColor = if (lead.consentGranted) SuccessDot else null,
          )
        }
        Text(
          "-${lead.pointsCost} pts",
          fontFamily = MonoFontFamily,
          style = MaterialTheme.typography.labelSmall,
          color = DangerInk,
        )
      }
    }
  }
}

@Composable
private fun TransactionItemCard(tx: PointTransaction) {
  val isCredit = tx.amount >= 0
  Surface(
    shape = MaterialTheme.shapes.small,
    color = PureWhite,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(tx.note, style = MaterialTheme.typography.labelLarge, color = InkBlack)
        Spacer(Modifier.height(2.dp))
        Text(
          "ID: ${tx.loginId}" + if (tx.counterpartyId.isNotBlank()) " · Partner: ${tx.counterpartyId}" else "",
          fontFamily = MonoFontFamily,
          style = MaterialTheme.typography.labelSmall,
          color = InkGraphite,
        )
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = (if (isCredit) "+" else "") + formatPoints(tx.amount),
          fontFamily = MonoFontFamily,
          style = MaterialTheme.typography.titleMedium,
          color = if (isCredit) SuccessInk else DangerInk,
        )
        Text(
          text = "Bal: ${formatPoints(tx.balanceAfter)}",
          fontFamily = MonoFontFamily,
          style = MaterialTheme.typography.labelSmall,
          color = InkGraphite,
        )
      }
    }
  }
}

@Composable
private fun EmptyRecordsCard(title: String, subtitle: String) {
  LeadXCard {
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
        contentDescription = null,
        tint = InkBlack,
        modifier = Modifier.size(34.dp),
      )
      Spacer(Modifier.height(10.dp))
      Text(title, style = MaterialTheme.typography.titleMedium, color = InkBlack)
      Spacer(Modifier.height(4.dp))
      Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = InkGraphite)
    }
  }
}
