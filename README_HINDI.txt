MACHINE PRODUCTION CYCLE TIME — GITHUB UPLOAD FILES

This folder contains the Android WebView app source files. It opens the existing Google Apps Script tracker URL from MainActivity.java and uses that web app's existing backend. Internet is required.

UPLOAD TO YOUR EXISTING REPOSITORY
1. Extract this ZIP.
2. Open your repository Code tab and choose Add file > Upload files.
3. From this extracted folder, upload the contents at the top level: app/ folder, build.gradle, settings.gradle, gradle.properties, and this README. Do not upload these inside a new nested folder.
4. The repository already has .github/workflows/main.yml, so you can leave the .github folder in this ZIP unuploaded for now. If you want to replace the workflow, upload .github/workflows/main.yml to that exact path instead.
5. Commit changes.
6. Open Actions and run Build Android APK. If successful, download the Machine-Production-Cycle-Time-APK artifact ZIP and extract app-debug.apk.

The app is a WebView wrapper around the existing deployed web app; it is not an offline-native rewrite. Build has not been executed in this environment, so APK availability depends on a successful GitHub Actions run.
