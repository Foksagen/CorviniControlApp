package io.github.corvini.corvinicontrolapp.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;
import io.github.corvini.corvinicontrolapp.R;

public final class ConnectionButton extends FrameLayout {

    private ImageView wifiIcon;
    private ProgressBar connectingStatus;

    public ConnectionButton(Context context) {
        super(context);
        this.initialize(context);
    }

    public ConnectionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.initialize(context);
    }

    public ConnectionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.initialize(context);
    }

    public void setConnecting(boolean connecting) {
        this.connectingStatus.setVisibility(connecting ? VISIBLE : GONE);
        //this.refreshDrawableState();
    }

    public void updateWifiBars(int bars) {

        Drawable icon;

        switch (bars) {

            case 0: {
                icon = this.getContext().getDrawable(R.drawable.ic_wifi_0_bar);
                break;
            }

            case 1: {
                icon = this.getContext().getDrawable(R.drawable.ic_wifi_1_bar);
                break;
            }

            case 2: {
                icon = this.getContext().getDrawable(R.drawable.ic_wifi_2_bar);
                break;
            }

            case 3: {
                icon = this.getContext().getDrawable(R.drawable.ic_wifi_3_bar);
                break;
            }

            case 4: {
                icon = this.getContext().getDrawable(R.drawable.ic_wifi_4_bar);
                break;
            }

            default: {
                icon = this.getContext().getDrawable(R.drawable.ic_wifi_no_connection);
                break;
            }
        }

        icon.mutate();
        this.wifiIcon.setImageDrawable(icon);
        this.wifiIcon.setImageTintList(ContextCompat.getColorStateList(this.getContext(), bars < 0 ? R.color.colorPrimaryLight : R.color.colorConnected));
    }

    private void initialize(Context context) {
        Drawable background = context.getDrawable(R.drawable.background_connection_1);
        background.mutate();
        this.setBackground(background);
        LayoutInflater.from(context).inflate(R.layout.connection_button_layout, this, true);
        this.wifiIcon = this.findViewById(R.id.wifi_icon);
        this.connectingStatus = this.findViewById(R.id.connecting_status);
        CircularProgressDrawable connectingDrawable = new CircularProgressDrawable(context);
        connectingDrawable.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, context.getResources().getDisplayMetrics()));
        connectingDrawable.setColorSchemeColors(AppCompatResources.getColorStateList(context, R.color.colorConnected).getDefaultColor());
        connectingDrawable.mutate();
        this.connectingStatus.setIndeterminateDrawable(connectingDrawable);
    }
}
