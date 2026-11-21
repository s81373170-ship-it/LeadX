package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ApkShareHelper
import com.example.data.InstalledApkStatus
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkGraphite
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftAlabaster
import com.example.ui.theme.SuccessDot
import com.example.ui.theme.SuccessInk
import com.example.ui.theme.SuccessSurface
import com.example.ui.theme.WarningInk
import com.example.ui.theme.WarningSurface
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareAppApkSheet(
  onDismiss: () -> Unit,
  onFeedback: (String, Boolean) -> Unit,
) {
  val context = LocalContext.current
  val isPreview = LocalInspectionMode.current
  val scope = rememberCoroutineScope()
  val status =
    remember(context, isPreview) {
      if (isPreview) {
        InstalledApkStatus(
          isUniversalApk = true,
          sourcePath = "",
          sizeMbText = "Universal APK",
          versionName = "1.0.2",
          versionCode = 60,
          splitCount = 0,
        )
      } else {
        ApkShareHelper.inspectInstalledApk(context)
      }
    }
  var isExportingApk by remember { mutableStateOf(false) }
  var isSavingDownloads by remember { mutableStateOf(false) }
  var showHowToInstall by remember { mutableStateOf(false) }

  if (showHowToInstall) {
    HowToInstallModal(
      onDismiss = { showHowToInstall = false },
      onFeedback = onFeedback,
    )
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = PureWhite,
    contentColor = InkBlack,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.Android, contentDescription = null, tint = InkBlack, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          SectionLabel("Share Installable LeadX Pro APK", color = InkBlack)
        }
        AppVersionBadge(inverted = true, testTag = "share_sheet_version_badge")
      }

      Spacer(Modifier.height(10.dp))

      if (status.isUniversalApk) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = SuccessSurface,
          border = BorderStroke(1.dp, SuccessInk.copy(alpha = 0.25f)),
          modifier = Modifier.fillMaxWidth().testTag("universal_apk_status_card"),
        ) {
          Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = SuccessInk, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column {
              StatusPill(
                text = "UNIVERSAL APK · ${status.sizeMbText}",
                containerColor = PureWhite,
                contentColor = SuccessInk,
                dotColor = SuccessDot,
              )
              Spacer(Modifier.height(6.dp))
              Text(
                text = "Exports a signed, verified ${ApkShareHelper.sanitizeApkFileName(status.versionName)} file (never a broken base.apk) ready to save or share.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkBlack,
              )
            }
          }
        }
      } else {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = WarningSurface,
          border = BorderStroke(1.dp, WarningInk.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth().testTag("split_aab_warning_card"),
        ) {
          Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = WarningInk, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
              text = "Split AAB install detected (${status.splitCount} splits). Sharing base.apk directly causes 'problem parsing the package'—share the Universal APK Download Link or build the universal APK instead.",
              style = MaterialTheme.typography.bodyMedium,
              color = WarningInk,
            )
          }
        }
      }

      Spacer(Modifier.height(14.dp))

      Button(
        onClick = {
          if (isExportingApk || isSavingDownloads) return@Button
          isExportingApk = true
          scope.launch {
            val prep = ApkShareHelper.prepareShareableApkFile(context)
            isExportingApk = false
            prep.fold(
              onSuccess = { apkFile ->
                val opened = ApkShareHelper.shareApkFile(context, apkFile)
                if (opened) {
                  onFeedback("Prepared ${apkFile.name} for sharing.", false)
                  onDismiss()
                } else {
                  onFeedback("No share target available. Save to Downloads or share the download link instead.", true)
                }
              },
              onFailure = { err ->
                onFeedback(err.message ?: "Unable to prepare APK file. Use the Download Link instead.", true)
              },
            )
          }
        },
        enabled = !isExportingApk && !isSavingDownloads && status.isUniversalApk,
        colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("share_apk_file_button"),
      ) {
        if (isExportingApk) {
          CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Verifying & Preparing APK...", fontWeight = FontWeight.Bold)
        } else {
          Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Share Installable .apk File", fontWeight = FontWeight.Bold)
        }
      }

      Spacer(Modifier.height(8.dp))

      OutlinedButton(
        onClick = {
          if (isExportingApk || isSavingDownloads) return@OutlinedButton
          isSavingDownloads = true
          scope.launch {
            val saved = ApkShareHelper.saveApkToDownloads(context)
            isSavingDownloads = false
            saved.fold(
              onSuccess = { res ->
                onFeedback("Saved verified APK to ${res.locationLabel}. Share from Files / Quick Share if WhatsApp fails to parse.", false)
                onDismiss()
              },
              onFailure = { err ->
                onFeedback(err.message ?: "Could not save APK to Downloads.", true)
              },
            )
          }
        },
        enabled = !isExportingApk && !isSavingDownloads && status.isUniversalApk,
        border = BorderStroke(1.dp, InkBlack),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_apk_downloads_button"),
      ) {
        if (isSavingDownloads) {
          CircularProgressIndicator(color = InkBlack, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Saving to Downloads...", color = InkBlack, fontWeight = FontWeight.Bold)
        } else {
          Icon(Icons.Rounded.SaveAlt, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Save .apk to Downloads", color = InkBlack, fontWeight = FontWeight.Bold)
        }
      }

      Spacer(Modifier.height(14.dp))
      SectionLabel("Direct Universal APK Download Link", color = InkGraphite)
      Spacer(Modifier.height(6.dp))

      Surface(
        shape = RoundedCornerShape(10.dp),
        color = SoftAlabaster,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = ApkShareHelper.DIRECT_APK_DOWNLOAD_URL,
          fontFamily = MonoFontFamily,
          style = MaterialTheme.typography.labelSmall,
          color = InkBlack,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(12.dp),
        )
      }

      Spacer(Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = {
            if (ApkShareHelper.copyDownloadLink(context)) {
              onFeedback("Universal APK download link copied to clipboard.", false)
            } else {
              onFeedback("Clipboard unavailable on this device.", true)
            }
            onDismiss()
          },
          border = BorderStroke(1.dp, InkBlack),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("copy_apk_link_button"),
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Copy Link", color = InkBlack, fontWeight = FontWeight.Bold)
        }
        Button(
          onClick = {
            val opened = ApkShareHelper.shareDownloadLink(context)
            if (!opened) {
              ApkShareHelper.copyDownloadLink(context)
              onFeedback("Share sheet unavailable — APK download link copied to clipboard.", false)
            }
            onDismiss()
          },
          colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("share_apk_link_button"),
        ) {
          Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Share Link", fontWeight = FontWeight.Bold)
        }
      }

      Spacer(Modifier.height(8.dp))
      OutlinedButton(
        onClick = {
          val opened = ApkShareHelper.openUrlInBrowser(context, ApkShareHelper.GITHUB_RELEASES_PAGE_URL)
          if (!opened) {
            ApkShareHelper.copyDownloadLink(context)
            onFeedback("Browser unavailable — APK download link copied to clipboard.", false)
          }
          onDismiss()
        },
        border = BorderStroke(1.dp, HairlineBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_apk_link_browser_button"),
      ) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Open GitHub Releases in Chrome", color = InkBlack, fontWeight = FontWeight.Bold)
      }

      Spacer(Modifier.height(8.dp))
      OutlinedButton(
        onClick = { showHowToInstall = true },
        border = BorderStroke(1.dp, InkBlack),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("how_to_install_button"),
      ) {
        Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("How to Install (Unknown Sources Guide)", color = InkBlack, fontWeight = FontWeight.Bold)
      }

      Spacer(Modifier.height(10.dp))
      Text(
        text = "Why downloads fail when sharing from GitHub / Chrome:\n" +
          "1. Private GitHub repo: Other people get 404/Login in Chrome. Set the repo to Public on GitHub (Settings -> Change visibility -> Public).\n" +
          "2. Never share Chrome's temporary download URL or an Actions .zip link: Chrome download URLs expire in minutes, and Actions artifacts require GitHub login. Always share the Release link above or tap 'Share Installable .apk File'.\n" +
          "3. WhatsApp / Realme / Oppo / Xiaomi parse error: Save the .apk and open it from your Files / Downloads app instead of tapping inside WhatsApp.",
        style = MaterialTheme.typography.bodyMedium,
        color = InkGraphite,
      )
      Spacer(Modifier.height(18.dp))
    }
  }
}
