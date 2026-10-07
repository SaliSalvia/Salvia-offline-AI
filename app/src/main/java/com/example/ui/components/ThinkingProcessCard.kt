package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonPinkLight
import com.example.ui.theme.NeonPinkPrimary
import com.example.ui.theme.ReasoningBorder
import com.example.ui.theme.ReasoningBoxBg
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun ThinkingProcessCard(
  thinkingText: String,
  isStreaming: Boolean,
  durationMs: Long,
  tokensPerSec: Float,
  modifier: Modifier = Modifier
) {
  var isExpanded by remember(isStreaming) {
    // Keep expanded while streaming, collapse or user controllable when finished
    mutableStateOf(isStreaming)
  }

  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseAlpha"
  )

  val durationSeconds = String.format("%.1f", durationMs / 1000f)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(ReasoningBoxBg)
      .border(1.dp, if (isStreaming) NeonPinkPrimary.copy(alpha = 0.5f) else ReasoningBorder, RoundedCornerShape(14.dp))
      .padding(12.dp)
  ) {
    // Header Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { isExpanded = !isExpanded }
        .testTag("toggle_thinking_button"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(NeonPinkPrimary.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isStreaming) Icons.Default.AutoAwesome else Icons.Default.Psychology,
            contentDescription = "فرآیند تفکر",
            tint = NeonPinkPrimary,
            modifier = Modifier
              .size(16.dp)
              .then(if (isStreaming) Modifier.alpha(pulseAlpha) else Modifier)
          )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
          Text(
            text = if (isStreaming) "در حال تفکر عمیق (Deep Thinking)..." else "فکر کرد به مدت $durationSeconds ثانیه",
            color = if (isStreaming) NeonPinkLight else TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )
          if (tokensPerSec > 0f) {
            Text(
              text = "سرعت استنتاج: ${tokensPerSec} توکن/ثانیه ⚡",
              color = TextTertiary,
              fontSize = 11.sp
            )
          }
        }
      }

      Icon(
        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = if (isExpanded) "بستن تفکر" else "مشاهده تفکر",
        tint = TextSecondary,
        modifier = Modifier.size(20.dp)
      )
    }

    // Expandable content
    AnimatedVisibility(
      visible = isExpanded,
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 10.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SurfaceCardBorder)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
          text = thinkingText.ifEmpty { "در حال جمع‌بندی استدلال‌ها..." },
          color = TextSecondary,
          fontSize = 12.5.sp,
          lineHeight = 20.sp,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier.padding(horizontal = 4.dp)
        )
      }
    }
  }
}
