package com.example.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.model.LeadRecord
import java.io.File

object CsvExporter {
  private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""

  fun buildLeadsCsv(leads: List<LeadRecord>): String {
    val header =
      "Source,Name,Title,Company,Email,Phone,Industry,Location,PlateNumber,VehicleDetails,ProfileUrl,ConsentGranted,FetchedBy,OwnerId,PointsCost"
    val rows =
      leads.map { l ->
        listOf(
            l.source,
            l.name,
            l.title,
            l.company,
            l.email,
            l.phone,
            l.industry,
            l.location,
            l.plateNumber,
            l.vehicleDetails,
            l.profileUrl,
            l.consentGranted.toString(),
            l.fetchedByLoginId,
            l.ownerAccountId,
            l.pointsCost.toString(),
          )
          .joinToString(",") { csvCell(it) }
      }
    return (listOf(header) + rows).joinToString("\n")
  }

  fun copyCsvToClipboard(context: Context, csvContent: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("LeadX Pro Leads CSV", csvContent))
  }

  fun shareCsvFile(context: Context, csvContent: String, fileName: String = "leadx_leads.csv"): Boolean {
    return runCatching {
      val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
      val csvFile = File(exportDir, fileName)
      csvFile.writeText(csvContent, Charsets.UTF_8)

      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
      val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
          type = "text/csv"
          putExtra(Intent.EXTRA_SUBJECT, "LeadX Pro Leads Export")
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_TEXT, csvContent)
          clipData = ClipData.newUri(context.contentResolver, fileName, uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      val chooser = Intent.createChooser(shareIntent, "Export Leads CSV").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
      true
    }.getOrDefault(false)
  }

  fun exportLeadsToCsv(context: Context, leads: List<LeadRecord>): String {
    val csvContent = buildLeadsCsv(leads)
    copyCsvToClipboard(context, csvContent)
    shareCsvFile(context, csvContent)
    return csvContent
  }
}
