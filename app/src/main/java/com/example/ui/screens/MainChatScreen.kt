package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.files.AttachedFile
import com.example.hardware.PerformanceMode
import com.example.ui.components.AttachedFileChip
import com.example.ui.components.MessageBubble
import com.example.ui.components.ModelManagerDialog
import com.example.ui.components.ThinkingProcessCard
import com.example.ui.components.XiaomiTuningDialog
import com.example.ui.components.ZipViewerDialog
import com.example.ui.theme.BackgroundPitchBlack
import com.example.ui.theme.HyperOsOrange
import com.example.ui.theme.NeonPinkContainer
import com.example.ui.theme.NeonPinkLight
import com.example.ui.theme.NeonPinkPrimary
import com.example.ui.theme.PerformanceBeastRed
import com.example.ui.theme.ReasoningBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceObsidian
import com.example.ui.theme.SurfaceObsidianElevated
import com.example.ui.theme.TextPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.TurboActiveGreen
import com.example.core.localization.AppLanguage
import com.example.core.localization.AppStrings
import com.example.viewmodel.AppScreen
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  val coroutineScope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val listState = rememberLazyListState()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  var inputText by remember { mutableStateOf("") }
  var showAttachMenu by remember { mutableStateOf(false) }

  // File pickers using Storage Access Framework (SAF)
  val filePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    uri?.let { viewModel.attachFileUri(it) }
  }

  val ggufPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    uri?.let {
      viewModel.loadGgufFromUri(it, "imported-model.gguf", "3.8 GB", "TEXT")
    }
  }

  // Handle back button for drawer
  BackHandler(enabled = drawerState.isOpen) {
    coroutineScope.launch { drawerState.close() }
  }

  // Handle back button to return to CHAT screen from sub-screens
  BackHandler(enabled = !drawerState.isOpen && uiState.currentScreen != AppScreen.CHAT) {
    viewModel.switchScreen(AppScreen.CHAT)
  }

  // Auto-scroll when new messages arrive or when streaming
  LaunchedEffect(uiState.messages.size, uiState.liveAnswer.length, uiState.liveThinking.length) {
    if (uiState.currentScreen == AppScreen.CHAT && (uiState.messages.isNotEmpty() || uiState.isStreaming)) {
      listState.animateScrollToItem(
        maxOf(0, uiState.messages.size + (if (uiState.isStreaming) 1 else 0) - 1)
      )
    }
  }

  // Status snackbar
  LaunchedEffect(uiState.statusNotice) {
    uiState.statusNotice?.let { notice ->
      snackbarHostState.showSnackbar(notice)
      viewModel.dismissStatusNotice()
    }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet(
        drawerContainerColor = SurfaceObsidian,
        drawerContentColor = TextPrimary,
        modifier = Modifier.width(310.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
        ) {
          // Drawer Header
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(NeonPinkPrimary),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = BackgroundPitchBlack,
                modifier = Modifier.size(22.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = AppStrings.appTitle(uiState.appLanguage),
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = AppStrings.drawerHeaderSubtitle(uiState.appLanguage),
                color = NeonPinkLight,
                fontSize = 11.5.sp
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // New Chat Button
          Surface(
            onClick = {
              viewModel.createNewSession()
              viewModel.switchScreen(AppScreen.CHAT)
              coroutineScope.launch { drawerState.close() }
            },
            shape = RoundedCornerShape(12.dp),
            color = NeonPinkContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPinkPrimary.copy(alpha = 0.5f)),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("new_chat_button")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.AddComment, contentDescription = null, tint = NeonPinkPrimary)
              Spacer(modifier = Modifier.width(10.dp))
              Text(AppStrings.newChat(uiState.appLanguage), color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            }
          }

          Spacer(modifier = Modifier.height(16.dp))
          Text(AppStrings.recentSessions(uiState.appLanguage), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
          Spacer(modifier = Modifier.height(8.dp))

          // Sessions List
          LazyColumn(modifier = Modifier.weight(1f)) {
            items(uiState.sessions) { session ->
              val isSelected = session.id == uiState.currentSession?.id
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 3.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(if (isSelected) SurfaceCard else Color.Transparent)
                  .border(1.dp, if (isSelected) NeonPinkPrimary else Color.Transparent, RoundedCornerShape(10.dp))
                  .clickable {
                    viewModel.selectSession(session)
                    viewModel.switchScreen(AppScreen.CHAT)
                    coroutineScope.launch { drawerState.close() }
                  }
                  .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = session.title,
                  color = if (isSelected) NeonPinkLight else TextSecondary,
                  fontSize = 13.sp,
                  fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f)
                )
                if (uiState.sessions.size > 1) {
                  IconButton(
                    onClick = { viewModel.deleteSession(session.id) },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(
                      Icons.Default.Delete,
                      contentDescription = "حذف گفت‌وگو",
                      tint = TextTertiary,
                      modifier = Modifier.size(14.dp)
                    )
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Drawer Footer Quick Specs
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(SurfaceCard)
              .padding(12.dp)
          ) {
            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = uiState.deviceProfile?.deviceModel ?: "Redmi Note 14 Pro",
                  color = HyperOsOrange,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "Android 16",
                  color = TextTertiary,
                  fontSize = 10.5.sp
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "رم آزاد: ${uiState.deviceProfile?.availRamGb ?: "5.2"} GB / ${uiState.deviceProfile?.totalRamGb ?: "12.0"} GB",
                color = TextSecondary,
                fontSize = 11.sp
              )
              Text(
                text = "چیپست: Helio G100-Ultra / Mali-G57",
                color = NeonPinkPrimary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }
    }
  ) {
    Scaffold(
      modifier = modifier
        .fillMaxSize()
        .background(BackgroundPitchBlack),
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        // Universal AI Hub Top Bar & Navigation Bar
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceObsidian)
            .statusBarsPadding()
        ) {
          // Top Bar
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            IconButton(
              onClick = { coroutineScope.launch { drawerState.open() } },
              modifier = Modifier.testTag("drawer_menu_button")
            ) {
              Icon(Icons.Default.Menu, contentDescription = "منو", tint = TextPrimary)
            }

            // Model Pill
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceObsidianElevated)
                .border(1.dp, NeonPinkPrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .clickable { viewModel.toggleModelManager(true) }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("model_selector_pill"),
              contentAlignment = Alignment.Center
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Memory,
                  contentDescription = null,
                  tint = NeonPinkPrimary,
                  modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val activeModelLabel = uiState.textSlotModel.modelName.take(18)
                Text(
                  text = activeModelLabel,
                  color = TextPrimary,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "[Q4_K_M]",
                  color = NeonPinkLight,
                  fontSize = 10.5.sp
                )
              }
            }

            // Xiaomi Turbo Pill
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceObsidianElevated)
                .border(1.dp, HyperOsOrange.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .clickable { viewModel.toggleXiaomiTuning(true) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("xiaomi_turbo_pill"),
              contentAlignment = Alignment.Center
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                      when (uiState.performanceMode) {
                        PerformanceMode.HYPER_TURBO -> PerformanceBeastRed
                        PerformanceMode.BALANCED -> TurboActiveGreen
                        PerformanceMode.ECO_BATTERY -> HyperOsOrange
                      }
                    )
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                  text = "Turbo 🚀",
                  color = HyperOsOrange,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            // Language Switcher Pill (Bilingual FA / EN)
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceObsidianElevated)
                .border(1.dp, NeonPinkPrimary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .clickable { viewModel.toggleLanguage() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("language_toggle_pill"),
              contentAlignment = Alignment.Center
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = if (uiState.appLanguage == AppLanguage.FA) "🇮🇷 فارسی" else "🇬🇧 English",
                  color = NeonPinkLight,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }

          // Top Hub Navigation Tabs
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            val tabs = listOf(
              Pair(AppScreen.CHAT, AppStrings.tabChat(uiState.appLanguage)),
              Pair(AppScreen.MODELS, AppStrings.tabModels(uiState.appLanguage)),
              Pair(AppScreen.FILES, AppStrings.tabFiles(uiState.appLanguage)),
              Pair(AppScreen.IMAGE, AppStrings.tabImage(uiState.appLanguage)),
              Pair(AppScreen.VOICE, AppStrings.tabVoice(uiState.appLanguage)),
              Pair(AppScreen.PERFORMANCE, AppStrings.tabPerformance(uiState.appLanguage)),
              Pair(AppScreen.SETTINGS, AppStrings.tabSettings(uiState.appLanguage))
            )

            tabs.forEach { (screen, label) ->
              val isSelected = uiState.currentScreen == screen
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(if (isSelected) NeonPinkContainer else SurfaceCard)
                  .border(1.dp, if (isSelected) NeonPinkPrimary else SurfaceCardBorder, RoundedCornerShape(12.dp))
                  .clickable { viewModel.switchScreen(screen) }
                  .padding(horizontal = 12.dp, vertical = 5.dp)
                  .testTag("nav_${screen.name.lowercase()}"),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = label,
                  color = if (isSelected) NeonPinkLight else TextSecondary,
                  fontSize = 11.5.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
              }
            }
          }
        }
      },
      bottomBar = {
        // Show Chat Bottom Bar only when in CHAT screen
        if (uiState.currentScreen == AppScreen.CHAT) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .background(SurfaceObsidian)
              .navigationBarsPadding()
              .imePadding()
              .padding(horizontal = 12.dp, vertical = 8.dp)
          ) {
            // Resource Guard Alert Banner (Rule 9: Hard Stop at 80% with Cooldown)
            if (uiState.resourceSnapshot?.isAiExecutionAllowed == false) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(bottom = 6.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(PerformanceBeastRed.copy(alpha = 0.2f))
                  .border(1.dp, PerformanceBeastRed, RoundedCornerShape(8.dp))
                  .padding(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Text(
                  text = "🛑 AI Paused — Resource limit reached (${uiState.resourceSnapshot?.activeConstraintReason ?: "سقف ۸۰٪ منابع"}). در حال خنک‌سازی و بازگشت خودکار به زیر ۷۰٪.",
                  color = PerformanceBeastRed,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            // Intelligent Router Notice (if activated)
            if (uiState.routerNotice != null) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(bottom = 6.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(NeonPinkContainer)
                  .padding(horizontal = 10.dp, vertical = 4.dp)
              ) {
                Text(
                  text = "🎯 مسیریاب هوشمند AI Router: ${uiState.routerNotice}",
                  color = NeonPinkLight,
                  fontSize = 11.sp
                )
              }
            }

            // Attachment Preview Tray
            if (uiState.attachedFiles.isNotEmpty()) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState())
                  .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                uiState.attachedFiles.forEach { file ->
                  AttachedFileChip(
                    file = file,
                    onRemove = { viewModel.removeAttachedFile(file) },
                    onInspectZip = { viewModel.inspectZipDetail(file) }
                  )
                }
              }
            }

            // Input Row
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Attachment Menu Button
              Box {
                IconButton(
                  onClick = { showAttachMenu = true },
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SurfaceObsidianElevated)
                    .border(1.dp, SurfaceCardBorder, CircleShape)
                    .testTag("attach_menu_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "پیوست فایل",
                    tint = NeonPinkPrimary,
                    modifier = Modifier.size(20.dp)
                  )
                }

                DropdownMenu(
                  expanded = showAttachMenu,
                  onDismissRequest = { showAttachMenu = false },
                  modifier = Modifier.background(SurfaceObsidianElevated)
                ) {
                  DropdownMenuItem(
                    text = { Text("📦 فایل فشرده ZIP (پارس و RAG)", color = TextPrimary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null, tint = NeonPinkPrimary) },
                    onClick = {
                      showAttachMenu = false
                      filePicker.launch(arrayOf("application/zip", "application/x-zip-compressed"))
                    }
                  )
                  DropdownMenuItem(
                    text = { Text("🖼️ تصویر / عکس (تحلیل Vision)", color = TextPrimary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, tint = NeonPinkPrimary) },
                    onClick = {
                      showAttachMenu = false
                      filePicker.launch(arrayOf("image/*"))
                    }
                  )
                  DropdownMenuItem(
                    text = { Text("📄 سند متنی، کد یا PDF", color = TextPrimary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = NeonPinkPrimary) },
                    onClick = {
                      showAttachMenu = false
                      filePicker.launch(arrayOf("text/*", "application/json", "application/pdf", "*/*"))
                    }
                  )
                  DropdownMenuItem(
                    text = { Text("🧠 بارگذاری مدل هوش مصنوعی .gguf", color = TextPink, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null, tint = TextPink) },
                    onClick = {
                      showAttachMenu = false
                      ggufPicker.launch(arrayOf("*/*"))
                    }
                  )
                }
              }

              Spacer(modifier = Modifier.width(8.dp))

              // Main Text Field
              OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                  Text(
                    text = AppStrings.inputPlaceholder(uiState.appLanguage),
                    color = TextTertiary,
                    fontSize = 13.sp
                  )
                },
                modifier = Modifier
                  .weight(1f)
                  .testTag("chat_input_field"),
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedContainerColor = SurfaceObsidianElevated,
                  unfocusedContainerColor = SurfaceObsidianElevated,
                  focusedBorderColor = NeonPinkPrimary,
                  unfocusedBorderColor = SurfaceCardBorder,
                  focusedTextColor = TextPrimary,
                  unfocusedTextColor = TextPrimary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                  if (inputText.isNotEmpty() || uiState.attachedFiles.isNotEmpty()) {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                  }
                }),
                maxLines = 4
              )

              Spacer(modifier = Modifier.width(8.dp))

              // Send / Stop Button
              if (uiState.isStreaming || uiState.isGeneratingImage) {
                IconButton(
                  onClick = { viewModel.stopGeneration() },
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PerformanceBeastRed)
                    .testTag("stop_generation_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "توقف",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                  )
                }
              } else {
                val canSend = inputText.isNotBlank() || uiState.attachedFiles.isNotEmpty()
                IconButton(
                  onClick = {
                    if (canSend) {
                      viewModel.sendMessage(inputText)
                      inputText = ""
                    }
                  },
                  enabled = canSend,
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (canSend) NeonPinkPrimary else SurfaceCard)
                    .testTag("send_button")
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "ارسال",
                    tint = if (canSend) BackgroundPitchBlack else TextTertiary,
                    modifier = Modifier.size(19.dp)
                  )
                }
              }
            }
          }
        }
      }
    ) { innerPadding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .background(BackgroundPitchBlack)
      ) {
        // Route according to selected Hub Screen!
        when (uiState.currentScreen) {
          AppScreen.MODELS -> ModelsHubScreen(viewModel = viewModel)
          AppScreen.FILES -> FilesRagScreen(viewModel = viewModel)
          AppScreen.IMAGE -> ImageHubScreen(viewModel = viewModel)
          AppScreen.VOICE -> VoiceHubScreen(viewModel = viewModel)
          AppScreen.PERFORMANCE -> PerformanceHubScreen(viewModel = viewModel)
          AppScreen.SETTINGS -> SettingsHubScreen(viewModel = viewModel)
          AppScreen.CHAT -> {
            // Main Chat Content
            if (uiState.messages.isEmpty() && !uiState.isStreaming && !uiState.isGeneratingImage) {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                Box(
                  modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(NeonPinkContainer)
                    .border(2.dp, NeonPinkPrimary, CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = NeonPinkPrimary,
                    modifier = Modifier.size(38.dp)
                  )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                  text = AppStrings.appTitle(uiState.appLanguage),
                  color = TextPrimary,
                  fontSize = 19.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = AppStrings.appSubtitle(uiState.appLanguage),
                  color = TextSecondary,
                  fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                val quickActions = if (uiState.appLanguage == AppLanguage.FA) listOf(
                  Triple("📦 تحلیل فایل ZIP با Local RAG", "پارس و کاوش سورس‌کدها در حافظه موقت بدون اینترنت", "این فایل زیپ رو بررسی و خلاصه کن"),
                  Triple("🎨 تصویرساز آفلاین MNN Diffusion", "تولید تصاویر نئونی سایبرپانک بدون سرور", "تصویر یک هوش مصنوعی سایبرپانک مشکی صورتی"),
                  Triple("⚡ استعلام سخت‌افزار Note 14 Pro", "مشاهده وضعیت فرکانس و تله‌متری چیپست", "مشخصات بهینه‌سازی سخت‌افزاری این دستگاه چیست؟"),
                  Triple("🎙️ سنتز گفتار و تبدیل صوت Whisper", "تولید گفتار با Kokoro-TTS و رونویسی صوتی", "این متن را به صورت گفتاری برایم بخوان")
                ) else listOf(
                  Triple("📦 Local RAG ZIP Inspection", "Inspect and search code archives in-memory offline", "Please inspect and summarize this ZIP archive"),
                  Triple("🎨 Offline Neural Image Studio", "Generate cyberpunk neon artwork with zero cloud dependency", "Draw a glowing cyberpunk AI chip in black and pink"),
                  Triple("⚡ Note 14 Pro Hardware Telemetry", "Inspect CPU governor, memory budget & thermal state", "What are the hardware optimization specs of this device?"),
                  Triple("🎙️ Whisper STT & Kokoro TTS", "Voice synthesis and on-device transcription", "Please read this response aloud for me")
                )

                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  quickActions.forEach { item ->
                    Surface(
                      onClick = { inputText = item.third },
                      shape = RoundedCornerShape(12.dp),
                      color = SurfaceCard,
                      border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Column(modifier = Modifier.weight(1f)) {
                          Text(item.first, color = NeonPinkLight, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                          Text(item.second, color = TextSecondary, fontSize = 11.sp)
                        }
                      }
                    }
                  }
                }
              }
            } else {
              LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)
              ) {
                items(uiState.messages, key = { it.id }) { msg ->
                  MessageBubble(
                    message = msg,
                    currentLanguage = uiState.appLanguage,
                    onInspectZip = { zipName ->
                      val found = uiState.attachedFiles.find { it.fileName == zipName }
                      if (found != null) viewModel.inspectZipDetail(found)
                    }
                  )
                }

                if (uiState.isGeneratingImage) {
                  item {
                    Box(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .border(1.dp, NeonPinkPrimary, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                    ) {
                      Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NeonPinkPrimary, strokeWidth = 2.dp)
                          Spacer(modifier = Modifier.width(8.dp))
                          Text(
                            text = "تولید تصویر در موتور انتشار محلی (${uiState.imageProgress?.step ?: 1}/${uiState.imageProgress?.totalSteps ?: 15})...",
                            color = NeonPinkLight,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                          )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = uiState.imageProgress?.currentStage ?: "Denoising...", color = TextSecondary, fontSize = 11.sp)
                      }
                    }
                  }
                }

                if (uiState.isStreaming) {
                  item {
                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                      Row(verticalAlignment = Alignment.Top) {
                        Box(
                          modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SurfaceObsidianElevated)
                            .border(1.dp, NeonPinkPrimary, CircleShape),
                          contentAlignment = Alignment.Center
                        ) {
                          Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonPinkPrimary, modifier = Modifier.size(17.dp))
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                          if (uiState.liveThinking.isNotEmpty()) {
                            ThinkingProcessCard(
                              thinkingText = uiState.liveThinking,
                              isStreaming = uiState.liveAnswer.isEmpty(),
                              durationMs = uiState.liveThinkingDurationMs,
                              tokensPerSec = uiState.liveTokensPerSecond,
                              modifier = Modifier.padding(bottom = 8.dp)
                            )
                          }

                          if (uiState.liveAnswer.isNotEmpty()) {
                            com.example.ui.components.MarkdownContent(rawText = uiState.liveAnswer)
                          } else if (uiState.liveThinking.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                              CircularProgressIndicator(modifier = Modifier.size(15.dp), color = NeonPinkPrimary, strokeWidth = 2.dp)
                              Spacer(modifier = Modifier.width(8.dp))
                              Text(text = "در حال بارگذاری کانتکست و زمان‌بندی هسته‌های ARM64...", color = TextSecondary, fontSize = 11.5.sp)
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Dialogs
  if (uiState.showModelManager) {
    ModelManagerDialog(
      textSlot = uiState.textSlotModel,
      imageSlot = uiState.imageSlotModel,
      onDismiss = { viewModel.toggleModelManager(false) },
      onPickGgufFile = { targetSlot ->
        viewModel.toggleModelManager(false)
        ggufPicker.launch(arrayOf("*/*"))
      },
      onSelectTextPreset = { name, arch, quant, size ->
        viewModel.setPresetTextModel(name, arch, quant, size)
        viewModel.toggleModelManager(false)
      },
      onSelectImagePreset = { name, quant, size ->
        viewModel.setPresetImageModel(name, quant, size)
        viewModel.toggleModelManager(false)
      }
    )
  }

  if (uiState.showXiaomiTuning) {
    XiaomiTuningDialog(
      telemetry = uiState.telemetry,
      currentMode = uiState.performanceMode,
      onSelectMode = { mode ->
        viewModel.setPerformanceMode(mode)
      },
      onDismiss = { viewModel.toggleXiaomiTuning(false) }
    )
  }

  uiState.activeZipDetail?.let { zipFile ->
    ZipViewerDialog(
      zipFile = zipFile,
      onDismiss = { viewModel.inspectZipDetail(null) }
    )
  }
}
