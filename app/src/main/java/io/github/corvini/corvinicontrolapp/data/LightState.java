package io.github.corvini.corvinicontrolapp.data;

import io.github.corvini.corvinicontrolapp.R;

public enum LightState implements DrawableState {
    OFF(R.drawable.ic_light_state_0),
    ON(R.drawable.ic_light_state_1),
    AUTO(R.drawable.ic_light_state_2);

    private final int drawableId;

    LightState(int drawableId) {
        this.drawableId = drawableId;
    }

    @Override
    public int getDrawableId() {
        return drawableId;
    }
}
