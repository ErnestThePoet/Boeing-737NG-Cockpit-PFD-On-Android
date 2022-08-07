package com.ecui.efis;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;

    private final LocationHelper locationHelper=new LocationHelper(this);

    private final float[] rotationMatrix = new float[9];

    private final Matrix HSIMatrix = new Matrix();
    private final Matrix ASIMatrix = new Matrix();

    private final float[] magnetometerReading = new float[3];

    // ui-related sensor data
    private final float[] accelerometerReading = new float[3];

    private final float[] orientationAngles = new float[3];

    private static final int SW_SIZE=16;
    private final SWFilter accFilter=new SWFilter(SW_SIZE);
    private final SWFilter[] orientationFilters={
            new SWFilter(SW_SIZE),
            new SWFilter(SW_SIZE),
            new SWFilter(SW_SIZE)
    };

    private double altMeters;
    private float speedMps;
    ///

    private ImageView ivHSI, ivBankPointer, ivASI, ivAsTrendUp, ivAsTrendDown;
    private TextView tvCurrentAirspeed, tvHeading, tvCurrentAlt, tvInfo;

    private boolean isTvInfoShow=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        ivHSI = findViewById(R.id.ivHSI);
        ivBankPointer = findViewById(R.id.ivBankPointer);
        ivASI = findViewById(R.id.ivASI);
        ivAsTrendUp = findViewById(R.id.ivAsTrendUp);
        ivAsTrendDown = findViewById(R.id.ivAsTrendDown);

        tvCurrentAirspeed = findViewById(R.id.tvCurrentAirspeed);
        tvHeading = findViewById(R.id.tvHeading);
        tvCurrentAlt = findViewById(R.id.tvCurrentAlt);
        tvInfo = findViewById(R.id.tvInfo);

        ivHSI.setOnClickListener(v -> runOnUiThread(() -> isTvInfoShow=!isTvInfoShow));

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            String[] permissions={Manifest.permission.ACCESS_FINE_LOCATION};
            ActivityCompat.requestPermissions(this,permissions,123);
        }

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    updatePFDDisplay();

                    if(isTvInfoShow){
                        tvInfo.setText(String.format(
                                "SPD:%.3fMPS(%.3fKPH)\nACC:%.3fm/s/s(%.3fkm/h/s)\nALT:%"
                                        + ".3fm\nAOA:%.1f%c\nBANK ANGLE:%.1f%c",
                                speedMps,
                                3.6*speedMps,
                                accelerometerReading[1],
                                3.6*accelerometerReading[1],
                                altMeters,
                                Math.toDegrees(Math.abs(orientationAngles[1])),
                                orientationAngles[1]<=0?'U':'D',
                                Math.toDegrees(Math.abs(orientationAngles[2])),
                                orientationAngles[2]<=0?'L':'R'));
                    }
                    else{
                        tvInfo.setText("");
                    }
                });
            }
        }, 0, 33);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(
                    this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL,
                    SensorManager.SENSOR_DELAY_UI);
        }

        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(this, magneticField,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(
                    event.values,
                    0,
                    accelerometerReading,
                    0,
                    accelerometerReading.length);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading,
                    0, magnetometerReading.length);
        }

        updateOrientationAngles();
        filterValues();
    }

    private void updateOrientationAngles() {
        SensorManager.getRotationMatrix(
                rotationMatrix, null, accelerometerReading, magnetometerReading);

        SensorManager.getOrientation(rotationMatrix, orientationAngles);
    }

    // speed and alt are not filtered.
    private void filterValues(){
        this.accelerometerReading[1]=this.accFilter.filter(this.accelerometerReading[1]);

        for(int i=0;i<this.orientationAngles.length;i++){
            this.orientationAngles[i]=this.orientationFilters[i].filter(
                    this.orientationAngles[i]
            );
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void updatePFDDisplay() {
        updateHSIDisplay();
        updateASTrendVector();
        updateHeadingDisplay();
        updateAltitudeAndSpeedDisplay();
    }

    private void updateHSIDisplay() {
        float bankAngleDegree = (float) Math.toDegrees(orientationAngles[1]);
        float unitsPerBankAngleDegree = 12.0f;
        // It seems that the translation and rotation for matrices
        // have different reference points yet identical units.
        HSIMatrix.setTranslate(-935, -935 - bankAngleDegree * unitsPerBankAngleDegree);
        HSIMatrix.preRotate(
                (float) Math.toDegrees(-orientationAngles[2]),
                1199,
                1199 + bankAngleDegree * unitsPerBankAngleDegree);
        ivHSI.setImageMatrix(HSIMatrix);

        ivBankPointer.setRotation((float) Math.toDegrees(-orientationAngles[2]));
    }

    private void updateASTrendVector() {
        float accelerationKt10s = accelerometerReading[1] * 1.944012f * 10;
        if (accelerationKt10s > 0) {
            if (accelerationKt10s > 60) {
                accelerationKt10s = 60;
            }
            ivAsTrendDown.setVisibility(View.INVISIBLE);
            Matrix matrix = new Matrix();
            matrix.setTranslate(0, 358 - 6.9f * accelerationKt10s);
            ivAsTrendUp.setImageMatrix(matrix);
            ivAsTrendUp.setVisibility(View.VISIBLE);
        } else if (accelerationKt10s < 0) {
            if (accelerationKt10s < -60) {
                accelerationKt10s = -60;
            }
            ivAsTrendUp.setVisibility(View.INVISIBLE);
            Matrix matrix = new Matrix();
            matrix.setTranslate(0, -525 - 6.9f * accelerationKt10s);
            ivAsTrendDown.setImageMatrix(matrix);
            ivAsTrendDown.setVisibility(View.VISIBLE);
        } else {
            ivAsTrendUp.setVisibility(View.INVISIBLE);
            ivAsTrendDown.setVisibility(View.INVISIBLE);
        }
    }

    private void updateHeadingDisplay() {
        DecimalFormat decimalFormat = new DecimalFormat("0");
        decimalFormat.setRoundingMode(RoundingMode.HALF_UP);
        float currentHeadingDegrees = (float) Math.toDegrees(orientationAngles[0]);
        if (currentHeadingDegrees < 0) {
            currentHeadingDegrees = 360 + currentHeadingDegrees;
            if(currentHeadingDegrees==360){
                currentHeadingDegrees=0;
            }
        }
        tvHeading.setText(decimalFormat.format(currentHeadingDegrees));
    }

    private void updateAltitudeAndSpeedDisplay(){
        altMeters=locationHelper.getLocation().getAltitude();
        speedMps=locationHelper.getLocation().getSpeed();

        tvCurrentAlt.setText(String.valueOf(Math.round(3.28084*altMeters)));
        float speedKts= (float) (1.944012*speedMps);
        tvCurrentAirspeed.setText(String.valueOf(Math.round(speedKts)));

        ASIMatrix.setTranslate(0,
                -1934 + 6.9f * speedKts);
        ivASI.setImageMatrix(ASIMatrix);
    }
}