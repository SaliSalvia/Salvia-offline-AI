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
    "مدیریت مدل‌های روی دستگاه • پروفایل منابع بر اساس سخت‌افزار شناسایی‌شده"
  else
    "On-device model workspace • Resource profile based on detected hardware"

  fun drawerHeaderSubtitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "مدل‌های محلی • وضعیت اجرا وابسته به runtime نصب‌شده"
  else
    "Local models • Inference depends on an installed runtime"

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
    "پایش منابع و پایداری دستگاه"
  else
    "Device Resources & Sustained Performance"

  fun hardwareSubtitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "مصرف فرایند، حافظه آزاد و شدت حرارتی گزارش‌شده توسط Android"
  else
    "App process use, available memory and Android thermal severity"

  fun profileBeastTurbo(lang: AppLanguage) = if (lang == AppLanguage.FA) "حداکثر عملکردِ ایمن" else "Maximum safe performance"
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
    "نشانگر رابط کاربریِ تست آفلاین"
  else
    "Offline test UI indicator (does not change network settings)"

  fun clearCache(lang: AppLanguage) = if (lang == AppLanguage.FA) "پاکسازی کش مدل‌ها" else "Clear Model Cache"
  fun userLevel(lang: AppLanguage) = if (lang == AppLanguage.FA) "سطح کاربری" else "User Experience Level"

  // Generation parameters (real llama.cpp sampling knobs)
  fun genParamsTitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "پارامترهای تولید مدل"
  else
    "Model Generation Parameters"

  fun genParamsSubtitle(lang: AppLanguage) = if (lang == AppLanguage.FA)
    "کنترل دقیق دما، تنوع، طول پاسخ و بار پردازنده؛ همه در محدودهٔ امن دستگاه"
  else
    "Fine control of temperature, diversity, answer length and CPU load — all within safe device limits"

  fun genTemperature(lang: AppLanguage) = if (lang == AppLanguage.FA) "دما (Temperature)" else "Temperature"
  fun genTopP(lang: AppLanguage) = if (lang == AppLanguage.FA) "بریدن هسته‌ای (Top-p)" else "Nucleus sampling (Top-p)"
  fun genTopK(lang: AppLanguage) = if (lang == AppLanguage.FA) "بریدن K تایی (Top-k)" else "Top-k"
  fun genMaxTokens(lang: AppLanguage) = if (lang == AppLanguage.FA) "حداکثر توکن پاسخ" else "Max answer tokens"
  fun genRepeatPenalty(lang: AppLanguage) = if (lang == AppLanguage.FA) "جریمهٔ تکرار" else "Repeat penalty"
  fun genContext(lang: AppLanguage) = if (lang == AppLanguage.FA) "پنجرهٔ زمینه (Context)" else "Context window"
  fun genThreads(lang: AppLanguage) = if (lang == AppLanguage.FA) "تعداد نخ پردازنده" else "CPU threads"
  fun genReset(lang: AppLanguage) = if (lang == AppLanguage.FA) "بازگشت به پیش‌فرض امن" else "Reset to safe defaults"
}
