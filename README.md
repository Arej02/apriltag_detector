1. Clone the repository in Android Studio
```bash
git clone https://github.com/Arej02/apriltag_detector.git
cd arej00_CK65
```

2. Open in Android Studio
Launch Android Studio
Select Open an existing project → choose the cloned folder
Wait for Gradle sync to finish

3. Verify OpenCV Native Libraries
Ensure the following native .so files exist:
```bash
app/src/main/jniLibs/armeabi-v7a/libopencv_java4.so
app/src/main/jniLibs/arm64-v8a/libopencv_java4.so
```

4. Build & Run the App
Connect an Android device (or emulator)
Click Run > Run 'app'
The app will start the camera preview and display detected AprilTags and Data Matrix codes in real time.
