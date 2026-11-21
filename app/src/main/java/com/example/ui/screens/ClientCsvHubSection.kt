package com.example.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.CsvBatchManager
import com.example.model.CsvBatchRecord
import com.example.model.LeadRecord
import com.example.ui.components.LeadXCard
import com.example.ui.components.MonoBadge
import com.example.ui.components.MonochromeBadgeTag
import com.example.ui.components.SectionLabel
import com.example.ui.theme.DangerInk
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkGraphite
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftAlabaster
import com.example.ui.theme.StrongBorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClientCsvHubTabContent(
  scopedLeads: List<LeadRecord>,
  csvBatches: List<CsvBatchRecord>,
  isBusy: Boolean,
  fieldColors: TextFieldColors,
  onSplitCsvs: (groupBy: String, scopedLeads: List<LeadRecord>, clientPrefix: String) -> Unit,
  onCreateSingleClientCsv: (scopedLeads: List<LeadRecord>, clientName: String) -> Unit,
  onOpenCsvBatch: (CsvBatchRecord) -> Unit,
  onImportClientCsv: (rawCsv: String, fileName: String, clientName: String) -> Unit,
  onDeleteCsvBatch: (String) -> Unit,
  onLocalFeedback: (String, Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val isInspection = LocalInspectionMode.current
  val hasActivityRegistry = LocalActivityResultRegistryOwner.current != null
  var clientCsvName by rememberSaveable { mutableStateOf("") }

  val csvPickerLauncher =
    if (!isInspection && hasActivityRegistry) {
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val displayName =
          runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
              if (cursor.moveToFirst()) cursor.getString(0) else null
            }
          }.getOrNull() ?: uri.lastPathSegment ?: "client_leads.csv"

        val rawText =
          runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
          }.getOrNull()

        if (rawText.isNullOrBlank()) {
          onLocalFeedback("Could not read selected CSV file.", true)
        } else {
          onImportClientCsv(rawText, displayName, clientCsvName)
        }
      }
    } else {
      null
    }

  Column(modifier = modifier) {
    LeadXCard(borderColor = StrongBorder) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(Icons.Rounded.TableChart, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          SectionLabel("Client CSV Delivery Hub", color = InkBlack)
        }
        MonoBadge("${csvBatches.size} FILES", color = InkBlack)
      }
      Spacer(Modifier.height(6.dp))
      Text(
        text = "Separate .csv files are auto-saved per 24h run or split by Company, Source, or City. Open any CSV to deliver leads directly to clients.",
        style = MaterialTheme.typography.bodyMedium,
        color = InkGraphite,
      )
      Spacer(Modifier.height(10.dp))
      OutlinedTextField(
        value = clientCsvName,
        onValueChange = { clientCsvName = it },
        label = { Text("Client / Campaign Name (e.g. Acme Corp)") },
        colors = fieldColors,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("csv_client_name_input"),
      )
      Spacer(Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = { onCreateSingleClientCsv(scopedLeads, clientCsvName) },
          enabled = !isBusy,
          colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("create_single_client_csv_button"),
        ) {
          Icon(Icons.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Create Client .csv", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
          onClick = {
            val launcher = csvPickerLauncher
            if (launcher != null) {
              launcher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
            } else {
              onLocalFeedback("Document picker is unavailable in this environment.", true)
            }
          },
          enabled = !isBusy,
          border = BorderStroke(1.dp, InkBlack),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("import_client_csv_button"),
        ) {
          Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Import .csv", color = InkBlack, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      Spacer(Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        OutlinedButton(
          onClick = { onSplitCsvs("COMPANY", scopedLeads, clientCsvName) },
          enabled = !isBusy,
          border = BorderStroke(1.dp, HairlineBorder),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("split_csv_company_button"),
        ) {
          Text(
            "Split Company",
            style = MaterialTheme.typography.labelSmall,
            color = InkBlack,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        OutlinedButton(
          onClick = { onSplitCsvs("SOURCE", scopedLeads, clientCsvName) },
          enabled = !isBusy,
          border = BorderStroke(1.dp, HairlineBorder),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("split_csv_source_button"),
        ) {
          Text(
            "Split Source",
            style = MaterialTheme.typography.labelSmall,
            color = InkBlack,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        OutlinedButton(
          onClick = { onSplitCsvs("CITY", scopedLeads, clientCsvName) },
          enabled = !isBusy,
          border = BorderStroke(1.dp, HairlineBorder),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("split_csv_city_button"),
        ) {
          Text(
            "Split City",
            style = MaterialTheme.typography.labelSmall,
            color = InkBlack,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    Spacer(Modifier.height(10.dp))

    if (csvBatches.isEmpty()) {
      LeadXCard {
        Column(
          modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(
            imageVector = Icons.Rounded.TableChart,
            contentDescription = null,
            tint = InkBlack,
            modifier = Modifier.size(34.dp),
          )
          Spacer(Modifier.height(10.dp))
          Text("No separate Client CSVs yet", style = MaterialTheme.typography.titleMedium, color = InkBlack)
          Spacer(Modifier.height(4.dp))
          Text(
            "Save LinkedIn leads, enable 24h Auto-Fetch, split stored leads above, or import any .csv to deliver client-ready leads directly.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkGraphite,
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(csvBatches, key = { it.id }) { batch ->
          CsvBatchCard(
            batch = batch,
            onViewLeads = { onOpenCsvBatch(batch) },
            onShareFile = {
              val shared = CsvBatchManager.shareCsvBatchFile(context, batch)
              if (!shared) {
                val leads = CsvBatchManager.readLeadsFromBatch(batch)
                val text = CsvBatchManager.buildClientDeliveryTextFromCsv(batch, leads)
                CsvBatchManager.copyClientDeliveryText(context, text)
                onLocalFeedback("No share app found — copied client leads from ${batch.fileName}.", false)
              }
            },
            onDelete = { onDeleteCsvBatch(batch.id) },
          )
        }
      }
    }
  }
}

@Composable
private fun CsvBatchCard(
  batch: CsvBatchRecord,
  onViewLeads: () -> Unit,
  onShareFile: () -> Unit,
  onDelete: () -> Unit,
) {
  val dateLabel =
    remember(batch.createdAtMillis) {
      SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(batch.createdAtMillis))
    }
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
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = batch.clientName,
            style = MaterialTheme.typography.titleMedium,
            color = InkBlack,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = batch.fileName,
            fontFamily = MonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(Modifier.width(8.dp))
        MonochromeBadgeTag(text = "${batch.leadCount} LEADS", inverted = true)
      }
      Spacer(Modifier.height(4.dp))
      Text(
        text = "${batch.categoryLabel} · $dateLabel",
        style = MaterialTheme.typography.bodyMedium,
        color = InkGraphite,
      )
      Spacer(Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = onViewLeads,
          colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("open_csv_batch_${batch.id}"),
        ) {
          Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("View & Send Leads", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
          onClick = onShareFile,
          border = BorderStroke(1.dp, InkBlack),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.height(48.dp).testTag("share_csv_batch_${batch.id}"),
        ) {
          Icon(Icons.Rounded.Share, contentDescription = "Send CSV File", tint = InkBlack, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text(".csv", color = InkBlack, fontWeight = FontWeight.Bold)
        }
        IconButton(
          onClick = onDelete,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_csv_batch_${batch.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = "Delete CSV batch",
            tint = DangerInk,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvBatchLeadsViewerSheet(
  batch: CsvBatchRecord,
  leads: List<LeadRecord>,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSaveLeadsToDb: (CsvBatchRecord) -> Unit,
  onFeedback: (String, Boolean) -> Unit,
) {
  val context = LocalContext.current
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = PureWhite,
    contentColor = InkBlack,
    modifier = Modifier.testTag("csv_batch_viewer_sheet"),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SectionLabel("Client Leads from CSV · ${batch.clientName}", color = InkBlack)
          Text(
            text = batch.fileName,
            fontFamily = MonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        MonoBadge("${leads.size} LEADS", color = InkBlack)
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("close_csv_viewer_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Close CSV viewer",
            tint = InkBlack,
          )
        }
      }
      Spacer(Modifier.height(6.dp))
      Text(
        text = "Parsed directly from ${batch.fileName}. Send the CSV, copy formatted client leads, download, or store into database.",
        style = MaterialTheme.typography.bodyMedium,
        color = InkGraphite,
      )
      Spacer(Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = {
            val shared = CsvBatchManager.shareCsvBatchFile(context, batch)
            if (!shared) {
              val delivery = CsvBatchManager.buildClientDeliveryTextFromCsv(batch, leads)
              CsvBatchManager.copyClientDeliveryText(context, delivery)
              onFeedback("No share app found — copied formatted client leads.", false)
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("csv_viewer_share_file_button"),
        ) {
          Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Send .csv", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
          onClick = {
            val delivery = CsvBatchManager.buildClientDeliveryTextFromCsv(batch, leads)
            val copied = CsvBatchManager.copyClientDeliveryText(context, delivery)
            onFeedback(
              if (copied) "Copied ${leads.size} client-ready leads from CSV." else "Clipboard unavailable.",
              !copied,
            )
          },
          border = BorderStroke(1.dp, InkBlack),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("csv_viewer_copy_leads_button"),
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Copy Leads", color = InkBlack, fontWeight = FontWeight.Bold)
        }
      }

      Spacer(Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = {
            val saved = CsvBatchManager.saveBatchToDownloads(context, batch)
            onFeedback(
              if (saved) "Saved ${batch.fileName} to Downloads/LeadXPro." else "Could not save to Downloads.",
              !saved,
            )
          },
          border = BorderStroke(1.dp, HairlineBorder),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("csv_viewer_save_downloads_button"),
        ) {
          Icon(Icons.Rounded.Download, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Save Downloads", color = InkBlack, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
          onClick = { onSaveLeadsToDb(batch) },
          enabled = !isBusy,
          border = BorderStroke(1.dp, HairlineBorder),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("csv_viewer_save_db_button"),
        ) {
          Icon(Icons.Rounded.SaveAlt, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Save DB", color = InkBlack, fontWeight = FontWeight.Bold)
        }
      }

      Spacer(Modifier.height(12.dp))

      LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        itemsIndexed(leads, key = { idx, item -> "${item.id}_$idx" }) { index, lead ->
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = SoftAlabaster,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "${index + 1}. ${lead.name}",
                  style = MaterialTheme.typography.titleMedium,
                  color = InkBlack,
                  modifier = Modifier.weight(1f),
                )
                IconButton(
                  onClick = {
                    val single = CsvBatchManager.buildClientDeliveryTextFromCsv(batch, listOf(lead))
                    CsvBatchManager.copyClientDeliveryText(context, single)
                    onFeedback("Copied ${lead.name} for client delivery.", false)
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("copy_csv_lead_$index"),
                ) {
                  Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy lead for client", tint = InkBlack, modifier = Modifier.size(16.dp))
                }
              }
              val subtitle = listOf(lead.title, lead.company).filter { it.isNotBlank() }.joinToString(" · ")
              if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = InkBlack, fontWeight = FontWeight.Medium)
              }
              if (lead.location.isNotBlank() || lead.industry.isNotBlank()) {
                Text(
                  listOf(lead.industry, lead.location).filter { it.isNotBlank() }.joinToString(" · "),
                  style = MaterialTheme.typography.bodyMedium,
                  color = InkGraphite,
                )
              }
              if (lead.email.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                  Icon(Icons.Rounded.Email, contentDescription = null, tint = InkMuted, modifier = Modifier.size(13.dp))
                  Spacer(Modifier.width(5.dp))
                  Text(lead.email, style = MaterialTheme.typography.bodyMedium, color = InkBlack)
                }
              }
              if (lead.phone.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                  Icon(Icons.Rounded.Phone, contentDescription = null, tint = InkMuted, modifier = Modifier.size(13.dp))
                  Spacer(Modifier.width(5.dp))
                  Text(lead.phone, fontFamily = MonoFontFamily, style = MaterialTheme.typography.bodyMedium, color = InkBlack)
                }
              }
              if (lead.vehicleDetails.isNotBlank()) {
                Text(
                  text = lead.vehicleDetails,
                  style = MaterialTheme.typography.bodyMedium,
                  color = InkGraphite,
                  modifier = Modifier.padding(top = 3.dp),
                )
              }
              if (lead.profileUrl.isNotBlank()) {
                Text(
                  text = lead.profileUrl,
                  fontFamily = MonoFontFamily,
                  style = MaterialTheme.typography.labelSmall,
                  color = InkMuted,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.padding(top = 3.dp),
                )
              }
            }
          }
        }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

@Preview(showBackground = true, showSystemUi = true, name = "Client CSV Hub Preview")
@Composable
private fun ClientCsvHubPreview() {
  com.example.ui.theme.LeadXTheme {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
      ClientCsvHubTabContent(
        scopedLeads = emptyList(),
        csvBatches =
          listOf(
            CsvBatchRecord(
              id = "batch_1",
              clientName = "Acme Corp",
              fileName = "acme_corp_leads.csv",
              filePath = "",
              categoryLabel = "24h Auto-Fetch",
              leadCount = 12,
              createdAtMillis = 1_730_000_000_000L,
            )
          ),
        isBusy = false,
        fieldColors = OutlinedTextFieldDefaults.colors(),
        onSplitCsvs = { _, _, _ -> },
        onCreateSingleClientCsv = { _, _ -> },
        onOpenCsvBatch = {},
        onImportClientCsv = { _, _, _ -> },
        onDeleteCsvBatch = {},
        onLocalFeedback = { _, _ -> },
      )
    }
  }
}
