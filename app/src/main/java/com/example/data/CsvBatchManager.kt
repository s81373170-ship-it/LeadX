package com.example.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.CsvBatchRecord
import com.example.model.LeadRecord
import com.example.model.LinkedInCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Manages separate per-client / per-run / per-category CSV files in internal storage (`filesDir/client_csvs/`).
 * Lets the app auto-generate separate CSVs during 24h continuous fetching, parse leads directly out of any CSV
 * to deliver straight to customers/clients, import external CSVs via SAF, and save/share with zero permissions.
 */
object CsvBatchManager {
  private const val PREFS_NAME = "leadx_csv_batches"
  private const val KEY_BATCHES_JSON = "batches_json"
  private const val CSV_DIR_NAME = "client_csvs"

  private val _batchesFlow = MutableStateFlow<List<CsvBatchRecord>>(emptyList())
  val batchesFlow: StateFlow<List<CsvBatchRecord>> = _batchesFlow.asStateFlow()

  @Volatile private var initialized = false

  fun init(context: Context) {
    if (initialized) return
    synchronized(this) {
      if (!initialized) {
        _batchesFlow.value = readBatches(context)
        initialized = true
      }
    }
  }

  private fun csvDirectory(context: Context): File =
    File(context.applicationContext.filesDir, CSV_DIR_NAME).apply { mkdirs() }

  private fun sanitizeSlug(raw: String, fallback: String): String {
    val cleaned =
      raw
        .trim()
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .take(28)
    return cleaned.ifBlank { fallback }
  }

  fun createBatchFromLeads(
    context: Context,
    leads: List<LeadRecord>,
    clientName: String,
    categoryLabel: String,
  ): CsvBatchRecord {
    init(context)
    val now = System.currentTimeMillis()
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
    val cleanClient = clientName.trim().ifBlank { "General Client" }
    val slug = sanitizeSlug(cleanClient, "client")
    val uniqueSuffix = UUID.randomUUID().toString().take(4)
    val fileName = "leadx_${slug}_${timestamp}_$uniqueSuffix.csv"

    val targetFile = File(csvDirectory(context), fileName)
    val csvContent = CsvExporter.buildLeadsCsv(leads)
    targetFile.writeText(csvContent, Charsets.UTF_8)

    val batch =
      CsvBatchRecord(
        id = "csv_${UUID.randomUUID()}",
        fileName = fileName,
        clientName = cleanClient,
        categoryLabel = categoryLabel.ifBlank { "Direct Capture" },
        leadCount = leads.size,
        createdAtMillis = now,
        filePath = targetFile.absolutePath,
      )
    prependBatch(context, batch)
    return batch
  }

  fun createBatchFromCandidates(
    context: Context,
    candidates: List<LinkedInCandidate>,
    ownerAccountId: String,
    fetchedByLoginId: String,
    clientName: String,
    categoryLabel: String,
  ): CsvBatchRecord {
    init(context)
    val now = System.currentTimeMillis()
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
    val cleanClient = clientName.trim().ifBlank { "General Client" }
    val slug = sanitizeSlug(cleanClient, "client")
    val uniqueSuffix = UUID.randomUUID().toString().take(4)
    val fileName = "leadx_${slug}_${timestamp}_$uniqueSuffix.csv"

    val targetFile = File(csvDirectory(context), fileName)
    val csvContent =
      CsvExporter.buildCandidatesCsv(
        candidates = candidates,
        ownerAccountId = ownerAccountId,
        fetchedByLoginId = fetchedByLoginId,
      )
    targetFile.writeText(csvContent, Charsets.UTF_8)

    val batch =
      CsvBatchRecord(
        id = "csv_${UUID.randomUUID()}",
        fileName = fileName,
        clientName = cleanClient,
        categoryLabel = categoryLabel.ifBlank { "LinkedIn Staged" },
        leadCount = candidates.size,
        createdAtMillis = now,
        filePath = targetFile.absolutePath,
      )
    prependBatch(context, batch)
    return batch
  }

  /**
   * Splits stored leads into multiple separate `.csv` files grouped by Company/Client, Source, or City,
   * so each customer receives a dedicated file instead of one mixed dump.
   */
  fun splitLeadsIntoSeparateCsvs(
    context: Context,
    leads: List<LeadRecord>,
    groupBy: String,
    customClientPrefix: String = "",
  ): List<CsvBatchRecord> {
    init(context)
    if (leads.isEmpty()) return emptyList()

    val grouped: Map<String, List<LeadRecord>> =
      when (groupBy.uppercase(Locale.US)) {
        "SOURCE" ->
          leads.groupBy { it.sourceEnum().label.ifBlank { "LinkedIn" } }
        "CITY" ->
          leads.groupBy {
            it.location.substringBefore(",").trim().ifBlank { "Unspecified City" }
          }
        else -> // "COMPANY"
          leads.groupBy {
            it.company.trim().ifBlank { "Independent" }
          }
      }

    val created = mutableListOf<CsvBatchRecord>()
    grouped.entries.take(25).forEach { (groupKey, groupLeads) ->
      val label =
        if (customClientPrefix.isNotBlank()) "${customClientPrefix.trim()} · $groupKey" else groupKey
      val category =
        when (groupBy.uppercase(Locale.US)) {
          "SOURCE" -> "Split by Source ($groupKey)"
          "CITY" -> "Split by City ($groupKey)"
          else -> "Client / Company ($groupKey)"
        }
      created.add(
        createBatchFromLeads(
          context = context,
          leads = groupLeads,
          clientName = label,
          categoryLabel = category,
        )
      )
    }
    return created
  }

  fun importExternalCsv(
    context: Context,
    csvContent: String,
    originalFileName: String,
    clientName: String,
  ): Pair<CsvBatchRecord, List<LeadRecord>> {
    init(context)
    val parsedLeads = CsvExporter.parseCsvLeads(csvContent)
    require(parsedLeads.isNotEmpty()) { "Selected CSV file contains no valid lead rows." }

    val now = System.currentTimeMillis()
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
    val cleanClient =
      clientName.trim().ifBlank {
        originalFileName.substringBeforeLast(".").ifBlank { "Imported Client" }
      }
    val slug = sanitizeSlug(cleanClient, "imported")
    val fileName = "leadx_${slug}_${timestamp}.csv"
    val targetFile = File(csvDirectory(context), fileName)
    // Normalize into standard CSV while preserving all parsed fields
    targetFile.writeText(CsvExporter.buildLeadsCsv(parsedLeads), Charsets.UTF_8)

    val batch =
      CsvBatchRecord(
        id = "csv_${UUID.randomUUID()}",
        fileName = fileName,
        clientName = cleanClient,
        categoryLabel = "Imported CSV (${parsedLeads.size} leads)",
        leadCount = parsedLeads.size,
        createdAtMillis = now,
        filePath = targetFile.absolutePath,
      )
    prependBatch(context, batch)
    return batch to parsedLeads
  }

  fun readRawCsv(batch: CsvBatchRecord): String {
    val file = File(batch.filePath)
    if (!file.exists()) return ""
    return runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
  }

  fun readLeadsFromBatch(batch: CsvBatchRecord): List<LeadRecord> {
    val raw = readRawCsv(batch)
    if (raw.isBlank()) return emptyList()
    return CsvExporter.parseCsvLeads(raw)
  }

  fun buildClientDeliveryTextFromCsv(
    batch: CsvBatchRecord,
    leads: List<LeadRecord>,
  ): String {
    val dateLabel = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(batch.createdAtMillis))
    return buildString {
      appendLine("LeadX Pro · Client Lead Pack (${batch.clientName})")
      appendLine("File: ${batch.fileName} · ${leads.size} Leads · $dateLabel")
      appendLine("============================================")
      leads.forEachIndexed { index, lead ->
        appendLine("${index + 1}. ${lead.name}")
        val roleCompany = listOf(lead.title, lead.company).filter { it.isNotBlank() }.joinToString(" @ ")
        if (roleCompany.isNotBlank()) appendLine("   Role: $roleCompany")
        if (lead.email.isNotBlank()) appendLine("   Email: ${lead.email}")
        if (lead.phone.isNotBlank()) appendLine("   Phone: ${lead.phone}")
        if (lead.industry.isNotBlank()) appendLine("   Industry: ${lead.industry}")
        if (lead.location.isNotBlank()) appendLine("   Location: ${lead.location}")
        if (lead.vehicleDetails.isNotBlank()) appendLine("   Info/Bio: ${lead.vehicleDetails}")
        if (lead.profileUrl.isNotBlank()) appendLine("   LinkedIn: ${lead.profileUrl}")
      }
    }.trim()
  }

  fun copyClientDeliveryText(context: Context, text: String): Boolean =
    runCatching {
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
      clipboard.setPrimaryClip(ClipData.newPlainText("Client Leads from CSV", text))
      true
    }.getOrDefault(false)

  fun shareCsvBatchFile(context: Context, batch: CsvBatchRecord): Boolean {
    return runCatching {
      val file = File(batch.filePath)
      if (!file.exists()) return false
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val leads = readLeadsFromBatch(batch)
      val summary = buildClientDeliveryTextFromCsv(batch, leads.take(15))
      val intent =
        Intent(Intent.ACTION_SEND).apply {
          type = "text/csv"
          putExtra(Intent.EXTRA_SUBJECT, "Leads for ${batch.clientName} (${batch.fileName})")
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_TEXT, summary)
          clipData = ClipData.newUri(context.contentResolver, batch.fileName, uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      val chooser = Intent.createChooser(intent, "Send Client CSV (${batch.fileName})").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
      true
    }.getOrDefault(false)
  }

  fun saveBatchToDownloads(context: Context, batch: CsvBatchRecord): Boolean {
    val file = File(batch.filePath)
    if (!file.exists()) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      return runCatching {
        val values =
          ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, batch.fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/LeadXPro")
            put(MediaStore.Downloads.IS_PENDING, 1)
          }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out ->
          file.inputStream().use { input -> input.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
      }.getOrDefault(false)
    }
    return shareCsvBatchFile(context, batch)
  }

  fun deleteBatch(context: Context, batchId: String) {
    init(context)
    val current = _batchesFlow.value
    val target = current.firstOrNull { it.id == batchId }
    if (target != null) {
      runCatching { File(target.filePath).delete() }
    }
    val updated = current.filterNot { it.id == batchId }
    persistBatches(context, updated)
  }

  fun getBatches(context: Context): List<CsvBatchRecord> {
    init(context)
    return _batchesFlow.value
  }

  private fun prependBatch(context: Context, batch: CsvBatchRecord) {
    val updated = (listOf(batch) + _batchesFlow.value.filterNot { it.id == batch.id }).take(60)
    persistBatches(context, updated)
  }

  private fun persistBatches(context: Context, batches: List<CsvBatchRecord>) {
    val array = JSONArray()
    batches.forEach { b ->
      array.put(
        JSONObject()
          .put("id", b.id)
          .put("fileName", b.fileName)
          .put("clientName", b.clientName)
          .put("categoryLabel", b.categoryLabel)
          .put("leadCount", b.leadCount)
          .put("createdAtMillis", b.createdAtMillis)
          .put("filePath", b.filePath)
      )
    }
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_BATCHES_JSON, array.toString())
      .apply()
    _batchesFlow.value = batches
  }

  private fun readBatches(context: Context): List<CsvBatchRecord> {
    val raw =
      context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_BATCHES_JSON, "")
        .orEmpty()
    if (raw.isBlank()) return emptyList()
    return runCatching {
      val array = JSONArray(raw)
      buildList {
        for (i in 0 until array.length()) {
          val obj = array.optJSONObject(i) ?: continue
          val path = obj.optString("filePath")
          if (path.isBlank() || !File(path).exists()) continue
          add(
            CsvBatchRecord(
              id = obj.optString("id"),
              fileName = obj.optString("fileName"),
              clientName = obj.optString("clientName"),
              categoryLabel = obj.optString("categoryLabel"),
              leadCount = obj.optInt("leadCount", 0),
              createdAtMillis = obj.optLong("createdAtMillis", 0L),
              filePath = path,
            )
          )
        }
      }
    }.getOrDefault(emptyList())
  }
}
