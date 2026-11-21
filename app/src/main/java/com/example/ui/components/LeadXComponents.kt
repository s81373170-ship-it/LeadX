package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.FlashBanner
import com.example.ui.theme.DangerInk
import com.example.ui.theme.DangerSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkCharcoal
import com.example.ui.theme.InkGraphite
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianCardEnd
import com.example.ui.theme.ObsidianCardMid
import com.example.ui.theme.ObsidianCardStart
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftAlabaster
import com.example.ui.theme.SuccessDot
import com.example.ui.theme.SuccessInk
import com.example.ui.theme.SuccessSurface
import com.example.ui.theme.WarningDot
import com.example.ui.theme.WarningInk
import com.example.ui.theme.WarningSurface
import java.text.NumberFormat
import java.util.Locale

fun formatPoints(points: Long): String =
  NumberFormat.getNumberInstance(Locale.US).format(points)

@Composable
fun LeadXCard(
  modifier: Modifier = Modifier,
  borderColor: Color = HairlineBorder,
  containerColor: Color = PureWhite,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = containerColor,
    border = BorderStroke(1.dp, borderColor),
    shadowElevation = 0.dp,
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      content = content,
    )
  }
}

@Composable
fun FlashBannerView(
  banner: FlashBanner?,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = banner != null,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier,
  ) {
    if (banner != null) {
      val bg = if (banner.isError) DangerSurface else InkBlack
      val fg = if (banner.isError) DangerInk else PureWhite
      val border = if (banner.isError) DangerInk.copy(alpha = 0.25f) else InkBlack
      Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).testTag("flash_banner"),
        shape = MaterialTheme.shapes.small,
        color = bg,
        border = BorderStroke(1.dp, border),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = if (banner.isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (banner.isError) DangerInk else SuccessDot,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(10.dp))
          Text(
            text = banner.message,
            color = fg,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
          )
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("dismiss_flash_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = "Dismiss notification",
              tint = fg,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
fun StatusPill(
  text: String,
  containerColor: Color,
  contentColor: Color,
  dotColor: Color? = null,
) {
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = containerColor,
    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.16f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (dotColor != null) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dotColor))
      }
      Text(
        text = text,
        color = contentColor,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
fun MinBalanceBadge(meetsMin: Boolean) {
  if (meetsMin) {
    StatusPill(
      text = "ACTIVE · ≥ 5,000 MIN",
      containerColor = Color(0xFF14261E),
      contentColor = Color(0xFF6EE7B7),
      dotColor = SuccessDot,
    )
  } else {
    StatusPill(
      text = "BELOW 5,000 MIN",
      containerColor = Color(0xFF2B2009),
      contentColor = Color(0xFFFCD34D),
      dotColor = WarningDot,
    )
  }
}

@Composable
fun RolePill(isOwner: Boolean, ownerLoginId: String) {
  StatusPill(
    text = if (isOwner) "OWNER" else "EMPLOYEE · $ownerLoginId",
    containerColor = if (isOwner) InkBlack else SoftAlabaster,
    contentColor = if (isOwner) PureWhite else InkGraphite,
  )
}

@Composable
fun MonolithWalletCard(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  val gradient =
    Brush.linearGradient(
      colors = listOf(ObsidianCardStart, ObsidianCardMid, ObsidianCardEnd)
    )
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
        .background(gradient)
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      color = Color.Transparent,
      shape = MaterialTheme.shapes.large,
      border = BorderStroke(1.dp, ObsidianCardBorder),
    ) {
      Column(modifier = Modifier.padding(20.dp), content = content)
    }
  }
}

@Composable
fun EditorialDivider() {
  HorizontalDivider(thickness = 1.dp, color = HairlineBorder)
}

@Composable
fun SectionLabel(text: String, color: Color = InkGraphite) {
  Text(
    text = text.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    color = color,
    fontWeight = FontWeight.Bold,
  )
}

@Composable
fun MonoBadge(text: String, color: Color = InkBlack) {
  Text(
    text = text,
    fontFamily = MonoFontFamily,
    style = MaterialTheme.typography.bodyMedium,
    fontWeight = FontWeight.Bold,
    color = color,
  )
}

@Composable
fun SegmentedMonochromeTabs(
  tabs: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
  tagPrefix: String = "segment_tab",
) {
  val useCompactText = tabs.size >= 4
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.small,
    color = SoftAlabaster,
    border = BorderStroke(1.dp, HairlineBorder),
  ) {
    Row(
      modifier = Modifier.padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      tabs.forEachIndexed { idx, label ->
        val selected = idx == selectedIndex
        Surface(
          onClick = { onSelect(idx) },
          shape = RoundedCornerShape(8.dp),
          color = if (selected) InkBlack else Color.Transparent,
          modifier = Modifier.weight(1f).height(40.dp).testTag("${tagPrefix}_$idx"),
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
              text = label,
              style = if (useCompactText) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
              color = if (selected) PureWhite else InkGraphite,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              maxLines = 1,
              overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

@Composable
fun ReserveMeterBar(balance: Long, minReserve: Long) {
  val ratio = (balance.toFloat() / (minReserve * 2f)).coerceIn(0f, 1f)
  val meets = balance >= minReserve
  Column(modifier = Modifier.fillMaxWidth()) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(5.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(Color(0xFF27272A)),
    ) {
      Box(
        modifier =
          Modifier.fillMaxWidth(ratio)
            .height(5.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (meets) PureWhite else WarningDot),
      )
    }
  }
}

@Composable
fun MonochromeBadgeTag(
  text: String,
  inverted: Boolean = false,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(6.dp),
    color = if (inverted) InkCharcoal else SoftAlabaster,
    border = BorderStroke(1.dp, if (inverted) InkCharcoal else HairlineBorder),
  ) {
    Text(
      text = text,
      fontFamily = MonoFontFamily,
      style = MaterialTheme.typography.labelSmall,
      color = if (inverted) PureWhite else InkBlack,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun AppVersionBadge(
  inverted: Boolean = false,
  testTag: String = "app_version_badge",
  modifier: Modifier = Modifier,
) {
  val versionLabel =
    if (androidx.compose.ui.platform.LocalInspectionMode.current) {
      "v1.0.2 · DEBUG"
    } else {
      androidx.compose.ui.res.stringResource(
        com.example.R.string.app_version_format,
        com.example.BuildConfig.VERSION_NAME,
        com.example.BuildConfig.VERSION_CODE,
        com.example.BuildConfig.BUILD_TYPE.uppercase(),
      )
    }
  MonochromeBadgeTag(
    text = versionLabel,
    inverted = inverted,
    modifier = modifier.testTag(testTag),
  )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "LeadX Core Components")
@Composable
private fun LeadXComponentsPreview() {
  com.example.ui.theme.LeadXTheme {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      MonolithWalletCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SectionLabel("Wallet Balance · ₹1 = 1 Pt", color = com.example.ui.theme.ObsidianMuted)
          MinBalanceBadge(meetsMin = true)
        }
        Spacer(Modifier.height(10.dp))
        Text(
          text = "25,000 pts",
          fontFamily = MonoFontFamily,
          style = MaterialTheme.typography.displaySmall,
          color = PureWhite,
        )
        Spacer(Modifier.height(8.dp))
        ReserveMeterBar(balance = 25_000L, minReserve = 5_000L)
      }
      AppVersionBadge()
    }
  }
}

