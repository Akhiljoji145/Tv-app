# TV Document Viewer (Android TV / Google TV)

A landscape Android TV starter app designed for a TCL Android TV and operated with a D-pad remote.

## Included
- TV launcher entry (`LEANBACK_LAUNCHER`) and touchscreen-optional manifest.
- Android Storage Access Framework file picker (USB drives appear when the TV exposes them to Android).
- PDF page rendering with Android `PdfRenderer`.
- DOCX text extraction.
- PPTX slide text extraction.
- XLSX first-sheet cell text extraction.
- TXT and common image viewing.
- Remote navigation: OK to open, left/right or page keys to move PDF pages, Back to return home.

## Important limitations
This is a functional starter/MVP, not a full Microsoft Office rendering engine.
- DOCX/PPTX/XLSX are shown as extracted text/cell content; original fonts, layouts, charts, animations, formulas, and slide design are not reproduced.
- Legacy `.doc`, `.ppt`, and `.xls`, macro-enabled formats, and password-protected files are not supported.
- USB access depends on the TCL firmware exposing the USB device through Android's system document picker.
- PDF page rendering is bitmap-based; very large pages may use substantial memory.

## Build the APK
1. Install Android Studio (current stable) on a Windows, macOS, or Linux computer.
2. Open this `TVDocumentViewer` folder in Android Studio.
3. Allow Gradle sync and install Android SDK Platform 35 if prompted.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Android Studio will create the debug APK at:
   `app/build/outputs/apk/debug/app-debug.apk`

For a release APK, configure a signing key in **Build > Generate Signed Bundle / APK**.

## Install on TCL TV
1. Copy the APK to a USB drive.
2. On the TV, enable installation from unknown apps for the file manager you use (menu names vary by TCL firmware).
3. Open the APK using the TV file manager and install.
4. If the app does not appear on the TV home screen, launch it from Settings > Apps or a sideload launcher. Some TV launchers hide sideloaded apps.

## Compatibility
- Minimum Android API: 21
- Target API: 28 (kept conservative for older TV firmware and file access compatibility)
- Package: `com.akhil.tvdocumentviewer`
