package eyeinteraction;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void sendMessage(View view){
        switch(view.getId()){
            case R.id.bt_eyetrackingdemo:
                Intent intent1 = new Intent(this, FaceTrackerActivity.class);
                startActivity(intent1);
                break;
            case R.id.bt_calibration_static:
                Intent intent9 = new Intent(this, CalibrationStaticSetupActivity.class);
                startActivity(intent9);
                break;
            case R.id.bt_calibration_dynamic:
                Intent intent10 = new Intent(this, CalibrationDynamicSetupActivity.class);
                startActivity(intent10);
                break;
            case R.id.bt_evaluationOne:
                Intent intent12 = new Intent(this, EvaluationOneSetupActivity.class);
                startActivity(intent12);
                break;
            case R.id.bt_evaluationTwo:
                Intent intent13 = new Intent(this, EvaluationTwoSetupActivity.class);
                startActivity(intent13);
                break;
//            case R.id.bt_evaluationthree:
//                Intent intent14 = new Intent(this, EvaluationThreeSetupActivity.class);
//                startActivity(intent14);
//                break;
            default:
                break;
        }
    }
}
