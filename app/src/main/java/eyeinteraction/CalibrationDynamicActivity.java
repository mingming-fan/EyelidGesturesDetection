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
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
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
import java.util.HashMap;
import java.util.Map;

import eyeinteraction.ui.camera.CameraSourcePreview;
import eyeinteraction.ui.camera.GraphicOverlay;
import eyeinteraction.utils.Classifier2D;
import eyeinteraction.utils.SensorData;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class CalibrationDynamicActivity extends AppCompatActivity implements View.OnClickListener{
    private final boolean chineseStr = false;

    private boolean initCounter = false;
    private final String TAG = CalibrationDynamicActivity.this.getClass().getSimpleName();

    private CameraSource mCameraSource = null;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;
    private float currLeftEyeScore = 0;
    private float currRightEyeScore = 0;
    private float prevLeftEyeScore = 0;
    private float prevRightEyeScore = 0;
    private String currentGesture = "";

    Button mBtEvaluation;
    Button mBtRedo;
    Button mBtPause;
    TextView mTvDetail;
    TextView mTvCntDown;
    TextView mTvFace;

    private long timeStarted = 0;
    private static final long MAX_TIME_TRAINING = 20000;
    private int validCounter = 0;
    private static final int MAX_VALID_POINTS = 200;
    private ToneGenerator tone;

    // Major classifier used for training & testing.
    private Classifier2D mClassifier;

    private ArrayList<SensorData> mSensorDataList = new ArrayList<>();
    private boolean hasTrainedSegmentModel = false;
    private boolean hasTrainedAllModels = false;

    // When finish collecting data, this attribute is set true and the model will be ready to train.
    private boolean hasEnoughData = false;
    private boolean collectingDataTag = false;
    private boolean evaluatingModelTag = false;
    private boolean mShowHint = true;
    private ArrayList<String> gestureResults = new ArrayList<>();

    String mDynamicFileName = "dynamic_detail.csv";
    File mDynamicFile;
    StringBuilder mDynamicBuilder = new StringBuilder();
    PrintWriter mDynamicPrinter = null;

    ArrayList<String> eyeGestures = new ArrayList<>();
    HashMap<String,String> eyeGesturesCmdDetail = new HashMap<>();

    String participantCode;
//    String glassCode;
    private int repeatTimes = 1;  // sets of repetitions of the eyelid gestures
    // If repeats < 5, then rest after each block;
    // If repeats > 5 (5x), then rest after each block of 5 sessions.
    private int repeatRestTimes = 5;
    int trialCnt = 0;
    final int startCnt = 1; //3; //count down timer
//    int redo = 0; //where user redid current trial
    private boolean mRedoAllowed = true;
    // This is the real trial number including those caused by redo.
    private int realTrialCnt = 0;
    private boolean mDataQuality = false;
    private HashMap<String, Integer> mValidTrials = new HashMap<>();

    private final String[] EYE_GESTURES = {"cLo", "cRo", "cL-o", "cR-o", "cB-o","cB-RLo", "cB-LRo", "dB", "sB"};
    private final String SHORT_GESTURES = "cLo_cRo_sB";
    private HashMap<String, String> TO_NEW_GESTURE = new HashMap<>();

    // Graph attr
    private LineGraphSeries<DataPoint> mGraphSeriesLeft;
    private LineGraphSeries<DataPoint> mGraphSeriesRight;
//    private int graphLastX = 0;
    private int mFrameCnt = 0;
    // Draw one frame for every 3 sensor data (1/3 of the sensor fq)
    private final int GRAPH_FRAME_RATIO = 3;
    private ArrayList<DataPoint> mGraphLeftPoints = new ArrayList<>();
    private ArrayList<DataPoint> mGraphRightPoints = new ArrayList<>();
    private final int GRAPH_X_LENGTH = 60;

    boolean showGraph = false;

    // Lock for button.
    private long mLastPressedTime = 0;

    private  Map<String, String> EN2CN = new HashMap<String, String>();

    private boolean counterPaused = false;
    private CountDownTimer countdown = new CountDownTimer(7000, 1000) {
        @Override
        public void onTick(long l) {
            mBtEvaluation.setText("Ready for this gesture?\nTimer: " + l / 1000);
//            mBtEvaluation.setText("请准备好做以上动作\n倒计时：" + l / 1000);
        }

        @Override
        public void onFinish() {
            clickOnMainBtn();
        }
    };

    //  Collection Timer: Short; Medium; Long
    private final int[] collectLength = {4500, 7000, 9000};

    private CountDownTimer collectTimerShort = new CountDownTimer(collectLength[0], 1000) {
        @Override
        public void onTick(long l) {
            commonTick(l);
        }

        @Override
        public void onFinish() {
            commonFinish();
        }
    };

    private CountDownTimer collectTimerMedium = new CountDownTimer(collectLength[1], 1000) {
        @Override
        public void onTick(long l) {
            commonTick(l);
        }

        @Override
        public void onFinish() {
            commonFinish();
        }
    };

    private CountDownTimer collectTimerLong = new CountDownTimer(collectLength[2], 1000) {
        @Override
        public void onTick(long l) {
            commonTick(l);
        }

        @Override
        public void onFinish() {
            commonFinish();
        }
    };

    private final CountDownTimer[] collectTimers = {collectTimerShort, collectTimerMedium, collectTimerLong};
    private HashMap<String, Integer> gesture2length = new HashMap<>();

    private boolean collectCounting = false;

    // Count down unified callback:
    private void commonTick(long l) {
        collectCounting = true;
        mBtEvaluation.setText("Start! Please wait for a few seconds if you have finished..." + l / 1000);
//        mBtEvaluation.setText("开始！做完动作后请等待程序自动结束..." + l / 1000);
    }

    private void commonFinish() {
        // as if I pressed the button to stop.
        collectCounting = false;
        clickOnMainBtn();
    }

    private void initGestureDict() {
        gesture2length.put("cLo", 0);
        gesture2length.put("cRo", 0);
        gesture2length.put("sB", 0);
        gesture2length.put("dB", 0);

        gesture2length.put("cL-o", 1);
        gesture2length.put("cR-o", 1);
        gesture2length.put("cB-o", 1);

        gesture2length.put("cB-LRo", 2);
        gesture2length.put("cB-RLo", 2);
    }

    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        EN2CN.put("Open","检测结果：双眼睁开");
        EN2CN.put("Close", "检测结果：单眼闭合/双眼闭合");

        EN2CN.put("cLo", "只闭左眼（短）");
        EN2CN.put("cRo", "只闭右眼（短）");
        EN2CN.put("cL-o", "只闭左眼（长）");
        EN2CN.put("cR-o", "只闭右眼（长）");
        EN2CN.put("cB-o", "闭双眼（长）");
        EN2CN.put("cB-RLo", "闭双眼-开右眼（长）");
        EN2CN.put("cB-LRo", "闭双眼-开左眼（长）");
        EN2CN.put("dB", "闭双眼-闭双眼");
        EN2CN.put("sB", "闭双眼");

        initGestureDict();

        setContentView(R.layout.activity_calibration_dynamic);

        Bundle b = getIntent().getExtras();
        participantCode = b.getString("participantCode");

        String repeats = b.getString("repeatsCode");
        repeatTimes = Integer.parseInt(repeats);
        boolean preTrainedCode = b.getBoolean("usePretrainedCode");
        showGraph = b.getBoolean("showGraph");

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

        /// UI binding ///

        mBtEvaluation = (Button)findViewById(R.id.bt_calibration_static);
        mBtEvaluation.setBackgroundColor(Color.rgb(20, 204, 54));
        mBtEvaluation.setOnClickListener(this);

        mBtRedo = (Button)findViewById(R.id.bt_redo);
        mBtRedo.setBackgroundColor(Color.rgb(255,165,0));
        mBtRedo.setOnClickListener(this);

        mBtPause = (Button)findViewById(R.id.bt_pause);
        mBtPause.setOnClickListener(this);

        mTvDetail = (TextView)findViewById(R.id.tv_detail);
        mTvCntDown = (TextView)findViewById(R.id.tv_countdown);
        mTvFace = (TextView)findViewById(R.id.tv_face);

//        mCntDownAnimation = new CountDownAnimation(mTvCntDown, startCnt);
//        mCntDownAnimation.setCountDownListener(this);

        /// Set up resting time ///

        if (repeatTimes < 5) {
            repeatRestTimes = repeatTimes;

            for(int i = 0; i < EYE_GESTURES.length; i++){
                for (int j = 0; j < repeatTimes; j++){
                    eyeGestures.add(EYE_GESTURES[i]);
                }
            }
        }
        else {
            repeatRestTimes = 5;
            int blockNum = repeatTimes / repeatRestTimes;
            for (int bk = 0; bk < blockNum; bk++) {
                for(int i = 0; i < EYE_GESTURES.length; i++){
                    for (int j = 0; j < repeatRestTimes; j++){
                        eyeGestures.add(EYE_GESTURES[i]);
                    }
                }
            }
        }

        /// INIT eye gestures ///

        if (chineseStr) {
            eyeGesturesCmdDetail.put("cLo",    "只闭左眼（短）：\n闭一下<b>左</b>眼，然后睁开                ");
            eyeGesturesCmdDetail.put("cRo",    "只闭右眼（短）：\n闭一下<b>右</b>眼，然后睁开                ");
            eyeGesturesCmdDetail.put("cL-o",   "只闭左眼（长）：\n长闭<b>左</b>眼，<i>3秒</i>后睁开                 ");
            eyeGesturesCmdDetail.put("cR-o",   "只闭右眼（长）：\n长闭<b>右</b>眼，<i>3秒</i>后睁开                 ");
            eyeGesturesCmdDetail.put("cB-o",   "闭双眼（长）：\n长闭<b>双</b>眼，<i>3秒</i>后睁开                 ");
            eyeGesturesCmdDetail.put("cB-RLo", "闭双眼-开右眼（长）：\n长闭<b>双</b>眼，<i>3秒</i>后先睁开<b>右</b>眼，再过3秒后全部睁开");
            eyeGesturesCmdDetail.put("cB-LRo", "闭双眼-开左眼（长）：\n长闭<b>双</b>眼，<i>3秒</i>后先睁开<b>左</b>眼，再过3秒后全部睁开");
            eyeGesturesCmdDetail.put("dB",     "闭双眼（短，2次）：\n闭一下<b>双</b>眼，睁开，再闭一下<b>双</b>眼，睁开");
            eyeGesturesCmdDetail.put("sB",     "闭双眼（短）：\n闭一下<b>双</b>眼，睁开              ");
        }
        else {
            eyeGesturesCmdDetail.put("cLo",    "close <b>Left</b> eye and open                ");
            eyeGesturesCmdDetail.put("cRo",    "close <b>Right</b> eye and open               ");
            eyeGesturesCmdDetail.put("cL-o",   "close <b>Left</b> eye, <i>Hold</i>, and open         ");
            eyeGesturesCmdDetail.put("cR-o",   "close <b>Right</b> eye, <i>Hold</i>, and open        ");
            eyeGesturesCmdDetail.put("cB-o",   "close <b>Both</b> eyes, <i>Hold</i>, and open        ");
            eyeGesturesCmdDetail.put("cB-RLo", "close <b>Both</b> eyes, <i>Hold</i>, open <b>Right</b>, <i>Hold</i>, and open Both");
            eyeGesturesCmdDetail.put("cB-LRo", "close <b>Both</b> eyes, <i>Hold</i>, open <b>Left</b>, <i>Hold</i>, and open Both");
            eyeGesturesCmdDetail.put("dB",     "(close Both eyes, open) <b>twice</b>          ");
            eyeGesturesCmdDetail.put("sB",     "close <b>Both</b> eyes, and open              ");
        }

        TO_NEW_GESTURE.put("cLo", "L");
        TO_NEW_GESTURE.put("cRo", "R");
        TO_NEW_GESTURE.put("cL-o", "L-");
        TO_NEW_GESTURE.put("cR-o", "R-");
        TO_NEW_GESTURE.put("cB-o", "B-");
        TO_NEW_GESTURE.put("cB-RLo", "B-L-");
        TO_NEW_GESTURE.put("cB-LRo", "B-R-");
        TO_NEW_GESTURE.put("dB", "BOB");
        TO_NEW_GESTURE.put("sB", "B");
        //"cLo", "cRo", "cL-o", "cR-o", "cB-o","cB-RLo", "cB-LRo", "dB", "sB"

        /// Key logic init ///

        currentGesture = eyeGestures.get(trialCnt);
        mTvDetail.setText(Html.fromHtml(String.format("[%d] %s", trialCnt, eyeGesturesCmdDetail.get(currentGesture))));

        mClassifier = new Classifier2D(participantCode, getExternalFilesDir(null),
                Classifier2D.BufferMethod.ROLLING_WINDOW, 5);
        tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        long time = System.currentTimeMillis();
        mDynamicFile = new File(getExternalFilesDir(null),
                String.format("%s_%d_%06d_%s", participantCode, repeatTimes, time % 1000000, mDynamicFileName));
        if(!mDynamicFile.exists()){
            try {
                mDynamicFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else{
            try {
                BufferedReader br = new BufferedReader((new FileReader(mDynamicFile)));
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

        try {
            mDynamicPrinter = new PrintWriter( new FileOutputStream(mDynamicFile), true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // Check if the static model exists:
        hasTrainedSegmentModel = mClassifier.hasExistingSegmentModel();
        if (hasTrainedSegmentModel) {
            // Usually it has the static model.
            if (preTrainedCode) {
                // The user wants to use the pretrained gesture model.
                if (mClassifier.hasTrainedGestureModel()) {
                    hasTrainedAllModels = mClassifier.buildModels();
                    // Model loaded.
                    if (hasTrainedAllModels) {
                        // Change the UI. Skip the training.
                        if (chineseStr) {
                            mBtEvaluation.setText("开始验证");
                            mTvDetail.setText("眼皮检测算法已就绪");
                        }
                        else {
                            mBtEvaluation.setText("Start evaluation");
                            mTvDetail.setText("Ready for evaluation.");
                        }

                        mBtEvaluation.setBackgroundColor(Color.rgb(43, 213, 219));

                        hasEnoughData = true;
                        timeStarted = System.currentTimeMillis();

                        if (chineseStr) {
                            Toast.makeText(getApplicationContext(), "模型加载成功.", Toast.LENGTH_LONG).show();
                        }
                        else {
                            Toast.makeText(getApplicationContext(), "Model loaded.", Toast.LENGTH_LONG).show();
                        }

                    }
                    else {
                        // Loading failed.
                        if (chineseStr) {
                            Toast.makeText(getApplicationContext(), "[错误] 模型加载失败.",
                                    Toast.LENGTH_LONG).show();
                        }
                        else {
                            Toast.makeText(getApplicationContext(), "Loading gesture model: failed.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }
                else {
                    // Model not found.
                    if (chineseStr) {
                        Toast.makeText(getApplicationContext(), "没有找到该用户的复合模型.",
                                Toast.LENGTH_LONG).show();
                    }
                    else {
                        Toast.makeText(getApplicationContext(), "Gesture pre-trained model not found.",
                                Toast.LENGTH_LONG).show();
                    }
                }
            }

            // Without the pretrained code, OR failed to load the pretrained code:
            if (!hasTrainedAllModels) {
                // Just load the segment model then.
                hasTrainedSegmentModel = mClassifier.loadSegmentModel();
                if (!hasTrainedSegmentModel) {
                    if (chineseStr) {
                        Toast.makeText(getApplicationContext(), "没有找到该用户的基本模型.",
                                Toast.LENGTH_LONG).show();
                    }
                    else {
                        Toast.makeText(getApplicationContext(), "Loading segment model: failed.",
                                Toast.LENGTH_LONG).show();
                    }
                }
            }
        }

        // Not even has the segment model
        if (!hasTrainedSegmentModel) {
            // Not exist, show warning.
            if (chineseStr) {
                new AlertDialog.Builder(this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("错误：没有找到基本模型（来自训练一）")
                        .setMessage("确定继续吗?")
                        .setPositiveButton("是", null)
                        .setNegativeButton("否", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }

                        })
                        .show();
            }
            else {
                new AlertDialog.Builder(this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("Warning: Segment Model not found")
                        .setMessage("Are you sure you want to proceed without an available segment model?")
                        .setPositiveButton("Yes", null)
                        .setNegativeButton("No", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }

                        })
                        .show();
            }
        }

        // we get graph view instance
        GraphView graph = (GraphView) findViewById(R.id.graph);

        if(showGraph){
            // data
            mGraphSeriesLeft = new LineGraphSeries<>();
            mGraphSeriesLeft.setColor(Color.BLUE);
            mGraphSeriesLeft.setTitle("Left Eye");

            mGraphSeriesRight = new LineGraphSeries<>();
            mGraphSeriesRight.setColor(Color.RED);
            mGraphSeriesRight.setTitle("Right Eye");

            graph.addSeries(mGraphSeriesLeft);
            graph.addSeries(mGraphSeriesRight);
            // customize a little bit viewport
            Viewport viewport = graph.getViewport();
            viewport.setYAxisBoundsManual(true);
            viewport.setMinY(-0.2);
            viewport.setMaxY(1.2);
            graph.getLegendRenderer().setVisible(true);
            graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);

        }
        else{
            graph.setVisibility(View.GONE);
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
                if (chineseStr) {
                    mTvFace.post(new Runnable() {
                        @Override
                        public void run() {
                            mTvFace.setText("轮廓检测成功");
                            mTvFace.setBackgroundColor(Color.rgb(65, 173, 85));
                        }
                    });
                }
                else {
                    mTvFace.post(new Runnable() {
                        @Override
                        public void run() {
                            mTvFace.setText("Face detected.");
                            mTvFace.setBackgroundColor(Color.rgb(65, 173, 85));
                        }
                    });
                }
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
        View decorView = getWindow().getDecorView();
        // Hide both the navigation bar and the status bar.
        // SYSTEM_UI_FLAG_FULLSCREEN is only available on Android 4.1 and higher, but as
        // a general rule, you should design your app to hide the status bar whenever you
        // hide the navigation bar.
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN| View.SYSTEM_UI_FLAG_IMMERSIVE;
        decorView.setSystemUiVisibility(uiOptions);

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
        mDynamicPrinter.append("task_gesture,left_eye,right_eye,time_stamp,real_trial_num,trial_cnt\n");
        mDynamicPrinter.append(mDynamicBuilder.toString());
        mDynamicPrinter.flush();
        mDynamicPrinter.close();

        mClassifier.saveLogToFile(mDynamicFile.getName());
        if (chineseStr) {
            Toast.makeText(getApplicationContext(), "数据保存至:" + mDynamicFile.getName(), Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(getApplicationContext(), "File saved to:" + mDynamicFile.getName(), Toast.LENGTH_SHORT).show();
        }

        countdown.cancel();
        if (gesture2length.containsKey(currentGesture))
            collectTimers[gesture2length.get(currentGesture)].cancel();

        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
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


    @Override
    public void onClick(View view) {
        long currTime = System.currentTimeMillis();
        long btnGapTime = currTime - mLastPressedTime;
        if (btnGapTime <= 500) {
            return;
        }
        mLastPressedTime = currTime;

        switch(view.getId()){
            // Note: borrowed from static, but this should be dynamic.
            case R.id.bt_calibration_static:
                clickOnMainBtn();
                break;
            case R.id.bt_redo:
                if (!mRedoAllowed) {
                    return;
                }

                if (!evaluatingModelTag) {
                    mRedoAllowed = false;

                    if (!collectingDataTag) {
                        // Current collecting process finished, need to check whether we can redo.
                        // return to the previous trial.
                        // In case people redo the 1st trial and make the app crash.
                        if (trialCnt > 0) {
                            // real trial cnt already added last-stop, so need to do again.
                            trialCnt -= 1;
                            currentGesture = eyeGestures.get(trialCnt);
                            mTvDetail.setText(Html.fromHtml(String.format("[%d] %s", trialCnt,
                                    eyeGesturesCmdDetail.get(currentGesture))));
                            hasEnoughData = false;
                            // Revoke the data quality tag
                            mDataQuality = false;
                            mClassifier.logVerboseMsg("onClick", currentGesture + ": Redo, switch back to #" + trialCnt);

                            countdown.cancel();
                            countdown.start();
                        }
                    }
                    else {
                        // During collecting period: stop current one
                        collectingDataTag = false;
                        mSensorDataList.clear();
                        realTrialCnt++;

                        if (chineseStr) {
                            mBtEvaluation.setText("开始");
                        }
                        else {
                            mBtEvaluation.setText("Start");
                        }

                        mBtEvaluation.setBackgroundColor(Color.rgb(20, 204, 54));

                        countdown.cancel();
                        countdown.start();

                        // stop  the silent counter as well (which was activated when data collection starts)
                        if (collectCounting) {
                            assert gesture2length.containsKey(currentGesture);
                            collectTimers[gesture2length.get(currentGesture)].cancel();
//                            collectCountdown.cancel();
                        }
                    }

                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
                }
                break;
            case R.id.bt_pause:
                if (!hasTrainedAllModels && !collectCounting) {
                    // only response when training is not ready
                    if (counterPaused) {
                        countdown.start();
                        mBtPause.setText("Pause the timer");
//                        mBtPause.setText("暂停计时器");
                        counterPaused = false;
                    }
                    else {
                        countdown.cancel();
                        mBtPause.setText("Resume the timer");
//                        mBtPause.setText("恢复计时器");
                        counterPaused = true;
                    }
                }
                else if (collectCounting) {
                    Toast.makeText(getApplicationContext(), "We cannot pause during data collection. Please redo this session.", Toast.LENGTH_SHORT).show();
//                    Toast.makeText(getApplicationContext(), "收集数据过程无法被暂停，请使用[重做]功能", Toast.LENGTH_SHORT).show();
                }
                break;
        }

    }

    private void clickOnMainBtn() {

        if (!initCounter) {
            initCounter = true;
            countdown.start();
            return;
        }

        if(!collectingDataTag){
            // Not collecting
            if (!hasEnoughData) {
                // Still need to collect more data
                collectStart();
            }
            else {
                // Has enough, train/evaluate.
                if (!evaluatingModelTag) {
                    evaluateStart();
                }
                else {
                    evaluateStop();
                }
            }
        }
        else{
            collectStop();
        }

    }

    private void collectStart() {
        // counter starts for this gesture;
//        collectCountdown.start();
        assert gesture2length.containsKey(currentGesture);
        collectTimers[gesture2length.get(currentGesture)].start();

        // Start collecting right now. Update current gesture and time stamp.
        timeStarted = System.currentTimeMillis();

        // Before clearing, save them, since the user is not "redo" the previous one.
        if (mDataQuality) {
            String newAddedName = mClassifier.addGestureTrainingData();
            Log.i("Add", newAddedName + ", current Short/Long:" + mClassifier.getShortTrainingDataSize() + "," + mClassifier.getLongTrainingDataSize());
            // Log the valid size;
            if (mValidTrials.containsKey(newAddedName)) {
                mValidTrials.put(newAddedName, mValidTrials.get(newAddedName) + 1);
            } else {
                mValidTrials.put(newAddedName, 1);
            }
        }

        mRedoAllowed = true;
        mDataQuality = false;
        mSensorDataList.clear();
        mShowHint = true;

//        mCntDownAnimation.cancel();
//        mCntDownAnimation.setStartCount(startCnt);
//        mCntDownAnimation.start();

        collectingDataTag = true;

        // Start collecting, set visual feedback
        if (chineseStr) {
            mBtEvaluation.setText("结束");
        } else {
            mBtEvaluation.setText("Stop");
        }

        mBtEvaluation.setBackgroundColor(Color.rgb(165, 1, 1));

        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
    }

    private void collectStop() {
        String logTag = "collectStop";
        // Stop collecting and save.
        collectingDataTag = false;
        // See how the results goes
        mDataQuality = mClassifier.segmentGestureTrainingData(currentGesture, mSensorDataList);
        if (mDataQuality) {
            if (chineseStr) {
                Toast.makeText(getApplicationContext(), "成功", Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(getApplicationContext(), "Good", Toast.LENGTH_SHORT).show();
            }

            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
        } else {
            if (chineseStr) {
                Toast.makeText(getApplicationContext(), "错误：请重新训练本组数据", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Error, please train this session again", Toast.LENGTH_SHORT).show();
            }

            tone.startTone(ToneGenerator.TONE_CDMA_PIP, 100);
        }

        trialCnt++;
        realTrialCnt++;

        // Do we need to increase the size?
        if (trialCnt >= eyeGestures.size()) {
            // double check the valid size:
            for (String kk : mValidTrials.keySet()) {
                if (mValidTrials.get(kk) < repeatTimes) {
                    int moreData = repeatTimes - mValidTrials.get(kk);
                    if (moreData == 1 && kk.equals(currentGesture) && mDataQuality) {
                        // Just fine, because we have one more.
                        mValidTrials.put(kk, mValidTrials.get(kk) + 1);
                    }
                    else {
                        for (int i = 0; i < moreData; ++i) {
                            eyeGestures.add(kk);
                        }
                        mClassifier.logVerboseMsg(logTag, String.format("Need to add [%d] more valid training trials for %s",
                                moreData, kk));
                    }
                }
            }
        }

        // Check the (maybe updated) size.
        if(trialCnt < eyeGestures.size()){
            currentGesture = eyeGestures.get(trialCnt);
            mTvDetail.setText(Html.fromHtml(String.format("[%d] %s", trialCnt, eyeGesturesCmdDetail.get(currentGesture))));
            boolean duringBreak = false;
            if (trialCnt > 0 && trialCnt % repeatRestTimes == 0) {
                duringBreak = true;
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

            if (chineseStr) {
                if (!duringBreak) {
                    countdown.start();
                }
                mBtEvaluation.setText("开始");
            } else {
                if (!duringBreak) {
                    countdown.start();
                }
                mBtEvaluation.setText("Start");
            }

            mBtEvaluation.setBackgroundColor(Color.rgb(20, 204, 54));
        }
        else{
            if (chineseStr) {
                mTvDetail.setText("完成！");
                mBtEvaluation.setText("开始评估算法");
            }
            else {
                mTvDetail.setText("Complete!");
                mBtEvaluation.setText("Start evaluation");
            }

            mBtEvaluation.setBackgroundColor(Color.rgb(43, 213, 219));
            int shortSize = mClassifier.getShortTrainingDataSize();
            int longSize = mClassifier.getLongTrainingDataSize();

            Toast.makeText(getApplicationContext(),
                    String.format("Short:%d, Long:%d", shortSize, longSize), Toast.LENGTH_SHORT).show();
            hasEnoughData = true;
        }
    }

    private void evaluateStart() {
        // Add the last batch
        // Before clearing, save them, since the user is not "redo" the previous one.
        if (mDataQuality) {
            mClassifier.addGestureTrainingData();

            mSensorDataList.clear();
            mDataQuality = false;
        }
        mRedoAllowed = false;

        // Start evaluation.
        if (!mClassifier.isGestureClfReady()) {
            // Need to train the model, or load the model.
            if (mClassifier.hasTrainedGestureModel()) {
                // Ask the user.
                AlertDialog.Builder askTrainDialog = new AlertDialog.Builder(this);
                if (chineseStr) {
                    askTrainDialog.setTitle("存在已有模型");
                    askTrainDialog.setMessage("您要训练新模型（并覆盖已有模型），还是直接载入已有模型?");
                    askTrainDialog.setPositiveButton("训练新模型", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            hasTrainedAllModels = mClassifier.trainGestureModel();
                            Log.i("TAG", "Trained model status:" + hasTrainedAllModels);
                        }
                    });
                    askTrainDialog.setNegativeButton("载入已有模型", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            hasTrainedAllModels = mClassifier.buildModels();
                            Log.i("TAG", "Load model from file status:" + hasTrainedAllModels);
                        }
                    });
                } else {
                    askTrainDialog.setTitle("Model file found");
                    askTrainDialog.setMessage("Do you want to train a new model or load the old one?");
                    askTrainDialog.setPositiveButton("Train a new model", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            hasTrainedAllModels = mClassifier.trainGestureModel();
                            Log.i("TAG", "Trained model status:" + hasTrainedAllModels);
                        }
                    });
                    askTrainDialog.setNegativeButton("Load the old model", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            hasTrainedAllModels = mClassifier.buildModels();
                            Log.i("TAG", "Load model from file status:" + hasTrainedAllModels);
                        }
                    });
                }

                askTrainDialog.create().show();
            }
            else {
                // Train.
                hasTrainedAllModels = mClassifier.trainGestureModel();
                Log.i("TAG", "Trained model status:" + hasTrainedAllModels);
            }
            timeStarted = System.currentTimeMillis();
        }

        if (hasTrainedAllModels) {
            evaluatingModelTag = true;
            if (chineseStr) {
                mBtPause.setText("--计时器已停止--");
                mBtEvaluation.setText("停止评估");
            } else {
                mBtPause.setText("--The timer has been stopped--");
                mBtEvaluation.setText("Stop evaluation");
            }

            mBtEvaluation.setBackgroundColor(Color.rgb(219, 134, 43));
        }

        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
    }

    private void evaluateStop() {
        evaluatingModelTag = false;
        if (chineseStr) {
            mBtEvaluation.setText("开始评估算法");
        } else {
            mBtEvaluation.setText("Start evaluation");
        }

        mBtEvaluation.setBackgroundColor(Color.rgb(43, 213, 219));

        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
    }

    @Override
    public void onBackPressed() {
        if (hasTrainedAllModels) {
            finish();
        }
        else {
            if (chineseStr) {
                new AlertDialog.Builder(this)
                        .setTitle("退出训练过程")
                        .setMessage("训练过程未完成。您确认退出吗？")
                        .setPositiveButton("确认", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }

                        })
                        .setNegativeButton("取消", null)
                        .show();
            } else {
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

            long currTime = System.currentTimeMillis() - timeStarted;

            if (mShowHint) {
                final String segmentResult = mClassifier.classifySegment(currLeftEyeScore, currRightEyeScore);
                mTvCntDown.post(new Runnable() {
                    @Override
                    public void run() {
                        if (chineseStr) {
                            mTvCntDown.setText(EN2CN.get(segmentResult));
                        } else {
                            mTvCntDown.setText(segmentResult);
                        }
                    }
                });
            }

            if(collectingDataTag){

                mSensorDataList.add(new SensorData(currLeftEyeScore, currRightEyeScore, currTime));

                // TO support previous data analysis process, we remain 'redo' field.
                // But actually we could use 'trialCnt' field to determine which one is the latest data.
                mDynamicBuilder.append(String.format("%s,%f,%f,%d,%d,%d\n", currentGesture,
                        currLeftEyeScore, currRightEyeScore, currTime, realTrialCnt, trialCnt));
            }

            if (evaluatingModelTag) {
                String resultRT = mClassifier.classify(currLeftEyeScore, currRightEyeScore);
                if (resultRT.length() > 0) {
                    String[] results = resultRT.split(",");
                    final String segmentResult = results[0].trim();
                    String gestureResult = results[1].trim();
//                    Log.d("DEBUG", resultRT + ", size:" + gestureResults.size());

                    mTvCntDown.post(new Runnable() {
                        @Override
                        public void run() {
                            if (chineseStr) {
                                mTvCntDown.setText(EN2CN.get(segmentResult));
                            } else {
                                mTvCntDown.setText(segmentResult);
                            }
                        }
                    });

                    String previousResult = "";
                    if (gestureResults.size() > 0) {
                        previousResult = gestureResults.get(gestureResults.size() - 1);
                    }
                    if (gestureResult.equals("NA") || gestureResult.equals("?") || gestureResult.equals("?-")) {
                        if (!previousResult.equals(gestureResult)) {
                            // Different with the previous one.
                            if (gestureResult.equals("?-") && previousResult.equals("?")) {
                                // Use "?-" to replace previous "?"
                                gestureResults.remove(gestureResults.size() - 1);
                            }
                            if (!(gestureResult.equals("?") && previousResult.equals("?-"))) {
                                gestureResults.add(gestureResult);
                            }

                        }
                        // else: ignore.
                    }
                    else {
                        // Real gesture result, or "UN" (at least the detection ended)
                        if (gestureResults.size() > 0 && (previousResult.equals("?") || previousResult.equals("?-"))) {
                            // Remove the unfinished detection result.
                            gestureResults.remove(gestureResults.size() - 1);
                        }
                        gestureResults.add(gestureResult);
                        Log.d("DEBUG", "Add new gesture:" + gestureResult);
                    }

                    if (gestureResults.size() > 5) {
                        gestureResults.remove(0);
                    }

                    StringBuilder showStr = new StringBuilder();


                    // For video shooting, use a simplified version.
                    if (TO_NEW_GESTURE.containsKey(gestureResult)) {
                        // Valid gesture
                        if (chineseStr) {
                            showStr.append(EN2CN.get(gestureResult));
                        } else {
                            showStr.append(TO_NEW_GESTURE.get(gestureResult));
                        }
                    }
                    else if (gestureResult.contains("?")) {
                        // Detecting...
                        showStr.append(gestureResult);
                    }
                    // else: don't change.

                    final String tvResult = showStr.toString();

                    if (tvResult.length() > 0) {
                        mTvDetail.post(new Runnable() {
                            @Override
                            public void run() {
                                mTvDetail.setText(tvResult);
                            }
                        });
                    }
                }
                else {
                    mTvDetail.post(new Runnable() {
                        @Override
                        public void run() {
                            mTvDetail.setText("Error: Empty result from the classifier");
                        }
                    });
                }

                if(showGraph){
                    // Draw on the graph
                    if (mFrameCnt % GRAPH_FRAME_RATIO == 0 && currLeftEyeScore > -0.1 && currRightEyeScore > -0.1) {
                        addDataPoint(currTime, currLeftEyeScore, currRightEyeScore);
                        mFrameCnt = 0;
                    }
                    mFrameCnt++;
                }
            }
            //Log.d("update", "left open = " + currLeftEyeScore + "; right open = " + currRightEyeScore);
        }

        private void addDataPoint(final float tt, final float ll, final float rr) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mGraphSeriesLeft.appendData(new DataPoint(tt / 1000.0, ll), false, 50);
                    mGraphSeriesRight.appendData(new DataPoint(tt / 1000.0, rr), false, 50);
                }
            });
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
