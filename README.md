# EyelidGesturesDetection

## Usage

1. Clone the project and run the Gradle Script using Android Studio.

2. Install and launch the app on an Android 8.0 or above smartphone (We have not yet tested the app on Android 7.0 or below, but it probably will work as well).

3. Click the first item on the UI, "EYE TRACKING (DEMO)," to start. You would see the real-time detection result overlay on the front-facing camera stream, and the probability of each eye being open will change as you wink your eyes. Because you have not trained the model yet, the detection results are only for demonstration and may not be accurate.
  * Troubleshoot: if you cannot see the camera feed, make sure you grant the corresponding permissions (see AndroidManifest.xml file for details).

4. "TRAINING ONE (STATES)": train the model to detect eyelid states. We suggest using '200' data points as the parameter. 
  * There will be a short 'beep' after each data-collection block, so you will know even if you have closed both eyes (i.e., the 'both eyelid close' state).
  * Source code: eyeinteraction\CalibrationStaticActivity.java

5. "TRAINING TWO (GESTURES)": train the model to detect eyelid gestures. To make training as simple as posible, we only need '5' samples per eyelid gesture. However, more samples would likely increase the recognition accuracy. Each user will be assigned with a PID, so make sure you use the same PID as the "TRAINING ONE" phase so the models will be named consistently.
  * If the face detection status shows "Face detection failed", or when the eyelid state detection result keeps fluctuating, you can try to adjust the camera angle or light condition. 
  * For consistency, you can try to count for certain numbers consistently for short and long gestures, respectively. E.g., you can try to count number '1' while performing *R*/*L*/*B* gestures (short gestures), and count numbers '1,2,3' while performing *R-*/*L-*/*B-* gestures (long gestures with one eyelid state), and count '1,2,3; 4,5,6' while performing *B-L-*/*B-R-* gestures (long gestures with two eyelid states). 
  * Source code: eyeinteraction\CalibrationDynamicActivity.java

6. "TESTING ONE (ACCURACY)": test the accuracy of each eyelid gestures. 
  * Source code: eyeinteraction\EvaluationOneActivity.java

7. "TESTING TWO (APP SWITCHING)": test the app switching interaction using eyelid gestures. 
  * Source code: eyeinteraction\EvaluationTwoiBlinkActivity.java

8. All models and result files will be stored under the path returned by "getExternalFilesDir()": Android\data\eyeinteraction.mingming.research\files.

9. The main detection algorithm is implemented in eyeinteraction\utils\Classifier2D.java, which is called by all other Android Activities. 

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

## APK

Here is the [link](https://drive.google.com/file/d/18_LR8tk9XhDDLzaRJl-ec1YWTuMdGtHh/view?usp=sharing) to a pre-compiled apk file.
