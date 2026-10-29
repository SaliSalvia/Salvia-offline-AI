package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentPurple
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
  var ggufTargetSlot by remember { mutableStateOf("TEXT") }
  var searchQuery by remember { mutableStateOf("") }
  var showChatSearch by remember { mutableStateOf(false) }

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
      if (ggufTargetSlot == "IMAGE") {
        viewModel.rejectImageModelImport()
      } else {
        // Same import path as the Models tab: registers the real file name/size,
        // rejects unsupported formats, and reports loaded only after llama.cpp succeeds.
        viewModel.attachFileUri(it)
      }
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

  // Follow new messages only when the user is already reading the bottom.
  // This effect no longer depends on token text, so the screen is not invalidated
  // for every streamed batch while the user scrolls through older answers.
  val isNearBottom = {
    val info = listState.layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
    info.totalItemsCount == 0 || lastVisible < 0 || lastVisible >= info.totalItemsCount - 2
  }

  LaunchedEffect(uiState.currentScreen, uiState.messages.size, uiState.isStreaming) {
    if (uiState.currentScreen == AppScreen.CHAT && (uiState.messages.isNotEmpty() || uiState.isStreaming) && isNearBottom()) {
      delay(24)
      val target = listState.layoutInfo.totalItemsCount - 1
      if (target >= 0) listState.scrollToItem(target)
    }
  }

  // Coalesce live-token scrolls into at most one reposition per ~70 ms.
  LaunchedEffect(uiState.currentScreen) {
    if (uiState.currentScreen != AppScreen.CHAT) return@LaunchedEffect
    viewModel.liveStream
      .map { it.answer.length + it.thinking.length }
      .distinctUntilChanged()
      .collectLatest {
        delay(70)
        if (isNearBottom()) {
          val last = listState.layoutInfo.totalItemsCount - 1
          if (last >= 0) listState.scrollToItem(last)
        }
      }
  }

  val showScrollToBottom by remember {
    derivedStateOf {
      val info = listState.layoutInfo
      val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
      info.totalItemsCount > 0 && lastVisible >= 0 && lastVisible < info.totalItemsCount - 3
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

          // New Chat Button — the single high-contrast action in the drawer.
          Surface(
            onClick = {
              viewModel.createNewSession()
              viewModel.switchScreen(AppScreen.CHAT)
              coroutineScope.launch { drawerState.close() }
            },
            shape = RoundedCornerShape(14.dp),
            color = NeonPinkPrimary,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("new_chat_button")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.AddComment, contentDescription = null, tint = BackgroundPitchBlack, modifier = Modifier.size(19.dp))
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                AppStrings.newChat(uiState.appLanguage),
                color = BackgroundPitchBlack,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Secondary controls moved out of the crowded top bar.
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Surface(
              onClick = { viewModel.toggleXiaomiTuning(true) },
              shape = RoundedCornerShape(11.dp),
              color = SurfaceCard,
              border = androidx.compose.foundation.BorderStroke(1.dp, HyperOsOrange.copy(alpha = 0.35f)),
              modifier = Modifier
                .weight(1f)
                .testTag("xiaomi_turbo_pill")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                      when (uiState.performanceMode) {
                        PerformanceMode.HYPER_TURBO -> PerformanceBeastRed
                        PerformanceMode.BALANCED -> TurboActiveGreen
                        PerformanceMode.ECO_BATTERY -> HyperOsOrange
                      }
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.Speed, contentDescription = null, tint = HyperOsOrange, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Turbo", color = HyperOsOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }

            Surface(
              onClick = { viewModel.toggleLanguage() },
              shape = RoundedCornerShape(11.dp),
              color = SurfaceCard,
              border = androidx.compose.foundation.BorderStroke(1.dp, NeonPinkPrimary.copy(alpha = 0.3f)),
              modifier = Modifier
                .weight(1f)
                .testTag("language_toggle_pill")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = if (uiState.appLanguage == AppLanguage.FA) "🇮🇷" else "🇬🇧",
                  fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (uiState.appLanguage == AppLanguage.FA) "فارسی" else "English",
                  color = NeonPinkLight,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(18.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(AppStrings.recentSessions(uiState.appLanguage), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(
              text = "${uiState.sessions.size}",
              color = TextTertiary,
              fontSize = 10.5.sp
            )
          }
          Spacer(modifier = Modifier.height(8.dp))

          // Sessions List
          LazyColumn(modifier = Modifier.weight(1f)) {
            items(uiState.sessions, key = { it.id }) { session ->
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
                  text = uiState.deviceProfile?.deviceModel ?: "Android device",
                  color = HyperOsOrange,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = uiState.deviceProfile?.androidVersion ?: "Android version unknown",
                  color = TextTertiary,
                  fontSize = 10.5.sp
                )
              }
              Spacer(modifier = Modifier.height(6.dp))

              val totalRam = uiState.deviceProfile?.totalRamGb?.takeIf { it > 0f }
              if (totalRam != null) {
                val usedRam = (totalRam - (uiState.deviceProfile?.availRamGb ?: totalRam)).coerceAtLeast(0f)
                LinearProgressIndicator(
                  progress = { (usedRam / totalRam).coerceIn(0f, 1f) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                  color = if (usedRam / totalRam > 0.85f) PerformanceBeastRed else NeonPinkPrimary,
                  trackColor = SurfaceObsidianElevated
                )
                Spacer(modifier = Modifier.height(5.dp))
              }

              Text(
                text = uiState.deviceProfile?.let { "RAM آزاد: ${it.availRamGb} GB / ${it.totalRamGb} GB" } ?: "RAM: در حال سنجش",
                color = TextSecondary,
                fontSize = 11.sp
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "SoC: ${uiState.deviceProfile?.socName ?: "نامشخص"}",
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
          // Top Bar: identity, the active model and one primary action.
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            IconButton(
              onClick = { coroutineScope.launch { drawerState.open() } },
              modifier = Modifier
                .size(40.dp)
                .testTag("drawer_menu_button")
            ) {
              Icon(
                Icons.Default.Menu,
                contentDescription = "منو",
                tint = TextSecondary,
                modifier = Modifier.size(21.dp)
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(30.dp)
                  .clip(RoundedCornerShape(9.dp))
                  .background(Brush.linearGradient(listOf(NeonPinkPrimary, AccentPurple))),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  Icons.Default.AutoAwesome,
                  contentDescription = null,
                  tint = BackgroundPitchBlack,
                  modifier = Modifier.size(17.dp)
                )
              }
              Spacer(modifier = Modifier.width(7.dp))
              Column {
                Text(
                  text = "Sali-Localo",
                  color = TextPrimary,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 0.2.sp
                )
                Text(
                  text = if (uiState.appLanguage == AppLanguage.FA) "هوش مصنوعی آفلاین" else "Offline AI",
                  color = TextTertiary,
                  fontSize = 9.5.sp
                )
              }
            }

            // Active model selector (fills remaining width, never overflows).
            Row(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceObsidianElevated)
                .border(
                  1.dp,
                  if (uiState.textSlotModel.isLoaded) NeonPinkPrimary.copy(alpha = 0.35f) else SurfaceCardBorder,
                  RoundedCornerShape(14.dp)
                )
                .clickable { viewModel.toggleModelManager(true) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("model_selector_pill"),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(7.dp)
                  .clip(CircleShape)
                  .background(if (uiState.textSlotModel.isLoaded) TurboActiveGreen else AccentAmber)
              )
              Spacer(modifier = Modifier.width(7.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = uiState.textSlotModel.modelName.take(20),
                  color = TextPrimary,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = if (uiState.textSlotModel.isLoaded) {
                    "${uiState.textSlotModel.quantization} • ${uiState.textSlotModel.contextLength} ctx"
                  } else {
                    "برای شروع لمس کنید"
                  },
                  color = if (uiState.textSlotModel.isLoaded) NeonPinkLight else TextTertiary,
                  fontSize = 9.5.sp,
                  maxLines = 1
                )
              }
              Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(16.dp)
              )
            }

            if (uiState.currentScreen == AppScreen.CHAT) {
              // Search inside the current conversation transcript.
              IconButton(
                onClick = {
                  showChatSearch = !showChatSearch
                  if (!showChatSearch) searchQuery = ""
                },
                modifier = Modifier
                  .size(40.dp)
                  .testTag("chat_search_toggle_button")
              ) {
                Icon(
                  Icons.Default.Search,
                  contentDescription = "جست‌وجو در گفت‌وگو",
                  tint = if (showChatSearch) NeonPinkPrimary else TextSecondary,
                  modifier = Modifier.size(19.dp)
                )
              }

              // Export the whole transcript as Markdown via the Android share sheet.
              IconButton(
                onClick = {
                  val markdown = viewModel.exportSessionAsMarkdown()
                  val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, markdown)
                    type = "text/markdown"
                  }
                  context.startActivity(Intent.createChooser(sendIntent, "خروجی گفت‌وگو (Markdown)"))
                },
                modifier = Modifier
                  .size(40.dp)
                  .testTag("export_session_button")
              ) {
                Icon(
                  Icons.Default.Share,
                  contentDescription = "خروجی گفت‌وگو",
                  tint = TextSecondary,
                  modifier = Modifier.size(19.dp)
                )
              }
            }

            // One-tap new conversation keeps the top bar calm.
            IconButton(
              onClick = {
                viewModel.createNewSession()
                viewModel.switchScreen(AppScreen.CHAT)
              },
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(NeonPinkContainer)
                .border(1.dp, NeonPinkPrimary.copy(alpha = 0.35f), CircleShape)
                .testTag("top_new_chat_button")
            ) {
              Icon(
                Icons.Default.AddComment,
                contentDescription = "گفت‌وگوی جدید",
                tint = NeonPinkPrimary,
                modifier = Modifier.size(19.dp)
              )
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
              val background by animateColorAsState(
                targetValue = if (isSelected) NeonPinkContainer else Color.Transparent,
                animationSpec = tween(180),
                label = "tabBackground"
              )
              val foreground by animateColorAsState(
                targetValue = if (isSelected) NeonPinkLight else TextSecondary,
                animationSpec = tween(180),
                label = "tabForeground"
              )
              Row(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(background)
                  .border(
                    1.dp,
                    if (isSelected) NeonPinkPrimary.copy(alpha = 0.8f) else SurfaceCardBorder,
                    RoundedCornerShape(12.dp)
                  )
                  .clickable { viewModel.switchScreen(screen) }
                  .padding(horizontal = 10.dp, vertical = 6.dp)
                  .testTag("nav_${screen.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = screenIcon(screen),
                  contentDescription = null,
                  tint = foreground,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                  text = label,
                  color = foreground,
                  fontSize = 11.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
              }
            }
          }

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(1.dp)
              .background(SurfaceCardBorder.copy(alpha = 0.55f))
          )
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
            // Resource Guard Alert Banner: actual memory pressure or critical Android thermal status
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
                  text = "🛑 تولید متوقف شد: ${uiState.resourceSnapshot?.activeConstraintReason ?: "فشار منابع"}. پس از بازگشت حافظه و وضعیت حرارتی به محدودهٔ ایمن ادامه دهید.",
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
                      ggufTargetSlot = "TEXT"
                      ggufPicker.launch(arrayOf("*/*"))
                    }
                  )
                }
              }

              Spacer(modifier = Modifier.width(8.dp))

              // Modern, low-overhead composer: one border, one text field, no
              // Material decoration layers recomposing on every keystroke.
              val inputBorder by animateColorAsState(
                targetValue = if (inputText.isNotBlank()) NeonPinkPrimary.copy(alpha = 0.65f) else SurfaceCardBorder,
                animationSpec = tween(180),
                label = "inputBorder"
              )

              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(24.dp))
                  .background(SurfaceObsidianElevated)
                  .border(1.dp, inputBorder, RoundedCornerShape(24.dp))
                  .padding(horizontal = 14.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart
              ) {
                if (inputText.isEmpty()) {
                  Text(
                    text = AppStrings.inputPlaceholder(uiState.appLanguage),
                    color = TextTertiary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                BasicTextField(
                  value = inputText,
                  onValueChange = { inputText = it },
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_input_field"),
                  textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                  ),
                  cursorBrush = SolidColor(NeonPinkPrimary),
                  keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                  keyboardActions = KeyboardActions(onSend = {
                    if (inputText.isNotEmpty() || uiState.attachedFiles.isNotEmpty()) {
                      viewModel.sendMessage(inputText)
                      inputText = ""
                    }
                  }),
                  maxLines = 5
                )
              }

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
          .background(
            Brush.verticalGradient(
              listOf(Color(0xFF0D0A13), BackgroundPitchBlack, Color(0xFF07070B))
            )
          )
      ) {
        // Cross-fade between hubs instead of an abrupt screen swap.
        AnimatedContent(
          targetState = uiState.currentScreen,
          transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(110)) },
          label = "hubScreen"
        ) { screen ->
        when (screen) {
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
                  .verticalScroll(rememberScrollState())
                  .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
              ) {
                Box(
                  modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(
                      Brush.radialGradient(
                        colors = listOf(NeonPinkPrimary.copy(alpha = 0.32f), Color.Transparent)
                      )
                    ),
                  contentAlignment = Alignment.Center
                ) {
                  Box(
                    modifier = Modifier
                      .size(62.dp)
                      .clip(CircleShape)
                      .background(Brush.linearGradient(listOf(NeonPinkPrimary, AccentPurple)))
                      .border(1.dp, NeonPinkLight.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Default.AutoAwesome,
                      contentDescription = null,
                      tint = BackgroundPitchBlack,
                      modifier = Modifier.size(30.dp)
                    )
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                  text = AppStrings.appTitle(uiState.appLanguage),
                  color = TextPrimary,
                  fontSize = 23.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 0.3.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = AppStrings.appSubtitle(uiState.appLanguage),
                  color = TextSecondary,
                  fontSize = 12.sp,
                  textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Honest model readiness card — tapping it opens the model hub.
                Surface(
                  onClick = { viewModel.switchScreen(AppScreen.MODELS) },
                  shape = RoundedCornerShape(14.dp),
                  color = if (uiState.textSlotModel.isLoaded) SurfaceCard else NeonPinkContainer,
                  border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (uiState.textSlotModel.isLoaded) SurfaceCardBorder else NeonPinkPrimary.copy(alpha = 0.5f)
                  )
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Box(
                      modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (uiState.textSlotModel.isLoaded) TurboActiveGreen else AccentAmber)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                      text = if (uiState.textSlotModel.isLoaded) {
                        "آماده: ${uiState.textSlotModel.modelName.take(24)}"
                      } else {
                        "مدلی بارگذاری نشده — انتخاب مدل GGUF"
                      },
                      color = if (uiState.textSlotModel.isLoaded) TextSecondary else NeonPinkLight,
                      fontSize = 11.5.sp,
                      fontWeight = FontWeight.Medium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                      Icons.AutoMirrored.Filled.ArrowForward,
                      contentDescription = null,
                      tint = TextTertiary,
                      modifier = Modifier.size(14.dp)
                    )
                  }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = if (uiState.appLanguage == AppLanguage.FA) "شروع سریع" else "Quick start",
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = if (uiState.appLanguage == AppLanguage.FA) "آفلاین • خصوصی" else "Offline • Private",
                    color = TextTertiary,
                    fontSize = 10.sp
                  )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val quickActions = if (uiState.appLanguage == AppLanguage.FA) listOf(
                  ChatSuggestion("گفت‌وگو با مدل محلی", "پرسش و پاسخ کاملاً آفلاین روی همین گوشی", "سلام! خودت را معرفی کن و بگو چه کارهایی می‌توانی آفلاین انجام دهی.", Icons.Default.Chat, NeonPinkPrimary),
                  ChatSuggestion("تحلیل فایل و سورس‌کد", "ZIP، متن و PDF با بازیابی محلی RAG", "این فایل را بررسی و خلاصه کن", Icons.Default.FolderZip, AccentCyan),
                  ChatSuggestion("انتخاب مدل مناسب گوشی", "مدل‌های سبک و سریع GGUF", "", Icons.Default.Memory, AccentPurple, opensModels = true),
                  ChatSuggestion("وضعیت سخت‌افزار", "RAM، دما و تنظیمات Turbo", "مشخصات و وضعیت بهینه‌سازی سخت‌افزاری این دستگاه چیست؟", Icons.Default.Speed, HyperOsOrange)
                ) else listOf(
                  ChatSuggestion("Chat with a local model", "Private answers running entirely on this phone", "Hello! Introduce yourself and tell me what you can do offline.", Icons.Default.Chat, NeonPinkPrimary),
                  ChatSuggestion("Analyse files and code", "ZIP, text and PDF with local RAG", "Please inspect and summarize this file", Icons.Default.FolderZip, AccentCyan),
                  ChatSuggestion("Choose the right model", "Fast, lightweight GGUF models", "", Icons.Default.Memory, AccentPurple, opensModels = true),
                  ChatSuggestion("Hardware status", "RAM, thermals and Turbo tuning", "What are the hardware optimization specs of this device?", Icons.Default.Speed, HyperOsOrange)
                )

                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  quickActions.forEach { item ->
                    Surface(
                      onClick = {
                        if (item.opensModels) {
                          viewModel.switchScreen(AppScreen.MODELS)
                        } else {
                          inputText = item.prompt
                        }
                      },
                      shape = RoundedCornerShape(14.dp),
                      color = SurfaceCard,
                      border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Box(
                          modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(item.accent.copy(alpha = 0.12f)),
                          contentAlignment = Alignment.Center
                        ) {
                          Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = item.accent,
                            modifier = Modifier.size(19.dp)
                          )
                        }
                        Spacer(modifier = Modifier.width(11.dp))
                        Column(modifier = Modifier.weight(1f)) {
                          Text(item.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                          Spacer(modifier = Modifier.height(1.dp))
                          Text(
                            text = item.subtitle,
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            maxLines = 2
                          )
                        }
                        Icon(
                          Icons.AutoMirrored.Filled.ArrowForward,
                          contentDescription = null,
                          tint = TextTertiary,
                          modifier = Modifier.size(15.dp)
                        )
                      }
                    }
                  }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                  text = if (uiState.appLanguage == AppLanguage.FA) {
                    "همهٔ مدل‌ها روی همین دستگاه اجرا می‌شوند؛ هیچ داده‌ای به اینترنت ارسال نمی‌شود."
                  } else {
                    "Every model runs on this device; no data leaves your phone."
                  },
                  color = TextTertiary,
                  fontSize = 10.sp,
                  lineHeight = 15.sp,
                  textAlign = TextAlign.Center
                )
              }
            } else {
              Column(modifier = Modifier.fillMaxSize()) {
                // In-conversation search field (toggled from the top bar).
                AnimatedVisibility(visible = showChatSearch) {
                  OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("جست‌وجو در این گفت‌وگو…", color = TextTertiary, fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                      focusedContainerColor = BackgroundPitchBlack,
                      unfocusedContainerColor = BackgroundPitchBlack,
                      focusedBorderColor = NeonPinkPrimary,
                      unfocusedBorderColor = SurfaceCardBorder,
                      focusedTextColor = TextPrimary,
                      unfocusedTextColor = TextPrimary
                    ),
                    trailingIcon = {
                      if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                          Icon(Icons.Default.Close, contentDescription = "پاک کردن", tint = TextTertiary, modifier = Modifier.size(15.dp))
                        }
                      }
                    },
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 14.dp, vertical = 4.dp)
                      .testTag("chat_search_field")
                  )
                }

                LazyColumn(
                  state = listState,
                  modifier = Modifier.fillMaxSize(),
                  contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 10.dp, bottom = 64.dp)
                ) {
                  items(
                    items = if (searchQuery.isBlank()) {
                      uiState.messages
                    } else {
                      uiState.messages.filter { msg ->
                        msg.content.contains(searchQuery, ignoreCase = true) ||
                          (msg.thinkingContent ?: "").contains(searchQuery, ignoreCase = true)
                      }
                    },
                    key = { it.id },
                    contentType = { "message" }
                  ) { msg ->
                  MessageBubble(
                    message = msg,
                    currentLanguage = uiState.appLanguage,
                    onInspectZip = { zipName ->
                      val found = uiState.attachedFiles.find { it.fileName == zipName }
                      if (found != null) viewModel.inspectZipDetail(found)
                    },
                    modifier = Modifier.animateItem()
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
                    item(key = "live_generation", contentType = "live") {
                      LiveGenerationItem(viewModel = viewModel)
                    }
                  }
                }
              }
            }
          }
        }
        }

        AnimatedVisibility(
          visible = showScrollToBottom &&
            uiState.currentScreen == AppScreen.CHAT &&
            uiState.messages.isNotEmpty(),
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 12.dp),
          enter = fadeIn(),
          exit = fadeOut()
        ) {
          SmallFloatingActionButton(
            onClick = {
              coroutineScope.launch {
                val last = listState.layoutInfo.totalItemsCount - 1
                if (last >= 0) listState.animateScrollToItem(last)
              }
            },
            containerColor = NeonPinkPrimary,
            contentColor = BackgroundPitchBlack,
            modifier = Modifier.testTag("scroll_to_bottom_button")
          ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "رفتن به آخرین پیام")
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
        ggufTargetSlot = targetSlot
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

private data class ChatSuggestion(
  val title: String,
  val subtitle: String,
  val prompt: String,
  val icon: ImageVector,
  val accent: Color,
  val opensModels: Boolean = false
)

/**
 * Isolated streaming composable. Only this subtree observes [MainViewModel.liveStream],
 * so token batches never recompose the app bar, drawer, tabs, composer or history.
 */
@Composable
private fun LiveGenerationItem(viewModel: MainViewModel) {
  val live by viewModel.liveStream.collectAsState()

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
          .border(1.dp, NeonPinkPrimary.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          Icons.Default.AutoAwesome,
          contentDescription = null,
          tint = NeonPinkPrimary,
          modifier = Modifier.size(17.dp)
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column(modifier = Modifier.weight(1f)) {
        if (live.thinking.isNotEmpty()) {
          ThinkingProcessCard(
            thinkingText = live.thinking,
            isStreaming = live.answer.isEmpty(),
            durationMs = live.thinkingDurationMs,
            tokensPerSec = live.tokensPerSecond,
            modifier = Modifier.padding(bottom = 8.dp)
          )
        }

        if (live.answer.isNotEmpty()) {
          // The caret is painted inside the last paragraph, so the live answer no
          // longer jumps by one line every time a streamed block is completed.
          com.example.ui.components.MarkdownContent(
            rawText = live.answer,
            trailingCaret = true
          )
        } else if (live.thinking.isEmpty()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
              modifier = Modifier.size(15.dp),
              color = NeonPinkPrimary,
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "در حال آماده‌سازی مدل و context...",
              color = TextSecondary,
              fontSize = 11.5.sp
            )
          }
        }
      }
    }
  }
}

private fun screenIcon(screen: AppScreen): ImageVector = when (screen) {
  AppScreen.CHAT -> Icons.Default.Chat
  AppScreen.MODELS -> Icons.Default.Memory
  AppScreen.FILES -> Icons.Default.Folder
  AppScreen.IMAGE -> Icons.Default.Image
  AppScreen.VOICE -> Icons.Default.Mic
  AppScreen.PERFORMANCE -> Icons.Default.Speed
  AppScreen.SETTINGS -> Icons.Default.Settings
}
