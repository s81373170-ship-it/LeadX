package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkMuted
import com.example.ui.theme.LeadXTheme
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PaperBackground
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftAlabaster
import com.example.ui.theme.StrongBorder
import com.example.ui.theme.SubtleSurface

private val LocalSkeletonShimmerShift = compositionLocalOf<State<Float>?> { null }

@Composable
fun ProvideSkeletonShimmer(content: @Composable () -> Unit) {
  if (LocalInspectionMode.current) {
    CompositionLocalProvider(
      LocalSkeletonShimmerShift provides mutableFloatStateOf(200f),
      content = content,
    )
    return
  }
  val transition = rememberInfiniteTransition(label = "leadx_skeleton_shimmer")
  val shiftState =
    transition.animateFloat(
      initialValue = -400f,
      targetValue = 1200f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "shimmer_offset",
    )
  CompositionLocalProvider(LocalSkeletonShimmerShift provides shiftState, content = content)
}

@Composable
fun SkeletonBlock(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(8.dp),
  darkSurface: Boolean = false,
) {
  val isPreview = LocalInspectionMode.current
  val baseColor = if (darkSurface) Color(0xFF232329) else SubtleSurface
  val highlightColor = if (darkSurface) Color(0xFF35353E) else PureWhite

  if (isPreview) {
    Box(
      modifier =
        modifier
          .clip(shape)
          .background(
            Brush.linearGradient(
              colors = listOf(baseColor, highlightColor, baseColor),
              start = Offset.Zero,
              end = Offset(600f, 300f),
            )
          ),
    )
    return
  }

  val sharedShift = LocalSkeletonShimmerShift.current
  val shiftState =
    sharedShift
      ?: rememberInfiniteTransition(label = "leadx_skeleton_shimmer_single").animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
          ),
        label = "shimmer_offset_single",
      )

  Box(
    modifier =
      modifier
        .clip(shape)
        .drawBehind {
          val shift = shiftState.value
          drawRect(
            brush =
              Brush.linearGradient(
                colors = listOf(baseColor, highlightColor, baseColor),
                start = Offset(shift, shift * 0.35f),
                end = Offset(shift + 360f, shift * 0.35f + 180f),
              )
          )
        },
  )
}

@Composable
fun WorkspaceSkeletonLoadingScreen(
  statusLabel: String = "Syncing LeadX Workspace...",
  modifier: Modifier = Modifier,
) {
  ProvideSkeletonShimmer {
  Scaffold(
    containerColor = PaperBackground,
    contentWindowInsets = WindowInsets.safeDrawing,
    modifier =
      modifier
        .fillMaxSize()
        .semantics { contentDescription = statusLabel }
        .testTag("workspace_skeleton_loading_screen"),
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(PaperBackground)
          .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 640.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
      ) {
        // 1. Editorial Masthead Skeleton
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            SkeletonBlock(modifier = Modifier.width(148.dp).height(12.dp))
            Spacer(Modifier.height(8.dp))
            SkeletonBlock(modifier = Modifier.width(196.dp).height(26.dp))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              SkeletonBlock(modifier = Modifier.width(72.dp).height(22.dp), shape = RoundedCornerShape(6.dp))
              SkeletonBlock(modifier = Modifier.width(92.dp).height(22.dp), shape = RoundedCornerShape(999.dp))
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
              SkeletonBlock(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(12.dp))
            }
          }
        }

        Spacer(Modifier.height(14.dp))

        // Subtle status pill indicating live hydration
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = SoftAlabaster,
          border = BorderStroke(1.dp, HairlineBorder),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            SkeletonBlock(modifier = Modifier.size(8.dp), shape = CircleShape)
            Text(
              text = statusLabel,
              fontFamily = MonoFontFamily,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = InkMuted,
            )
          }
        }

        Spacer(Modifier.height(14.dp))

        // 2. Monolithic Obsidian Wallet Hero Card Skeleton
        MonolithWalletCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            SkeletonBlock(modifier = Modifier.width(140.dp).height(12.dp), darkSurface = true)
            SkeletonBlock(
              modifier = Modifier.width(108.dp).height(24.dp),
              shape = RoundedCornerShape(999.dp),
              darkSurface = true,
            )
          }
          Spacer(Modifier.height(16.dp))
          SkeletonBlock(modifier = Modifier.width(184.dp).height(36.dp), darkSurface = true)
          Spacer(Modifier.height(12.dp))
          SkeletonBlock(
            modifier = Modifier.fillMaxWidth().height(6.dp),
            shape = RoundedCornerShape(999.dp),
            darkSurface = true,
          )
          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            SkeletonBlock(modifier = Modifier.width(120.dp).height(14.dp), darkSurface = true)
            SkeletonBlock(modifier = Modifier.width(104.dp).height(14.dp), darkSurface = true)
          }
          Spacer(Modifier.height(18.dp))
          SkeletonBlock(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            darkSurface = true,
          )
        }

        Spacer(Modifier.height(12.dp))

        // 3. Two Metric Cards Skeleton
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          repeat(2) {
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = PureWhite,
              border = BorderStroke(1.dp, HairlineBorder),
              modifier = Modifier.weight(1f),
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                SkeletonBlock(modifier = Modifier.width(82.dp).height(11.dp))
                Spacer(Modifier.height(10.dp))
                SkeletonBlock(modifier = Modifier.width(56.dp).height(26.dp))
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(modifier = Modifier.width(96.dp).height(12.dp))
              }
            }
          }
        }

        Spacer(Modifier.height(12.dp))

        // 4. Fetch Action Tiles Skeleton
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          repeat(2) {
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = PureWhite,
              border = BorderStroke(1.dp, HairlineBorder),
              modifier = Modifier.weight(1f),
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  SkeletonBlock(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(10.dp))
                  SkeletonBlock(modifier = Modifier.width(54.dp).height(20.dp), shape = RoundedCornerShape(6.dp))
                }
                Spacer(Modifier.height(14.dp))
                SkeletonBlock(modifier = Modifier.width(98.dp).height(16.dp))
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.85f).height(12.dp))
              }
            }
          }
        }

        Spacer(Modifier.height(12.dp))

        // 5. 24h Auto-Fetch Banner Skeleton
        LeadXCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              SkeletonBlock(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(10.dp))
              Spacer(Modifier.width(12.dp))
              Column {
                SkeletonBlock(modifier = Modifier.width(176.dp).height(16.dp))
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(modifier = Modifier.width(140.dp).height(12.dp))
              }
            }
            SkeletonBlock(modifier = Modifier.width(58.dp).height(24.dp), shape = RoundedCornerShape(999.dp))
          }
        }

        Spacer(Modifier.height(14.dp))

        // 6. Recent Activity / Transactions List Skeleton
        LeadXCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            SkeletonBlock(modifier = Modifier.width(144.dp).height(13.dp))
            SkeletonBlock(modifier = Modifier.width(84.dp).height(16.dp))
          }
          Spacer(Modifier.height(14.dp))
          repeat(3) { index ->
            if (index > 0) {
              Spacer(Modifier.height(8.dp))
              EditorialDivider()
              Spacer(Modifier.height(8.dp))
            }
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.72f).height(15.dp))
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.45f).height(11.dp))
              }
              Spacer(Modifier.width(12.dp))
              Column(horizontalAlignment = Alignment.End) {
                SkeletonBlock(modifier = Modifier.width(56.dp).height(16.dp))
                Spacer(Modifier.height(4.dp))
                SkeletonBlock(modifier = Modifier.width(68.dp).height(11.dp))
              }
            }
          }
        }

        Spacer(Modifier.height(20.dp))
      }
    }
  }
  }
}

@Composable
fun LeadResultsSkeletonSection(
  itemCount: Int = 3,
  title: String = "Fetching Real B2B Leads...",
  modifier: Modifier = Modifier,
) {
  ProvideSkeletonShimmer {
  LeadXCard(
    borderColor = StrongBorder,
    modifier = modifier.testTag("lead_results_skeleton_section"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        SectionLabel(title, color = InkBlack)
        Spacer(Modifier.height(4.dp))
        Text(
          text = "Extracting structured names, roles, companies, and contacts...",
          style = MaterialTheme.typography.bodySmall,
          color = InkMuted,
        )
      }
      MonochromeBadgeTag(text = "LOADING", inverted = true)
    }

    Spacer(Modifier.height(14.dp))

    repeat(itemCount.coerceAtLeast(1)) { idx ->
      if (idx > 0) {
        Spacer(Modifier.height(10.dp))
      }
      Surface(
        shape = MaterialTheme.shapes.small,
        color = SoftAlabaster,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              SkeletonBlock(modifier = Modifier.size(34.dp), shape = CircleShape)
              Spacer(Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.68f).height(15.dp))
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.84f).height(12.dp))
              }
            }
            Spacer(Modifier.width(8.dp))
            SkeletonBlock(modifier = Modifier.size(22.dp), shape = RoundedCornerShape(6.dp))
          }
          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(modifier = Modifier.width(110.dp).height(22.dp), shape = RoundedCornerShape(6.dp))
            SkeletonBlock(modifier = Modifier.width(88.dp).height(22.dp), shape = RoundedCornerShape(6.dp))
          }
        }
      }
    }
  }
  }
}

@Preview(showBackground = true, showSystemUi = true, name = "Workspace Skeleton Loading")
@Composable
private fun WorkspaceSkeletonLoadingPreview() {
  LeadXTheme {
    WorkspaceSkeletonLoadingScreen()
  }
}

@Preview(showBackground = true, name = "Lead Results Skeleton")
@Composable
private fun LeadResultsSkeletonPreview() {
  LeadXTheme {
    Box(modifier = Modifier.background(PaperBackground).padding(16.dp)) {
      LeadResultsSkeletonSection()
    }
  }
}
