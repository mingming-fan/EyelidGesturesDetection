package eyeinteraction;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import eyeinteraction.ui.camera.CameraSourcePreview;
import eyeinteraction.ui.camera.GraphicOverlay;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import eyeinteraction.utils.*;

/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class CalibrationStaticActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener, View.OnClickListener {
    private final boolean chineseStr = false;
    private boolean initCounter = false;

    private final String TAG = CalibrationStaticActivity.this.getClass().getSimpleName();

    private CameraSource mCameraSource = null;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;
    private float currLeftEyeScore = 0;
    private float currRightEyeScore = 0;
    private String currentGesture = "";

    Button bt_calibration;
    Button bt_cancel;
    Button bt_pause;
    TextView tv_command;
    TextView mTvFace;

    private long timeStarted = 0;
    // Just in case he is totally lost. This shouldn't happen.
    private static final long MAX_TIME_TRAINING = 20000;
    private int validCounter = 0;
    private int mRealTrialCnt = 0;
    private boolean mIsAutoMode = true;
    private ToneGenerator tone;

    // Major classifier used for training & testing.
    private Classifier2D mClassifier;
    // Temporary list for training data. Maximum length controlled by time & valid points.
    private ArrayList<Float> mLeftEyeList = new ArrayList<>();
    private ArrayList<Float> mRightEyeList = new ArrayList<>();
    // Whether we have enough data for CURRENT SESSION.
    private boolean mHasEnoughDataForCurrent = false;
    private boolean mIsTrainingReady = false;
    // Whether we have the entire model of segmenting
    private boolean mHasSegmentModelReady = false;

    private boolean collectingDataTag = false;
    private boolean evaluatingModelTag = false;

    String participantCode;

    // TO be defined onCreate: maximum points per gesture
    private int MAX_VALID_POINTS = 200;
    // Let them take a break: points per session.
    private int mRepeatRestPoints = 100;
    private int mTrialCnt = 0;
    private final String[] STATIC_GESTURES = {"Open Both Eyes", "Close Left Eye Only", "Open Both Eyes",
            "Close Right Eye Only", "Open Both Eyes", "Close Both Eyes"};
//    private final String[] STATIC_GESTURES_C = {"睁开双眼", "只闭左眼", "睁开双眼",
//            "只闭右眼", "睁开双眼", "闭合双眼"};
    private  Map<String, String> EN2CN = new HashMap<String, String>();

    private ArrayList<String> mStaticGestureSequence = new ArrayList<>();

    private String mStaticFileName = "static_detail.csv";
    private File mStaticFile;
    private StringBuilder mStaticFileBuilder = new StringBuilder();

    // Lock for button.
    private long mLastPressedTime = 0;

    private final boolean counterMode = true;
    private boolean counterPaused = false;
    private CountDownTimer countdown = new CountDownTimer(11000, 1000) {
        @Override
        public void onTick(long l) {
            if (l / 1000 > 2)
            {
                bt_calibration.setText("Please open both eyes and read the instructions.\nTimer：" + (l/1000 - 2));
//                bt_calibration.setText("请睁开双眼，阅读动作介绍，做好准备\n倒计时：" + (l/1000 - 2));
            }
            else if (l / 1000 > 1) {
                tone.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 100);
                bt_calibration.setText("Start!");
//                bt_calibration.setText("Start!\n开始！");
                bt_calibration.setBackgroundColor(Color.rgb(165, 1, 1));
            }
        }

        @Override
        public void onFinish() {
            clickOnMainBtn();
        }
    };
    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        EN2CN.put("Open Both Eyes", "睁开双眼");
        EN2CN.put("Close Left Eye Only", "只闭左眼");
        EN2CN.put("Close Right Eye Only", "只闭右眼");
        EN2CN.put("Close Both Eyes", "闭合双眼");
        EN2CN.put("Open","检测结果：双眼睁开");
        EN2CN.put("Close", "检测结果：单眼闭合/双眼闭合");

        setContentView(R.layout.activity_calibration_static);

        Bundle b = getIntent().getExtras();
        participantCode = b.getString("participantCode");
        String repeats = b.getString("repeatsCode");
        MAX_VALID_POINTS = Integer.parseInt(repeats);

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }

        /// UI Binding ///

        Spinner spinner = (Spinner) findViewById(R.id.eyegestures_spinner);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.eye_gestures, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        bt_calibration = (Button)findViewById(R.id.bt_calibration_static);
        bt_calibration.setBackgroundColor(Color.GREEN);
        bt_calibration.setOnClickListener(this);

        bt_cancel = (Button)findViewById(R.id.bt_cancel);
        bt_cancel.setOnClickListener(this);

        bt_pause = (Button)findViewById(R.id.bt_pause);
        bt_pause.setOnClickListener(this);

        tv_command = (TextView)findViewById(R.id.tv_command);
        mTvFace = (TextView)findViewById(R.id.tv_face);

        mClassifier = new Classifier2D(participantCode, getExternalFilesDir(null));

        tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        /// Set up resting time ///

        if (MAX_VALID_POINTS < 100) {
            mRepeatRestPoints = MAX_VALID_POINTS;
            // Generate all at once.

            for (String ges : STATIC_GESTURES) {
                mStaticGestureSequence.add(ges);
            }
        } else {
            mRepeatRestPoints = 100;
            int blockNum = MAX_VALID_POINTS / mRepeatRestPoints;

            for (int bk = 0; bk < blockNum; bk++) {
                for (String ges : STATIC_GESTURES) {
                    mStaticGestureSequence.add(ges);
                }
            }
        }

        mTrialCnt = 0;
        mRealTrialCnt = 0;
        currentGesture = mStaticGestureSequence.get(mTrialCnt);
        resetStaticGestureTV(mTrialCnt);

        /// Load/Create files ///

        long time = System.currentTimeMillis();
        mStaticFile = new File(getExternalFilesDir(null),
                String.format("%s_%d_%06d_%s", participantCode, MAX_VALID_POINTS, time % 1000000, mStaticFileName));
        if(!mStaticFile.exists()){
            try {
                mStaticFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else{
            try {
                BufferedReader br = new BufferedReader((new FileReader(mStaticFile)));
                String textFromFile = "";
                String line = "";
                while ((line = br.readLine()) != null) {
                    textFromFile += line.toString();
                    textFromFile += "\n";
                }
                br.close();

                Log.i("Debug", "content from the debug file: \n" + textFromFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }


    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setProminentFaceOnly(true)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS) //.ALL_LANDMARKS)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        int width = FaceGraphic.CAMERA_WIDTH;
        int height = FaceGraphic.CAMERA_HEIGHT;
        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(width, height)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    /**
     * Set visual feedback for face detector result
     * @param flag
     */
    private void setFaceStatus(boolean flag, float left, float right)
    {
        if (flag)
        {
            if (left > -0.1f && right > -0.1f)
            {
                // Normal
                mTvFace.post(new Runnable() {
                    @Override
                    public void run() {
                        if (chineseStr) {
                            mTvFace.setText("识别就绪");
                        }
                        else {
                            mTvFace.setText("Face detected.");
                        }
                        mTvFace.setBackgroundColor(Color.rgb(65, 173, 85));
                    }
                });
            }
            else
            {
                // Something wrong
                String info_prefix = "";
                if (chineseStr) {
                    info_prefix = String.format("轮廓检测不稳定 (%f, %f)", left, right);
                }
                else {
                    info_prefix = String.format("Face detected (%f, %f)", left, right);
                }
                final String strInfo = info_prefix;
                mTvFace.post(new Runnable() {
                    @Override
                    public void run() {
                        mTvFace.setText(strInfo);
                        mTvFace.setBackgroundColor(Color.rgb(216, 132, 43));
                    }
                });
            }

        }
        else
        {
            // Missing
            mTvFace.post(new Runnable() {
                @Override
                public void run() {
                    if (chineseStr) {
                        mTvFace.setText("轮廓检测失败");
                    }
                    else {
                        mTvFace.setText("Face detection failed.");
                    }
                    mTvFace.setBackgroundColor(Color.rgb(173, 65, 65));
                }
            });
        }
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        if (mCameraSource != null) {
            mCameraSource.release();
        }

        countdown.cancel();

        try {

            String content = mStaticFileBuilder.toString();
            if(content.contains("Eyes")){
                PrintWriter pw = new PrintWriter( new FileOutputStream(mStaticFile));
                pw.append("task_gesture,left_eye,right_eye,time_stamp,real_trial_num,trial_cnt\n");
                pw.append(content);
                pw.flush();
                pw.close();

                mClassifier.saveLogToFile(mStaticFile.getName());
                if (chineseStr) {
                    Toast.makeText(getApplicationContext(), "数据保存至:" + mStaticFile.getName(), Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(getApplicationContext(), "Save file to:" + mStaticFile.getName(), Toast.LENGTH_SHORT).show();
                }
            }
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        super.onDestroy();
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    private void resetStaticGestureTV(int trial) {
        String label = "NA: ";
        if (trial >= 0) {
            label = "[" + trial + "]: ";
        }
        if (chineseStr)
            tv_command.setText(label + EN2CN.get(currentGesture) + "\n 1. 请先熟悉要求的眼皮动作，然后做好准备; \n 2. 每做完一个动作会有相应提示，也可以要求额外休息时间。");
        else
            tv_command.setText(label + currentGesture + "\n 1. Please get ready to perform the eye gesture; \n 2. There will be breaks during the session.");
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
        currentGesture = adapterView.getItemAtPosition(pos).toString();
        if (currentGesture.contains("Eye")) {
            // manual mode.
            resetStaticGestureTV(-1);
            mIsAutoMode = false;
        }
        else if (currentGesture.contains("Evaluation")){
            setupEvaluation();
        }
        else if (currentGesture.contains("Auto")) {
            // Resume auto mode:
            mIsAutoMode = true;
            if (mTrialCnt < mStaticGestureSequence.size()) {
                currentGesture = mStaticGestureSequence.get(mTrialCnt);
                resetStaticGestureTV(mTrialCnt);
            }
            else {
                Toast.makeText(getApplicationContext(), "Trial already ended.", Toast.LENGTH_LONG).show();
            }
        }
        Log.e(TAG, "Current gesture:" + currentGesture);
    }

    private void setupEvaluation() {
        // Start training & evaluation.
        if (chineseStr) {
            tv_command.setText("研究人员注意: \n 1. 保存数据，训练识别模型. 2. 测试当前模型.\n");
            bt_pause.setText("--计时器已停止--");

            Integer[] sizePosNeg = mClassifier.getSegmentTrainingDataSize();
            // at least 100 * 3 for each.
            if (sizePosNeg[0] >= 300 && sizePosNeg[1] >= 300) {
                Toast.makeText(getApplicationContext(), String.format("获得 %d / %d 数据点, 收集完成，准备训练模型",
                        sizePosNeg[0], sizePosNeg[1]), Toast.LENGTH_LONG).show();
                bt_calibration.setText("开始训练模型");
                mIsTrainingReady = true;
                currentGesture = "Evaluation";
            }
            else {
                Toast.makeText(getApplicationContext(), String.format("需要更多数据点，目前： %d / %d.",
                        sizePosNeg[0], sizePosNeg[1]), Toast.LENGTH_LONG).show();
            }
        }
        else {
            tv_command.setText("For the researcher: \n 1. Save the data and train the model. 2. Test the model.\n");
            bt_pause.setText("--The timer has been stopped--");

            Integer[] sizePosNeg = mClassifier.getSegmentTrainingDataSize();
            // at least 100 * 3 for each.
            if (sizePosNeg[0] >= 300 && sizePosNeg[1] >= 300) {
                Toast.makeText(getApplicationContext(), String.format("Got %d / %d data points, ready for training",
                        sizePosNeg[0], sizePosNeg[1]), Toast.LENGTH_LONG).show();
                bt_calibration.setText("Start training");
                mIsTrainingReady = true;
                currentGesture = "Evaluation";
            }
            else {
                Toast.makeText(getApplicationContext(), String.format("Need more data, currently we have %d / %d.",
                        sizePosNeg[0], sizePosNeg[1]), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    @Override
    public void onClick(View view) {
        long currTime = System.currentTimeMillis();
        long btnGapTime = currTime - mLastPressedTime;
        if (btnGapTime <= 500) {
            return;
        }
        mLastPressedTime = currTime;

        switch(view.getId()) {
            case R.id.bt_calibration_static:
                clickOnMainBtn();
                break;
            case R.id.bt_cancel:
                if (currentGesture.contains("Evaluation")) {
                    return;
                }

                tone.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 100);

                // Just stop, clear, and don't save.
                if (chineseStr) {
                    bt_calibration.setText("开始");
                }
                else {
                    bt_calibration.setText("Start");
                }

                bt_calibration.setBackgroundColor(Color.rgb(20, 204, 54));
                mRealTrialCnt++;
                collectingDataTag = false;

                // Also reset the counter as well.
                if (!counterPaused) {
                    countdown.cancel();
                    countdown.start();
                }

                break;
            case R.id.bt_pause:
                if (currentGesture != "Evaluation") {
                    if (counterPaused) {
                        countdown.start();
                        bt_pause.setText("Pause the timer");
//                        bt_pause.setText("暂停计时器");
                        counterPaused = false;
                    }
                    else {
                        countdown.cancel();
                        bt_pause.setText("Resume the timer");
//                        bt_pause.setText("暂停计时器");
                        counterPaused = true;
                    }
                }
                break;
        }
    }

    private void clickOnMainBtn(){
//        tone.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 100);
        if (!initCounter) {
            initCounter = true;
            countdown.start();
            return;
        }

        if (currentGesture.contains("Evaluation")) {
            // Evaluation.
            tone.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 100);
            if (!evaluatingModelTag) {
                evaluateStart();
            }
            else {
                evaluateStop();
            }
        }
        else {
            // Collecting period: "XXX Eye(s)" gesture.
            if(!collectingDataTag){
                collectStart();
            }
            else{
                tone.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 100);
                collectStop();
            }
        }
    }

    private void collectStart() {
        // Start collecting, set visual feedback
        if (chineseStr) {
            if (counterMode)
                bt_calibration.setText("开始！\n正在收集数据，请保持动作... ...");
            else
                bt_calibration.setText("停止");
        }
        else {
            if (counterMode)
                bt_calibration.setText("Start! \nData is being collected... ...");
            else
                bt_calibration.setText("Stop");
        }
        bt_calibration.setBackgroundColor(Color.rgb(165, 1, 1));

        mLeftEyeList.clear();
        mRightEyeList.clear();
        mHasEnoughDataForCurrent = false;
        validCounter = 0;
        timeStarted = System.currentTimeMillis();
        collectingDataTag = true;
    }

    private void collectStop() {
        // Stop collecting.
        collectingDataTag = false;

        mClassifier.addSegmentTrainingData(currentGesture, mLeftEyeList, mRightEyeList);
        mRealTrialCnt++;

        boolean breakTime = false;
        // Update trial counter if this is the manual version.
        if (mIsAutoMode) {
            // Inc the counter and update.
            mTrialCnt++;

            if (mTrialCnt < mStaticGestureSequence.size()) {
                currentGesture = mStaticGestureSequence.get(mTrialCnt);
                resetStaticGestureTV(mTrialCnt);

                // Take a break
                if (mTrialCnt > 0 && mTrialCnt % STATIC_GESTURES.length == 0) {
                    breakTime = true;
                    if (chineseStr) {
                        new AlertDialog.Builder(this)
                                .setIcon(android.R.drawable.ic_dialog_info)
                                .setTitle("休息一下")
                                .setMessage("您已经完成了一组实验，准备好进行下一组了吗？")
                                .setPositiveButton("好的", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        countdown.start();
                                    }
                                })
                                .show();
                    }
                    else {
                        new AlertDialog.Builder(this)
                                .setIcon(android.R.drawable.ic_dialog_info)
                                .setTitle("Take a break")
                                .setMessage("You have finished one group of tasks. Ready for the next one?")
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        countdown.start();
                                    }
                                })
                                .show();
                    }

                }
                else
                    countdown.start();
            }
            else {
                // Prepare for evaluation (practice!)
                breakTime = true;
                setupEvaluation();
            }
        }
        else {
            // Manual version.
            mClassifier.logVerboseMsg("collectStop", String.format("Add a manual mode record for %s, size: %d.",
                    currentGesture, mLeftEyeList.size()));
        }
        // Update UI
        if (chineseStr) {
            if (counterMode && !breakTime) {
                countdown.start();
            }
        }
        else {
            bt_calibration.setText("Start");
        }
        bt_calibration.setBackgroundColor(Color.rgb(20, 204, 54));
    }

    private void evaluateStart() {
        final String logTag = "evaluateStart";
        // Start evaluation process.
        if (!mClassifier.isSegmentClfReady()) {
            // Need to train the model, or load the model.
            if (mClassifier.hasExistingSegmentModel())
            {
                // Ask the user.
                AlertDialog.Builder askTrainDialog = new AlertDialog.Builder(this);
                if (chineseStr)
                {
                    askTrainDialog.setTitle("[注意]找到已有训练模型");
                    askTrainDialog.setMessage("您想训练新的模型还是导入已有模型?");
                    askTrainDialog.setPositiveButton("训练新模型（并覆盖旧模型）", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (mIsTrainingReady) {
                                mHasSegmentModelReady = mClassifier.trainSegmentModel();
                            }
                            mClassifier.logVerboseMsg(logTag, String.format("TrainingReady:%b, ModelDone:%b, Time (sec):%f",
                                    mIsTrainingReady, mHasSegmentModelReady, System.currentTimeMillis() / 1000.0));
                        }
                    });
                    askTrainDialog.setNegativeButton("导入已有模型", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            mHasSegmentModelReady = mClassifier.loadSegmentModel();
                            mClassifier.logVerboseMsg(logTag, String.format("Load model status:%b, Time (sec):%f",
                                    mHasSegmentModelReady, System.currentTimeMillis() / 1000.0));
                        }
                    });
                }
                else {
                    askTrainDialog.setTitle("Model file found");
                    askTrainDialog.setMessage("Do you want to train a new model or load the old one?");
                    askTrainDialog.setPositiveButton("Train a new model", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (mIsTrainingReady) {
                                mHasSegmentModelReady = mClassifier.trainSegmentModel();
                            }
                            mClassifier.logVerboseMsg(logTag, String.format("TrainingReady:%b, ModelDone:%b, Time (sec):%f",
                                    mIsTrainingReady, mHasSegmentModelReady, System.currentTimeMillis() / 1000.0));
                        }
                    });
                    askTrainDialog.setNegativeButton("Load the old model", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            mHasSegmentModelReady = mClassifier.loadSegmentModel();
                            mClassifier.logVerboseMsg(logTag, String.format("Load model status:%b, Time (sec):%f",
                                    mHasSegmentModelReady, System.currentTimeMillis() / 1000.0));
                        }
                    });
                }
                askTrainDialog.create().show();
            }
            else {
                if (mIsTrainingReady) {
                    mHasSegmentModelReady = mClassifier.trainSegmentModel();
                }
                mClassifier.logVerboseMsg(logTag, String.format("TrainingReady:%b, ModelDone:%b, Time (sec):%f",
                        mIsTrainingReady, mHasSegmentModelReady, System.currentTimeMillis() / 1000.0));
            }
        }

        if (mHasSegmentModelReady) {
            if (chineseStr) {
                bt_calibration.setText("停止测试");
            }
            else {
                bt_calibration.setText("Stop evaluation");
            }

            bt_calibration.setBackgroundColor(Color.rgb(219, 134, 43));

            timeStarted = System.currentTimeMillis();
            evaluatingModelTag = true;
        }
    }

    private void evaluateStop() {
        // Stop evaluation.
        if (chineseStr) {
            bt_calibration.setText("继续测试");
        }
        else {
            bt_calibration.setText("Continue evaluation");
        }
        bt_calibration.setBackgroundColor(Color.rgb(43, 213, 219));
        evaluatingModelTag = false;
    }

    @Override
    public void onBackPressed() {
        if (mHasSegmentModelReady) {
            finish();
        }
        else {
            new AlertDialog.Builder(this)
                    .setTitle("Closing Activity")
                    .setMessage("Are you sure you want to leave before training the new model?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }

                    })
                    .setNegativeButton("No", null)
                    .show();
        }
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {

        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            if (face == null) {
                setFaceStatus(false, -1, -1);
                return;
            }

            currLeftEyeScore = face.getIsLeftEyeOpenProbability();
            currRightEyeScore = face.getIsRightEyeOpenProbability();

            setFaceStatus(true, currLeftEyeScore, currRightEyeScore);

            if (collectingDataTag) {
                long currTime = System.currentTimeMillis() - timeStarted;
                if (currTime < MAX_TIME_TRAINING && validCounter < mRepeatRestPoints) {

                    // Use valid points to train. But log the entire data.
                    if (currLeftEyeScore > -0.1f && currRightEyeScore > -0.1f) {
                        ++validCounter;

                        mLeftEyeList.add(currLeftEyeScore);
                        mRightEyeList.add(currRightEyeScore);
                    }

                    int trial = mTrialCnt;
                    if (!mIsAutoMode) {
                        trial = -1;
                    }
                    mStaticFileBuilder.append(String.format("%s,%f,%f,%d,%d,%d\n",
                            currentGesture, currLeftEyeScore, currRightEyeScore, currTime, mRealTrialCnt, trial));
                }
                else if (!mHasEnoughDataForCurrent) {
                    // Time out and not marked yet: Change the UI to make sure it is paused.
                    if (currTime >= MAX_TIME_TRAINING) {
                        // This is not expected. Show a toast.
                        Toast.makeText(getApplicationContext(), "Warning: time limit exceeded.", Toast.LENGTH_LONG).show();
                    }
                    ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                    tg.startTone(ToneGenerator.TONE_CDMA_PIP, 100);

                    bt_calibration.post(new Runnable() {
                        @Override
                        public void run() {
                            if (chineseStr) {
                                if (counterMode) {
                                    // start next trial automatically.
                                    clickOnMainBtn();
                                }
                                else
                                    bt_calibration.setText("完成");
                            }
                            else {
                                if (counterMode) {
                                    // start next trial automatically.
                                    clickOnMainBtn();
                                }
                                else
                                    bt_calibration.setText("Done.");
                            }

                            bt_calibration.setBackgroundColor(Color.rgb(197, 214, 49));
                        }
                    });
                    mHasEnoughDataForCurrent = true;
                }
            }

            if (evaluatingModelTag && mHasSegmentModelReady) {
                long currTime = System.currentTimeMillis() - timeStarted;
                final String result = mClassifier.classifySegment(currLeftEyeScore, currRightEyeScore);
                tv_command.post(new Runnable() {
                    @Override
                    public void run() {
                        if(chineseStr) {
                            tv_command.setText("分类结果: " + EN2CN.get(result));
                        }
                        else {
                            tv_command.setText("Classifier Result: " + result);
                        }
                    }
                });
            }

            //Log.d("update", "left open = " + currLeftEyeScore + "; right open = " + currRightEyeScore);
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            setFaceStatus(false, -1, -1);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {

        }
    }
}
