# MOTION SENSOR AI: Stage 1 (Gradle + Hand Tracking)

Stage 1 gives you a working camera screen with live MediaPipe hand tracking.
A single START button turns on the front camera, draws the 21 hand landmarks,
and shows the detected hand and tracking FPS. Gestures and phone control come in later stages.

Minimum Android: 10 (API 29). Tested target: OPPO A5 2020 (Android 10).

## Folder structure

    MotionSensorAI/
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── gradle.properties
    ├── gradle/wrapper/gradle-wrapper.properties
    ├── app/
    │   ├── build.gradle.kts
    │   ├── proguard-rules.pro
    │   └── src/main/
    │       ├── AndroidManifest.xml
    │       ├── assets/                  <- hand_landmarker.task goes here
    │       ├── java/com/motionsensor/ai/
    │       │   ├── MainActivity.kt
    │       │   ├── camera/CameraManager.kt
    │       │   ├── tracking/HandTrackingManager.kt
    │       │   ├── tracking/HandTrackingResult.kt
    │       │   └── ui/HandOverlayView.kt
    │       └── res/
    │           ├── layout/activity_main.xml
    │           ├── drawable/start_button_bg.xml
    │           └── values/ (strings, colors, themes)
    └── README.md

## Step 1: Add the MediaPipe model (required)

The app will not track hands without this file. It is about 8 MB and is not included in the project.

1. On your phone, open this link in Chrome and let it download:
   https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task
2. Put the file in the project at `app/src/main/assets/hand_landmarker.task`.
   - **Using GitHub on mobile:** open your repository in the browser, go to `app/src/main/assets/`, tap **Add file → Upload files**, and upload `hand_landmarker.task`. Keep the name exactly the same.
   - **Using a computer:** copy the file into `app/src/main/assets/`.
3. The `PUT_MODEL_HERE.txt` file can stay in that folder.

## Step 2: Build the debug APK

**Option A: Android Studio on a computer (simplest)**
1. Open Android Studio (Ladybug or newer) and choose **Open**, then select the `MotionSensorAI` folder.
2. Let Gradle sync. Android Studio will create the Gradle wrapper files it needs.
3. Choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

**Option B: GitHub Actions (no computer needed, see Stage 2 below)**

## Step 3: Install on the OPPO A5 2020

1. On the phone, open **Settings → Additional settings → Developer options** and turn on **USB debugging**.
   If you cannot see Developer options, go to **Settings → About phone** and tap **Build number** seven times.
2. Copy `app-debug.apk` to the phone and open it. If Android asks, allow installs from that app (Files or Chrome).
3. Open **Motion Sensor AI** and tap **Allow** when it asks for camera access.

## Step 4: Test Stage 1

| Test | Expected result |
|---|---|
| Open the app | Dark navy screen with title and a round START button |
| Tap START, allow camera | Front camera preview appears faintly behind the UI |
| Show one open hand | Cyan landmark dots and lines follow your hand; status shows "Hand: 1 detected" |
| Move your hand around | Landmarks follow smoothly; FPS shows roughly 10 to 20 on the A5 |
| Hand out of view | Status returns to "Hand: not detected" |
| Tap STOP | Camera closes, overlay clears, status returns to Standby |
| Press Home while running | Camera stops; this is expected (see limitation below) |

**Known limitation:** On Android 10 and newer, a normal app cannot use the camera
once it leaves the foreground. Stage 1 stops the camera when you press Home. Keeping
gesture control running while you use other apps requires a foreground service, which
comes in a later stage.

## Troubleshooting

- **"Model not found"**: the file is missing or misnamed. It must be exactly `app/src/main/assets/hand_landmarker.task`.
- **Preview works but no landmarks**: check that the model file is not zero bytes. Re-download it if needed.
- **Landmarks are lagging or FPS is under 5**: close other apps and try again. The model runs on the CPU for stability on the Snapdragon 665.
- **Landmarks appear slightly offset**: this is a known alignment limit in Stage 1. We will refine it in the gesture stage.

## Stage 2: Build the APK on GitHub (from your phone)

Stage 2 adds `.github/workflows/android-build.yml`. GitHub's servers build the APK,
so you don't need Android Studio.

### What the workflow does
1. Installs JDK 17 and the Android SDK (platform 34, build-tools 34.0.0).
2. Creates the Gradle Wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`) if it is missing.
3. Downloads `hand_landmarker.task` automatically if it is not already in `app/src/main/assets/`.
   It checks that the file is at least 1 MB.
4. Runs `./gradlew :app:assembleDebug`.
5. Uploads the APK as the artifact **MotionSensorAI-debug-apk**.

### Steps on your phone
1. Create a new repository on github.com (private is fine).
2. Get the project files into the repository. Upload is easiest from a computer:
   drag the contents of the `MotionSensorAI` folder (including the hidden `.github` folder)
   into the repository. On mobile, GitHub's "Upload files" picker does not reliably
   include subfolders, so use a computer for this one-time upload.
3. Open the **Actions** tab. Select **Android Debug Build** and tap **Run workflow**
   (or push any change).
4. Wait for the green check (usually 5 to 10 minutes the first time).
5. Open the finished run, scroll to **Artifacts**, and download **MotionSensorAI-debug-apk**.
   It is a ZIP. Extract it and install `MotionSensorAI-debug.apk` on the OPPO A5.

### Install on the phone
Allow installs from your browser or file manager when Android asks. The APK is
debug-signed, so it will install directly for testing but cannot go on the Play Store.

### If the build fails
Open the failed run, then the red step, and copy the first error lines to me.
A **build-reports-on-failure** artifact is also attached to failed runs.
