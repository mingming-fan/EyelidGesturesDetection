# EyelidGesturesDetection

## Demo video

To get a sense of how well our algorithm works in real-time and its applications, please watch a demo video here: https://www.youtube.com/watch?v=WpDDUEHhy18&feature=youtu.be


## How to install the app?
Our app currently works on Android smartphones with version 8.0 or above.
 
Download and install the APK to your phone from the following link: https://github.com/mingming-fan/EyelidGesturesDetection/releases/tag/v0.1



## How to use the app?

1. For the first-time user, the eyelid gesture classifier needs to be trained. Complete the first three steps shown in the app landing user interface (UI): "Step 1: Eye Detection Checking", "Step 2: Eyelid State Training", "Step 3: Eyelid Gesture Training". You only need to train the model once only. 


2. Click "Step 1: Eye Detection Checking" to check whether the app detects your eyes or not. You would see the real-time detection result overlay on the front-facing camera stream. Try to close an eyelid and see whether it detects the number of open eyes correctly or not.

  * Troubleshoot: if you cannot see the camera feed, you need to grant the corresponding permissions. In this case, the permission to use the Camera is needed (see AndroidManifest.xml file for details).

4. Click "Step 2: Eyelid State Training" to train the model to detect eyelid states (i.e., both eyelids open, both eyelids close, open right eyelid only, and open left eyelid only). On the UI, you can choose a participant ID (PID). You can simply proceed with the default one.  

  * There will be a short 'beep' after each data-collection block, so you will know even if you have closed both eyes (i.e., the 'both eyelid close' state).
  * Source code: eyeinteraction\CalibrationStaticActivity.java

5. Click "Step 3: Eyelid Gesture Training" to train the model to detect nine eyelid gestures. By default, the model only needs to collect '5' samples per eyelid gesture. However, more samples per gesture would likely increase the recognition accuracy. Make sure you use the same PID as the previous step.

  * If the face detection status shows "Face detection failed", or when the eyelid state detection result keeps fluctuating, you can try to adjust the camera angle or your ambient light condition. 
  * For consistency, you can try to count for certain numbers consistently for short and long gestures, respectively. E.g., you can try to count number '1' while performing *R*/*L*/*B* gestures (short gestures), and count numbers '1,2,3' while performing *R-*/*L-*/*B-* gestures (long gestures with one eyelid state), and count '1,2,3; 4,5,6' while performing *B-L-*/*B-R-* gestures (long gestures with two eyelid states). 
  * Source code: eyeinteraction\CalibrationDynamicActivity.java


After completing the first three steps, the model will have been created and saved. You can then test the model's performance by clicking on "Eyelid Gesture Testing."
 

6. Click "Eyelid Gesture Testing." Choose the same PID for which you trained your models in the previous steps and Click "Confirm." On the next screen, click on the "Start" Button。 Then, you can perform any of the nine eyelid gestures and see the real-time recognition result. The UI also plots the probability of each eyelid being open in real-time. 
  * Source code: eyeinteraction\EvaluationOneActivity.java


We have also created two applications for you to interact with your phone using your eyelid gestures. One is "Within and Between Apps Navigation," and the other one is "Cross-App Text Entry App"

7. "Within and Between Apps Navigation": navigate within and between apps using eyelid gestures. According to our gesture matching scheme, you can navigate between *apps* (B-R-, B-L-), *tabs/screens* witin an app (R-, L-), and *containers* within a tab/screen (R, L) only using eyelid gestures. 
  * Source code: eyeinteraction\EvaluationTwoiBlinkActivity.java

8. "Cross-App Text Entry App": One simulated app shows a target phrase, and your goal is to type the phrase into another simulated app. You can use "normal" switch method by pressing the switch app button at the bottom of the screen. You can also use "eyelid_gesture" to switch apps by closing any one of your eyelids. 

  * Source code: eyeinteraction\EvaluationThreeMethodTwoActivity.class (using eyelid gestures to switch between two applications for text entry) and eyeinteraction\EvaluationThreeMethodThreeActivity.class (using eyelid gestures + porous interfaces to switch between two applications for text entry).

9. All models and result files will be stored under the path returned by "getExternalFilesDir()": Android\data\eyeinteraction.mingming.research\files.


## How to use the code?

1. Clone the project and run the Gradle Script using Android Studio.

2. Install and launch the app on an Android 8.0 or above smartphone (We have not yet tested the app on Android 7.0 or below, but it probably will work as well).

3. The main detection algorithm is implemented in eyeinteraction\utils\Classifier2D.java, which is called by all other Android Activities. 

## Gesture names

The gesture names used in the source code and those in our manuscript are different. Here is a table for your reference.

| Gestures in the source code | Gestures in the manuscript |
| ------------- |:------------------:|
| cLo  | L |
| cRo  | R |
| sB   | B |
| cL-o | L- |
| cR-o | R- |
| cB-o | B- |
| cB-LRo | B-R- |
| cB-RLo | B-L- |
| dB | BOB |



## References

If you use our source code or the APK, please cite the following papers:

1. Mingming Fan, Zhen Li, and Franklin Mingzhe Li. 2020. Eyelid Gestures on
Mobile Devices for People with Motor Impairments. In The 22nd International ACM SIGACCESS Conference on Computers and Accessibility (ASSETS
’20), October 26–28, 2020, Virtual Event, Greece. ACM, New York, NY, USA,
8 pages. https://doi.org/10.1145/3373625.3416987

2. Zhen Li, Mingming Fan, Ying Han, and Khai N. Truong. 2020. IWink: Exploring Eyelid Gestures on Mobile Devices. In Proceedings of the 1st International Workshop on Human-centric Multimedia Analysis (HuMA'20). Association for Computing Machinery, New York, NY, USA, 83–89. DOI:https://doi.org/10.1145/3422852.3423479
