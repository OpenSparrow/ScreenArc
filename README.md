# ScreenArc (Screenshot Sorter)

ScreenArc is an intelligent, privacy-focused Android application designed to automatically detect, organize, OCR-index, and manage screenshots seamlessly using a floating overlay service and Jetpack Compose.

---

## ✨ Features

### 1. 🔄 Real-Time Category Synchronization
- Category creation, edits, deletions, and reordering in the main app immediately update the floating overlay service via Room Database `Flow` observation and `OverlayEventBus`.
- Zero service restarts needed.

### 2. 🎯 Adaptive Category Drop Targets
- **Dynamic Arc Menu Layout:** Radial arc arrangement with collision avoidance when up to 8 categories exist.
- **Adaptive Paginated Tray:** Automatically switches to a responsive multi-column paginated drop tray when many categories (> 8) are present.
- **Intelligent Scaling:** Scale computation based on screen density, width, and category counts to prevent overlapping or screen overflow.

### 3. ✨ Hover Feedback & Dynamic Drop Targets
- **Spring Enlargement Animation:** Hovered drop target smoothly expands by 1.35× to 1.6× with spring physics.
- **Dynamic Contrast & Glow:** Drop target highlights with extracted screenshot palette colors.
- **Haptic & Accessibility:** Distinct haptic feedback on hover enter/leave/drop, with high-contrast labels and TalkBack accessibility descriptions.

### 4. 🔍 Comprehensive In-App Search
- **Multi-Field Search:** Search screenshots by filename, on-device OCR extracted text, date range, and category.
- **Debounced Flow:** 300ms debounce with instant UI updates, result count, and match highlighting.
- **Filter Chips:** Easily filter by category and OCR index status (All, Indexed, Unindexed).
- **Quick Overlay Entry:** Floating overlay includes a direct shortcut into the search experience.

### 5. ⚡ Local On-Device OCR (ML Kit)
- **100% On-Device Privacy:** Powered by Google ML Kit Text Recognition with zero cloud dependencies.
- **Bounded Concurrency:** Concurrency-limited processing (`Semaphore(2)`) to ensure battery and thermal efficiency without blocking the UI thread.
- **Structured Storage:** Extracted text, normalized search tokens, block bounding boxes, confidence scores, and timestamps stored in Room Database (`ocr_texts` table).

### 6. 📄 OCR Modal & Visual Text Overlay
- **Detailed OCR Dialog:** Displays recognized text with one-tap copy to clipboard and share actions.
- **Bounding Box Overlay Viewer:** Interactive image viewer rendering bounding boxes over recognized text segments directly on the screenshot preview.
- **Status Badges & Retries:** Visual indicators (`INDEXED`, `PROCESSING`, `FAILED`, `NO_TEXT`) with single-tap manual re-index capabilities.

### 7. ⚙️ Background OCR Indexing & WorkManager
- **OCR Processing Modes:**
  - **Manual:** OCR is triggered on-demand by the user.
  - **Ask Before Processing (Default):** Prompts the user before batch processing unindexed screenshots.
  - **Automatic (Background):** `WorkManager` periodically indexes newly taken screenshots during idle charging states.

---

## 🏗️ Architecture

ScreenArc adheres strictly to modern Android architecture principles:

- **UI Layer:** Jetpack Compose + Material 3 + Kotlin Coroutines & StateFlow.
- **Domain/State Layer:** Unidirectional Data Flow (UDF) with MVVM (`SearchViewModel`, `CategoriesViewModel`, etc.).
- **Data Layer:**
  - **Room Database:** `AppDatabase` with `CategoryEntity`, `ScreenshotEntity`, and `OcrTextEntity`.
  - **MediaStore:** Android scoped storage and `MediaStore.Images.Media` queries with copy/move file relocation.
  - **ML Kit:** `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` for on-device recognition.
  - **WorkManager:** `OcrBackgroundWorker` for constrained background indexing.
- **Service Layer:** Foreground `OverlayService` hosting Compose `ComposeView` windows with `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`.

---

## 🛠️ Tech Stack & Dependencies

- **Language:** Kotlin `2.0.21` (K2 compiler enabled)
- **Build System:** Gradle `8.7.2` + KSP `2.0.21-1.0.27`
- **UI:** Jetpack Compose (BOM `2024.10.01`), Material 3 `1.3.1`
- **Database:** Room `2.6.1` (KSP Room compiler, Coroutines KTX)
- **Machine Learning:** Google ML Kit Text Recognition `16.0.1`
- **Async & Background:** Kotlinx Coroutines `1.9.0`, WorkManager `2.10.0`
- **Image Loading:** Coil Compose `2.7.0`, AndroidX Palette `1.0.0`

---

## 🚀 Building and Testing

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```
