package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeBlockBg
import com.example.ui.theme.CodeBlockHeaderBg
import com.example.ui.theme.NeonPinkPrimary
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CodeBlockView(
  code: String,
  language: String = "code",
  modifier: Modifier = Modifier
) {
  val clipboardManager = LocalClipboardManager.current
  val context = LocalContext.current

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(CodeBlockBg)
      .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
  ) {
    // Header Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(CodeBlockHeaderBg)
        .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Terminal,
          contentDescription = null,
          tint = NeonPinkPrimary,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = language.ifEmpty { "source" },
          color = TextSecondary,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          fontFamily = FontFamily.Monospace
        )
      }

      IconButton(
        onClick = {
          clipboardManager.setText(AnnotatedString(code))
          Toast.makeText(context, "کد در حافظه کپی شد", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier
          .size(32.dp)
          .testTag("copy_code_button")
      ) {
        Icon(
          imageVector = Icons.Default.ContentCopy,
          contentDescription = "کپی کد",
          tint = TextSecondary,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    // Code Content
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(14.dp)
    ) {
      Text(
        text = code,
        color = TextPrimary,
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
        fontFamily = FontFamily.Monospace
      )
    }
  }
}
