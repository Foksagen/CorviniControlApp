package io.github.corvini.corvinicontrolapp;

import android.Manifest;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
//import android.net.Network;
import android.net.NetworkCapabilities;
//import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
//import android.net.wifi.WifiNetworkSpecifier;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.*;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import io.github.corvini.corvinicontrolapp.data.LightState;
import io.github.corvini.corvinicontrolapp.data.WheelMode;
import io.github.corvini.corvinicontrolapp.views.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;

import static io.github.corvini.corvinicontrolapp.References.*;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private final Runnable SIGNAL_ICON_UPDATE = new Runnable() {

        @Override
        public void run() {

            if (MainActivity.this.isConnectedToCar()) {
                MainActivity.this.connectionButton.updateWifiBars(WifiManager.calculateSignalLevel(MainActivity.this.wifiManager.getConnectionInfo().getRssi(), 5));
                MainActivity.this.handler.postDelayed(SIGNAL_ICON_UPDATE, 2000);

            } else {

                if (MainActivity.this.connection != null) MainActivity.this.connection.closeConnection(false);
            }
        }
    };

    private final View.OnTouchListener STEERING_WHEEL_LISTENER = (view, event) -> {
        final float x = event.getX() - (view.getWidth() / 2.F);
        final float y = event.getY() - (view.getHeight() / 2.F);

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN: {
                double angle = Math.atan2(y, x) + Math.PI / 2;
                this.lastTouchedAngle = angle < 0 ? angle + 2 * Math.PI : angle;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final double radius = view.getHeight() / Math.PI;
                final double distance = Math.sqrt(x * x + y * y);
                final double coeff = distance < radius ? distance * distance / (radius * radius) : 1.F;

                double currentAngle = Math.atan2(y, x) + Math.PI / 2;

                if (currentAngle < 0) currentAngle += 2 * Math.PI;

                double correctedAngle;

                if (currentAngle <= 2 * Math.PI && currentAngle > 3 * Math.PI / 2 && this.lastTouchedAngle >= 0 && this.lastTouchedAngle < Math.PI / 2) {
                    correctedAngle = Math.toDegrees(currentAngle - this.lastTouchedAngle) - 360;

                } else if (this.lastTouchedAngle <= 2 * Math.PI && this.lastTouchedAngle > 3 * Math.PI / 2 && currentAngle >= 0 && currentAngle < Math.PI / 2) {
                    correctedAngle = Math.toDegrees(currentAngle - this.lastTouchedAngle) + 360;

                } else {
                    correctedAngle = Math.toDegrees(currentAngle - this.lastTouchedAngle);
                }

                double updatedAngle = this.steerAngle + correctedAngle * coeff;

                if (updatedAngle > 127) {
                    updatedAngle = 127;

                } else if (updatedAngle < -127) {
                    updatedAngle = -127;

                }

                RotateAnimation rotation = new RotateAnimation(this.steerAngle, (float) updatedAngle, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5F);
                rotation.setDuration(0);
                rotation.setFillEnabled(true);
                rotation.setFillAfter(true);
                view.startAnimation(rotation);
                this.steerAngle = (float) updatedAngle;

                if (Math.abs((this.wheelMode == WheelMode.INTERSECTING ? -this.steerAngle : this.steerAngle) - this.lastSentAngle) >= 5) this.sendMessage(MSG_STEER,  this.lastSentAngle = (int) (this.wheelMode == WheelMode.INTERSECTING ? -this.steerAngle : this.steerAngle));

                this.lastTouchedAngle = currentAngle;
                break;
            }

            case MotionEvent.ACTION_UP: {

                if (Math.abs(this.steerAngle) <= 5) {
                    RotateAnimation rotation = new RotateAnimation(this.steerAngle, 0, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5F);
                    rotation.setDuration(0);
                    rotation.setFillEnabled(true);
                    rotation.setFillAfter(true);
                    view.startAnimation(rotation);
                    this.sendMessage(MSG_STEER, (int) (this.steerAngle = this.lastSentAngle = 0));
                    view.performClick();
                }

                break;
            }
        }

        return true;
    };

    private final View.OnTouchListener PEDAL_LISTENER = (view, event) -> {
        final float delta = event.getY() - (view.getHeight() / 2.F);

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {

                if (delta < 0 && this.throttle != 1) {
                    view.setRotationX(4F);
                    this.sendMessage(MSG_THROTTLE, this.throttle = 1);

                } else if (delta > 0 && this.throttle != -1) {
                    view.setRotationX(-4F);
                    this.sendMessage(MSG_THROTTLE, this.throttle = -1);

                } else if (delta == 0 && this.throttle != 0) {
                    view.setRotationX(0F);
                    this.sendMessage(MSG_THROTTLE, this.throttle = 0);
                }

                break;
            }

            case MotionEvent.ACTION_UP: {

                if (this.throttle != 0) {
                    view.setRotationX(0F);
                    this.sendMessage(MSG_THROTTLE, this.throttle = 0);
                }

                view.performClick();
                break;
            }
        }

        return true;
    };

    private final Handler handler = new Handler();

    //private CarDataModel carData;

    private GestureDetector rcGesture;
    private GestureDetector gyroGesture;

    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private SensorManager sensorManager;

    private ConnectionThread connection;

    private ConnectionButton connectionButton;

    private ImageView steeringWheel;
    private ImageView pedal;
    private CheckableImageView wheelModeButton;
    private RelativeLayout wheelModeMenu;
    private CheckableImageView chassisPositionButton;
    private RelativeLayout chassisPositionMenu;
    private SeekBar positionSlider;
    private CheckableImageView lightStateButton;
    private RelativeLayout lightStateMenu;

    private int batteryCharge;
    private float steerAngle;
    private int throttle;
    private WheelMode wheelMode = WheelMode.NORMAL;
    private LightState lightState = LightState.OFF;
    private int chassisPosition;
    private int speed;

    private boolean panic;
    private double lastTouchedAngle;
    private int lastSentAngle;

    //Orientation data
    private Timer updateTimer;
    private float[] gyro = new float[3];
    private float[] gyroMatrix = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    private float[] gyroOrientation = {0, 0, 0};
    private float[] magnet = new float[3];
    private float[] accel = new float[3];
    private float[] accMagOrientation = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] fusedOrientation = new float[3];
    private float sensorTimestamp;
    private boolean sensorInitState = true;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_main);
        this.hideSystemUI();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

        } else {
            Log.i("MainActivity", "Location permission is granted");
        }

        this.wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.connectivityManager = (ConnectivityManager) this.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        this.sensorManager = (SensorManager) this.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);

        this.connectionButton = this.findViewById(R.id.connection_button);

        this.steeringWheel = this.findViewById(R.id.steering_wheel);
        this.steeringWheel.setOnTouchListener(STEERING_WHEEL_LISTENER);

        this.pedal = this.findViewById(R.id.pedal);
        this.pedal.setOnTouchListener(PEDAL_LISTENER);

        SeekBar speedControl = this.findViewById(R.id.speed_control);

        speedControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                MainActivity.this.sendMessage(MSG_SPEED, MainActivity.this.speed = seekBar.getProgress());
            }
        });

        this.findViewById(R.id.gyro_mode).setOnClickListener(view -> {
            Checkable checkable = (Checkable) view;
            checkable.toggle();

            if (checkable.isChecked()) {
                this.steeringWheel.setOnTouchListener(null);
                this.sensorManager.registerListener(this, this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
                this.sensorManager.registerListener(this, this.sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
                this.sensorManager.registerListener(this, this.sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_FASTEST);

            } else {
                this.sensorManager.unregisterListener(this);
                this.updateTimer.cancel();
                this.resetSensorData();
                this.steeringWheel.setOnTouchListener(STEERING_WHEEL_LISTENER);
            }
        });

        this.wheelModeButton = this.findViewById(R.id.wheel_mode);

        this.wheelModeMenu = this.findViewById(R.id.wheel_mode_menu);

        CheckableImageView modeButton1 = this.findViewById(R.id.mode1);
        CheckableImageView modeButton2 = this.findViewById(R.id.mode2);
        CheckableImageView modeButton3 = this.findViewById(R.id.mode3);

        this.rcGesture = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                Log.i("MainActivity", "Single tap");

                if (MainActivity.this.wheelMode != WheelMode.RC) {
                    MainActivity.this.wheelModeButton.toggle();

                    if (MainActivity.this.wheelModeButton.isChecked()) {
                        MainActivity.this.wheelModeMenu.setVisibility(View.VISIBLE);

                        MainActivity.this.wheelModeMenu.animate().alpha(1).setDuration(500).setInterpolator(new DecelerateInterpolator()).setUpdateListener(animation -> {
                            float alpha = (float) animation.getAnimatedValue();
                            modeButton1.setRotation(-alpha * 360);
                            modeButton2.setRotation(-alpha * 360);
                            modeButton3.setRotation(-alpha * 360);
                        }).withStartAction(() -> {
                            MainActivity.this.wheelModeButton.setClickable(false);
                            modeButton1.setClickable(false);
                            modeButton2.setClickable(false);
                            modeButton3.setClickable(false);
                        }).withEndAction(() -> {
                            MainActivity.this.wheelModeButton.setClickable(true);
                            modeButton1.setClickable(true);
                            modeButton2.setClickable(true);
                            modeButton3.setClickable(true);
                        }).start();

                    } else {
                        MainActivity.this.wheelModeMenu.animate().alpha(0).setDuration(500).setInterpolator(new DecelerateInterpolator()).setUpdateListener(animation -> {
                            float alpha = (float) animation.getAnimatedValue();
                            modeButton1.setRotation(alpha * 360);
                            modeButton2.setRotation(alpha * 360);
                            modeButton3.setRotation(alpha * 360);
                        }).withStartAction(() -> {
                            MainActivity.this.wheelModeButton.setClickable(false);
                            modeButton1.setClickable(false);
                            modeButton2.setClickable(false);
                            modeButton3.setClickable(false);
                        }).withEndAction(() -> {
                            MainActivity.this.wheelModeMenu.setVisibility(View.GONE);
                            MainActivity.this.wheelModeButton.setClickable(true);
                            modeButton1.setClickable(true);
                            modeButton2.setClickable(true);
                            modeButton3.setClickable(true);
                        }).start();
                    }
                }

                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                Log.i("MainActivity", "Long press");

                if (MainActivity.this.wheelMode != WheelMode.RC) {
                    MainActivity.this.sendMessage(MSG_WHEEL_MODE, (MainActivity.this.wheelMode = WheelMode.RC).ordinal());

                    if (MainActivity.this.wheelModeButton.isChecked()) {
                        MainActivity.this.wheelModeButton.toggle();
                        MainActivity.this.wheelModeMenu.animate().alpha(0).setDuration(500).setInterpolator(new DecelerateInterpolator()).setUpdateListener(animation -> {
                            float alpha = (float) animation.getAnimatedValue();
                            modeButton1.setRotation(alpha * 360);
                            modeButton2.setRotation(alpha * 360);
                            modeButton3.setRotation(alpha * 360);
                        }).withStartAction(() -> {
                            MainActivity.this.wheelModeButton.setClickable(false);
                            modeButton1.setClickable(false);
                            modeButton2.setClickable(false);
                            modeButton3.setClickable(false);
                        }).withEndAction(() -> {
                            MainActivity.this.wheelModeMenu.setVisibility(View.GONE);
                            MainActivity.this.wheelModeButton.setClickable(true);
                            modeButton1.setClickable(true);
                            modeButton2.setClickable(true);
                            modeButton3.setClickable(true);
                        }).start();
                    }

                    MainActivity.this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, WheelMode.RC.getDrawableId()));
                    MainActivity.this.wheelModeButton.setBackgroundTintList(AppCompatResources.getColorStateList(MainActivity.this, R.color.colorPrimaryLight));
                    MainActivity.this.steeringWheel.setOnTouchListener(null);
                    MainActivity.this.steeringWheel.setAlpha(.5F);
                    MainActivity.this.pedal.setOnTouchListener(null);
                    MainActivity.this.pedal.setAlpha(.5F);

                } else {

                    if (modeButton1.isChecked()) {
                        MainActivity.this.wheelMode = WheelMode.NORMAL;

                    } else if (modeButton2.isChecked()) {
                        MainActivity.this.wheelMode = WheelMode.NORMAL;

                    } else if (modeButton3.isChecked()) {
                        MainActivity.this.wheelMode = WheelMode.NORMAL;
                    }

                    MainActivity.this.sendMessage(MSG_WHEEL_MODE, MainActivity.this.wheelMode.ordinal());
                    MainActivity.this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, MainActivity.this.wheelMode.getDrawableId()));
                    MainActivity.this.wheelModeButton.setBackgroundTintList(AppCompatResources.getColorStateList(MainActivity.this, R.color.colorAccent));
                    MainActivity.this.steeringWheel.setOnTouchListener(STEERING_WHEEL_LISTENER);
                    MainActivity.this.steeringWheel.setAlpha(1.F);
                    MainActivity.this.pedal.setOnTouchListener(PEDAL_LISTENER);
                    MainActivity.this.pedal.setAlpha(1.F);
                }
            }

        });

        this.wheelModeButton.setOnTouchListener((view, event) -> this.rcGesture.onTouchEvent(event));

        switch (this.wheelMode) {

            case NORMAL: {

                if(!modeButton1.isChecked()) modeButton1.toggle();

                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this, this.wheelMode.getDrawableId()));
                this.wheelModeButton.setBackgroundTintList(AppCompatResources.getColorStateList(this, R.color.colorAccent));
                break;
            }

            case INTERSECTING: {

                if(!modeButton2.isChecked()) modeButton2.toggle();

                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this, this.wheelMode.getDrawableId()));
                this.wheelModeButton.setBackgroundTintList(AppCompatResources.getColorStateList(this, R.color.colorAccent));
                break;
            }

            case PARALLEL: {

                if(!modeButton3.isChecked()) modeButton3.toggle();

                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this, this.wheelMode.getDrawableId()));
                this.wheelModeButton.setBackgroundTintList(AppCompatResources.getColorStateList(this, R.color.colorAccent));
                break;
            }

            case RC: {
                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this, this.wheelMode.getDrawableId()));
                this.wheelModeButton.setBackgroundTintList(AppCompatResources.getColorStateList(this, R.color.colorPrimaryLight));
                MainActivity.this.steeringWheel.setOnTouchListener(null);
                MainActivity.this.steeringWheel.setAlpha(.5F);
                MainActivity.this.pedal.setOnTouchListener(null);
                MainActivity.this.pedal.setAlpha(.5F);
            }
        }

        modeButton1.setOnClickListener(view -> {
            Checkable checkable = (Checkable) view;

            if (!checkable.isChecked()) {
                checkable.toggle();
                this.sendMessage(MSG_WHEEL_MODE, (this.wheelMode = WheelMode.NORMAL).ordinal());

                if (modeButton2.isChecked()) modeButton2.toggle();

                if (modeButton3.isChecked()) modeButton3.toggle();

                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_wheel_mode_0));
            }
        });

        modeButton2.setOnClickListener(view -> {
            Checkable checkable = (Checkable) view;

            if (!checkable.isChecked()) {
                checkable.toggle();
                this.sendMessage(MSG_WHEEL_MODE, (this.wheelMode = WheelMode.INTERSECTING).ordinal());

                if (modeButton1.isChecked()) modeButton1.toggle();

                if (modeButton3.isChecked()) modeButton3.toggle();

                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_wheel_mode_1));
            }
        });

        modeButton3.setOnClickListener(view -> {
            Checkable checkable = (Checkable) view;

            if (!checkable.isChecked()) {
                checkable.toggle();
                this.sendMessage(MSG_WHEEL_MODE, (this.wheelMode = WheelMode.PARALLEL).ordinal());

                if (modeButton1.isChecked()) modeButton1.toggle();

                if (modeButton2.isChecked()) modeButton2.toggle();

                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_wheel_mode_2));
            }
        });

        this.chassisPositionButton = this.findViewById(R.id.chassis_position);

        this.chassisPositionMenu = this.findViewById(R.id.chassis_position_menu);
        this.positionSlider = this.findViewById(R.id.position_slider);

        this.positionSlider.setProgress(4);

        this.gyroGesture = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                Log.i("MainActivity", "Single tap");

                if (!MainActivity.this.panic) {
                    MainActivity.this.chassisPositionButton.toggle();

                    if (MainActivity.this.chassisPositionButton.isChecked()) {
                        MainActivity.this.chassisPositionMenu.setVisibility(View.VISIBLE);

                        MainActivity.this.chassisPositionMenu.animate().alpha(1).setDuration(500).setInterpolator(new DecelerateInterpolator()).withStartAction(() -> {
                            MainActivity.this.chassisPositionButton.setClickable(false);
                            MainActivity.this.positionSlider.setClickable(false);
                        }).withEndAction(() -> {
                            MainActivity.this.chassisPositionButton.setClickable(true);
                            MainActivity.this.positionSlider.setClickable(true);
                        }).start();

                    } else {
                        MainActivity.this.chassisPositionMenu.animate().alpha(0).setDuration(500).setInterpolator(new DecelerateInterpolator()).withStartAction(() -> {
                            MainActivity.this.chassisPositionButton.setClickable(false);
                            MainActivity.this.positionSlider.setClickable(false);
                        }).withEndAction(() -> {
                            MainActivity.this.chassisPositionMenu.setVisibility(View.GONE);
                            MainActivity.this.chassisPositionButton.setClickable(true);
                            MainActivity.this.positionSlider.setClickable(true);
                        }).start();
                    }
                }

                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                Log.i("MainActivity", "Long press");

                if (MainActivity.this.panic) {
                    MainActivity.this.sendMessage(MSG_CHASSIS_POS, MainActivity.this.chassisPosition = MainActivity.this.positionSlider.getProgress());

                    if (MainActivity.this.chassisPosition > 4) {
                        MainActivity.this.chassisPositionButton.setImageDrawable(AppCompatResources.getDrawable(MainActivity.this, R.drawable.ic_chassis_position_1));

                    } else if (MainActivity.this.chassisPosition < 4) {
                        MainActivity.this.chassisPositionButton.setImageDrawable(AppCompatResources.getDrawable(MainActivity.this, R.drawable.ic_chassis_position_2));

                    } else {
                        MainActivity.this.chassisPositionButton.setImageDrawable(AppCompatResources.getDrawable(MainActivity.this, R.drawable.ic_chassis_position_0));
                    }

                    MainActivity.this.chassisPositionButton.setBackgroundTintList(AppCompatResources.getColorStateList(MainActivity.this, R.color.colorAccent));
                    MainActivity.this.panic = false;

                } else {
                    MainActivity.this.sendMessage(MSG_CHASSIS_POS, MainActivity.this.chassisPosition = -1);

                    if (MainActivity.this.chassisPositionButton.isChecked()) {
                        MainActivity.this.chassisPositionButton.toggle();
                        MainActivity.this.chassisPositionMenu.animate().alpha(0).setDuration(500).setInterpolator(new DecelerateInterpolator()).withStartAction(() -> {
                            MainActivity.this.chassisPositionButton.setClickable(false);
                            MainActivity.this.positionSlider.setClickable(false);
                        }).withEndAction(() -> {
                            MainActivity.this.chassisPositionMenu.setVisibility(View.GONE);
                            MainActivity.this.chassisPositionButton.setClickable(true);
                            MainActivity.this.positionSlider.setClickable(true);
                        }).start();
                    }

                    MainActivity.this.chassisPositionButton.setImageDrawable(AppCompatResources.getDrawable(MainActivity.this, R.drawable.ic_panic));
                    MainActivity.this.chassisPositionButton.setBackgroundTintList(AppCompatResources.getColorStateList(MainActivity.this, R.color.colorPrimaryLight));
                    MainActivity.this.panic = true;
                }
            }
        });

        this.chassisPositionButton.setOnTouchListener((view, event) -> this.gyroGesture.onTouchEvent(event));

        this.positionSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                MainActivity.this.chassisPosition = progress;

                if (fromUser) {
                    MainActivity.this.sendMessage(MSG_CHASSIS_POS, 8 - MainActivity.this.chassisPosition);
                }

                if (MainActivity.this.chassisPosition > 4) {
                    MainActivity.this.chassisPositionButton.setImageDrawable(AppCompatResources.getDrawable(MainActivity.this, R.drawable.ic_chassis_position_1));

                } else if (MainActivity.this.chassisPosition < 4) {
                    MainActivity.this.chassisPositionButton.setImageDrawable(AppCompatResources.getDrawable(MainActivity.this, R.drawable.ic_chassis_position_2));

                } else {
                    MainActivity.this.chassisPositionButton.setImageDrawable(AppCompatResources.getDrawable(MainActivity.this, R.drawable.ic_chassis_position_0));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }

        });

        this.lightStateButton = this.findViewById(R.id.light_state);

        this.lightStateMenu = this.findViewById(R.id.light_state_menu);

        CheckableImageView stateButton1 = this.findViewById(R.id.state1);
        CheckableImageView stateButton2 = this.findViewById(R.id.state2);
        CheckableImageView stateButton3 = this.findViewById(R.id.state3);

        switch (this.lightState) {

            case OFF: {

                if(!stateButton1.isChecked()) stateButton1.toggle();

                int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, this.getResources().getDisplayMetrics());
                int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, this.getResources().getDisplayMetrics());
                this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this, this.lightState.getDrawableId()));
                break;
            }

            case ON: {

                if(!stateButton2.isChecked()) stateButton2.toggle();

                int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, this.getResources().getDisplayMetrics());
                int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, this.getResources().getDisplayMetrics());
                this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this, this.lightState.getDrawableId()));
                break;
            }

            case AUTO: {

                if(!stateButton3.isChecked()) stateButton3.toggle();

                int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, this.getResources().getDisplayMetrics());
                int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, this.getResources().getDisplayMetrics());
                this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this, this.lightState.getDrawableId()));
                break;
            }
        }

        this.lightStateButton.setOnClickListener(view -> {
            CheckableImageView buttonView = (CheckableImageView) view;
            buttonView.toggle();

            if (buttonView.isChecked()) {
                this.lightStateMenu.setVisibility(View.VISIBLE);

                this.lightStateMenu.animate().alpha(1).setDuration(500).setInterpolator(new DecelerateInterpolator()).setUpdateListener(animation -> {
                    float alpha = (float) animation.getAnimatedValue();
                    stateButton1.setRotation(-alpha * 360);
                    stateButton2.setRotation(-alpha * 360);
                    stateButton3.setRotation(-alpha * 360);
                }).withStartAction(() -> {
                    buttonView.setClickable(false);
                    stateButton1.setClickable(false);
                    stateButton2.setClickable(false);
                    stateButton3.setClickable(false);
                }).withEndAction(() -> {
                    buttonView.setClickable(true);
                    stateButton1.setClickable(true);
                    stateButton2.setClickable(true);
                    stateButton3.setClickable(true);
                }).start();

            } else {
                this.lightStateMenu.animate().alpha(0).setDuration(500).setInterpolator(new DecelerateInterpolator()).setUpdateListener(animation -> {
                    float alpha = (float) animation.getAnimatedValue();
                    stateButton1.setRotation(alpha * 360);
                    stateButton2.setRotation(alpha * 360);
                    stateButton3.setRotation(alpha * 360);
                }).withStartAction(() -> {
                    buttonView.setClickable(false);
                    stateButton1.setClickable(false);
                    stateButton2.setClickable(false);
                    stateButton3.setClickable(false);
                }).withEndAction(() -> {
                    this.lightStateMenu.setVisibility(View.GONE);
                    buttonView.setClickable(true);
                    stateButton1.setClickable(true);
                    stateButton2.setClickable(true);
                    stateButton3.setClickable(true);
                }).start();
            }
        });

        stateButton1.setOnClickListener(view -> {
            Checkable checkable = (Checkable) view;

            if (!checkable.isChecked()) {
                checkable.toggle();
                this.sendMessage(MSG_LIGHT_STATE, (this.lightState = LightState.OFF).ordinal());

                if (stateButton2.isChecked()) stateButton2.toggle();

                if (stateButton3.isChecked()) stateButton3.toggle();

                int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, this.getResources().getDisplayMetrics());
                int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, this.getResources().getDisplayMetrics());
                this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_light_state_0));
            }
        });

        stateButton2.setOnClickListener(buttonView -> {
            Checkable checkable = (Checkable) buttonView;

            if (!checkable.isChecked()) {
                checkable.toggle();
                this.sendMessage(MSG_LIGHT_STATE, (this.lightState = LightState.ON).ordinal());

                if (stateButton1.isChecked()) stateButton1.toggle();

                if (stateButton3.isChecked()) stateButton3.toggle();

                int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, this.getResources().getDisplayMetrics());
                int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, this.getResources().getDisplayMetrics());
                this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_light_state_1));
            }
        });

        stateButton3.setOnClickListener(view -> {
            Checkable checkable = (Checkable) view;

            if (!checkable.isChecked()) {
                checkable.toggle();
                this.sendMessage(MSG_LIGHT_STATE, (this.lightState = LightState.AUTO).ordinal());

                if (stateButton1.isChecked()) stateButton1.toggle();

                if (stateButton2.isChecked()) stateButton2.toggle();

                int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, this.getResources().getDisplayMetrics());
                int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, this.getResources().getDisplayMetrics());
                this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_light_state_2));
            }
        });

        ((SeekBar) this.findViewById(R.id.speed_control)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                if (!fromUser) MainActivity.this.speed = progress;

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

                if (MainActivity.this.speed != seekBar.getProgress()) MainActivity.this.sendMessage(MSG_SPEED, MainActivity.this.speed = seekBar.getProgress());
            }

        });
    }

    @Override
    public void onResume() {
        super.onResume();

        if (this.isConnectedToCar() && this.connection == null) {
            SIGNAL_ICON_UPDATE.run();
            this.showSnackbar("Establishing connection...");
            this.setupConnectionThread();

        } else if (!this.isConnectedToCar() && this.connection != null) {
            this.connectionButton.setClickable(false);
            new RunnableTask(() -> {

                try {
                    this.connection.join();

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                this.connection = null;
                this.showSnackbar("Disconnected from network");
                this.updateConnectionButton(ConnectionState.NOT_CONNECTED);
                this.connectionButton.setClickable(true);
            }).execute();

        } else if (!this.isConnectedToCar()) {
            this.updateConnectionButton(ConnectionState.NOT_CONNECTED);
            this.showSnackbar("Not connected to car");
        }

        Log.i("MainActivity", this.wifiManager.getConnectionInfo().getSSID());

        if (((Checkable) this.findViewById(R.id.gyro_mode)).isChecked()) {
            this.sensorManager.registerListener(this, this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
            this.sensorManager.registerListener(this, this.sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
            this.sensorManager.registerListener(this, this.sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        this.findViewById(R.id.root).setClickable(false);

        if (((Checkable) this.findViewById(R.id.gyro_mode)).isChecked()) {
            this.sensorManager.unregisterListener(this);
            this.updateTimer.cancel();
            this.resetSensorData();
        }

        if (this.connection != null) {
            this.connection.closeConnection(true);
            new RunnableTask(() -> {

                try {
                    this.connection.join();

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                MainActivity.this.connection = null;
            }).execute();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            this.hideSystemUI();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        switch(event.sensor.getType()) {

            case Sensor.TYPE_ACCELEROMETER: {
                // copy new accelerometer data into accel array
                // then calculate new orientation
                System.arraycopy(event.values, 0, this.accel, 0, 3);

                if(SensorManager.getRotationMatrix(this.rotationMatrix, null, this.accel, this.magnet)) {
                    SensorManager.getOrientation(this.rotationMatrix, this.accMagOrientation);
                }

                break;
            }

            case Sensor.TYPE_GYROSCOPE: {
                // process gyro data
                if (this.accMagOrientation != null) {
                    // initialisation of the gyroscope based rotation matrix
                    if(this.sensorInitState) {
                        float[] initMatrix = this.getRotationMatrixFromOrientation(this.accMagOrientation);
                        this.gyroMatrix = this.matrixMultiplication(this.gyroMatrix, initMatrix);
                    }

                    // copy the new gyro values into the gyro array
                    // convert the raw gyro data into a rotation vector
                    float[] deltaVector = new float[4];

                    if(this.sensorTimestamp != 0) {
                        final float dT = (event.timestamp - this.sensorTimestamp) * 1e-9F;
                        System.arraycopy(event.values, 0, this.gyro, 0, 3);
                        this.getRotationVectorFromGyro(this.gyro, deltaVector, dT / 2.0f);
                    }

                    // measurement done, save current time for next interval
                    this.sensorTimestamp = event.timestamp;

                    // convert rotation vector into rotation matrix
                    float[] deltaMatrix = new float[9];
                    SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);

                    // apply the new rotation interval on the gyroscope based rotation matrix
                    this.gyroMatrix = this.matrixMultiplication(this.gyroMatrix, deltaMatrix);

                    // get the gyroscope based orientation from the rotation matrix
                    SensorManager.getOrientation(this.gyroMatrix, this.gyroOrientation);

                    if (this.sensorInitState) {
                        this.updateTimer = new Timer();
                        this.updateTimer.scheduleAtFixedRate(new TimerTask() {

                            @Override
                            public void run() {
                                MainActivity.this.fusedOrientation[0] = .98F * MainActivity.this.gyroOrientation[0] + .02F * MainActivity.this.accMagOrientation[0];
                                MainActivity.this.fusedOrientation[1] = .98F * MainActivity.this.gyroOrientation[1] + .02F * MainActivity.this.accMagOrientation[1];
                                MainActivity.this.fusedOrientation[2] = .98F * MainActivity.this.gyroOrientation[2] + .02F * MainActivity.this.accMagOrientation[2];

                                // overwrite gyro matrix and orientation with fused orientation
                                // to compensate gyro drift
                                MainActivity.this.gyroMatrix = getRotationMatrixFromOrientation(MainActivity.this.fusedOrientation);
                                System.arraycopy(MainActivity.this.fusedOrientation, 0, MainActivity.this.gyroOrientation, 0, 3);

                                MainActivity.this.handler.post(() -> {

                                    if (MainActivity.this.fusedOrientation[1] >= -Math.PI / 2 || MainActivity.this.fusedOrientation[1] <= Math.PI / 2) {
                                        float updatedAngle = (float) (-254 / Math.PI * MainActivity.this.fusedOrientation[1]);
                                        RotateAnimation rotation = new RotateAnimation(MainActivity.this.steerAngle, MainActivity.this.steerAngle = updatedAngle, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5F);
                                        rotation.setDuration(0);
                                        rotation.setInterpolator(new AccelerateDecelerateInterpolator());
                                        rotation.setFillEnabled(true);
                                        rotation.setFillAfter(true);
                                        MainActivity.this.steeringWheel.startAnimation(rotation);

                                        if (Math.abs((MainActivity.this.wheelMode == WheelMode.INTERSECTING ? -MainActivity.this.steerAngle : MainActivity.this.steerAngle) - MainActivity.this.lastSentAngle) >= 5) MainActivity.this.sendMessage(MSG_STEER,  MainActivity.this.lastSentAngle = (int) (MainActivity.this.wheelMode == WheelMode.INTERSECTING ? -MainActivity.this.steerAngle : MainActivity.this.steerAngle));
                                    }
                                });
                            }
                        }, 0, 30);
                        this.sensorInitState = false;
                    }
                }

                break;
            }

            case Sensor.TYPE_MAGNETIC_FIELD: {
                // copy new magnetometer data into magnet array
                System.arraycopy(event.values, 0, this.magnet, 0, 3);
                break;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void handleConnectionButtonClick(View button) {

        if (!this.isConnectedToCar()) {
            this.updateConnectionButton(ConnectionState.CONNECTING);
            Intent panelIntent = new Intent(android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ? "android.settings.panel.action.WIFI" : Settings.ACTION_WIFI_SETTINGS);
            this.startActivityForResult(panelIntent, -1);

        } else {

            if (this.connection == null) {
                this.setupConnectionThread();

            } else {
                this.updateConnectionButton(ConnectionState.CONNECTING);
                Intent panelIntent = new Intent(android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ? "android.settings.panel.action.WIFI" : Settings.ACTION_WIFI_SETTINGS);
                this.startActivityForResult(panelIntent, -1);
            }
        }
    }

    public void sendMessage(int id, int value) {

        if (this.connection != null && !this.connection.isDisconnected()) {
            this.connection.executeMessageTask(id, value);
        }
    }

    private void setupConnectionThread() {
        this.connection = new ConnectionThread();
        this.connection.start();
    }

    private void updateUI(byte[] data) {

        if (data.length == MAX_PACKET_SIZE) {

            //Battery charge
            ((GaugeProgress) this.findViewById(R.id.batteryProgress)).animate(data[0] / 100.F);
            ValueAnimator animator = new ValueAnimator();
            animator.setObjectValues(this.batteryCharge, (int) data[0]);
            animator.addUpdateListener(animation -> ((TextView) this.findViewById(R.id.batteryDisplay)).setText(animation.getAnimatedValue() + "﹪"));
            animator.setEvaluator((TypeEvaluator<Integer>) (fraction, startValue, endValue) -> Math.round(startValue + (endValue - startValue) * fraction));
            animator.setDuration(this.batteryCharge > data[0] ? 750 : 500);
            animator.setInterpolator(this.batteryCharge > data[0] ? new DecelerateInterpolator() : new AccelerateDecelerateInterpolator());
            animator.start();
            this.batteryCharge = data[0];

            //Steer angle
            RotateAnimation rotation = new RotateAnimation(0, this.steerAngle = data[1], Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5F);
            rotation.setDuration(500);
            rotation.setInterpolator(new AccelerateDecelerateInterpolator());
            rotation.setFillEnabled(true);
            rotation.setFillAfter(true);
            this.steeringWheel.startAnimation(rotation);

            //Wheel mode
            CheckableImageView modeButton1 = this.findViewById(R.id.mode1);
            CheckableImageView modeButton2 = this.findViewById(R.id.mode2);
            CheckableImageView modeButton3 = this.findViewById(R.id.mode3);

            switch (WheelMode.values()[data[3]]) {

                case NORMAL: {

                    if(!modeButton1.isChecked()) modeButton1.toggle();

                    this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this, this.wheelMode.getDrawableId()));

                    if (this.wheelMode == WheelMode.RC) this.wheelModeButton.setBackgroundTintList(AppCompatResources.getColorStateList(this, R.color.colorAccent));

                    break;
                }

                case INTERSECTING: {

                    if(!modeButton2.isChecked()) modeButton2.toggle();

                    this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this, this.wheelMode.getDrawableId()));

                    if (this.wheelMode == WheelMode.RC) this.wheelModeButton.setBackgroundTintList(AppCompatResources.getColorStateList(this, R.color.colorAccent));

                    break;
                }

                case PARALLEL: {

                    if(!modeButton3.isChecked()) modeButton3.toggle();

                    this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this, this.wheelMode.getDrawableId()));

                    if (this.wheelMode == WheelMode.RC) this.wheelModeButton.setBackgroundTintList(AppCompatResources.getColorStateList(this, R.color.colorAccent));

                    break;
                }

                case RC: {
                    this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this, this.wheelMode.getDrawableId()));
                    this.wheelModeButton.setBackgroundTintList(AppCompatResources.getColorStateList(this, R.color.colorPrimaryLight));
                }
            }

            this.wheelMode = WheelMode.values()[data[3]];

            //Light state
            CheckableImageView stateButton1 = this.findViewById(R.id.state1);
            CheckableImageView stateButton2 = this.findViewById(R.id.state2);
            CheckableImageView stateButton3 = this.findViewById(R.id.state3);

            switch (this.lightState = LightState.values()[data[4]]) {

                case OFF: {

                    if(!stateButton1.isChecked()) stateButton1.toggle();

                    int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, this.getResources().getDisplayMetrics());
                    int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, this.getResources().getDisplayMetrics());
                    this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                    this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this, this.lightState.getDrawableId()));
                    break;
                }

                case ON: {

                    if(!stateButton2.isChecked()) stateButton2.toggle();

                    int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, this.getResources().getDisplayMetrics());
                    int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, this.getResources().getDisplayMetrics());
                    this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                    this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this, this.lightState.getDrawableId()));
                    break;
                }

                case AUTO: {

                    if(!stateButton3.isChecked()) stateButton3.toggle();

                    int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, this.getResources().getDisplayMetrics());
                    int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, this.getResources().getDisplayMetrics());
                    this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                    this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this, this.lightState.getDrawableId()));
                    break;
                }
            }

            //Chassis position
            this.positionSlider.setProgress(data[5]);

            //Speed
            ((SeekBar) this.findViewById(R.id.speed_control)).setProgress(data[6]);

        } else {

            /*switch(data[0]) {

                case MSG_COMM_STATE:

            }*/
        }
    }

    private void hideSystemUI() {
        this.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private void showSnackbar(String message) {
        Snackbar snackbar = Snackbar.make(this.connectionButton, message, BaseTransientBottomBar.LENGTH_SHORT);
        View view = snackbar.getView();
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        params.setMargins(12, 12, 12, 14);
        view.setLayoutParams(params);
        view.setBackground(this.getDrawable(R.drawable.background_snackbar));
        view.setElevation(6);
        snackbar.show();
    }

    private void resetSensorData() {
        this.gyro = new float[3];
        this.gyroMatrix = new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1};
        this.gyroOrientation = new float[]{0, 0, 0};
        this.magnet = new float[3];
        this.accel = new float[3];
        this.accMagOrientation = new float[3];
        this.rotationMatrix = new float[9];
        this.fusedOrientation = new float[3];
        this.sensorInitState = true;
        this.sensorTimestamp = 0;
    }

    private void getRotationVectorFromGyro(float[] gyroValues, float[] deltaRotationVector, float timeFactor) {
        float[] normValues = new float[3];

        // Calculate the angular speed of the sample
        float omegaMagnitude = (float) Math.sqrt(gyroValues[0] * gyroValues[0] + gyroValues[1] * gyroValues[1] + gyroValues[2] * gyroValues[2]);

        // Normalize the rotation vector if it's big enough to get the axis
        if(omegaMagnitude > .000000001F) {
            normValues[0] = gyroValues[0] / omegaMagnitude;
            normValues[1] = gyroValues[1] / omegaMagnitude;
            normValues[2] = gyroValues[2] / omegaMagnitude;
        }

        // Integrate around this axis with the angular speed by the timestep
        // in order to get a delta rotation from this sample over the timestep
        // We will convert this axis-angle representation of the delta rotation
        // into a quaternion before turning it into the rotation matrix.
        float thetaOverTwo = omegaMagnitude * timeFactor;
        float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
        float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);
        deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
        deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
        deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
        deltaRotationVector[3] = cosThetaOverTwo;
    }

    private float[] getRotationMatrixFromOrientation(float[] orientation) {
        float[] xM = new float[9];
        float[] yM = new float[9];
        float[] zM = new float[9];

        float sinX = (float) Math.sin(orientation[1]);
        float cosX = (float) Math.cos(orientation[1]);
        float sinY = (float) Math.sin(orientation[2]);
        float cosY = (float) Math.cos(orientation[2]);
        float sinZ = (float) Math.sin(orientation[0]);
        float cosZ = (float) Math.cos(orientation[0]);

        // rotation about x-axis (pitch)
        xM[0] = 1.0f; xM[1] = 0.0f; xM[2] = 0.0f;
        xM[3] = 0.0f; xM[4] = cosX; xM[5] = sinX;
        xM[6] = 0.0f; xM[7] = -sinX; xM[8] = cosX;

        // rotation about y-axis (roll)
        yM[0] = cosY; yM[1] = 0.0f; yM[2] = sinY;
        yM[3] = 0.0f; yM[4] = 1.0f; yM[5] = 0.0f;
        yM[6] = -sinY; yM[7] = 0.0f; yM[8] = cosY;

        // rotation about z-axis (azimuth)
        zM[0] = cosZ; zM[1] = sinZ; zM[2] = 0.0f;
        zM[3] = -sinZ; zM[4] = cosZ; zM[5] = 0.0f;
        zM[6] = 0.0f; zM[7] = 0.0f; zM[8] = 1.0f;

        // rotation order is y, x, z (roll, pitch, azimuth)
        float[] resultMatrix = this.matrixMultiplication(xM, yM);
        resultMatrix = this.matrixMultiplication(zM, resultMatrix);
        return resultMatrix;
    }

    private float[] matrixMultiplication(float[] matrix1, float[] matrix2) {
        float[] result = new float[9];

        result[0] = matrix1[0] * matrix2[0] + matrix1[1] * matrix2[3] + matrix1[2] * matrix2[6];
        result[1] = matrix1[0] * matrix2[1] + matrix1[1] * matrix2[4] + matrix1[2] * matrix2[7];
        result[2] = matrix1[0] * matrix2[2] + matrix1[1] * matrix2[5] + matrix1[2] * matrix2[8];

        result[3] = matrix1[3] * matrix2[0] + matrix1[4] * matrix2[3] + matrix1[5] * matrix2[6];
        result[4] = matrix1[3] * matrix2[1] + matrix1[4] * matrix2[4] + matrix1[5] * matrix2[7];
        result[5] = matrix1[3] * matrix2[2] + matrix1[4] * matrix2[5] + matrix1[5] * matrix2[8];

        result[6] = matrix1[6] * matrix2[0] + matrix1[7] * matrix2[3] + matrix1[8] * matrix2[6];
        result[7] = matrix1[6] * matrix2[1] + matrix1[7] * matrix2[4] + matrix1[8] * matrix2[7];
        result[8] = matrix1[6] * matrix2[2] + matrix1[7] * matrix2[5] + matrix1[8] * matrix2[8];

        return result;
    }

    private void updateConnectionButton(ConnectionState state) {

        switch (state) {

            case NOT_CONNECTED: {
                this.resetConnectionButton();
                break;
            }

            case CONNECTING: {
                MainActivity.this.showSnackbar("Connecting to the message network...");
                this.connectionButton.setBackground(ContextCompat.getDrawable(MainActivity.this, android.R.color.transparent));
                this.connectionButton.setConnecting(true);
                break;
            }

            case RECONNECTING: {
                MainActivity.this.showSnackbar("Reconnecting from the message network...");
                this.connectionButton.setBackground(ContextCompat.getDrawable(MainActivity.this, android.R.color.transparent));
                this.connectionButton.setConnecting(true);
                break;
            }

            case CONNECTED: {
                MainActivity.this.showSnackbar("Connected to the message network.");
                this.connectionButton.setConnecting(false);
                this.connectionButton.setBackground(ContextCompat.getDrawable(MainActivity.this, R.drawable.background_connection_2));
                break;
            }

            case DISCONNECTED: {
                MainActivity.this.showSnackbar("Disconnected from the message network.");
                this.connectionButton.setConnecting(false);
                this.connectionButton.setBackground(ContextCompat.getDrawable(MainActivity.this, R.drawable.background_connection_1));
                break;
            }
        }
    }

    private void resetConnectionButton() {
        this.connectionButton.setConnecting(false);
        this.connectionButton.updateWifiBars(-1);
        this.connectionButton.setBackground(ContextCompat.getDrawable(MainActivity.this, R.drawable.background_connection_1));
    }

    private boolean isConnectedToCar() {

        if (this.connectivityManager.getActiveNetwork() != null) {
            return this.connectivityManager.getNetworkCapabilities(this.connectivityManager.getActiveNetwork()).hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && this.wifiManager.getConnectionInfo().getSSID().equals(String.format("\"%s\"", References.CAR_AP_SSID));

        } else {
            return false;
        }
    }

    private final class ConnectionThread extends Thread {

        private DatagramSocket socket;
        private boolean disconnected;
        private Thread readerThread;

        public ConnectionThread() {
            super("ConnectionThread");
        }

        @Override
        public void run() {

            while (MainActivity.this.isConnectedToCar() && !(this.socket != null && this.socket.isClosed())) {

                if (this.socket == null || this.isDisconnected()) {

                    if (this.isDisconnected()) {

                        try {
                            this.readerThread.join();

                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }

                        Log.i("ConnectingThread", "Reconnecting...");
                    }

                    try {
                        this.initialize();

                    } catch (Exception e) {
                        Log.i("ConnectionThread", "There has been an error while reconnecting ConnectionThread");
                        e.printStackTrace();
                        MainActivity.this.updateConnectionButton(ConnectionState.DISCONNECTED);
                        MainActivity.this.connectionButton.setClickable(false);
                        break;
                    }
                }
            }

            try {
                if (this.readerThread != null) this.readerThread.join();

            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }

            Log.i("ConnectionThread", "Stopping connection");
            this.readerThread = null;
            MainActivity.this.showSnackbar("Connection stopped");
            MainActivity.this.connectionButton.setClickable(true);
        }

        public void executeMessageTask(int id, int value, RunnableTask.OnTaskCompleted listener) {
            new RunnableTask(() -> {

                try {
                    DatagramPacket packet = new DatagramPacket(new byte[]{(byte) id, (byte) value}, 2);
                    packet.setSocketAddress(new InetSocketAddress(InetAddress.getByName(ESP_ADDRESS), ESP_PORT));
                    this.socket.send(packet);

                } catch (IOException e) {
                    Log.i("ConnectionThread", "Error sending packet");
                    e.printStackTrace();
                }

            }, listener).execute();
        }

        public void executeMessageTask(int id, int value) {
            this.executeMessageTask(id, value, null);
        }

        public void closeConnection(boolean appClosure) {
            Log.i("ConnectionThread", "Closing connection");
            this.executeMessageTask(MSG_COMM_STATE, 1, () -> {
                this.socket.close();

                if (!appClosure) {
                    MainActivity.this.showSnackbar("Connection was closed");
                    this.disconnected = true;
                    MainActivity.this.handler.post(() -> MainActivity.this.updateConnectionButton(ConnectionState.DISCONNECTED));

                } else {
                    this.disconnected = true;
                }
            });
        }

        public boolean isDisconnected() {
            return disconnected;
        }

        private void initialize() throws Exception {

            Log.i("ConnectionThread", "Initializing...");

            if (this.isDisconnected()) {
                MainActivity.this.handler.post(() -> MainActivity.this.updateConnectionButton(ConnectionState.RECONNECTING));

            } else {
                MainActivity.this.handler.post(() -> MainActivity.this.updateConnectionButton(ConnectionState.CONNECTING));
            }

            this.disconnected = false;

            this.socket = new DatagramSocket(null);
            MainActivity.this.connectivityManager.getActiveNetwork().bindSocket(this.socket);

            this.executeMessageTask(MSG_COMM_STATE, 0);
            this.readerThread = new Thread(() -> {

                while (MainActivity.this.isConnectedToCar() && !this.isDisconnected() && !this.socket.isClosed()) {

                    try {
                        DatagramPacket packet = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
                        this.socket.receive(packet);

                        Log.i("ReaderThread", "Received packet of size " + packet.getLength() + ".");

                        String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                        Log.i("MainActivity", message);

                        if (message.equals("Hello")) {
                            Log.i("ReaderThread", "Received new connection update packet.");
                            MainActivity.this.handler.post(() -> {
                                MainActivity.this.updateConnectionButton(ConnectionState.CONNECTED);

                                //Setup UI
                                MainActivity.this.positionSlider.setProgress(4);

                                CheckableImageView stateButton1 = MainActivity.this.findViewById(R.id.state1);
                                CheckableImageView stateButton2 = MainActivity.this.findViewById(R.id.state2);
                                CheckableImageView stateButton3 = MainActivity.this.findViewById(R.id.state3);

                                if(stateButton1.isChecked()) stateButton1.toggle();

                                if(stateButton2.isChecked()) stateButton2.toggle();

                                if(!stateButton3.isChecked()) stateButton3.toggle();

                                int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, MainActivity.this.getResources().getDisplayMetrics());
                                int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, MainActivity.this.getResources().getDisplayMetrics());
                                MainActivity.this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                                MainActivity.this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, (MainActivity.this.lightState = LightState.AUTO).getDrawableId()));
                            });
                            Log.i("ConnectionThread", "Connection established");

                        } else if (packet.getLength() == 2) {
                            Log.i("ReaderThread", "Received continuous update packet.");
                            //MainActivity.this.handler.post(() -> MainActivity.this.updateUI(packet.getData()));

                        } else {
                            Log.i("ReaderThread", "Received unknown packet.");
                        }

                    } catch (IOException e) {
                        Log.i("ReaderThread", "Reached IOException");
                        e.printStackTrace();

                        if (MainActivity.this.isConnectedToCar() && !this.socket.isClosed()) {
                            Log.i("ConnectionThread", "Disconnecting socket...");
                            MainActivity.this.showSnackbar("Disconnected from the message network");
                            this.socket.disconnect();
                            MainActivity.this.handler.post(() -> MainActivity.this.updateConnectionButton(ConnectionState.DISCONNECTED));
                            this.disconnected = true;
                        }

                        break;
                    }
                }

                Log.i("Reader thread", "Stopping reader connection");
            }, "ReaderThread");
            this.readerThread.start();
        }
    }

    private static class RunnableTask extends AsyncTask<Void, Void, Void> {
        private final Runnable runnable;
        private OnTaskCompleted listener;

        public RunnableTask(Runnable runnable, @Nullable OnTaskCompleted listener) {
            this.runnable = runnable;
            this.listener = listener;
        }

        public RunnableTask(Runnable runnable) {
            this(runnable, null);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            this.runnable.run();
            return null;
        }

        @Override
        protected void onPostExecute(Void arg) {
            if (this.listener != null) this.listener.onTaskCompleted();
        }

        private interface OnTaskCompleted {
            void onTaskCompleted();
        }
    }

    private enum ConnectionState {
        NOT_CONNECTED,
        CONNECTING,
        RECONNECTING,
        CONNECTED,
        DISCONNECTED
    }
}
