package com.example.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.model.LeadRecord
import com.example.model.LeadSource
import com.example.model.LinkedInCandidate
import java.io.File
import java.util.UUID

object CsvExporter {
  private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""

  private val STANDARD_HEADER =
    "Source,Name,Title,Company,Email,Phone,Industry,Location,PlateNumber,VehicleDetails,ProfileUrl,ConsentGranted,FetchedBy,OwnerId,PointsCost"

  fun buildLeadsCsv(leads: List<LeadRecord>): String {
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
    return (listOf(STANDARD_HEADER) + rows).joinToString("\n")
  }

  fun buildCandidatesCsv(
    candidates: List<LinkedInCandidate>,
    ownerAccountId: String = "",
    fetchedByLoginId: String = "",
  ): String {
    val leads =
      candidates.mapIndexed { idx, c ->
        LeadRecord(
          id = c.id.ifBlank { "csv_cand_$idx" },
          source = LeadSource.LINKEDIN.wireValue,
          ownerAccountId = ownerAccountId,
          fetchedByLoginId = fetchedByLoginId,
          name = c.name,
          title = c.title,
          company = c.company,
          email = c.email,
          phone = c.phone,
          industry = c.industry,
          location = c.location,
          plateNumber = "",
          vehicleDetails = listOfNotNull(
            c.bio.takeIf { it.isNotBlank() }?.let { "Bio: $it" },
            c.captureMethod.takeIf { it.isNotBlank() },
          ).joinToString(" · "),
          profileUrl = c.profileUrl,
          consentGranted = true,
          pointsCost = 0L,
        )
      }
    return buildLeadsCsv(leads)
  }

  /**
   * Parses RFC-4180 CSV content (including quoted cells, escaped quotes, and multiline values)
   * back into structured [LeadRecord] items for direct client preview/delivery or DB import.
   */
  fun parseCsvLeads(csvText: String): List<LeadRecord> {
    if (csvText.isBlank()) return emptyList()
    val rows = parseCsvRows(csvText.trim())
    if (rows.isEmpty()) return emptyList()

    val firstRowLower = rows.first().map { it.trim().lowercase() }
    val hasHeader =
      firstRowLower.any { col ->
        col in setOf("name", "full name", "fullname", "email", "company", "source", "profileurl", "profile url", "linkedin")
      }

    val headerMap =
      if (hasHeader) {
        firstRowLower.mapIndexed { index, name -> name to index }.toMap()
      } else {
        emptyMap()
      }

    val dataRows = if (hasHeader) rows.drop(1) else rows
    return dataRows.mapNotNull { cols ->
      fun cell(vararg keys: String, fallbackIndex: Int = -1): String {
        if (headerMap.isNotEmpty()) {
          for (key in keys) {
            val idx = headerMap[key]
            if (idx != null && idx in cols.indices) {
              val value = cols[idx].trim()
              if (value.isNotBlank()) return value
            }
          }
          return ""
        }
        return if (fallbackIndex in cols.indices) cols[fallbackIndex].trim() else ""
      }

      val name = cell("name", "full name", "fullname", "contact", "person", fallbackIndex = 1)
      val company = cell("company", "organization", "org", fallbackIndex = 3)
      val email = cell("email", "work email", "personal email", fallbackIndex = 4)
      val phone = cell("phone", "mobile", "telephone", "contact number", fallbackIndex = 5)
      val url = cell("profileurl", "profile url", "linkedin", "linkedin url", "url", fallbackIndex = 10)

      if (name.isBlank() && company.isBlank() && email.isBlank() && phone.isBlank() && url.isBlank()) {
        return@mapNotNull null
      }

      val sourceRaw = cell("source", fallbackIndex = 0)
      val resolvedSource =
        if (sourceRaw.contains("plate", ignoreCase = true) || sourceRaw.contains("vehicle", ignoreCase = true)) {
          LeadSource.NUMBER_PLATE.wireValue
        } else {
          LeadSource.LINKEDIN.wireValue
        }

      LeadRecord(
        id = "csv_row_${UUID.randomUUID()}",
        source = resolvedSource,
        ownerAccountId = cell("ownerid", "owner id", "owner", fallbackIndex = 13),
        fetchedByLoginId = cell("fetchedby", "fetched by", "agent", fallbackIndex = 12),
        name = name.ifBlank { company.ifBlank { url.substringAfter("/in/", "Client Lead") } },
        title = cell("title", "role", "designation", "headline", "job title", fallbackIndex = 2),
        company = company,
        email = email,
        phone = phone,
        industry = cell("industry", "sector", fallbackIndex = 6),
        location = cell("location", "city", "region", fallbackIndex = 7),
        plateNumber = cell("platenumber", "plate number", "vehicle number", fallbackIndex = 8),
        vehicleDetails = cell("vehicledetails", "vehicle details", "bio", "summary", "notes", "about", fallbackIndex = 9),
        profileUrl = url,
        consentGranted = cell("consentgranted", "consent granted", fallbackIndex = 11).equals("true", ignoreCase = true),
        pointsCost = cell("pointscost", "points cost", fallbackIndex = 14).toLongOrNull() ?: 0L,
      )
    }
  }

  private fun parseCsvRows(input: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var currentRow = mutableListOf<String>()
    val currentCell = StringBuilder()
    var inQuotes = false
    var i = 0

    while (i < input.length) {
      val ch = input[i]
      if (inQuotes) {
        if (ch == '"') {
          if (i + 1 < input.length && input[i + 1] == '"') {
            currentCell.append('"')
            i++
          } else {
            inQuotes = false
          }
        } else {
          currentCell.append(ch)
        }
      } else {
        when (ch) {
          '"' -> inQuotes = true
          ',' -> {
            currentRow.add(currentCell.toString())
            currentCell.setLength(0)
          }
          '\r' -> {
            if (i + 1 < input.length && input[i + 1] == '\n') i++
            currentRow.add(currentCell.toString())
            currentCell.setLength(0)
            if (currentRow.any { it.isNotBlank() }) rows.add(currentRow)
            currentRow = mutableListOf()
          }
          '\n' -> {
            currentRow.add(currentCell.toString())
            currentCell.setLength(0)
            if (currentRow.any { it.isNotBlank() }) rows.add(currentRow)
            currentRow = mutableListOf()
          }
          else -> currentCell.append(ch)
        }
      }
      i++
    }
    if (currentCell.isNotEmpty() || currentRow.isNotEmpty()) {
      currentRow.add(currentCell.toString())
      if (currentRow.any { it.isNotBlank() }) rows.add(currentRow)
    }
    return rows
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
