package io.github.corvini.corvinicontrolapp.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.*;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import io.github.corvini.corvinicontrolapp.MainActivity;
import io.github.corvini.corvinicontrolapp.R;
import io.github.corvini.corvinicontrolapp.data.CarDataModel;
import io.github.corvini.corvinicontrolapp.data.LightState;
import io.github.corvini.corvinicontrolapp.data.WheelMode;
import io.github.corvini.corvinicontrolapp.views.CheckableImageView;
import io.github.corvini.corvinicontrolapp.views.GaugeProgress;

import java.util.Timer;
import java.util.TimerTask;

import static io.github.corvini.corvinicontrolapp.References.*;

public class ControlFragment extends Fragment implements SensorEventListener {

    private final Handler handler = new Handler(Looper.getMainLooper());

    private CarDataModel carData;

    private SensorManager sensorManager;

    private ImageView steeringWheel;
    private CheckableImageView wheelModeButton;
    private RelativeLayout wheelModeMenu;
    private CheckableImageView lightStateButton;
    private RelativeLayout lightStateMenu;

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

                double updatedAngle = this.carData.getSteerAngle().getValue() + correctedAngle * coeff;

                if (updatedAngle > 127) {
                    updatedAngle = 127;

                } else if (updatedAngle < -127) {
                    updatedAngle = -127;

                }

                RotateAnimation rotation = new RotateAnimation(this.carData.getSteerAngle().getValue(), (float) updatedAngle, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5F);
                rotation.setDuration(0);
                rotation.setFillEnabled(true);
                rotation.setFillAfter(true);
                view.startAnimation(rotation);
                this.carData.setSteerAngle((float) updatedAngle);
                this.lastTouchedAngle = currentAngle;
                break;
            }

            case MotionEvent.ACTION_UP: {

                if (Math.abs(this.carData.getSteerAngle().getValue()) < 5) {
                    RotateAnimation rotation = new RotateAnimation(this.carData.getSteerAngle().getValue(), 0, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5F);
                    rotation.setDuration(0);
                    rotation.setFillEnabled(true);
                    rotation.setFillAfter(true);
                    view.startAnimation(rotation);
                    this.carData.setSteerAngle((float) (this.lastSentAngle = 0));
                    view.performClick();
                }

                break;
            }
        }

        return true;
    };

    public ControlFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.carData = new ViewModelProvider(this.getActivity()).get(CarDataModel.class);
        this.carData.getSteerAngle().observe(this, value -> {
            Log.i("CarDataModel", "Steer angle changed");

            if (Math.abs(value - this.lastSentAngle) > 5 || (Math.abs(value) == 127 && Math.abs(this.lastSentAngle) != 127) || value == 0 && this.lastSentAngle == 0) {
                ((MainActivity) this.getActivity()).sendMessage(MSG_STEER, this.lastSentAngle = value.intValue());
            }
        });
        this.carData.getThrottle().observe(this, value -> {
            Log.i("CarDataModel", "Throttle changed");
            ((MainActivity) this.getActivity()).sendMessage(MSG_THROTTLE, value);
        });
        this.carData.getWheelMode().observe(this, value -> {
            Log.i("CarDataModel", "Wheel mode changed");
            ((MainActivity) this.getActivity()).sendMessage(MSG_WHEEL_MODE, value.ordinal());
        });
        this.carData.getLightState().observe(this, value -> {
            Log.i("CarDataModel", "Light state changed");
            ((MainActivity) this.getActivity()).sendMessage(MSG_WHEEL_MODE, value.ordinal());
        });

        this.sensorManager = (SensorManager) this.getActivity().getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View fragmentView = inflater.inflate(R.layout.fragment_control, container, false);

        this.steeringWheel = fragmentView.findViewById(R.id.steering_wheel);
        this.steeringWheel.setOnTouchListener(STEERING_WHEEL_LISTENER);
        RotateAnimation rotation = new RotateAnimation(0, this.carData.getSteerAngle().getValue(), Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5F);
        rotation.setDuration(500);
        rotation.setInterpolator(new AccelerateDecelerateInterpolator());
        rotation.setFillEnabled(true);
        rotation.setFillAfter(true);
        this.steeringWheel.startAnimation(rotation);

        ((GaugeProgress) fragmentView.findViewById(R.id.batteryProgress)).setProgress(this.carData.getBatteryCharge().getValue() / 100.F);
        ((TextView) fragmentView.findViewById(R.id.batteryDisplay)).setText(this.carData.getBatteryCharge().getValue() + "%");

        fragmentView.findViewById(R.id.pedal).setOnTouchListener((view, event) -> {
            final float delta = event.getY() - (view.getHeight() / 2.F);

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE: {

                    if (delta < 0 && this.carData.getThrottle().getValue() != 1) {
                        view.setRotationX(4F);
                        this.carData.setThrottle(1);

                    } else if (delta > 0 && this.carData.getThrottle().getValue() != -1) {
                        view.setRotationX(-4F);
                        this.carData.setThrottle(-1);

                    } else if (delta == 0 && this.carData.getThrottle().getValue() != 0){
                        view.setRotationX(0F);
                        this.carData.setThrottle(0);
                    }

                    break;
                }

                case MotionEvent.ACTION_UP: {

                    if (this.carData.getThrottle().getValue() != 0) {
                        view.setRotationX(0F);
                        this.carData.setThrottle(0);
                    }

                    break;
                }
            }

            return true;
        });

        SeekBar speedControl = fragmentView.findViewById(R.id.speed_control);

        speedControl.setProgress(this.carData.getSpeed().getValue());

        speedControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                ControlFragment.this.carData.setSpeed(seekBar.getProgress());
            }
        });

        fragmentView.findViewById(R.id.gyro_mode).setOnClickListener(view -> {
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

        this.wheelModeButton = fragmentView.findViewById(R.id.wheel_mode);

        this.wheelModeMenu = fragmentView.findViewById(R.id.wheel_mode_menu);

        CheckableImageView modeButton1 = fragmentView.findViewById(R.id.mode1);
        CheckableImageView modeButton2 = fragmentView.findViewById(R.id.mode2);
        CheckableImageView modeButton3 = fragmentView.findViewById(R.id.mode3);

        switch (this.carData.getWheelMode().getValue()) {

            case INTERSECTING: {

                if(!modeButton1.isChecked()) modeButton1.toggle();

                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), this.carData.getWheelMode().getValue().getDrawableId()));
                break;
            }

            case PARALLEL: {

                if(!modeButton2.isChecked()) modeButton2.toggle();

                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), this.carData.getWheelMode().getValue().getDrawableId()));
                break;
            }

            case RC: {

                if(!modeButton3.isChecked()) modeButton3.toggle();

                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), this.carData.getWheelMode().getValue().getDrawableId()));
                break;
            }
        }

        this.wheelModeButton.setOnClickListener(view -> {
            CheckableImageView buttonView = (CheckableImageView) view;
            buttonView.toggle();

            if (buttonView.isChecked()) {
                this.wheelModeMenu.setVisibility(View.VISIBLE);

                this.wheelModeMenu.animate().alpha(1).setDuration(500).setInterpolator(new DecelerateInterpolator()).setUpdateListener(animation -> {
                    float alpha = (float) animation.getAnimatedValue();
                    modeButton1.setRotation(-alpha * 360);
                    modeButton2.setRotation(-alpha * 360);
                    modeButton3.setRotation(-alpha * 360);
                }).withStartAction(() -> {
                    buttonView.setClickable(false);
                    modeButton1.setClickable(false);
                    modeButton2.setClickable(false);
                    modeButton3.setClickable(false);
                }).withEndAction(() -> {
                    buttonView.setClickable(true);
                    modeButton1.setClickable(true);
                    modeButton2.setClickable(true);
                    modeButton3.setClickable(true);
                }).start();

            } else {
                this.wheelModeMenu.animate().alpha(0).setDuration(500).setInterpolator(new DecelerateInterpolator()).setUpdateListener(animation -> {
                    float alpha = (float) animation.getAnimatedValue();
                    modeButton1.setRotation(alpha * 360);
                    modeButton2.setRotation(alpha * 360);
                    modeButton3.setRotation(alpha * 360);
                }).withStartAction(() -> {
                    buttonView.setClickable(false);
                    modeButton1.setClickable(false);
                    modeButton2.setClickable(false);
                    modeButton3.setClickable(false);
                }).withEndAction(() -> {
                    this.wheelModeMenu.setVisibility(View.GONE);
                    buttonView.setClickable(true);
                    modeButton1.setClickable(true);
                    modeButton2.setClickable(true);
                    modeButton3.setClickable(true);
                }).start();
            }
        });

        modeButton1.setOnClickListener(view -> {
            Checkable checkable = (Checkable) view;
            checkable.toggle();

            if (checkable.isChecked()) {
                this.carData.setWheelMode(WheelMode.INTERSECTING);

                if (modeButton2.isChecked()) modeButton2.toggle();

                if (modeButton3.isChecked()) {
                    modeButton3.toggle();
                    int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, this.getResources().getDisplayMetrics());
                    this.wheelModeButton.setPadding(padding, padding, padding, padding);
                }

                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), R.drawable.ic_wheel_mode_1));

            } else {
                this.carData.setWheelMode(WheelMode.NORMAL);
                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), R.drawable.ic_wheel_mode_0));
            }
        });

        modeButton2.setOnClickListener(view -> {
            Checkable checkable = (Checkable) view;
            checkable.toggle();

            if (checkable.isChecked()) {
                this.carData.setWheelMode(WheelMode.PARALLEL);

                if (modeButton1.isChecked()) modeButton1.toggle();

                if (modeButton3.isChecked()) {
                    modeButton3.toggle();
                    int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, this.getResources().getDisplayMetrics());
                    this.wheelModeButton.setPadding(padding, padding, padding, padding);
                }

                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), R.drawable.ic_wheel_mode_2));

            } else {
                this.carData.setWheelMode(WheelMode.NORMAL);
                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), R.drawable.ic_wheel_mode_0));
            }
        });

        modeButton3.setOnClickListener(view -> {
            Checkable checkable = (Checkable) view;
            checkable.toggle();

            if (checkable.isChecked()) {
                this.carData.setWheelMode(WheelMode.RC);

                if (modeButton1.isChecked()) modeButton1.toggle();

                if (modeButton2.isChecked()) modeButton2.toggle();

                int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, this.getResources().getDisplayMetrics());
                this.wheelModeButton.setPadding(padding, padding, padding, padding);
                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), R.drawable.ic_wheel_mode_rc));

            } else {
                this.carData.setWheelMode(WheelMode.RC);
                int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, this.getResources().getDisplayMetrics());
                this.wheelModeButton.setPadding(padding, padding, padding, padding);
                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), R.drawable.ic_wheel_mode_0));
            }
        });

        CheckableImageView chassisPositionButton = fragmentView.findViewById(R.id.chassis_position);

        RelativeLayout chassisPositionMenu = fragmentView.findViewById(R.id.chassis_position_menu);
        SeekBar positionSlider = chassisPositionMenu.findViewById(R.id.position_slider);

        positionSlider.setProgress(this.carData.getChassisPosition().getValue());

        chassisPositionButton.setOnClickListener(view -> {
            CheckableImageView buttonView = (CheckableImageView) view;
            buttonView.toggle();

            if (buttonView.isChecked()) {
                chassisPositionMenu.setVisibility(View.VISIBLE);

                chassisPositionMenu.animate().alpha(1).setDuration(500).setInterpolator(new DecelerateInterpolator()).withStartAction(() -> positionSlider.setClickable(false)).withEndAction(() -> {
                    buttonView.setClickable(true);
                    positionSlider.setClickable(true);
                }).start();

            } else {
                chassisPositionMenu.animate().alpha(0).setDuration(500).setInterpolator(new DecelerateInterpolator()).withStartAction(() -> {
                    buttonView.setClickable(false);
                    positionSlider.setClickable(false);
                }).withEndAction(() -> {
                    chassisPositionMenu.setVisibility(View.GONE);
                    buttonView.setClickable(true);
                    positionSlider.setClickable(true);
                }).start();
            }
        });

        positionSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ControlFragment.this.carData.setChassisPosition(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        this.lightStateButton = fragmentView.findViewById(R.id.light_state);

        this.lightStateMenu = fragmentView.findViewById(R.id.light_state_menu);

        CheckableImageView stateButton1 = fragmentView.findViewById(R.id.state1);
        CheckableImageView stateButton2 = fragmentView.findViewById(R.id.state2);
        CheckableImageView stateButton3 = fragmentView.findViewById(R.id.state3);

        switch (this.carData.getLightState().getValue()) {

            case OFF: {

                if(!stateButton1.isChecked()) stateButton1.toggle();

                int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, this.getResources().getDisplayMetrics());
                int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, this.getResources().getDisplayMetrics());
                this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), this.carData.getLightState().getValue().getDrawableId()));
                break;
            }

            case ON: {

                if(!stateButton2.isChecked()) stateButton2.toggle();

                int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, this.getResources().getDisplayMetrics());
                int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, this.getResources().getDisplayMetrics());
                this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), this.carData.getLightState().getValue().getDrawableId()));
                break;
            }

            case AUTO: {

                if(!stateButton3.isChecked()) stateButton3.toggle();

                int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, this.getResources().getDisplayMetrics());
                int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, this.getResources().getDisplayMetrics());
                this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), this.carData.getLightState().getValue().getDrawableId()));
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
                this.carData.setLightState(LightState.OFF);

                if (stateButton2.isChecked()) stateButton2.toggle();

                if (stateButton3.isChecked()) stateButton3.toggle();

                int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, this.getResources().getDisplayMetrics());
                int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, this.getResources().getDisplayMetrics());
                this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), R.drawable.ic_light_state_0));
            }
        });

        stateButton2.setOnClickListener(buttonView -> {
            Checkable checkable = (Checkable) buttonView;

            if (!checkable.isChecked()) {
                checkable.toggle();
                this.carData.setLightState(LightState.ON);

                if (stateButton1.isChecked()) stateButton1.toggle();

                if (stateButton3.isChecked()) stateButton3.toggle();

                int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, this.getResources().getDisplayMetrics());
                int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, this.getResources().getDisplayMetrics());
                this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), R.drawable.ic_light_state_1));
            }
        });

        stateButton3.setOnClickListener(view -> {
            Checkable checkable = (Checkable) view;

            if (!checkable.isChecked()) {
                checkable.toggle();
                this.carData.setLightState(LightState.AUTO);

                if (stateButton1.isChecked()) stateButton1.toggle();

                if (stateButton2.isChecked()) stateButton2.toggle();

                int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, this.getResources().getDisplayMetrics());
                int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, this.getResources().getDisplayMetrics());
                this.lightStateButton.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                this.lightStateButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), R.drawable.ic_light_state_2));
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (((Checkable) this.getView().findViewById(R.id.gyro_mode)).isChecked()) {
            this.sensorManager.registerListener(this, this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
            this.sensorManager.registerListener(this, this.sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
            this.sensorManager.registerListener(this, this.sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_FASTEST);
            //this.sensorManager.registerListener(this, this.sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (((Checkable) this.getView().findViewById(R.id.gyro_mode)).isChecked()) {
            this.sensorManager.unregisterListener(this);
            this.updateTimer.cancel();
            this.resetSensorData();
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
                    float inclination = (float) Math.acos(this.rotationMatrix[8]);
                    if (inclination < 25 * Math.PI / 180 || inclination > 155 * Math.PI / 180) {
                        float[] remappedMatrix = new float[9];
                        SensorManager.remapCoordinateSystem(this.rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remappedMatrix);
                        SensorManager.getOrientation(remappedMatrix, this.accMagOrientation);

                    } else {
                        SensorManager.getOrientation(this.rotationMatrix, this.accMagOrientation);
                    }
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
                                ControlFragment.this.fusedOrientation[0] = .98F * ControlFragment.this.gyroOrientation[0] + .02F * ControlFragment.this.accMagOrientation[0];
                                ControlFragment.this.fusedOrientation[1] = .98F * ControlFragment.this.gyroOrientation[1] + .02F * ControlFragment.this.accMagOrientation[1];
                                ControlFragment.this.fusedOrientation[2] = .98F * ControlFragment.this.gyroOrientation[2] + .02F * ControlFragment.this.accMagOrientation[2];

                                // overwrite gyro matrix and orientation with fused orientation
                                // to compensate gyro drift
                                ControlFragment.this.gyroMatrix = getRotationMatrixFromOrientation(ControlFragment.this.fusedOrientation);
                                System.arraycopy(ControlFragment.this.fusedOrientation, 0, ControlFragment.this.gyroOrientation, 0, 3);

                                ControlFragment.this.handler.post(() -> {

                                    if (ControlFragment.this.fusedOrientation[1] >= -Math.PI / 2 || ControlFragment.this.fusedOrientation[1] <= Math.PI / 2) {
                                        float updatedAngle = (float) (-254 / Math.PI * ControlFragment.this.fusedOrientation[1]);
                                        RotateAnimation rotation = new RotateAnimation(ControlFragment.this.carData.getSteerAngle().getValue(), updatedAngle, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5F);
                                        rotation.setDuration(0);
                                        rotation.setInterpolator(new AccelerateDecelerateInterpolator());
                                        rotation.setFillEnabled(true);
                                        rotation.setFillAfter(true);
                                        ControlFragment.this.steeringWheel.startAnimation(rotation);
                                        ControlFragment.this.carData.setSteerAngle(updatedAngle);
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
}
