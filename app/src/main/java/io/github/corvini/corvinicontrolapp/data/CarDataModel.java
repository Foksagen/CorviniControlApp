package io.github.corvini.corvinicontrolapp.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public final class CarDataModel extends ViewModel {

    private final MutableLiveData<Integer> batteryCharge;
    private final MutableLiveData<Float> steerAngle;
    private final MutableLiveData<Integer> throttle;
    private final MutableLiveData<WheelMode> wheelMode;
    private final MutableLiveData<LightState> lightState;
    private final MutableLiveData<Integer> chassisPosition;
    private final MutableLiveData<Integer> speed;
    private final MutableLiveData<Boolean> rcControl;

    public CarDataModel(final byte[] initPacketData) {
        this.batteryCharge = new MutableLiveData<>(initPacketData[0] / 100);
        this.steerAngle = new MutableLiveData<>((float) initPacketData[1]);
        this.throttle = new MutableLiveData<>((int) initPacketData[2]);
        this.wheelMode = new MutableLiveData<>(WheelMode.values()[(int) initPacketData[3]]);
        this.lightState = new MutableLiveData<>(LightState.values()[(int) initPacketData[4]]);
        this.chassisPosition = new MutableLiveData<>((int) initPacketData[5]);
        this.speed = new MutableLiveData<>((int) initPacketData[6]);
        this.rcControl = new MutableLiveData<>(initPacketData[7] != 0);
    }

    //for testing
    public CarDataModel() {
        this.batteryCharge = new MutableLiveData<>(75);
        this.steerAngle = new MutableLiveData<>(0.F);
        this.throttle = new MutableLiveData<>(0);
        this.wheelMode = new MutableLiveData<>(WheelMode.NORMAL);
        this.lightState = new MutableLiveData<>(LightState.OFF);
        this.chassisPosition = new MutableLiveData<>(5);
        this.speed = new MutableLiveData<>(128);
        this.rcControl = new MutableLiveData<>(false);
    }

    public LiveData<Integer> getBatteryCharge() {
        return batteryCharge;
    }

    public void setBatteryCharge(int batteryCharge) {
        this.batteryCharge.setValue(batteryCharge);
    }

    public LiveData<Float> getSteerAngle() {
        return steerAngle;
    }

    public void setSteerAngle(float steerAngle) {
        if (this.getSteerAngle().getValue() != steerAngle) this.steerAngle.setValue(steerAngle);
    }

    public LiveData<Integer> getThrottle() {
        return throttle;
    }

    public void setThrottle(int throttle) {
        if (this.getThrottle().getValue() != throttle) this.throttle.setValue(throttle);
    }

    public LiveData<WheelMode> getWheelMode() {
        return wheelMode;
    }

    public void setWheelMode(WheelMode wheelMode) {
        if (this.getWheelMode().getValue() != wheelMode) this.wheelMode.setValue(wheelMode);
    }

    public LiveData<LightState> getLightState() {
        return lightState;
    }

    public void setLightState(LightState lightState) {
        if (this.getLightState().getValue() != lightState) this.lightState.setValue(lightState);
    }

    public LiveData<Integer> getChassisPosition() {
        return chassisPosition;
    }

    public void setChassisPosition(int chassisPosition) {
        if (this.getChassisPosition().getValue() != chassisPosition) this.chassisPosition.setValue(chassisPosition);
    }

    public LiveData<Integer> getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        if (this.getSpeed().getValue() != speed) this.speed.setValue(speed);
    }

    public LiveData<Boolean> getRcControl() {
        return rcControl;
    }

    public void setRcControl(boolean connected) {
        if (this.getRcControl().getValue() != connected) this.rcControl.setValue(connected);
    }
}
