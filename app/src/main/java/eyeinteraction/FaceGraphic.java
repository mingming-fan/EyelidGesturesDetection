/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eyeinteraction;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import eyeinteraction.ui.camera.GraphicOverlay;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.Landmark;

import static android.content.ContentValues.TAG;

/**
 * Graphic instance for rendering face position, orientation, and landmarks within an associated
 * graphic overlay view.
 */
class FaceGraphic extends GraphicOverlay.Graphic {
    private static final float FACE_POSITION_RADIUS = 10.0f;
    private static final float ID_TEXT_SIZE = 40.0f;
    private static final float ID_Y_OFFSET = -90.0f;
    private static final float ID_X_OFFSET = -100.0f;
    private static final float BOX_STROKE_WIDTH = 5.0f;

//    public static final int CAMERA_WIDTH = 600;
//    public static final int CAMERA_HEIGHT = 800;
    public static final int CAMERA_WIDTH = 480;
    public static final int CAMERA_HEIGHT = 640;

    private static final int COLOR_CHOICES[] = {
        Color.BLUE,
        Color.GREEN,
        Color.CYAN,
        Color.MAGENTA,
        Color.RED,
        Color.WHITE,
        Color.YELLOW
    };
    private static int mCurrentColorIndex = 0;

    private Paint mFacePositionPaint;
    private Paint mIdPaint;
    private Paint mBoxPaint;

    private volatile Face mFace;
    private int mFaceId;
    private int numEye = 0;

    private float leftEyeOpenProb = 0;
    private float rightEyeOpenProb = 0;
    static final float alpha = 0.25f;
    static final boolean lowPassFilter = false; //true;


    FaceGraphic(GraphicOverlay overlay) {
        super(overlay);

        //mCurrentColorIndex = (mCurrentColorIndex + 1) % COLOR_CHOICES.length;
        //final int selectedColor = COLOR_CHOICES[mCurrentColorIndex];

        final int selectedColor = Color.GREEN;
        mFacePositionPaint = new Paint();
        mFacePositionPaint.setColor(selectedColor);

        mIdPaint = new Paint();
        mIdPaint.setColor(selectedColor);
        mIdPaint.setTextSize(ID_TEXT_SIZE);

        mBoxPaint = new Paint();
        mBoxPaint.setColor(selectedColor);
        mBoxPaint.setStyle(Paint.Style.STROKE);
        mBoxPaint.setStrokeWidth(BOX_STROKE_WIDTH);
    }

    void setId(int id) {
        mFaceId = id;
    }


    /**
     * Updates the face instance from the detection of the most recent frame.  Invalidates the
     * relevant portions of the overlay to trigger a redraw.
     */
    void updateFace(Face face) {
        if (face == null) {
            return;
        }

        mFace = face;
        numEye = 0;
        /*
        for (Landmark landmark : face.getLandmarks()) {
            switch (landmark.getType()) {
                case Landmark.LEFT_EYE:
                    if(face.getIsLeftEyeOpenProbability() > 0.5) {
                        numEye ++;
                    }
                    break;
                case Landmark.RIGHT_EYE:
                    if(face.getIsRightEyeOpenProbability() > 0.5) {
                        numEye ++;
                    }
                    break;
                default:
                    break;
            }
        }
        */

        float currentLeftEyeOpen = face.getIsLeftEyeOpenProbability();
        float currentRightEyeOpen = face.getIsRightEyeOpenProbability();
        if(lowPassFilter){
            leftEyeOpenProb = leftEyeOpenProb + alpha * (currentLeftEyeOpen - leftEyeOpenProb);
            rightEyeOpenProb = rightEyeOpenProb + alpha * (currentRightEyeOpen - rightEyeOpenProb);

            if(leftEyeOpenProb >= 0.5) {
                numEye ++;
            }
            if(rightEyeOpenProb >= 0.5) {
                numEye ++;
            }
        }
        else{
            if(currentLeftEyeOpen >= 0.5) {
                numEye ++;
            }
            if(currentRightEyeOpen >= 0.5) {
                numEye ++;
            }
        }

        postInvalidate();
    }

    /**
     * Draws the face annotations for position on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        Face face = mFace;

        // Draws a circle at the position of the detected face, with the face's track id below.
        float x = translateX(face.getPosition().x + face.getWidth() / 2);
        float y = translateY(face.getPosition().y + face.getHeight() / 2);
        //canvas.drawCircle(x, y, FACE_POSITION_RADIUS, mFacePositionPaint);
        canvas.drawText("num of open eyes: " + numEye, x + ID_X_OFFSET, y + ID_Y_OFFSET, mIdPaint);

        //canvas.drawText("happiness: " + String.format("%.2f", face.getIsSmilingProbability()), x - ID_X_OFFSET, y - ID_Y_OFFSET, mIdPaint);
        //canvas.drawText("right eye: " + String.format("%.2f", face.getIsRightEyeOpenProbability()), x + ID_X_OFFSET * 2, y + ID_Y_OFFSET * 2, mIdPaint);
        //canvas.drawText("left eye: " + String.format("%.2f", face.getIsLeftEyeOpenProbability()), x - ID_X_OFFSET*2, y - ID_Y_OFFSET*2, mIdPaint);

        // Draws a bounding box around the face.
        float xOffset = scaleX(face.getWidth() / 2.0f);
        float yOffset = scaleY(face.getHeight() / 2.0f);
        float left = x - xOffset;
        float top = y - yOffset;
        float right = x + xOffset;
        float bottom = y + yOffset;
        canvas.drawRect(left, top, right, bottom, mBoxPaint);
    }

    public int getNumEye(){
        return numEye;
    }

}
