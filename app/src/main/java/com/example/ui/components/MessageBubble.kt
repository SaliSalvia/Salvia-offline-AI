package com.example.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.localization.AppLanguage
import com.example.core.translation.OfflineTranslator
import com.example.data.ChatMessageEntity
import com.example.ui.theme.BackgroundPitchBlack
import com.example.ui.theme.NeonPinkContainer
import com.example.ui.theme.NeonPinkLight
import com.example.ui.theme.NeonPinkPrimary
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceObsidianElevated
import com.example.ui.theme.TextPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MessageBubble(
  message: ChatMessageEntity,
  currentLanguage: AppLanguage = AppLanguage.FA,
  onInspectZip: ((String) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val isUser = message.role == "user"
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val coroutineScope = rememberCoroutineScope()

  var isTranslating by remember { mutableStateOf(false) }
  var translatedPersianText by remember { mutableStateOf<String?>(null) }
  var showTranslation by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 6.dp),
    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
  ) {
    if (isUser) {
      // User Message Box (Dark Pink / Cyber style)
      Column(
        modifier = Modifier
          .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
          .background(NeonPinkContainer)
          .border(1.dp, NeonPinkPrimary.copy(alpha = 0.4f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
          .padding(horizontal = 14.dp, vertical = 10.dp)
      ) {
        if (!message.attachmentsJson.isNullOrEmpty()) {
          val parts = message.attachmentsJson.split(";")
          for (part in parts) {
            val tokens = part.split(":")
            if (tokens.size >= 2) {
              val cat = tokens[0]
              val name = tokens[1]
              val size = tokens.getOrNull(2) ?: ""
              Text(
                text = "📎 [$cat] $name ($size)",
                color = NeonPinkPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp)
              )
            }
          }
        }

        Text(
          text = message.content,
          color = TextPrimary,
          fontSize = 14.5.sp,
          lineHeight = 22.sp
        )
      }
    } else {
      // Assistant DeepSeek / ChatGPT Style Message
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
      ) {
        // AI Neon Brain Avatar
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(SurfaceObsidianElevated)
            .border(1.dp, NeonPinkPrimary.copy(alpha = 0.5f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "DeepGGUF AI",
            tint = NeonPinkPrimary,
            modifier = Modifier.size(17.dp)
          )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
          // Thinking Process Card (if model logged reasoning thoughts)
          if (!message.thinkingContent.isNullOrEmpty()) {
            ThinkingProcessCard(
              thinkingText = message.thinkingContent,
              isStreaming = false,
              durationMs = message.thinkingDurationMs,
              tokensPerSec = message.tokensPerSecond,
              modifier = Modifier.padding(bottom = 10.dp)
            )
          }

          // Generated Image Display (if any)
          if (!message.imageResultUri.isNullOrEmpty()) {
            val imgFile = File(message.imageResultUri)
            if (imgFile.exists()) {
              AsyncImage(
                model = imgFile,
                contentDescription = "تصویر تولید شده با هوش مصنوعی آفلاین",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(280.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .border(1.dp, NeonPinkPrimary.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
              )
              Spacer(modifier = Modifier.height(10.dp))
            }
          }

          // Rendered Markdown Content
          MarkdownContent(rawText = message.content)

          // Footer Action Bar (Copy & Share)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
          ) {
            IconButton(
              onClick = {
                clipboardManager.setText(AnnotatedString(message.content))
                Toast.makeText(context, "متن در حافظه کپی شد", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier
                .size(28.dp)
                .testTag("copy_message_button")
            ) {
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "کپی",
                tint = TextSecondary,
                modifier = Modifier.size(15.dp)
              )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
              onClick = {
                val sendIntent = Intent().apply {
                  action = Intent.ACTION_SEND
                  putExtra(Intent.EXTRA_TEXT, message.content)
                  type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "اشتراک‌گذاری پاسخ هوش مصنوعی"))
              },
              modifier = Modifier
                .size(28.dp)
                .testTag("share_message_button")
            ) {
              Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "اشتراک‌گذاری",
                tint = TextSecondary,
                modifier = Modifier.size(15.dp)
              )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 🌐 Translate to Persian Button (Prominent in English mode)
            Surface(
              onClick = {
                if (translatedPersianText == null) {
                  coroutineScope.launch {
                    isTranslating = true
                    translatedPersianText = OfflineTranslator.translateToPersian(message.content)
                    isTranslating = false
                    showTranslation = true
                  }
                } else {
                  showTranslation = !showTranslation
                }
              },
              shape = RoundedCornerShape(12.dp),
              color = if (showTranslation) NeonPinkContainer else SurfaceObsidianElevated,
              border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (currentLanguage == AppLanguage.EN || showTranslation) NeonPinkPrimary else SurfaceCardBorder
              ),
              modifier = Modifier
                .height(28.dp)
                .testTag("translate_to_persian_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (isTranslating) {
                  CircularProgressIndicator(
                    color = NeonPinkPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(12.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(text = "در حال ترجمه...", color = NeonPinkLight, fontSize = 10.5.sp)
                } else {
                  Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = "ترجمه به فارسی",
                    tint = if (showTranslation || currentLanguage == AppLanguage.EN) NeonPinkPrimary else TextSecondary,
                    modifier = Modifier.size(13.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (showTranslation) "بستن ترجمه 🇮🇷" else "ترجمه فارسی 🇮🇷",
                    color = if (showTranslation || currentLanguage == AppLanguage.EN) NeonPinkLight else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                }
              }
            }

            if (message.tokensPerSecond > 0f) {
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "${message.tokensPerSecond} t/s",
                color = TextSecondary,
                fontSize = 11.sp
              )
            }
          }

          // 🇮🇷 Persian Translation Output Card
          AnimatedVisibility(visible = showTranslation && !translatedPersianText.isNullOrEmpty()) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 10.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(SurfaceObsidianElevated)
                  .border(1.dp, NeonPinkPrimary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                  .padding(12.dp)
                  .testTag("translated_persian_card")
              ) {
                Column {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text("🇮🇷", fontSize = 13.sp)
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "ترجمه دقیق و روان به فارسی",
                        color = NeonPinkLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                    IconButton(
                      onClick = {
                        clipboardManager.setText(AnnotatedString(translatedPersianText ?: ""))
                        Toast.makeText(context, "ترجمه فارسی کپی شد", Toast.LENGTH_SHORT).show()
                      },
                      modifier = Modifier.size(24.dp)
                    ) {
                      Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "کپی ترجمه",
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = translatedPersianText ?: "",
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    lineHeight = 22.sp
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
