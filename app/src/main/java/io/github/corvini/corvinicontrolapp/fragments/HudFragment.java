package io.github.corvini.corvinicontrolapp.fragments;

import androidx.fragment.app.Fragment;

public class HudFragment extends Fragment {

    /*private CarDataModel carData;

    private CheckableImageView wheelModeButton;
    private RelativeLayout wheelModeMenu;
    private CheckableImageView lightStateButton;
    private RelativeLayout lightStateMenu;

    public HudFragment() {
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

        this.wheelModeButton.setOnClickListener(view -> {
            CheckableImageView buttonView = (CheckableImageView) view;
            buttonView.toggle();

            if (buttonView.isChecked()) {

                switch (this.carData.getWheelMode().getValue()) {

                    case INTERSECTING: {

                        if(!modeButton1.isChecked()) modeButton1.toggle();

                        buttonView.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), this.carData.getWheelMode().getValue().getDrawableId()));
                        break;
                    }

                    case PARALLEL: {

                        if(!modeButton2.isChecked()) modeButton2.toggle();

                        buttonView.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), this.carData.getWheelMode().getValue().getDrawableId()));
                        break;
                    }

                    case PERPENDICULAR: {

                        if(!modeButton3.isChecked()) modeButton3.toggle();

                        buttonView.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), this.carData.getWheelMode().getValue().getDrawableId()));
                        break;
                    }
                }

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
                this.carData.setWheelMode(WheelMode.PERPENDICULAR);

                if (modeButton1.isChecked()) modeButton1.toggle();

                if (modeButton2.isChecked()) modeButton2.toggle();

                int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, this.getResources().getDisplayMetrics());
                this.wheelModeButton.setPadding(padding, padding, padding, padding);
                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), R.drawable.ic_wheel_mode_3));

            } else {
                this.carData.setWheelMode(WheelMode.PERPENDICULAR);
                int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, this.getResources().getDisplayMetrics());
                this.wheelModeButton.setPadding(padding, padding, padding, padding);
                this.wheelModeButton.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), R.drawable.ic_wheel_mode_0));
            }
        });

        this.lightStateButton = fragmentView.findViewById(R.id.light_state);

        this.lightStateMenu = fragmentView.findViewById(R.id.light_state_menu);

        CheckableImageView stateButton1 = fragmentView.findViewById(R.id.state1);
        CheckableImageView stateButton2 = fragmentView.findViewById(R.id.state2);
        CheckableImageView stateButton3 = fragmentView.findViewById(R.id.state3);

        this.lightStateButton.setOnClickListener(view -> {
            CheckableImageView buttonView = (CheckableImageView) view;
            buttonView.toggle();

            if (buttonView.isChecked()) {

                switch (this.carData.getLightState().getValue()) {

                    case OFF: {

                        if(!stateButton1.isChecked()) stateButton1.toggle();

                        int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, this.getResources().getDisplayMetrics());
                        int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, this.getResources().getDisplayMetrics());
                        buttonView.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                        buttonView.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), this.carData.getLightState().getValue().getDrawableId()));
                        break;
                    }

                    case ON: {

                        if(!stateButton2.isChecked()) stateButton2.toggle();

                        int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, this.getResources().getDisplayMetrics());
                        int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, this.getResources().getDisplayMetrics());
                        buttonView.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                        buttonView.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), this.carData.getLightState().getValue().getDrawableId()));
                        break;
                    }

                    case AUTO: {

                        if(!stateButton3.isChecked()) stateButton3.toggle();

                        int paddingStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, this.getResources().getDisplayMetrics());
                        int paddingRest = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, this.getResources().getDisplayMetrics());
                        buttonView.setPadding(paddingStart, paddingRest, paddingRest, paddingRest);
                        buttonView.setImageDrawable(ContextCompat.getDrawable(this.getActivity(), this.carData.getLightState().getValue().getDrawableId()));
                        break;
                    }
                }

                this.lightStateMenu.setVisibility(View.VISIBLE);

                this.lightStateMenu.animate().alpha(1).setDuration(500).setInterpolator(new DecelerateInterpolator()).setUpdateListener(animation -> {
                    float alpha = (float) animation.getAnimatedValue();
                    stateButton1.setRotation(-alpha * 360);
                    stateButton2.setRotation(-alpha * 360);
                    stateButton3.setRotation(-alpha * 360);
                }).withStartAction(() -> {
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
    }*/
}
