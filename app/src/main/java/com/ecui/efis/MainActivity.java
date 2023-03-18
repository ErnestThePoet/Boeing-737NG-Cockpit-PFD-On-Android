package com.ecui.efis;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;

    private Sensor gyroSensor;

    private final LocationHelper locationHelper = new LocationHelper(this);

    private final Matrix HSIMatrix = new Matrix();
    private final Matrix ASIMatrix = new Matrix();

    private AttSource attSource = AttSource.GYRO;
    private boolean isGpsSpdOn = false;
    private boolean isIrsSpdOn = false;

    private boolean isAccFirstUpdate = true;
    private boolean isGyroFirstUpdate = true;

    private long previousTimeGyroNs = System.nanoTime();
    private long previousTimeAccNs = System.nanoTime();

    private float previousGpsSpeedMps;

    private final float[] magnetometerReading = new float[3];

    // ui-related sensor data
    private final float[] accelerometerReading = new float[3];

    private final float[] orientationAngles = new float[3];

    private static final int SW_SIZE = 16;
    private final SWFilter accFilter = new SWFilter(SW_SIZE);
    private final SWFilter[] orientationFilters = {
            new SWFilter(SW_SIZE),
            new SWFilter(SW_SIZE),
            new SWFilter(SW_SIZE)
    };

    private double altMeters;
    private float speedMps;
    ///

    private ImageView ivHSI, ivBankPointer, ivASI, ivAsTrendUp, ivAsTrendDown;
    private TextView tvCurrentAirspeed, tvHeading, tvCurrentAlt, tvInfo;

    private boolean isTvInfoShow = false;

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

        SwitchCompat swAttSource = findViewById(R.id.swAttSource);
        SwitchCompat swGpsSpd = findViewById(R.id.swGpsSpd);
        SwitchCompat swIrsSpd = findViewById(R.id.swIrsSpd);
        Button btnShowInfo = findViewById(R.id.btnShowInfo);

        ivHSI.setOnClickListener(e -> resetAttitude());

        swAttSource.setOnCheckedChangeListener(
                (e, c) -> {
                    if (c) {
                        attSource = AttSource.ACC;
                        orientationFilters[1].clear();
                        orientationFilters[2].clear();
                        sensorManager.unregisterListener(this, gyroSensor);
                        isGyroFirstUpdate = true;
                    } else {
                        attSource = AttSource.GYRO;
                        registerGyroListener();
                    }
                });

        swGpsSpd.setOnCheckedChangeListener(
                (e, c) -> {
                    isGpsSpdOn = c;
                });

        swIrsSpd.setOnCheckedChangeListener(
                (e, c) -> {
                    isIrsSpdOn = c;
                    if (!c) {
                        isAccFirstUpdate = true;
                    }
                });

        btnShowInfo.setOnClickListener(
                e -> runOnUiThread(() -> isTvInfoShow = !isTvInfoShow));

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
            ActivityCompat.requestPermissions(this, permissions, 123);
        }

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    updatePFDDisplay();

                    if (isTvInfoShow) {
                        tvInfo.setText(String.format(
                                "SPD:%.3fMPS(%.3fKPH)\nACC:%.3fm/s/s(%.3fkm/h/s)\nALT:%"
                                        + ".3fm\nAOA:%.1f%c\nBANK ANGLE:%.1f%c",
                                speedMps,
                                3.6 * speedMps,
                                accelerometerReading[1],
                                3.6 * accelerometerReading[1],
                                altMeters,
                                Math.toDegrees(Math.abs(orientationAngles[1])),
                                orientationAngles[1] <= 0 ? 'U' : 'D',
                                Math.toDegrees(Math.abs(orientationAngles[2])),
                                orientationAngles[2] <= 0 ? 'L' : 'R'));
                    } else {
                        tvInfo.setText("");
                    }
                });
            }
        }, 0, 33);
    }

    private void registerGyroListener() {
        sensorManager.registerListener(
                this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(
                    this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL,
                    SensorManager.SENSOR_DELAY_GAME);
        }

        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyroSensor != null) {
            registerGyroListener();
        }

        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(
                    this, magneticField, SensorManager.SENSOR_DELAY_NORMAL,
                    SensorManager.SENSOR_DELAY_GAME);
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

            if (isIrsSpdOn) {
                long currentTimeAccNs = System.nanoTime();

                if (isAccFirstUpdate) {
                    isAccFirstUpdate = false;
                } else {
                    double dtNs = currentTimeAccNs - previousTimeAccNs;
                    speedMps += (dtNs * accelerometerReading[1]) / 1e9;
                }

                previousTimeAccNs = currentTimeAccNs;
            }
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE
                && attSource == AttSource.GYRO) {
            long currentTimeGyroNs = System.nanoTime();

            if (isGyroFirstUpdate) {
                isGyroFirstUpdate = false;
            } else {
                double dtNs = currentTimeGyroNs - previousTimeGyroNs;

                orientationAngles[1] -= (dtNs * event.values[0]) / 1e9;
                orientationAngles[2] += (dtNs * event.values[1]) / 1e9;
            }

            previousTimeGyroNs = currentTimeGyroNs;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading,
                    0, magnetometerReading.length);
        }

        updateOrientationAngles();
        filterValues();
    }

    private void resetAttitude() {
        if (attSource == AttSource.GYRO) {
            orientationAngles[1] = 0.0f;
            orientationAngles[2] = 0.0f;
        }
    }

    private void updateOrientationAngles() {
        float[] rotationMatrix = new float[9];

        SensorManager.getRotationMatrix(
                rotationMatrix, null, accelerometerReading, magnetometerReading);

        float[] calculatedOrientationAngles = new float[3];
        SensorManager.getOrientation(rotationMatrix, calculatedOrientationAngles);

        if (attSource == AttSource.GYRO) {
            orientationAngles[0] = calculatedOrientationAngles[0];
        } else {
            System.arraycopy(calculatedOrientationAngles, 0,
                    orientationAngles, 0, calculatedOrientationAngles.length);
        }
    }

    // speed and alt are not filtered.
    private void filterValues() {
        this.accelerometerReading[1] = this.accFilter.filter(this.accelerometerReading[1]);

        if (attSource == AttSource.ACC) {
            for (int i = 0; i < this.orientationAngles.length; i++) {
                this.orientationAngles[i] = this.orientationFilters[i].filter(
                        this.orientationAngles[i]
                );
            }
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
            if (currentHeadingDegrees == 360) {
                currentHeadingDegrees = 0;
            }
        }
        tvHeading.setText(decimalFormat.format(currentHeadingDegrees));
    }

    private void updateAltitudeAndSpeedDisplay() {
        altMeters = locationHelper.getLocation().getAltitude();
        
        if(isGpsSpdOn){
            float currentGpsSpeedMps = locationHelper.getLocation().getSpeed();

            if (Math.abs(currentGpsSpeedMps - previousGpsSpeedMps) > 1e-3) {
                speedMps = currentGpsSpeedMps;
                previousGpsSpeedMps = currentGpsSpeedMps;
            }
        }

        tvCurrentAlt.setText(String.valueOf(Math.round(3.28084 * altMeters)));
        float speedKts = (float) (1.944012 * speedMps);
        tvCurrentAirspeed.setText(String.valueOf(Math.round(speedKts)));

        ASIMatrix.setTranslate(0,
                -1934 + 6.9f * speedKts);
        ivASI.setImageMatrix(ASIMatrix);
    }
}