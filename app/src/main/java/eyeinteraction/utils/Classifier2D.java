package eyeinteraction.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;

import android.util.Log;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.UnivariateInterpolator;
import org.apache.commons.math3.distribution.*;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import weka.classifiers.functions.LibSVM;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

public class Classifier2D {
    private String mPID = "P99";

    public enum BufferMethod { RAW, ROLLING_WINDOW, ONE_EURO }
    private BufferMethod mMethod = BufferMethod.RAW;

    private ArrayList<Float> mBufferLeft = new ArrayList<>();
    private ArrayList<Float> mBufferRight = new ArrayList<>();
    private OneEuroFilter oefLeft = null;
    private OneEuroFilter oefRight = null;
    public float recentLeft = -1;
    public float recentRight = -1;
    private String recentSegmentState = "Close";

    private String prevGesture = "";
    private long prevGestureEndTime = 0;

    // TODO: To be tested with experiments.
    private int mBufferSize = 10;
    private static final int TRAINING_SIZE = 1000;
    private static final int SAMPLE_NUM_SHORT = 50;
    private static final int SAMPLE_NUM_LONG = 100;

    private MultivariateNormalDistribution m2DNormalModel;

    private File mPath = null;

    // Attributes for real-time classifier
    // Discard the stored candidate points if the gesture exceeds 10 seconds.
    private static final long MAX_GESTURE_TIME = 10000;
    // Use long gesture classifier if the gesture lasts longer than 1 second.
    private int MAX_SHORT_GESTURE = 1500;
    // Set during training.
    private long MAX_DOUBLE_BLINK_GAP = 700;
    // Wait for a short time before reporting the result (sometimes a long gesture is cut into halves)
    private static final long CLASSIFY_WAIT_TIME = 150;
    // If the user blinks faster than this (blinks / time < MAX), then it could be sensor error.
    private static final float MAX_BLINK_TIME = 100;

    private long timeStartOffset = 0;
    private long gestureStartTime = 0;
    private long gestureEndTime = 0;
    private ArrayList<SensorData> gestureCandidate = new ArrayList<>();
    private SensorData prevDataPoint = null;

    private double[] mGestureCandidateSamples;
    private int mGestureCandidateDuration;
    private String mGestureCandidateName;
    // double blink must be trained at last, since it relies on the single blink
    // Store them first.
    private ArrayList<SensorData[]> mDoubleBlinkGestures = new ArrayList<>();
    private int mDoubleBlinkDataCnt = 0;
    ArrayList<Float> mDoubleThresholds = new ArrayList<>();

    /*
    INIT: no data yet.
    OPEN: ready to accept new gesture (-> CLOSE, mark StartTime)
    CLOSE: collecting data; ready to end (-> OPEN_WAITING, mark EndTime)
    OPEN_WAITING: waiting to continue (-> CLOSE); waiting to report (-> OPEN; check blink rate)
     */
    private enum RealTimeGesture { INIT, CLOSE, OPEN, OPEN_WAITING }
    private int mBlinkCount = 0;
    private RealTimeGesture currGestureState = RealTimeGesture.INIT;

    // Attributes for segment classifier
    private LibSVM mSegmentClf = null;
    private Instances mSegmentInstances;
    private StringBuilder mSegmentTrainingData  = new StringBuilder();
    private int mSegmentTrainingPosCnt = 0;
    private int mSegmentTrainingNegCnt = 0;
    final private String SEGMENT_TRAINING_NAME = "segment_training.arff";
    final private String SEGMENT_MODEL_NAME = "segment_model.libsvm";

    // Attributes for short gesture classifier
    private LibSVM mShortClf = null;
    private Instances mShortInstances;
    private StringBuilder mShortTrainingData = new StringBuilder();
    private int mShortTrainingDataCnt = 0;
    private ArrayList<Integer> mShortDuration = new ArrayList<>();

    final private String[] SHORT_GESTURES = { "cLo", "cRo", "sB" };
    final private String SHORT_TRAINING_NAME = "short_training.arff";
    final private String SHORT_MODEL_NAME = "short_model.libsvm";

    // Attributes for long gesture classifier
    private LibSVM mLongClf = null;
    private Instances mLongInstances;
    private StringBuilder mLongTrainingData = new StringBuilder();
    private int mLongTrainingDataCnt = 0;
    private ArrayList<Integer> mLongDuration = new ArrayList<>();

    final private String[] LONG_GESTURES = { "cL-o", "cR-o", "cB-o","cB-RLo", "cB-LRo" };
    final private String LONG_TRAINING_NAME = "long_training.arff";
    final private String LONG_MODEL_NAME = "long_model.libsvm";

    final private String THRESHOLD_MODEL = "gesture_threshold.log";

    private StringBuilder mErrorLogger = new StringBuilder();


    public Classifier2D() {
    }

    /**
     * Constructor of Classifier2D.
     * @param pid Participant's ID, e.g., "P68".
     * @param path App path: getApplicationContext().getFilesDir().
     *             Try a different path for easier access: getExternalFilesDir(null).
     */
    public Classifier2D (String pid, File path) {
        mPID = pid;
        mPath = path;
    }

    public Classifier2D (String pid, File path, BufferMethod method) {
        mPID = pid;
        mPath = path;
        mMethod = method;
    }

    public Classifier2D (String pid, File path, BufferMethod method, int BufferSize) {
        mPID = pid;
        mPath = path;
        mMethod = method;
        mBufferSize = BufferSize;
    }

    // Check ready status
    public boolean isSegmentClfReady() {
        return mSegmentClf != null;
    }

    public boolean isGestureClfReady() {
        return mShortClf != null && mLongClf != null;
    }

    /**
     * Public interface to load the model.
     * @return true if the model is loaded successfully.
     */
    public boolean buildModels() {
        boolean flagS = true;
        if (!isSegmentClfReady()) {
            flagS = loadSegmentModel();
        }

        boolean flagG = true;
        if (!isGestureClfReady()) {
            flagG = loadGestureModel();
        }

        clearBuffer();
        currGestureState = RealTimeGesture.INIT;
        timeStartOffset = System.currentTimeMillis();
        logVerboseMsg("buildModels", "Result:" + flagS + "," + flagG);
        return flagS && flagG;
    }

    /**
     * Public interface to classify the gesture.
     * @param left score of the left eye from the sensor
     * @param right score of the right eye from the sensor
     * @return Gesture string if detected, else return an empty string.
     */
    public String classify(float left, float right) {
        String logTag = "classify";
        long timeStamp = System.currentTimeMillis() - timeStartOffset;
//        Log.i("Time", "Start:" + timeStamp);
        String resultStr = "";

        if (left > -0.5 || right > -0.5) {
            // valid point (at least one side is valid)
            float timeSec = (float) (timeStamp) / 1000.0f;
            updateBuffer(left, right, timeSec);

            boolean isOpen = setOpenState(recentLeft, recentRight);

            // State changes in the following case
            switch (currGestureState) {
                case INIT:
                    if (isOpen) {
                        // Normally it starts from open both
                        currGestureState = RealTimeGesture.OPEN;
                        resultStr = "NA";
                    }
                    else {
                        resultStr = "?";
                    }
                    break;
                case OPEN:
                    if (!isOpen) {
                        // Start a gesture, record.
                        currGestureState = RealTimeGesture.CLOSE;
                        gestureStartTime = timeStamp;
                        mBlinkCount = 0;
                        gestureCandidate.clear();
                        // Add the leading "open" point
                        if (prevDataPoint != null) {
                            gestureCandidate.add(new SensorData(prevDataPoint));
                        }
                        resultStr = "?";
                    }
                    else {
                        resultStr = "NA";
                    }
                    break;
                case CLOSE:
                    if (isOpen) {
                        // Gesture ended, classify later
                        gestureEndTime = timeStamp;
                        // Add one more ending "open" point
                        gestureCandidate.add(new SensorData(left, right, timeStamp));

                        if (gestureCandidate.size() > 4) {
                            // If this is not a single blink, then need to wait.
                            if (gestureEndTime - gestureStartTime <= MAX_SHORT_GESTURE) {
                                // Short:
                                double[] sampledData = sampleData(gestureCandidate, SAMPLE_NUM_SHORT);
                                resultStr = classifyShort(sampledData);
                                if (resultStr.equals("sB")) {
                                    // it's a single blink, report and no need to wait
                                    boolean isDouble = specialJudgeBlink("sB", timeStamp);
                                    if (isDouble) {
                                        resultStr = "dB";
                                    }
                                    currGestureState = RealTimeGesture.OPEN;
                                } else {
                                    // Other gestures (short or long): still need to wait
                                    currGestureState = RealTimeGesture.OPEN_WAITING;
                                    mBlinkCount++;
                                    resultStr = "NA";
                                }
                            } else {
                                // Other gestures (short or long): still need to wait
                                currGestureState = RealTimeGesture.OPEN_WAITING;
                                mBlinkCount++;
                                resultStr = "NA";
                            }
                        }
                        else {
                            // Ignore this (could be a single blink, or not)
                            boolean isDouble = specialJudgeBlink("UN", timeStamp);
                            if (isDouble) {
                                resultStr = "dB";
                            }
                            else {
                                resultStr = "UN";
                                logVerboseMsg(logTag, String.format("Ignore this %s, too short: size: %d, duration:%d", resultStr, gestureCandidate.size(),
                                        gestureCandidate.get(gestureCandidate.size() - 1).Time - gestureCandidate.get(0).Time));
                            }
                            currGestureState = RealTimeGesture.OPEN;
                        }
                    }
                    else {
                        resultStr = "?";
                    }
                    break;
                case OPEN_WAITING:
                    if (timeStamp - gestureEndTime > CLASSIFY_WAIT_TIME) {
                        // Long enough. Classify and switch to normal OPEN state.
                        float blinkSpeed = (float)(gestureEndTime - gestureStartTime) / mBlinkCount;
                        if (blinkSpeed > MAX_BLINK_TIME) {
                            // Normal speed. Start classifying.
                            if (gestureEndTime - gestureStartTime <= MAX_SHORT_GESTURE) {
                                // Short:
                                double[] sampledData = sampleData(gestureCandidate, SAMPLE_NUM_SHORT);
                                resultStr = classifyShort(sampledData);
                                if (resultStr.length() == 0) {
                                    // Got a gesture, but failed to recognize it
                                    resultStr = "UN";
                                }
                            } else {
                                // Long:
                                double[] sampledData = sampleData(gestureCandidate, SAMPLE_NUM_LONG);
                                resultStr = classifyLong(sampledData);
                                if (resultStr.length() == 0) {
                                    // Got a gesture, but failed to recognize it
                                    resultStr = "UN-";
                                }
                            }
                        } else {
                            // Blink too fast, might be error. Ignore this and switch back.
                            resultStr = "UN";
                            logVerboseMsg(logTag, String.format("Too many blinks (%d) within %d - %d",
                                    mBlinkCount, gestureStartTime, gestureEndTime));
                        }
                        // Switch to OPEN anyway (note: could be actually close, but deal with it later)
                        currGestureState = RealTimeGesture.OPEN;
                    }
                    else {
                        // Still waiting
                        if (isOpen) {
                            resultStr = "NA";
                        }
                        else {
                            // Keep on counting, return to CLOSE, remove the last end point
                            gestureCandidate.remove(gestureCandidate.size() - 1);
                            currGestureState = RealTimeGesture.CLOSE;
                            resultStr = "?";
                            logVerboseMsg(logTag, "Back to close during waiting:" + timeStamp);
                        }
                    }
                    break;
            }

            // Regular case: store when the gesture is ON
            if (currGestureState == RealTimeGesture.CLOSE) {
                if (timeStamp - gestureStartTime >= MAX_GESTURE_TIME) {
                    // Too long, maybe the user is away.
                    gestureCandidate.clear();
                    resultStr = "?-";
                    currGestureState = RealTimeGesture.INIT;
                }
                else if (timeStamp - gestureStartTime > MAX_SHORT_GESTURE) {
                    // Long enough, change the marker
                    resultStr = "?-";
                }
                else {
                    resultStr = "?";
                }
                gestureCandidate.add(new SensorData(left, right, timeStamp));
            }

            resultStr = recentSegmentState + "," + resultStr;
        }
        else {
            // Lost data. Repeat previous one.
            if (recentSegmentState.equals("Open")) {
                resultStr = "Open,NA";
            } else {
                resultStr = "Close,?";
            }
        }

//        Log.i("Time", "End:" + timeStamp);
        return resultStr;
    }

    public String flushResult() {
        String result = "";
        if (currGestureState == RealTimeGesture.OPEN_WAITING) {
            // Normal speed. Start classifying.
            if (gestureEndTime - gestureStartTime <= MAX_SHORT_GESTURE) {
                // Short:
                double[] sampledData = sampleData(gestureCandidate, SAMPLE_NUM_SHORT);
                result = classifyShort(sampledData);
                if (result.length() == 0) {
                    // Got a gesture, but failed to recognize it
                    result = "UN";
                }
            } else {
                // Long:
                double[] sampledData = sampleData(gestureCandidate, SAMPLE_NUM_LONG);
                result = classifyLong(sampledData);
                if (result.length() == 0) {
                    // Got a gesture, but failed to recognize it
                    result = "UN-";
                }
            }
        }
        return result;
    }

    private boolean setOpenState(float ll, float rr) {
        recentSegmentState = classifySegment(ll, rr);
        boolean isOpen = recentSegmentState.equals("Open");

        return isOpen;
    }

    private boolean specialJudgeBlink(String gesture, long currTime) {
        boolean isDouble = false;
        if ((gesture.equals("sB") || gesture.equals("UN")) &&
                (prevGesture.equals("sB") || prevGesture.equals("UN")) &&
                currTime - prevGestureEndTime <= MAX_DOUBLE_BLINK_GAP) {
            isDouble = true;
            prevGesture = "";
            prevGestureEndTime = 0;
        }

        prevGesture = gesture;
        prevGestureEndTime = currTime;

        return isDouble;
    }

    // Public interface for training

    /**
     * Adding training data for the segment model: accept list of points, and transfer the label into
     * Open_Both_Eyes vs. Others.
     * @param category Original labels of 4 types.
     * @param leftList
     * @param rightList
     */
    public void addSegmentTrainingData(String category,
                                       ArrayList<Float> leftList, ArrayList<Float> rightList) {
        // Calculate the threshold for this category.
        assert leftList.size() == rightList.size();

        String label = "Others";
        boolean isPos = false;
        if (category.contains("Open Both Eyes")) {
            label = "Open_Both_Eyes";
            isPos = true;
        }

        for (int i = 0; i < leftList.size(); ++i) {
            mSegmentTrainingData.append(String.format("%f,%f,%s\n", leftList.get(i), rightList.get(i), label));
        }

        if (isPos) {
            mSegmentTrainingPosCnt += leftList.size();
        } else {
            mSegmentTrainingNegCnt += leftList.size();
        }
    }

    /**
     * Return the positiveSize,negativeSize,dbSize
     * @return
     */
    public Integer[] getSegmentTrainingDataSize() {
        Integer[] intList = {mSegmentTrainingPosCnt, mSegmentTrainingNegCnt, mDoubleBlinkDataCnt};
        return intList;
    }

    public int getShortTrainingDataSize() {
        return mShortTrainingDataCnt;
    }

    public int getLongTrainingDataSize() {
        return mLongTrainingDataCnt;
    }

    public int getDoubleBlinkTrainingDataSize() {
        return mDoubleBlinkDataCnt;
    }

//    public void setWorkingPath(File path) {
//        mPath = path;
//    }

    public boolean trainSegmentModel() {
        String logTag = "trainSegment";
        if (!saveSegmentTrainingData()) {
            logVerboseMsg("trainSegmentModel", "no training file");
            return false;
        }

        File dataFile = new File(mPath, String.format("%s_%s", mPID, SEGMENT_TRAINING_NAME));
        if(!dataFile.exists()){
            logVerboseMsg("trainSegmentModel", "no training file");
            return false;
        }

        File modelFile = new File(mPath, String.format("%s_%s", mPID, SEGMENT_MODEL_NAME));
        if(!modelFile.exists()){
            try {
                modelFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            logVerboseMsg(logTag, "start training segment (ms):" + System.currentTimeMillis());
            BufferedReader br = new BufferedReader(new FileReader(dataFile));
            mSegmentInstances = new Instances(br);

            br.close();
            Log.i("TAG", "number of attributes: " + mSegmentInstances.numAttributes());
            mSegmentInstances.setClassIndex(mSegmentInstances.numAttributes() - 1);

            mSegmentClf = new LibSVM();

            mSegmentClf.buildClassifier(mSegmentInstances);

            logVerboseMsg(logTag, "finish training segment (ms):" + System.currentTimeMillis());

            // Save
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(modelFile));
            oos.writeObject(mSegmentClf);
            oos.flush();
            oos.close();
            logVerboseMsg(logTag, "save training segment (ms):" + System.currentTimeMillis());

        } catch (Exception e) {
            logVerboseMsg("trainSegmentModel", "Fail to build the classifier for segment model.");
            e.printStackTrace();
        }
        return true;
    }

    private boolean saveSegmentTrainingData() {
        if (mSegmentTrainingData.length() == 0) {
            Log.e("Error", "Empty training data.");
            return false;
        }
        if (mPath == null) {
            Log.e("Error", "File path not initialized.");
            return false;
        }

        File dataFile = new File(mPath, String.format("%s_%s", mPID, SEGMENT_TRAINING_NAME));
        if(!dataFile.exists()){
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            PrintWriter pw = new PrintWriter(new FileOutputStream(dataFile));
            pw.write("@relation iBlink\n");
            pw.write("@ATTRIBUTE \"Left\" NUMERIC\n");
            pw.write("@ATTRIBUTE \"Right\" NUMERIC\n");
            pw.write("@ATTRIBUTE Class {Open_Both_Eyes,Others}\n");
            pw.write("@DATA\n");
            pw.write(mSegmentTrainingData.toString());
            pw.flush();
            pw.close();

            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Call this function when the user exits the Calibration activity
     * @return true if content is saved.
     */
    public boolean saveParameters() {
//        float averageL = leftEyeOpenAvg.getValue();
//        float stdL = leftEyeOpenAvg.getStd();
//        float averageR = rightEyeOpenAvg.getValue();
//        float stdR = rightEyeOpenAvg.getStd();
//
//        sb.append(currentGesture + ","  + averageL + "," + stdL + "," + averageR + "," + stdR + "\n");
        return true;
    }

    public boolean hasExistingSegmentModel() {
        File modelFile = new File(mPath, String.format("%s_%s", mPID, SEGMENT_MODEL_NAME));
        return modelFile.exists();
    }

    public boolean loadSegmentModel() {
        if (mPath == null) {
            Log.e("Error", "Working path not initialized.");
            return false;
        }

        File modelFile = new File(mPath, String.format("%s_%s", mPID, SEGMENT_MODEL_NAME));
        if (!modelFile.exists()) {
            Log.e("Error", "Model file not exists.");
            return false;
        }

        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelFile));
            mSegmentClf = (LibSVM) ois.readObject();
            ois.close();
//            Log.i("TAG", mSegmentClf.getOptions().toString());
        } catch (IOException|ClassNotFoundException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public String classifySegment(float ll, float rr) {
        if (ll > -0.5f || rr > -0.5f) {
            ll = Math.max(0, ll);
            rr = Math.max(0, rr);
            try{
                ArrayList<Attribute> attributeList = new ArrayList<>();

                attributeList.add(new Attribute("Left"));
                attributeList.add(new Attribute("Right"));

                FastVector FVClass = new FastVector(2);
                FVClass.addElement("Open_Both_Eyes");
                FVClass.addElement("Others");

                attributeList.add(new Attribute("Class", FVClass));

                Instances dataset = new Instances("test_data", attributeList, 1);
                dataset.setClassIndex(attributeList.size() - 1);
                DenseInstance testexp = new DenseInstance(dataset.numAttributes());

                testexp.setValue(attributeList.get(0), ll);
                testexp.setValue(attributeList.get(1), rr);

                dataset.add(testexp);

//              Log.i("TAG","size of instances: " + dataset.size());

                for(Instance inst : dataset)
                {
                    double index = mSegmentClf.classifyInstance(inst);
                    Log.i("TAG","class index: "+index);
                    String testResult =  dataset.classAttribute().value((int)index);

                    if (testResult.equals("Open_Both_Eyes")) {
                        return "Open";
                    }
                    else if (testResult.equals("Others")) {
                        return "Close";
                    }
                }
            }
            catch(Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return "";
    }

    /**
     * Segment the gesture data to the target sample.
     * @param category
     * @param dataList
     * @return Boolean result of segment quality.
     */
    public boolean segmentGestureTrainingData(String category, ArrayList<SensorData> dataList) {
        String logTag = "segmentGestureTraining";
        logVerboseMsg(logTag, "Got new data points for gesture training:" + category);
        if (!isSegmentClfReady()) {
            // Load
            loadSegmentModel();
            if (!isSegmentClfReady()) {
                Log.e(logTag, "The segment classifier is not ready");
                return false;
            }
        }

        // Currently 'dB' is determined by double 'sB', not by direct training
        if (category.equals("dB")) {
            SensorData[] copiedData = new SensorData[dataList.size()];
            for (int i = 0; i < dataList.size(); ++i) {
                copiedData[i] = dataList.get(i);
            }
            mDoubleBlinkGestures.add(copiedData);
            mGestureCandidateName = category;
            return true;
        }
        boolean currIsShort = isShort(category);

        // Segment the data using the first SVM.
        RealTimeGesture currState = RealTimeGesture.INIT;
        int realSignalStartPos = -1;

        ArrayList<SensorData> candidate = new ArrayList<>();
        ArrayList<SensorData> candidateLong = new ArrayList<>();
        long currDuration = 0;

        clearBuffer();

        for (int i = 0; i < dataList.size(); ++i) {
            SensorData dt = dataList.get(i);
            if (dt.Left > -0.5f || dt.Right > -0.5f) {
                // react on valid data (at least one point is valid);
                float timeSec = (float) (dt.Time) / 1000.0f;
                updateBuffer(dt.Left, dt.Right, timeSec);

                boolean isOpen = setOpenState(recentLeft, recentRight);
//                Log.d("OOOOpen", "open:" + isOpen);
                switch (currState) {
                    case INIT:
                        // Just start
                        if (isOpen) {
                            // Normally it starts from open both
                            currState = RealTimeGesture.OPEN;
                            realSignalStartPos = -1;
                        } else {
                            // Or fails to start from open both
                            realSignalStartPos = i;
                        }
                        break;
                    case OPEN:
                        if (!isOpen) {
                            // Start a gesture, record.
                            currState = RealTimeGesture.CLOSE;
                            gestureStartTime = dt.Time;
                            mBlinkCount = 0;
                            candidate.clear();
                            // Add the leading "open" point
                            if (i > 0) {
                                candidate.add(dataList.get(i - 1));
                            }
                        }
                        break;
                    case CLOSE:
                        if (isOpen) {
                            // Gesture ended, save now or later.
                            gestureEndTime = dt.Time;
                            // Add one more ending "open" point
                            candidate.add(dt);

                            if (candidate.size() > 4) {
                                // Still need to wait
                                currState = RealTimeGesture.OPEN_WAITING;
                                mBlinkCount++;
                            } else {
                                // Ignore this, too short
                                currState = RealTimeGesture.OPEN;
                                logVerboseMsg(logTag, String.format("Ignore this %s, too short: size: %d, duration:%d",
                                        category, candidate.size(), candidate.get(candidate.size() - 1).Time - candidate.get(0).Time));
                            }
                        }
                        break;
                    case OPEN_WAITING:
                        if (dt.Time - gestureEndTime > CLASSIFY_WAIT_TIME) {
                            // Long enough. Classify and switch to normal OPEN state.
                            long durationTime = gestureEndTime - gestureStartTime;
                            float blinkSpeed = (float)(durationTime) / mBlinkCount;

                            if (blinkSpeed > MAX_BLINK_TIME) {
                                // Normal speed. Save.

                                if (durationTime > currDuration &&
                                        ( (currIsShort && durationTime < MAX_SHORT_GESTURE) ||
                                                (!currIsShort && durationTime < MAX_GESTURE_TIME) )) {
                                    // For short: longer but still short
                                    // For long: longer but still valid
                                    candidateLong = candidate;
                                    currDuration = durationTime;
                                    candidate = new ArrayList<>();
                                    logVerboseMsg(logTag, String.format("Replaced with candidate of %s, size:%d, duration:%d",
                                            category, candidateLong.size(), durationTime));
                                }
                                else {
                                    logVerboseMsg(logTag, String.format("Found without replacing %s, size:%d, duration:%d",
                                            category, candidate.size(), durationTime));
                                }
                            }
                            else {
                                logVerboseMsg(logTag, String.format("Too many blinks (%d) within %d - %d",
                                        mBlinkCount, gestureStartTime, gestureEndTime));
                            }
                            candidate.clear();
                            // else Blink too fast, might be error. Ignore this and switch back.

                            // Switch to OPEN anyway (note: could be actually close, but deal with it later)
                            currState = RealTimeGesture.OPEN;
                            if (!isOpen && i > 0) {
                                // Special case: if this is already close (if we lost some points between the
                                // previous OPEN and this CLOSE), then add an open point as a start
                                candidate.add(dataList.get(i - 1));
                            }
                        }
                        else {
                            // Still waiting
                            if (!isOpen) {
                                // Back to collecting, remove the last end point
                                candidate.remove(candidate.size() - 1);
                                currState = RealTimeGesture.CLOSE;
                                logVerboseMsg(logTag, "Back to close during waiting:" + dt.Time);
                            }
                        }
                        break;
                }

                // Regular case: store when the gesture is ON
                if (currState == RealTimeGesture.CLOSE || currState == RealTimeGesture.INIT) {
                    candidate.add(dt);
                }
            }
        }

        // Flush the waiting state
        if (currState == RealTimeGesture.OPEN_WAITING) {
            // Long enough. Classify and switch to normal OPEN state.
            long durationTime = gestureEndTime - gestureStartTime;
            float blinkSpeed = (float)(durationTime) / mBlinkCount;

            if (blinkSpeed > MAX_BLINK_TIME) {
                // Normal speed. Save.
                logVerboseMsg(logTag, "Flush the result successfully:");
                if (durationTime > currDuration &&
                        ( (currIsShort && durationTime < MAX_SHORT_GESTURE) ||
                                (!currIsShort && durationTime < MAX_GESTURE_TIME) )) {
                    // For short: longer but still short
                    // For long: longer but still valid
                    candidateLong = candidate;
                    currDuration = durationTime;
                    candidate = new ArrayList<>();
                    logVerboseMsg(logTag, String.format("Replaced with candidate of %s, size:%d, duration:%d",
                            category, candidateLong.size(), durationTime));
                }
                logVerboseMsg(logTag, String.format("Found without replacing %s, size:%d, duration:%d",
                        category, candidate.size(), durationTime));
            }
            else {
                logVerboseMsg(logTag, String.format("Too many blinks (%d) within %d - %d",
                        mBlinkCount, gestureStartTime, gestureEndTime));
            }
        }

        // Parse candidates
        if (currDuration == 0 || candidateLong.size() < 4) {
            // Not found a good segment: find out why, and redo
            if (currState == RealTimeGesture.CLOSE && candidate.size() > 0) {

                long timeSpent = candidate.get(candidate.size() - 1).Time - candidate.get(0).Time;
                logVerboseMsg(logTag, category + " gesture started but did not end, time:" + timeSpent);
            } else if (realSignalStartPos > 0 && candidate.size() > 0) {

                long timeSpent = candidate.get(candidate.size() - 1).Time - candidate.get(0).Time;
                logVerboseMsg(logTag, category + " gesture did not start properly, time:" + timeSpent
                        + " start pos:" + realSignalStartPos);
            } else {
                // Not gesture found.
                logVerboseMsg(logTag, category + " no gesture found.");
            }
            return false;
        }

        logVerboseMsg(logTag, String.format("Final version used %s, size:%d, duration:%d",
                category, candidateLong.size(), currDuration));
        if (currIsShort) {
            mGestureCandidateSamples = sampleData(candidateLong, SAMPLE_NUM_SHORT);
        } else {
            mGestureCandidateSamples = sampleData(candidateLong, SAMPLE_NUM_LONG);
        }
        mGestureCandidateDuration = (int)currDuration;
        mGestureCandidateName = category;

        return true;
    }

    /**
     * Confirmed the segment, and add to training data collection.
     * @return
     */
    public String addGestureTrainingData() {
        String logTag = "addGestureTraining";
        if (mGestureCandidateSamples == null) {
            logVerboseMsg(logTag, "No samples found for gesture (dB is an exception):" + mGestureCandidateName);
            return "";
        }

        if (isShort(mGestureCandidateName)) {
            mShortDuration.add(mGestureCandidateDuration);

            for (double dt : mGestureCandidateSamples) {
                mShortTrainingData.append(dt);
                mShortTrainingData.append(",");
            }
            mShortTrainingData.append(mGestureCandidateName + "\n");

            mShortTrainingDataCnt++;
            logVerboseMsg(logTag, String.format("Added a short gesture, duration:%d, total size:%d",
                    mGestureCandidateDuration, mShortTrainingDataCnt));
        } else {
            mLongDuration.add(mGestureCandidateDuration);

            for (double dt : mGestureCandidateSamples) {
                mLongTrainingData.append(dt);
                mLongTrainingData.append(",");
            }
            mLongTrainingData.append(mGestureCandidateName + "\n");

            mLongTrainingDataCnt++;
            logVerboseMsg(logTag, String.format("Added a long gesture, duration:%d, total size:%d",
                    mGestureCandidateDuration, mShortTrainingDataCnt));
        }
        mGestureCandidateSamples = null;

        return mGestureCandidateName;
    }

    private boolean trainDoubleBlinkParam() {
        String logTag = "trainDoubleBlinkParam";
        if (!isSegmentClfReady() || !isGestureClfReady()) {
            Log.e(logTag, "The segment classifier is not ready");
            logVerboseMsg(logTag, "Failed to calibrate dB because: The segment classifier is not ready");
            return false;
        }

        logVerboseMsg(logTag, "Found candidate trials to determine double blink threshold, size:" + mDoubleBlinkGestures.size());
        for (SensorData[] dataList : mDoubleBlinkGestures) {
            logVerboseMsg(logTag, "Start parsing [dB] data, size:" + dataList.length);

            // Segment the data using the first SVM.
            RealTimeGesture currState = RealTimeGesture.INIT;
            prevDataPoint = null;

            ArrayList<SensorData> candidate = new ArrayList<>();
            ArrayList<Integer> currDurations = new ArrayList<>();

            clearBuffer();

            for (SensorData dt : dataList) {
                if (dt.Left > -0.5 || dt.Right > -0.5) {
                    // valid point (at least one side is valid)
                    float timeSec = (float) (dt.Time) / 1000.0f;
                    updateBuffer(dt.Left, dt.Right, timeSec);

                    boolean isOpen = setOpenState(recentLeft, recentRight);

                    // State changes in the following case
                    switch (currState) {
                        case INIT:
                            if (isOpen) {
                                // Normally it starts from open both
                                currState = RealTimeGesture.OPEN;
                            }
                            break;
                        case OPEN:
                            if (!isOpen) {
                                // Start a gesture, record.
                                currState = RealTimeGesture.CLOSE;
                                gestureStartTime = dt.Time;
                                mBlinkCount = 0;
                                candidate.clear();
                                // Add the leading "open" point
                                if (prevDataPoint != null) {
                                    candidate.add(new SensorData(prevDataPoint));
                                }
                            }
                            break;
                        case CLOSE:
                            if (isOpen) {
                                // Gesture ended, classify later
                                gestureEndTime = dt.Time;
                                // Add one more ending "open" point
                                candidate.add(new SensorData(dt.Left, dt.Right, dt.Time));

                                if (candidate.size() > 4) {
                                    // If this is not a single blink, then need to wait.
                                    if (gestureEndTime - gestureStartTime <= MAX_SHORT_GESTURE) {
                                        // Short:
                                        double[] sampledData = sampleData(candidate, SAMPLE_NUM_SHORT);
                                        String gestureStr = classifyShort(sampledData);

                                        if (gestureStr.equals("sB")) {
                                            // it's a single blink, report and no need to wait
                                            if (isShort(prevGesture) || prevGesture.equals("UN")) {
                                                // Update the threshold.
                                                int dbDuration = (int)(dt.Time - prevGestureEndTime);
                                                currDurations.add(dbDuration);
                                                logVerboseMsg(logTag, String.format("Find dB candidate, prev:%s, curr:%s, gap:%d", prevGesture, gestureStr, dbDuration));
                                                prevGesture = "";
                                                prevGestureEndTime = 0;
                                            }
                                            else {
                                                // Update the prev
                                                prevGesture = gestureStr;
                                                prevGestureEndTime = dt.Time;
                                            }
                                            currState = RealTimeGesture.OPEN;
                                        } else {
                                            // Other gestures (short or long): still need to wait
                                            currState = RealTimeGesture.OPEN_WAITING;
                                            mBlinkCount++;
                                        }
                                    } else {
                                        // Other gestures (short or long): still need to wait
                                        currState = RealTimeGesture.OPEN_WAITING;
                                        mBlinkCount++;
                                    }
                                }
                                else {
                                    // Ignore this (could be a single blink, or not)
                                    String gestureStr = "UN";
                                    if (isShort(prevGesture) || prevGesture.equals("UN")) {
                                        // Update the threshold.
                                        int dbDuration = (int)(dt.Time - prevGestureEndTime);
                                        currDurations.add(dbDuration);
                                        logVerboseMsg(logTag, String.format("Find dB candidate, prev:%s, curr:%s, gap:%d", prevGesture, gestureStr, dbDuration));
                                        prevGesture = "";
                                        prevGestureEndTime = 0;
                                    }
                                    else {
                                        logVerboseMsg(logTag, String.format("Ignore this %s, too short: size: %d, duration:%d", gestureStr, candidate.size(),
                                                candidate.get(candidate.size() - 1).Time - candidate.get(0).Time));
                                        prevGesture = gestureStr;
                                        prevGestureEndTime = dt.Time;
                                    }
                                    currState = RealTimeGesture.OPEN;
                                }
                            }
                            break;
                        case OPEN_WAITING:
                            if (dt.Time - gestureEndTime > CLASSIFY_WAIT_TIME) {
                                // Long enough. Classify and switch to normal OPEN state.
                                float blinkSpeed = (float)(gestureEndTime - gestureStartTime) / mBlinkCount;
                                if (blinkSpeed > MAX_BLINK_TIME) {
                                    // Normal speed. Start classifying.
                                    if (gestureEndTime - gestureStartTime <= MAX_SHORT_GESTURE) {
                                        // Short:
                                        double[] sampledData = sampleData(candidate, SAMPLE_NUM_SHORT);
                                        String gestureStr = classifyShort(sampledData);

                                        // To train the time threshold for double blink, we make it easier
                                        // by just looking at the gap regardless of the short gesture errors.
                                        if (isShort(gestureStr) && (isShort(prevGesture) || prevGesture.equals("UN"))) {
                                            // Update the threshold.
                                            int dbDuration = (int)(dt.Time - prevGestureEndTime);
                                            currDurations.add(dbDuration);
                                            logVerboseMsg(logTag, String.format("Find dB candidate, prev:%s, curr:%s, gap:%d", prevGesture, gestureStr, dbDuration));
                                            prevGesture = "";
                                            prevGestureEndTime = 0;
                                        }
                                        else if (gestureStr.length() > 0){
                                            prevGesture = gestureStr;
                                            prevGestureEndTime = dt.Time;
                                        }
                                    } else {
                                        // Long:
                                        double[] sampledData = sampleData(candidate, SAMPLE_NUM_LONG);
                                        String gestureStr = classifyLong(sampledData);
                                        prevGesture = gestureStr;
                                        prevGestureEndTime = dt.Time;
                                    }
                                } else {
                                    // Blink too fast, might be error. Ignore this and switch back.
                                    logVerboseMsg(logTag, String.format("Too many blinks (%d) within %d - %d",
                                            mBlinkCount, gestureStartTime, gestureEndTime));
                                }
                                // Switch to OPEN anyway (note: could be actually close, but deal with it later)
                                currState = RealTimeGesture.OPEN;
                            }
                            else {
                                // Still waiting
                                if (!isOpen) {
                                    // Keep on counting, return to CLOSE, remove the last end point
                                    candidate.remove(candidate.size() - 1);
                                    currState = RealTimeGesture.CLOSE;
                                    logVerboseMsg(logTag, "Back to close during waiting:" + dt.Time);
                                }
                            }
                            break;
                    }

                    // Regular case: store when the gesture is ON
                    if (currState == RealTimeGesture.CLOSE) {
                        if (dt.Time - gestureStartTime >= MAX_GESTURE_TIME) {
                            // Too long, maybe the user is away.
                            candidate.clear();
                            currState = RealTimeGesture.INIT;
                        }

                        candidate.add(new SensorData(dt.Left, dt.Right, dt.Time));
                    }
                }
            }
            // Check the duration of this trial
            if (!currDurations.isEmpty()) {
                float avgSum = 0;
                for (int dr : currDurations) {
                    avgSum += (float)(dr);
                }
                avgSum /= currDurations.size();
                mDoubleThresholds.add(avgSum);
                logVerboseMsg(logTag, String.format("For this single trial, found %d [dB], avg:%f", currDurations.size(), avgSum));
            }
        }

        mDoubleBlinkGestures.clear();
        // Report the current average.
        float avgSum = 0;
        if (!mDoubleThresholds.isEmpty()) {
            double thresholds[] = new double[mDoubleThresholds.size()];
            for (int i = 0; i < mDoubleThresholds.size(); ++i) {
                avgSum += mDoubleThresholds.get(i);
                thresholds[i] = mDoubleThresholds.get(i);
            }

            avgSum /= mDoubleThresholds.size();
            mDoubleBlinkDataCnt = mDoubleThresholds.size();
            StandardDeviation std = new StandardDeviation();
            double stdVal = std.evaluate(thresholds);
            double newGap = avgSum + stdVal * 3;
            MAX_DOUBLE_BLINK_GAP = (long)(Math.min(1500, Math.max(500, newGap)));
            logVerboseMsg(logTag, String.format("Update double blink threshold: avg: %f, std:%f, final:%d, total size: %d",
                    avgSum, stdVal, MAX_DOUBLE_BLINK_GAP, mDoubleBlinkDataCnt));
        }
        else {
            logVerboseMsg(logTag, String.format("No results found yet for double blink threshold."));
        }

        return mDoubleBlinkDataCnt > 0;
    }

    private void saveModelThreshold() {
        // Create threshold model
        File thresholdFile = new File(mPath, String.format("%s_%s", mPID, THRESHOLD_MODEL));
        if(!thresholdFile.exists()){
            try {
                thresholdFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Save.
        String content = String.format("MAX_SHORT_GESTURE:%d\nMAX_DOUBLE_BLINK_GAP:%d", MAX_SHORT_GESTURE, MAX_DOUBLE_BLINK_GAP);
        try {
            PrintWriter pw = new PrintWriter(new FileOutputStream(thresholdFile));
            pw.write(content);
            pw.flush();
            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private boolean loadModelThreshold() {
        String logTag = "loadThreshold";
        // Create threshold model
        File thresholdFile = new File(mPath, String.format("%s_%s", mPID, THRESHOLD_MODEL));
        if(!thresholdFile.exists()){
            logVerboseMsg(logTag, "threshold not found.");
            return false;
        }

        // Load.
        try {
            BufferedReader br = new BufferedReader((new FileReader(thresholdFile)));
            String line = "";
            while ((line = br.readLine()) != null) {
                String[] lines = line.split(":");
                if (lines[0].contains("SHORT")) {
                    MAX_SHORT_GESTURE = Integer.parseInt(lines[1]);
                    logVerboseMsg(logTag, "Load short threshold:" + MAX_SHORT_GESTURE);
                } else if (lines[0].contains("DOUBLE")) {
                    MAX_DOUBLE_BLINK_GAP = Long.parseLong(lines[1]);
                    logVerboseMsg(logTag, "Load double threshold:" + MAX_DOUBLE_BLINK_GAP);
                }
            }
            br.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    public boolean trainGestureModel() {
        boolean flagS = trainShortSVM();
        boolean flagL = trainLongSVM();
        // Calculate proper value for the threshold between these.
        float avgShort = 0;
        for (int tt : mShortDuration) {
            avgShort += tt;
        }
        avgShort /= (float)(mShortDuration.size());

        float minLong = 15000;
        for (int tt : mLongDuration) {
            minLong = Math.min(tt, minLong);
        }
        MAX_SHORT_GESTURE = (int)(Math.max(900, (avgShort + minLong) / 2.0f));

        String thresholdInfo = String.format("update short-long threshold: avgShort:%f, minLong:%f, final:%d",
                avgShort, minLong, MAX_SHORT_GESTURE);

        logVerboseMsg("trainGestureModel", thresholdInfo);

        // Train double  blink gesture.
        boolean flagDB = trainDoubleBlinkParam();

        // save these two variables.
        saveModelThreshold();

        return flagS && flagL && flagDB;
    }

    public boolean hasTrainedGestureModel() {
        File modelFileS = new File(mPath, String.format("%s_%s", mPID, SHORT_MODEL_NAME));
        File modelFileL = new File(mPath, String.format("%s_%s", mPID, LONG_MODEL_NAME));
        return modelFileS.exists() && modelFileL.exists();
    }

    public boolean loadGestureModel() {
        if (mPath == null) {
            Log.e("Error", "Working path not initialized.");
            return false;
        }

        if (mShortClf == null) {
            File modelFile = new File(mPath, String.format("%s_%s", mPID, SHORT_MODEL_NAME));
            if (!modelFile.exists()) {
                Log.e("Error", "Model file not exists.");
                return false;
            }

            try {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelFile));
                mShortClf = (LibSVM) ois.readObject();
                ois.close();
//                Log.i("TAG", mShortClf.getOptions().toString());
            } catch (IOException|ClassNotFoundException e) {
                e.printStackTrace();
                return false;
            }
        }

        if (mLongClf == null) {
            File modelFile = new File(mPath, String.format("%s_%s", mPID, LONG_MODEL_NAME));
            if (!modelFile.exists()) {
                Log.e("Error", "Model file not exists.");
                return false;
            }

            try {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelFile));
                mLongClf = (LibSVM) ois.readObject();
                ois.close();
//                Log.i("TAG", mLongClf.getOptions().toString());
            } catch (IOException|ClassNotFoundException e) {
                e.printStackTrace();
                return false;
            }
        }

        loadModelThreshold();

        return true;
    }

    private boolean trainShortSVM() {
        String logTag = "trainShortSVM";
        if (!saveShortTrainingData()) {
            return false;
        }

        File dataFile = new File(mPath, String.format("%s_%s", mPID, SHORT_TRAINING_NAME));
        if(!dataFile.exists()){
            return false;
        }

        File modelFile = new File(mPath, String.format("%s_%s", mPID, SHORT_MODEL_NAME));
        if(!modelFile.exists()){
            try {
                modelFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            logVerboseMsg(logTag, "start training (ms):" + System.currentTimeMillis());

            BufferedReader br = new BufferedReader(new FileReader(dataFile));
            mShortInstances = new Instances(br);

            br.close();
            Log.i("TAG","[short]-number of attributes: " + mShortInstances.numAttributes());
            mShortInstances.setClassIndex(mShortInstances.numAttributes() - 1);

            mShortClf = new LibSVM();

            try {
                mShortClf.buildClassifier(mShortInstances);
                logVerboseMsg(logTag, "finish training (ms):" + System.currentTimeMillis());

                // Save
                ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(modelFile));
                oos.writeObject(mShortClf);
                oos.flush();
                oos.close();

                logVerboseMsg(logTag, "save training (ms):" + System.currentTimeMillis());
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    private boolean saveShortTrainingData() {
        if (mShortTrainingData.length() == 0) {
            Log.e("Error", "Empty training data.");
            return false;
        }
        if (mPath == null) {
            Log.e("Error", "File path not initialized.");
            return false;
        }

        File dataFile = new File(mPath, String.format("%s_%s", mPID, SHORT_TRAINING_NAME));
        if(!dataFile.exists()){
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            PrintWriter pw = new PrintWriter(new FileOutputStream(dataFile));
            pw.write("@relation iBlink\n");
            for (int i = 0; i < SAMPLE_NUM_SHORT * 2; ++i) {
                pw.write(String.format("@ATTRIBUTE \"attr-%d\" NUMERIC\n", i));
            }
            pw.write("@ATTRIBUTE Class {cLo,cRo,sB}\n");
            pw.write("@DATA\n");
            pw.write(mShortTrainingData.toString());
            pw.flush();
            pw.close();

            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean trainLongSVM() {
        String logTag = "trainLongSVM";
        if (!saveLongTrainingData()) {
            return false;
        }

        File dataFile = new File(mPath, String.format("%s_%s", mPID, LONG_TRAINING_NAME));
        if(!dataFile.exists()){
            return false;
        }

        File modelFile = new File(mPath, String.format("%s_%s", mPID, LONG_MODEL_NAME));
        if(!modelFile.exists()){
            try {
                modelFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            logVerboseMsg(logTag, "start training (ms):" + System.currentTimeMillis());
            BufferedReader br = new BufferedReader(new FileReader(dataFile));
            mLongInstances = new Instances(br);

            br.close();
            Log.i("TAG","[long]-number of attributes: " + mLongInstances.numAttributes());
            mLongInstances.setClassIndex(mLongInstances.numAttributes() - 1);

            mLongClf = new LibSVM();

            try {
                mLongClf.buildClassifier(mLongInstances);
                logVerboseMsg(logTag, "finish training (ms):" + System.currentTimeMillis());
                // Save
                ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(modelFile));
                oos.writeObject(mLongClf);
                oos.flush();
                oos.close();
                logVerboseMsg(logTag, "save training (ms):" + System.currentTimeMillis());
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    private boolean saveLongTrainingData() {
        if (mLongTrainingData.length() == 0) {
            Log.e("Error", "Empty training data.");
            return false;
        }
        if (mPath == null) {
            Log.e("Error", "File path not initialized.");
            return false;
        }

        File dataFile = new File(mPath, String.format("%s_%s", mPID, LONG_TRAINING_NAME));
        if(!dataFile.exists()){
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            PrintWriter pw = new PrintWriter(new FileOutputStream(dataFile));
            pw.write("@relation iBlink\n");
            for (int i = 0; i < SAMPLE_NUM_LONG * 2; ++i) {
                pw.write(String.format("@ATTRIBUTE \"attr-%d\" NUMERIC\n", i));
            }
            pw.write("@ATTRIBUTE Class {cL-o,cR-o,cB-o,cB-RLo,cB-LRo}\n");
            pw.write("@DATA\n");
            pw.write(mLongTrainingData.toString());
            pw.flush();
            pw.close();

            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    private String classifyShort(double[] dt) {
        assert dt.length == SAMPLE_NUM_SHORT * 2;
        try{
            ArrayList<Attribute> attributeList = new ArrayList<>();

            for (int i = 0; i < SAMPLE_NUM_SHORT * 2; ++i) {
                attributeList.add(new Attribute("attr-" + i));
            }

            FastVector FVClass = new FastVector(SHORT_GESTURES.length);
            for (String cat : SHORT_GESTURES) {
                FVClass.addElement(cat);
            }

            attributeList.add(new Attribute("Class", FVClass));

            Instances dataset = new Instances("test_short_data", attributeList, 1);
            dataset.setClassIndex(attributeList.size() - 1);
            DenseInstance testexp = new DenseInstance(dataset.numAttributes());

            for (int i = 0; i < dt.length; ++i) {
                testexp.setValue(attributeList.get(i), dt[i]);
            }

            dataset.add(testexp);

//            Log.i("TAG","size of instances: " + dataset.size());

            for(Instance inst : dataset)
            {
                double index = mShortClf.classifyInstance(inst);
                Log.i("TAG","class index: " + index);
                String testResult =  dataset.classAttribute().value((int)index);

                return testResult;
            }
        }
        catch(Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return "";
    }

    private String classifyLong(double[] dt) {
        assert dt.length == SAMPLE_NUM_LONG * 2;
        try{
            ArrayList<Attribute> attributeList = new ArrayList<>();

            for (int i = 0; i < SAMPLE_NUM_LONG * 2; ++i) {
                attributeList.add(new Attribute("attr-" + i));
            }

            FastVector FVClass = new FastVector(LONG_GESTURES.length);
            for (String cat : LONG_GESTURES) {
                FVClass.addElement(cat);
            }

            attributeList.add(new Attribute("Class", FVClass));

            Instances dataset = new Instances("test_long_data", attributeList, 1);
            dataset.setClassIndex(attributeList.size() - 1);
            DenseInstance testexp = new DenseInstance(dataset.numAttributes());

            for (int i = 0; i < dt.length; ++i) {
                testexp.setValue(attributeList.get(i), dt[i]);
            }

            dataset.add(testexp);

//            Log.i("TAG","size of instances: " + dataset.size());

            for(Instance inst : dataset)
            {
                double index = mLongClf.classifyInstance(inst);
                Log.i("TAG","class index: " + index);
                String testResult =  dataset.classAttribute().value((int)index);

                return testResult;
            }
        }
        catch(Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return "";
    }

    // Private helper
    private boolean isShort(String label) {
        for (String str : SHORT_GESTURES) {
            if (label.equals(str)) {
                return true;
            }
        }
        return false;
    }

    public void clearBuffer() {
        if (mMethod == BufferMethod.ROLLING_WINDOW) {
            mBufferLeft.clear();
            mBufferRight.clear();
        }
        else if (mMethod == BufferMethod.ONE_EURO) {
            try {
                oefLeft = new OneEuroFilter(30, 1.0, 1.0, 1.0);
                oefRight = new OneEuroFilter(30, 1.0, 1.0, 1.0);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        recentSegmentState = "Close";
        prevGesture = "";
        prevDataPoint = null;
        mBlinkCount = 0;
        currGestureState = RealTimeGesture.INIT;
    }

    /**
     * Update the buffer, but only use the valid point.
     * @param ll
     * @param rr
     * @param tt
     */
    private void updateBuffer (float ll, float rr, float tt) {
        switch (mMethod) {
            case RAW:
                if (ll > -0.5f && rr > -0.5f) {
                    recentLeft = ll;//Math.max(ll, 0.0f);
                    recentRight = rr;//Math.max(rr, 0.0f);
                }
                // Else: unchanged.
                break;
            case ROLLING_WINDOW:
                if (ll > -0.5f && rr > -0.5f) {
                    if (mBufferLeft.size() == mBufferSize) {
                        mBufferLeft.remove(0);
                        mBufferRight.remove(0);
                    }

                    // Add to buffer list.
                    mBufferLeft.add(ll);
                    mBufferRight.add(rr);
                    // Update
                    recentLeft = 0;
                    recentRight = 0;
                    for (int i = 0; i < mBufferLeft.size(); ++i) {
                        recentLeft += mBufferLeft.get(i);
                        recentRight += mBufferRight.get(i);
                    }
                    recentLeft /= mBufferLeft.size();
                    recentRight /= mBufferRight.size();
                }
                // Else: recent value unchanged.
                break;
            case ONE_EURO:
                if (ll > -0.5f && rr > -0.5f) {
                    try {
                        recentLeft = (float)oefLeft.filter((double)ll, (double)tt);
                        recentRight = (float)oefRight.filter((double)ll, (double)tt);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
        }
    }

    private double[] sampleData(ArrayList<SensorData> data, int sampleNum) {
        double[] ll = new double[data.size()];
        double[] rr = new double[data.size()];
        double[] tt = new double[data.size()];
        // Parse min, max, and -1
        double minL = 2;
        double minR = 2;

        for (int i = 0; i < data.size(); ++i) {
            ll[i] = data.get(i).Left;
            rr[i] = data.get(i).Right;
            tt[i] = data.get(i).Time;
            if (ll[i] > -0.5) {
                if (minL > ll[i]) {
                    minL = ll[i];
                }
            }
            if (rr[i] > -0.5) {
                if (minR > rr[i]) {
                    minR = rr[i];
                }
            }
        }

        for (int i = 0; i < data.size(); ++i) {
            if (ll[i] < -0.5) {
                ll[i] = minL;
            }

            if (rr[i] < -0.5) {
                rr[i] = minR;
            }
        }
        logVerboseMsg("sampleData", String.format("Update -1 with :%f/%f", minL, minR));

        UnivariateInterpolator interpolator = new org.apache.commons.math3.analysis.interpolation.LinearInterpolator();
        UnivariateFunction funcL = interpolator.interpolate(tt, ll);
        UnivariateFunction funcR = interpolator.interpolate(tt, rr);
        double timeSt = tt[0];
        double timeEd = tt[tt.length - 1];

        // Connected vector of left & right sampled value
        double[] result = new double[sampleNum * 2];
        // Sample more dots on the both sides: 4:2:4.
        // Update: switch it back.
        int startSample = (int)((double)(sampleNum) / 3.0);
        int middleSample = (int)((double)(sampleNum) / 3.0);
        int endSample = sampleNum - startSample - middleSample - 1;

        // [st, mid1)
        double timeDt = (timeEd - timeSt) / 3.0 / startSample;
        for (int i = 0; i < startSample; ++i) {
            double ti = timeSt + i * timeDt;
            result[i] = funcL.value(ti);
            result[i + sampleNum] = funcR.value(ti);
        }

        // [mid1, mid2)
        timeDt = (timeEd - timeSt) / 3.0 / middleSample;
        double newTimeSt = timeSt + (timeEd - timeSt) / 3.0;
        int indexOffset = startSample;
        for (int i = 0; i < middleSample; ++i) {
            double ti = newTimeSt + i * timeDt;
            result[indexOffset + i] = funcL.value(ti);
            result[indexOffset + i + sampleNum] = funcR.value(ti);
        }

        // [mid2, ed)
        timeDt = (timeEd - timeSt) / 3.0 / endSample;
        newTimeSt = timeSt + (timeEd - timeSt) / 3.0 * 2.0;
        indexOffset = startSample + middleSample;
        for (int i = 0; i < endSample; ++i) {
            double ti = newTimeSt + i * timeDt;
            result[indexOffset + i] = funcL.value(ti);
            result[indexOffset + i + sampleNum] = funcR.value(ti);
        }

        // [ed]
        result[sampleNum - 1] = funcL.value(timeEd);
        result[sampleNum * 2 - 1] = funcR.value(timeEd);

        return result;
    }

    public void logVerboseMsg(String tag, String msg) {
        // PID-function/tag-info
        String newMsg = String.format("%s; %s; %s\n", mPID, tag, msg);
        Log.i(tag, newMsg);
        mErrorLogger.append(newMsg);
    }

    public void saveLogToFile(String contextName) {
        if (mPath != null && mErrorLogger.length() > 0) {
            File errorFile = new File(mPath, String.format("%slog",
                    contextName.substring(0, contextName.length() - 3)));
            try {
                if (!errorFile.exists()) {
                    errorFile.createNewFile();
                }

                PrintWriter pw = new PrintWriter(new FileOutputStream(errorFile));
                pw.write(mErrorLogger.toString());
                pw.flush();
                pw.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
