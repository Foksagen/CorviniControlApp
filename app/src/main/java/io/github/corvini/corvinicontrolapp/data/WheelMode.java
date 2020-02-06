package io.github.corvini.corvinicontrolapp.data;

import io.github.corvini.corvinicontrolapp.R;

public enum WheelMode implements DrawableState {
    RC(R.drawable.ic_wheel_mode_rc),
    INTERSECTING(R.drawable.ic_wheel_mode_1),
    PARALLEL(R.drawable.ic_wheel_mode_2),
    NORMAL(R.drawable.ic_wheel_mode_0);

    private final int drawableId;

    WheelMode(int drawableId) {
        this.drawableId = drawableId;
    }

    @Override
    public int getDrawableId() {
        return drawableId;
    }
}
