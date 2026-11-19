package com.example.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile

data class InstalledApkStatus(
  val isUniversalApk: Boolean,
  val sourcePath: String,
  val sizeMbText: String,
  val versionName: String,
  val versionCode: Int,
  val splitCount: Int,
)

data class SavedApkResult(
  val fileName: String,
  val locationLabel: String,
  val shareFile: File? = null,
)

object ApkShareHelper {
  const val DIRECT_APK_DOWNLOAD_URL =
    "https://github.com/s81373170-ship-it/Linkedin-data-apk/releases/latest/download/LeadX-Pro.apk"
  const val WEB_PORTAL_DOWNLOAD_URL =
    "https://s81373170-ship-it.github.io/Linkedin-data-apk/"
  const val GITHUB_RELEASES_PAGE_URL =
    "https://github.com/s81373170-ship-it/Linkedin-data-apk/releases/latest"

  fun sanitizeApkFileName(versionName: String = BuildConfig.VERSION_NAME): String {
    val cleanVersion = versionName.trim().ifBlank { "1.0" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return "LeadX-Pro-v$cleanVersion.apk"
  }

  fun inspectInstalledApk(context: Context): InstalledApkStatus {
    val appInfo = context.applicationInfo
    val splits = appInfo.splitSourceDirs?.filter { it.isNotBlank() }.orEmpty()
    val metaData =
      appInfo.metaData
        ?: runCatching {
          val pm = context.packageManager
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(
              context.packageName,
              PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            ).metaData
          } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
          }
        }.getOrNull()
    val metaRequiresSplits = metaData?.getBoolean("com.android.vending.splits.required", false) == true
    val sourcePath = appInfo.sourceDir.orEmpty()
    val sizeBytes = runCatching { if (sourcePath.isNotBlank()) File(sourcePath).length() else 0L }.getOrDefault(0L)
    val sizeMb = sizeBytes / (1024.0 * 1024.0)
    val sizeText =
      if (sizeBytes > 0L) String.format(Locale.US, "%.1f MB", sizeMb)
      else "Universal APK"
    return InstalledApkStatus(
      isUniversalApk = splits.isEmpty() && !metaRequiresSplits,
      sourcePath = sourcePath,
      sizeMbText = sizeText,
      versionName = BuildConfig.VERSION_NAME,
      versionCode = BuildConfig.VERSION_CODE,
      splitCount = if (splits.isNotEmpty()) splits.size else if (metaRequiresSplits) 1 else 0,
    )
  }

  suspend fun prepareShareableApkFile(context: Context): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        val status = inspectInstalledApk(context)
        check(status.isUniversalApk) {
          "This device uses a Split AAB install (${status.splitCount} splits). Sharing base.apk directly causes 'problem parsing the package'—share the Universal APK Download Link or build APK instead."
        }
        check(status.sourcePath.isNotBlank()) {
          "Installed APK path unavailable on this device. Share the Universal APK Download Link instead."
        }
        val sourceFile = File(status.sourcePath)
        check(sourceFile.exists() && sourceFile.canRead() && sourceFile.length() > 100_000L) {
          "Cannot read complete APK package from device storage. Share the Universal APK Download Link instead."
        }

        val shareDir = File(context.filesDir, "shared_apks").apply { mkdirs() }
        shareDir.listFiles()?.forEach { old ->
          if (old.name.startsWith("LeadX-Pro-") && old.name.endsWith(".apk")) {
            old.delete()
          }
        }

        val destFile = File(shareDir, sanitizeApkFileName(status.versionName))
        FileInputStream(sourceFile).use { input ->
          FileOutputStream(destFile).use { output ->
            input.copyTo(output, bufferSize = 64 * 1024)
            output.fd.sync()
          }
        }

        check(isValidApkArchive(context, destFile)) {
          destFile.delete()
          "Exported APK failed signature/package validation. Share the Universal APK Download Link instead."
        }
        destFile
      }
    }

  suspend fun saveApkToDownloads(context: Context): Result<SavedApkResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val verifiedApk = prepareShareableApkFile(context).getOrThrow()
        val fileName = verifiedApk.name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val resolver = context.contentResolver
          val values =
            ContentValues().apply {
              put(MediaStore.Downloads.DISPLAY_NAME, fileName)
              put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
              put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
              put(MediaStore.Downloads.IS_PENDING, 1)
            }
          val itemUri =
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
              ?: throw IllegalStateException("Could not create file in Downloads.")
          try {
            resolver.openOutputStream(itemUri)?.use { out ->
              FileInputStream(verifiedApk).use { input ->
                input.copyTo(out, bufferSize = 64 * 1024)
              }
            } ?: throw IllegalStateException("Could not write APK to Downloads.")
            val completed =
              ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
              }
            resolver.update(itemUri, completed, null, null)
          } catch (e: Exception) {
            runCatching { resolver.delete(itemUri, null, null) }
            throw e
          }
          SavedApkResult(
            fileName = fileName,
            locationLabel = "Downloads/$fileName",
            shareFile = verifiedApk,
          )
        } else {
          val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.apply { mkdirs() }
            ?: verifiedApk.parentFile
          val outFile = File(downloadsDir, fileName)
          if (outFile.absolutePath != verifiedApk.absolutePath) {
            FileInputStream(verifiedApk).use { input ->
              FileOutputStream(outFile).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
                output.fd.sync()
              }
            }
          }
          SavedApkResult(
            fileName = fileName,
            locationLabel = outFile.absolutePath,
            shareFile = outFile,
          )
        }
      }
    }

  internal fun hasRequiredZipEntries(apkFile: File): Boolean =
    runCatching {
      ZipFile(apkFile).use { zip ->
        val hasManifest = zip.getEntry("AndroidManifest.xml") != null
        val hasResources = zip.getEntry("resources.arsc") != null
        val hasDex = zip.getEntry("classes.dex") != null
        hasManifest && hasResources && hasDex
      }
    }.getOrDefault(false)

  @Suppress("DEPRECATION")
  internal fun hasValidSignatures(pkgInfo: PackageInfo): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val signingInfo = pkgInfo.signingInfo
      val contentsSigners = signingInfo?.apkContentsSigners
      val historySigners = signingInfo?.signingCertificateHistory
      (contentsSigners != null && contentsSigners.isNotEmpty()) ||
        (historySigners != null && historySigners.isNotEmpty())
    } else {
      val sigs = pkgInfo.signatures
      sigs != null && sigs.isNotEmpty()
    }
  }

  internal fun isValidApkArchive(context: Context, apkFile: File): Boolean {
    if (!apkFile.exists() || apkFile.length() < 100_000L) return false
    val hasZipMagic =
      runCatching {
        FileInputStream(apkFile).use { stream ->
          val header = ByteArray(2)
          stream.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        }
      }.getOrDefault(false)
    if (!hasZipMagic) return false
    if (!hasRequiredZipEntries(apkFile)) return false

    val pkgInfo =
      runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          val flags =
            PackageManager.GET_SIGNING_CERTIFICATES.toLong() or
              PackageManager.GET_META_DATA.toLong()
          pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags))
        } else {
          @Suppress("DEPRECATION")
          val sigFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
              PackageManager.GET_SIGNING_CERTIFICATES
            } else {
              PackageManager.GET_SIGNATURES
            }
          @Suppress("DEPRECATION")
          pm.getPackageArchiveInfo(apkFile.absolutePath, sigFlag or PackageManager.GET_META_DATA)
        }
      }.getOrNull() ?: return false

    val appInfo = pkgInfo.applicationInfo ?: return false
    val requiresSplits = appInfo.metaData?.getBoolean("com.android.vending.splits.required", false) == true
    if (requiresSplits) return false

    return hasValidSignatures(pkgInfo)
  }

  fun shareApkFile(context: Context, apkFile: File): Boolean {
    return runCatching {
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
      val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
          type = "application/vnd.android.package-archive"
          putExtra(Intent.EXTRA_STREAM, uri)
          putExtra(Intent.EXTRA_TITLE, apkFile.name)
          putExtra(Intent.EXTRA_SUBJECT, "LeadX Pro Android APK (v${BuildConfig.VERSION_NAME})")
          putExtra(
            Intent.EXTRA_TEXT,
            "Install LeadX Pro (v${BuildConfig.VERSION_NAME}).\n" +
              "Note for WhatsApp / Realme / ColorOS / Oppo / Xiaomi phones: if tapping inside WhatsApp shows 'There was a problem parsing the package', tap Save to Downloads (or open Files -> Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents) and install ${apkFile.name} from there.",
          )
          clipData = ClipData.newUri(context.contentResolver, apkFile.name, uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
      val chooser =
        Intent.createChooser(shareIntent, "Share LeadX Pro APK").apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(chooser)
      true
    }.getOrDefault(false)
  }

  fun buildDownloadMessage(versionName: String = BuildConfig.VERSION_NAME): String =
    buildString {
      appendLine("LeadX Pro Android App (v$versionName)")
      appendLine("Direct Universal APK (Chrome):")
      appendLine(DIRECT_APK_DOWNLOAD_URL)
      appendLine()
      appendLine("Web Portal & Releases Page:")
      appendLine(WEB_PORTAL_DOWNLOAD_URL)
      appendLine()
      appendLine("How to install on Android:")
      appendLine("1. Open the direct link in Chrome to download LeadX-Pro.apk (make sure the GitHub repo is Public)")
      appendLine("2. Tap LeadX-Pro.apk in Chrome Downloads or your Files app (avoid installing directly inside WhatsApp if your phone shows a parse error)")
      append("3. Allow 'Install unknown apps' if prompted and tap Install")
    }

  fun openUrlInBrowser(context: Context, url: String = DIRECT_APK_DOWNLOAD_URL): Boolean {
    return runCatching {
      val intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }

  fun copyDownloadLink(context: Context): Boolean {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("LeadX Pro APK Download Link", DIRECT_APK_DOWNLOAD_URL))
    return true
  }

  fun shareDownloadLink(context: Context): Boolean {
    return runCatching {
      val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_SUBJECT, "Download LeadX Pro Android APK (v${BuildConfig.VERSION_NAME})")
          putExtra(Intent.EXTRA_TEXT, buildDownloadMessage())
        }
      val chooser =
        Intent.createChooser(shareIntent, "Share LeadX Pro Download Link").apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(chooser)
      true
    }.getOrDefault(false)
  }
}
