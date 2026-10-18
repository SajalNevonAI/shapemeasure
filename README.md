# SmartShape AI — Android

Shape detection, calibrated measurement, OCR word search and scan history. Native Android
(Kotlin + Jetpack Compose) with a shared C++/OpenCV vision core.

## Modules

| Module | What it holds |
| --- | --- |
| `:app` | Compose UI, navigation, CameraX scanner, editor, history, detail, settings, export/share |
| `:core:model` | Pure-Kotlin domain: shapes, calibration, homography, `MeasurementEngine`, units, OCR search. Unit-tested on the JVM |
| `:core:cv` | C++17 vision core (`src/main/cpp/core`, no JNI) + JNI bridge + Kotlin `ShapeDetector` / `MarkerDetector` |
| `:core:ocr` | ML Kit on-device text recognition (bundled model, works offline) → `OcrResult` |
| `:core:data` | Room DB with FTS4 index over title/notes/tags/shapes/OCR text, image store, DataStore settings |

```
Camera / Gallery ──► :core:cv (C++ OpenCV) ──► DetectedShape (pixels)
                                  │                   │
                     ArUco marker / reference / known size
                                  ▼                   ▼
                          Calibration (px→mm homography) ──► MeasurementEngine ──► dimensions
Image ──► :core:ocr (ML Kit) ──► OcrResult ──► TextSearch (highlight boxes)
All of it ──► :core:data (Room + FTS) ──► History / search / export
```

## Measurement model

A camera alone cannot know real size, so the app never shows pixel values as physical dimensions.
Each scan carries a `Calibration`:

| Mode | How | Perspective corrected |
| --- | --- | --- |
| Calibration marker | ArUco DICT_4X4_50, 40 mm black square, found automatically | Yes (homography) |
| Reference object | User picks a detected 4-corner shape and its size (bank card, A4, Letter, custom) | Yes (homography) |
| Known dimension | User enters the real length of one dimension of a shape | No (scale only) |
| Uncalibrated | Values shown in px, with an amber "Not calibrated" banner | — |

Outlines are mapped onto the measurement plane *before* computing lengths, so perspective is
removed for marker/reference modes. Measurements are always recomputed from outlines +
calibration, which makes unit switching and corner edits consistent.

## Build

Requirements: Android Studio (JDK 21+ bundled), SDK 36, NDK 27.1.12297006, CMake 3.22.1.

```bash
./gradlew :core:model:testDebugUnitTest :core:data:testDebugUnitTest
./gradlew :app:assembleDebug
```

Toolchain: AGP 9.3 (built-in Kotlin), Gradle 9.5, Kotlin 2.3, OpenCV 4.12 (Maven prefab AAR).

## Next steps

1. **Accuracy study (gating milestone).** Build `smartshape_core` on desktop (`OpenCV_DIR=…`)
   with a test harness over a photo set of objects with known sizes, and report error against
   the SOW targets (±2 % / ±2 mm calibrated, ≥95 % classification).
2. Edge-offset correction: contours come from a closed Canny map and sit ~0.5–1 px outside the
   true edge; quantify in the study and compensate.
3. Manual two-point measurement tool, perspective-corrected crop/rectify, multi-page batch scan.
4. Branded icon/onboarding art from Figma, printable calibration-marker PDF in-app.
5. Phase 2: ARCore depth measurement, accounts + cloud sync.
