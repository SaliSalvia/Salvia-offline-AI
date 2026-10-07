package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.rag.RetrievalResult
import com.example.ui.theme.BackgroundPitchBlack
import com.example.ui.theme.NeonPinkContainer
import com.example.ui.theme.NeonPinkLight
import com.example.ui.theme.NeonPinkPrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceObsidianElevated
import com.example.ui.theme.TextPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TurboActiveGreen
import com.example.viewmodel.MainViewModel

@Composable
fun FilesRagScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  var searchQuery by remember { mutableStateOf("") }

  val filePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    uri?.let { viewModel.attachFileUri(it) }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(BackgroundPitchBlack)
      .padding(16.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "مرکز اسناد، ZIP و RAG آفلاین",
          color = TextPrimary,
          fontSize = 19.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "پارس پرونده‌ها، امنیت ضد Zip-Bomb و بازیابی وکتور کاملاً محلی",
          color = TextSecondary,
          fontSize = 12.sp
        )
      }

      Button(
        onClick = { filePicker.launch(arrayOf("*/*")) },
        colors = ButtonDefaults.buttonColors(containerColor = NeonPinkPrimary),
        shape = RoundedCornerShape(10.dp)
      ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("افزودن سند/ZIP", fontSize = 12.sp)
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Local RAG Summary Box
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = SurfaceCard),
      border = androidx.compose.foundation.BorderStroke(1.dp, NeonPinkPrimary.copy(alpha = 0.4f)),
      shape = RoundedCornerShape(14.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "پایگاه برداری محلی (Vector Store):",
            color = NeonPinkLight,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "${uiState.ragSummary.totalDocuments} سند نمایه شده • شامل ${uiState.ragSummary.totalChunks} بردار متنی چندزبانه",
            color = TextSecondary,
            fontSize = 11.5.sp
          )
          if (uiState.ragSummary.indexedDocumentNames.isNotEmpty()) {
            Text(
              text = "اسناد فعال: " + uiState.ragSummary.indexedDocumentNames.joinToString(", "),
              color = TextTertiary,
              fontSize = 10.5.sp,
              maxLines = 1
            )
          }
        }

        if (uiState.ragSummary.totalChunks > 0) {
          IconButton(
            onClick = { viewModel.clearRagIndex() },
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(SurfaceObsidianElevated)
          ) {
            Icon(Icons.Default.CleaningServices, contentDescription = "پاکسازی", tint = TextTertiary, modifier = Modifier.size(16.dp))
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // RAG Search Field
    OutlinedTextField(
      value = searchQuery,
      onValueChange = {
        searchQuery = it
        if (it.length >= 2) {
          viewModel.executeRagQuery(it)
        }
      },
      placeholder = { Text("پرسش و جستجوی معنایی بین اسناد محلی (RAG)...", color = TextTertiary, fontSize = 12.5.sp) },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonPinkPrimary) },
      modifier = Modifier
        .fillMaxWidth()
        .testTag("rag_search_field"),
      shape = RoundedCornerShape(12.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = SurfaceCard,
        unfocusedContainerColor = SurfaceCard,
        focusedBorderColor = NeonPinkPrimary,
        unfocusedBorderColor = SurfaceCardBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
      ),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(14.dp))

    // Results or Security Status
    if (uiState.ragResults.isNotEmpty()) {
      Text(
        text = "نتایج برتر بازیابی‌شده با Cosine Similarity:",
        color = TextPink,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold
      )
      Spacer(modifier = Modifier.height(8.dp))
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(uiState.ragResults) { result ->
          RagResultItem(result = result)
        }
      }
    } else {
      // Security Architecture & ZIP Capabilities
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = TurboActiveGreen, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "محافظت امنیتی محلی فایل‌ها (Security Guard)",
              color = TurboActiveGreen,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "• محافظت در برابر Zip Slip: بررسی دقیق مسیر کانونیکال و جلوگیری از فرار فایل از دایرکتوری سندباکس.\n" +
                   "• محافظت در برابر Zip Bomb: محدودیت سقف بازگشایی حداکثر ۱۵۰ مگابایت و حداکثر ۵۰۰۰ فایل.\n" +
                   "• پردازش محلی ۰-Permission با استفاده از Storage Access Framework بدون ارسال حتی ۱ بیت به کلود.",
            color = TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 18.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "پرونده‌های ضمیمه‌شده در حافظه:",
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
      )
      Spacer(modifier = Modifier.height(8.dp))

      if (uiState.attachedFiles.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp)),
          contentAlignment = Alignment.Center
        ) {
          Text("هنوز فایلی وارد نشده است. دکمه «افزودن سند/ZIP» را بزنید.", color = TextTertiary, fontSize = 12.sp)
        }
      } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(uiState.attachedFiles) { file ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = if (file.category == com.example.files.FileCategory.ZIP) Icons.Default.FolderZip else Icons.Default.Description,
                  contentDescription = null,
                  tint = NeonPinkPrimary,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(text = file.fileName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                  Text(text = "${file.sizeFormatted} • ${file.category.label}", color = TextSecondary, fontSize = 11.sp)
                }
              }

              if (file.category == com.example.files.FileCategory.ZIP) {
                Button(
                  onClick = { viewModel.inspectZipDetail(file) },
                  colors = ButtonDefaults.buttonColors(containerColor = NeonPinkContainer),
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Text("کاوش درختی 🔍", color = NeonPinkLight, fontSize = 11.sp)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RagResultItem(result: RetrievalResult) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
    shape = RoundedCornerShape(10.dp)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = result.chunk.documentName,
          color = NeonPinkLight,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold
        )
        val scorePercent = (result.similarityScore * 100).toInt().coerceIn(0, 100)
        Text(
          text = "تطابق معنایی: $scorePercent%",
          color = TurboActiveGreen,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = result.chunk.text,
        color = TextPrimary,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 17.sp
      )
    }
  }
}
