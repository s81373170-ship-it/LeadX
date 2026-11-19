package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkGraphite
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftAlabaster
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.ceil

data class QrCodeMatrix(
  val dimension: Int,
  val modules: BooleanArray,
) {
  fun isDark(row: Int, col: Int): Boolean {
    if (row !in 0 until dimension || col !in 0 until dimension) return false
    return modules[row * dimension + col]
  }
}

fun encodeQrCodeMatrix(content: String): QrCodeMatrix? {
  val trimmed = content.trim()
  if (trimmed.isEmpty()) return null
  return runCatching {
    val hints =
      mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 0,
        EncodeHintType.CHARACTER_SET to "UTF-8",
      )
    val bitMatrix = QRCodeWriter().encode(trimmed, BarcodeFormat.QR_CODE, 0, 0, hints)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val size = maxOf(width, height)
    val dark = BooleanArray(size * size)
    for (y in 0 until height) {
      for (x in 0 until width) {
        if (bitMatrix[x, y]) {
          dark[y * size + x] = true
        }
      }
    }
    QrCodeMatrix(dimension = size, modules = dark)
  }.getOrNull()
}

@Composable
fun ApkDownloadQrCard(
  url: String,
  onCopyUrl: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val matrix = remember(url) { encodeQrCodeMatrix(url) }

  Surface(
    shape = MaterialTheme.shapes.small,
    color = SoftAlabaster,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = modifier.fillMaxWidth().testTag("apk_download_qr_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Rounded.QrCode2,
            contentDescription = null,
            tint = InkBlack,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(6.dp))
          SectionLabel("Scan with Android Camera", color = InkBlack)
        }
        MonochromeBadgeTag(text = "DIRECT APK", inverted = true)
      }

      Spacer(Modifier.height(10.dp))

      if (matrix != null) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = PureWhite,
          border = BorderStroke(1.dp, InkBlack),
          modifier =
            Modifier.widthIn(max = 200.dp)
              .fillMaxWidth(0.62f)
              .aspectRatio(1f)
              .semantics { contentDescription = "QR code for direct LeadX Pro APK download" }
              .testTag("apk_download_qr_code"),
        ) {
          Box(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            contentAlignment = Alignment.Center,
          ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              val dim = matrix.dimension
              val cellSize = size.minDimension / dim.toFloat()
              val offsetX = (size.width - cellSize * dim) / 2f
              val offsetY = (size.height - cellSize * dim) / 2f
              val drawSize = Size(ceil(cellSize), ceil(cellSize))
              for (row in 0 until dim) {
                for (col in 0 until dim) {
                  if (matrix.isDark(row, col)) {
                    drawRect(
                      color = InkBlack,
                      topLeft = Offset(offsetX + col * cellSize, offsetY + row * cellSize),
                      size = drawSize,
                    )
                  }
                }
              }
            }
          }
        }
      }

      Spacer(Modifier.height(10.dp))

      Text(
        text = "Point another phone's camera at this QR code to open Chrome and start downloading LeadX-Pro.apk immediately.",
        style = MaterialTheme.typography.bodySmall,
        color = InkGraphite,
      )

      Spacer(Modifier.height(8.dp))

      Surface(
        shape = RoundedCornerShape(8.dp),
        color = PureWhite,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = url,
            fontFamily = MonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = InkBlack,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          IconButton(
            onClick = onCopyUrl,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("qr_copy_link_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.ContentCopy,
              contentDescription = "Copy direct APK download URL",
              tint = InkBlack,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }
  }
}
