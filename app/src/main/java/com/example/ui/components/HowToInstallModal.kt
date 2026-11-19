package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ApkShareHelper
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkGraphite
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianMuted
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftAlabaster
import com.example.ui.theme.SuccessDot
import com.example.ui.theme.SuccessInk
import com.example.ui.theme.SuccessSurface
import com.example.ui.theme.WarningInk
import com.example.ui.theme.WarningSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToInstallModal(
  onDismiss: () -> Unit,
  onFeedback: (String, Boolean) -> Unit,
) {
  val context = LocalContext.current

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
          .padding(horizontal = 20.dp, vertical = 12.dp)
          .testTag("how_to_install_modal"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SectionLabel("Android Sideload Guide", color = InkGraphite)
          Spacer(Modifier.height(2.dp))
          Text(
            text = "How to Install LeadX Pro (.apk)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = InkBlack,
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("close_how_to_install_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Close install guide",
            tint = InkBlack,
          )
        }
      }

      Spacer(Modifier.height(10.dp))

      Surface(
        shape = MaterialTheme.shapes.small,
        color = WarningSurface,
        border = BorderStroke(1.dp, WarningInk.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Icon(
            imageVector = Icons.Rounded.Security,
            contentDescription = null,
            tint = WarningInk,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(10.dp))
          Text(
            text = "Why shared APKs look blocked: Android blocks installs from Chrome, Files, or WhatsApp until 'Install from unknown sources' ('Allow from this source') is enabled once for that app.",
            style = MaterialTheme.typography.bodyMedium,
            color = WarningInk,
          )
        }
      }

      Spacer(Modifier.height(14.dp))

      InstallStepCard(
        stepNumber = "01",
        title = "Download or open LeadX-Pro.apk in Chrome / Files",
        description =
          "Open the downloaded .apk from Chrome Downloads or your phone's Files app. On Realme, Oppo (ColorOS), or Xiaomi phones, avoid tapping the .apk directly inside WhatsApp — save/open it from Files instead.",
        icon = Icons.Rounded.Download,
      )

      Spacer(Modifier.height(10.dp))

      InstallStepCard(
        stepNumber = "02",
        title = "Enable 'Install from unknown sources'",
        description =
          "When Android displays \"For your security, your phone isn't allowed to install unknown apps from this source\", tap Settings and turn ON 'Allow from this source' for Chrome or Files.\nManual path: Settings -> Apps -> Special app access -> Install unknown apps -> Select Chrome / Files -> Allow.",
        icon = Icons.Rounded.AdminPanelSettings,
      )

      Spacer(Modifier.height(10.dp))

      InstallStepCard(
        stepNumber = "03",
        title = "Tap Back & Install Universal APK",
        description =
          "Return to the install prompt and tap Install. LeadX Pro is built as a single standard Universal .apk (all CPU architectures included, not an .aab app bundle) so it installs directly on Android 7.0+ devices.",
        icon = Icons.Rounded.CheckCircle,
      )

      Spacer(Modifier.height(12.dp))

      Surface(
        shape = MaterialTheme.shapes.small,
        color = SuccessSurface,
        border = BorderStroke(1.dp, SuccessInk.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          StatusPill(
            text = "UNIVERSAL APK",
            containerColor = PureWhite,
            contentColor = SuccessInk,
            dotColor = SuccessDot,
          )
          Spacer(Modifier.width(10.dp))
          Text(
            text = "If Google Play Protect prompts, tap More details -> Install anyway.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkBlack,
          )
        }
      }

      Spacer(Modifier.height(14.dp))

      Button(
        onClick = {
          val opened = ApkShareHelper.openUnknownAppSourcesSettings(context)
          if (!opened) {
            onFeedback("Open Settings -> Apps -> Special app access -> Install unknown apps.", false)
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("open_unknown_sources_settings_button"),
      ) {
        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Open 'Install Unknown Apps' Settings", fontWeight = FontWeight.Bold)
      }

      Spacer(Modifier.height(8.dp))

      OutlinedButton(
        onClick = {
          if (ApkShareHelper.copyInstallGuide(context)) {
            onFeedback("Install guide & download link copied to clipboard.", false)
          } else {
            onFeedback("Clipboard unavailable on this device.", true)
          }
          onDismiss()
        },
        border = BorderStroke(1.dp, InkBlack),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("copy_install_guide_button"),
      ) {
        Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Copy Install Guide for Teammates", color = InkBlack, fontWeight = FontWeight.Bold)
      }

      Spacer(Modifier.height(18.dp))
    }
  }
}

@Composable
private fun InstallStepCard(
  stepNumber: String,
  title: String,
  description: String,
  icon: ImageVector,
) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = SoftAlabaster,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = InkBlack,
        modifier = Modifier.size(34.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(icon, contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
        }
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "STEP $stepNumber",
            fontFamily = MonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = InkGraphite,
          )
          Text(
            text = "·",
            color = ObsidianMuted,
            style = MaterialTheme.typography.labelSmall,
          )
          Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = InkBlack,
          )
        }
        Spacer(Modifier.height(4.dp))
        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          color = InkGraphite,
        )
      }
    }
  }
}
