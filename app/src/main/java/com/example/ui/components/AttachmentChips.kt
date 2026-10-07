package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.files.AttachedFile
import com.example.files.FileCategory
import com.example.ui.theme.NeonPinkPrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun AttachedFileChip(
  file: AttachedFile,
  onRemove: (() -> Unit)? = null,
  onInspectZip: ((AttachedFile) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(SurfaceCard)
      .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
      .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (file.category == FileCategory.IMAGE && file.previewBitmap != null) {
      Image(
        bitmap = file.previewBitmap.asImageBitmap(),
        contentDescription = file.fileName,
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .size(34.dp)
          .clip(RoundedCornerShape(8.dp))
      )
    } else {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(NeonPinkPrimary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
      ) {
        val icon = when (file.category) {
          FileCategory.ZIP -> Icons.Default.FolderZip
          FileCategory.IMAGE -> Icons.Default.Image
          FileCategory.CODE -> Icons.Default.Code
          else -> Icons.Default.Description
        }
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = NeonPinkPrimary,
          modifier = Modifier.size(18.dp)
        )
      }
    }

    Spacer(modifier = Modifier.width(8.dp))

    Column {
      Text(
        text = file.fileName,
        color = TextPrimary,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = if (file.category == FileCategory.ZIP) "${file.sizeFormatted} • ${file.zipEntriesCount} فایل" else file.sizeFormatted,
          color = TextSecondary,
          fontSize = 10.5.sp
        )
        if (file.category == FileCategory.ZIP && onInspectZip != null) {
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "کاوش درختی 🔍",
            color = TextPink,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
              .clickable { onInspectZip(file) }
              .testTag("inspect_zip_chip")
          )
        }
      }
    }

    if (onRemove != null) {
      Spacer(modifier = Modifier.width(6.dp))
      IconButton(
        onClick = onRemove,
        modifier = Modifier
          .size(26.dp)
          .testTag("remove_attachment_button")
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "حذف",
          tint = TextTertiary,
          modifier = Modifier.size(14.dp)
        )
      }
    }
  }
}
