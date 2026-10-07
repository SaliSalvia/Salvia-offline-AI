package com.example.core.localization

object AppStrings {
  // Navigation Tabs
  fun tabChat(lang: AppLanguage) = if (lang == AppLanguage.FA) "💬 گفت‌وگو" else "💬 Chat"
  fun tabModels(lang: AppLanguage) = if (lang == AppLanguage.FA) "🧠 مدل‌ها" else "🧠 Models"
  fun tabFiles(lang: AppLanguage) = if (lang == AppLanguage.FA) "📁 اسناد و RAG" else "📁 Files & RAG"
  fun tabImage(lang: AppLanguage) = if (lang == AppLanguage.FA) "🎨 تصویرساز" else "🎨 Image Studio"
  fun tabVoice(lang: AppLanguage) = if (lang == AppLanguage.FA) "🎙️ صوت" else "🎙️ Voice"
  fun tabPerformance(lang: AppLanguage) = if (lang == AppLanguage.FA) "⚡ سخت‌افزار" else "⚡ Hardware"
  fun tabSettings(lang: AppLanguage) = if (lang == AppLanguage.FA) "⚙️ تنظیمات" else "⚙️ Settings"

  // App Title & Tagline
  fun appTitle(lang: AppLanguage) = "DeepGGUF"
  fun appSubtitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "ایستگاه کاری هوش مصنوعی محلی • بهینه‌شده برای Helio G100-Ultra و رم ۱۲GB"
  else
    "Native On-Device AI Workstation • Tuned for Helio G100-Ultra & 12GB RAM"

  fun drawerHeaderSubtitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "شیائومی ردمی نوت ۱۴ پرو • آفلاین محلی"
  else
    "Xiaomi Redmi Note 14 Pro • Pure Offline"

  fun newChat(lang: AppLanguage) = if (lang == AppLanguage.FA) "+ گفت‌وگوی جدید" else "+ New Chat"
  fun recentSessions(lang: AppLanguage) = if (lang == AppLanguage.FA) "جلسات اخیر" else "Recent Chats"
  fun noChatsYet(lang: AppLanguage) = if (lang == AppLanguage.FA) "هنوز گفت‌وگویی وجود ندارد" else "No chat sessions yet"

  // Chat Screen
  fun inputPlaceholder(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "پیام یا درخواست خود را بنویسید (متن، کد، تحلیل ZIP، ساخت تصویر)..."
  else
    "Type your request (text, code, zip analysis, image generation)..."

  fun send(lang: AppLanguage) = if (lang == AppLanguage.FA) "ارسال" else "Send"
  fun stop(lang: AppLanguage) = if (lang == AppLanguage.FA) "توقف" else "Stop"
  fun thinking(lang: AppLanguage) = if (lang == AppLanguage.FA) "در حال تفکر و پردازش..." else "Thinking & processing..."
  fun thinkingDuration(lang: AppLanguage, seconds: String) = if (lang == AppLanguage.FA)
    "مدت زمان تفکر: $seconds ثانیه"
  else
    "Thinking time: $seconds s"

  fun tokensPerSec(lang: AppLanguage, speed: String) = if (lang == AppLanguage.FA)
    "$speed توکن در ثانیه"
  else
    "$speed tok/s"

  fun modelThinkingProcess(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "فرآیند و مراحل تفکر هوش مصنوعی (DeepSeek Reasoning)"
  else
    "DeepSeek AI Reasoning & Thought Trace"

  fun clickToExpandThinking(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "مشاهده استدلال عمیق"
  else
    "View Deep Thought Process"

  fun clickToCollapseThinking(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "بستن استدلال"
  else
    "Collapse Thought Process"

  // Quick Action Prompts
  fun promptCode(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "یک اسکریپت پایتون بنویس"
  else
    "Write a Python script"

  fun promptImage(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "یک تصویر نئونی صورتی بکش"
  else
    "Draw a neon pink cyberpunk artwork"

  fun promptZip(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "تحلیل ساختار فایل ZIP"
  else
    "Inspect & analyze ZIP archive"

  fun promptXiaomi(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "وضعیت سخت‌افزار شیائومی"
  else
    "Xiaomi hardware telemetry"

  // Models Hub
  fun modelsTitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "مدیریت مدل‌های محلی (AI Models)"
  else
    "Local AI Models Manager"

  fun modelsSubtitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "تفکیک مدل‌های ذخیره‌شده روی حافظه و مدل‌های فعال در RAM"
  else
    "Manage device storage models & active models resident in RAM"

  fun importModel(lang: AppLanguage) = if (lang == AppLanguage.FA) "وارد کردن مدل" else "Import Model"
  fun activeSlotsTitle(lang: AppLanguage) = if (lang == AppLanguage.FA) "اسلات‌های دوگانه فعال در RAM" else "Dual Active Slots in RAM"
  fun slotA(lang: AppLanguage) = if (lang == AppLanguage.FA) "اسلات A: مدل زبانی متنی (LLM)" else "Slot A: Text LLM Slot"
  fun slotB(lang: AppLanguage) = if (lang == AppLanguage.FA) "اسلات B: تصویرساز آفلاین (Diffusion)" else "Slot B: Image Diffusion Slot"
  fun loadToRam(lang: AppLanguage) = if (lang == AppLanguage.FA) "بارگذاری در RAM" else "Load to RAM"
  fun unloadFromRam(lang: AppLanguage) = if (lang == AppLanguage.FA) "تخلیه از RAM" else "Unload from RAM"
  fun contextLength(lang: AppLanguage) = if (lang == AppLanguage.FA) "طول کانتکست" else "Context Length"
  fun quantType(lang: AppLanguage) = if (lang == AppLanguage.FA) "کوانتیزاسیون" else "Quantization"

  // Files & RAG
  fun filesTitle(lang: AppLanguage) = if (lang == AppLanguage.FA) "اسناد، تحلیل ZIP و RAG آفلاین" else "Files, ZIP Inspector & Local RAG"
  fun filesSubtitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "ایندکس اسناد محلی و جستجوی معنایی بدون نیاز به اینترنت"
  else
    "Offline document indexing and semantic retrieval without internet"

  fun uploadDocument(lang: AppLanguage) = if (lang == AppLanguage.FA) "بارگذاری سند / ZIP" else "Attach File / ZIP"
  fun indexForRag(lang: AppLanguage) = if (lang == AppLanguage.FA) "ایندکس در حافظه RAG" else "Index for RAG"
  fun inspectZip(lang: AppLanguage) = if (lang == AppLanguage.FA) "مشاهده محتویات ZIP" else "Inspect ZIP Contents"

  // Hardware Hub
  fun hardwareTitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "مرکز پایش و توربو شیائومی (HyperOS)"
  else
    "Xiaomi HyperOS Hardware & Turbo Hub"

  fun hardwareSubtitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "پایش زنده دما، بار ممتد CPU، سهمیه رم و قانون سقف ۸۰٪"
  else
    "Real-time thermal monitoring, sustained CPU load, RAM budget & 80% Safety Guard"

  fun profileBeastTurbo(lang: AppLanguage) = if (lang == AppLanguage.FA) "حالت توربو بیست (Beast Turbo)" else "Beast Turbo Profile"
  fun profileBalanced(lang: AppLanguage) = if (lang == AppLanguage.FA) "حالت متعادل (Balanced)" else "Balanced Profile"
  fun profileEcoBattery(lang: AppLanguage) = if (lang == AppLanguage.FA) "حالت مصرف بهینه (Eco Battery)" else "Eco Battery Profile"

  fun ramBudget(lang: AppLanguage) = if (lang == AppLanguage.FA) "سهمیه رم هوش مصنوعی" else "AI RAM Budget"
  fun sustainedCpu(lang: AppLanguage) = if (lang == AppLanguage.FA) "بار ممتد CPU" else "Sustained CPU"
  fun thermalTemp(lang: AppLanguage) = if (lang == AppLanguage.FA) "وضعیت حرارتی" else "Thermal Status"
  fun runBenchmark(lang: AppLanguage) = if (lang == AppLanguage.FA) "اجرای بنچمارک سخت‌افزار" else "Run Hardware Benchmark"

  // Settings
  fun settingsTitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "تنظیمات ایستگاه کاری هوش مصنوعی"
  else
    "AI Workstation Settings"

  fun settingsSubtitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "زبان برنامه، حالت کاربر و ایزولاسیون امنیتی"
  else
    "Language, user mode & security sandbox isolation"

  fun languageSettingTitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "زبان اپلیکیشن (App Language)"
  else
    "App Language (زبان برنامه)"

  fun languageSettingSubtitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "تغییر آنی بین فارسی (راست‌به‌چپ) و انگلیسی (چپ‌به‌راست)"
  else
    "Instant switch between Persian (RTL) and English (LTR)"

  fun airplaneModeTest(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "شبیه‌سازی حالت هواپیما (تأیید آفلاین ۱۰۰٪)"
  else
    "Airplane Mode Test (Verify 100% Offline)"

  fun clearCache(lang: AppLanguage) = if (lang == AppLanguage.FA) "پاکسازی کش مدل‌ها" else "Clear Model Cache"
  fun userLevel(lang: AppLanguage) = if (lang == AppLanguage.FA) "سطح کاربری" else "User Experience Level"
}
