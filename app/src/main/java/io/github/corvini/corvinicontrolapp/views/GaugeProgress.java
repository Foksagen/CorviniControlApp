package io.github.corvini.corvinicontrolapp.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.FloatRange;
import androidx.core.content.ContextCompat;
import io.github.corvini.corvinicontrolapp.R;

public final class GaugeProgress extends View {

    private static final float STROKE_WIDTH = 12;
    private static final float ARC_ANGLE = .7F * 360;

    private RectF rectF = new RectF();
    private Paint paint;
    private int backgroundColor;

    private float progress;

    public GaugeProgress(Context context) {
        super(context);
        this.initialize(context);
    }

    public GaugeProgress(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.initialize(context);
    }

    public GaugeProgress(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.initialize(context);
    }

    @Override
    public void invalidate() {
        this.initializePaint();
        super.invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        this.rectF.set(STROKE_WIDTH / 2.F, STROKE_WIDTH / 2.F, width - STROKE_WIDTH / 2.F, MeasureSpec.getSize(heightMeasureSpec) - STROKE_WIDTH / 2.F);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float startAngle = 270 - ARC_ANGLE / 2.F;
        float finishedSweepAngle = this.progress * ARC_ANGLE;

        this.paint.setColor(this.backgroundColor);
        canvas.drawArc(this.rectF, startAngle, ARC_ANGLE, false, this.paint);
        this.paint.setColor(this.calculateColorInGradient());
        canvas.drawArc(this.rectF, this.progress == 0 ? .01F : startAngle, finishedSweepAngle, false, this.paint);
    }

    public void setProgress(@FloatRange(from = .0F, to = 1.F) float progress) {
        this.progress = progress;
        this.postInvalidate();
    }

    public void animate(@FloatRange(from = .0F, to = 1.F) float progress) {
        ValueAnimator anim = ValueAnimator.ofFloat(this.progress, progress);

        if (this.progress > progress) {
            anim.setInterpolator(new DecelerateInterpolator());
            anim.setDuration(750);

        } else {
            anim.setInterpolator(new AccelerateDecelerateInterpolator());
            anim.setDuration(500);
        }

        anim.addUpdateListener(valueAnimator -> this.setProgress((float) valueAnimator.getAnimatedValue()));
        anim.start();
    }

    private void initialize(Context context) {
        this.initializePaint();
        this.backgroundColor = ContextCompat.getColor(context, R.color.colorPrimaryDark);

    }

    private void initializePaint() {
        this.paint = new Paint();
        this.paint.setColor(Color.BLACK);
        this.paint.setAntiAlias(true);
        this.paint.setStrokeWidth(STROKE_WIDTH);
        this.paint.setStyle(Paint.Style.STROKE);
        this.paint.setStrokeCap(Paint.Cap.ROUND);
    }

    private int calculateColorInGradient() {

        if (this.progress > .5F) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return Color.rgb(1.F - (this.progress - .5F) / .5F, 1.F, 0.F);

            } else {
                return (0xff) << 24 | (((int) (255 * (1.F - (this.progress - .5F) / .5F))) & 0xff) << 16 | (0xff) << 8 | (0);
            }

        } else if (this.progress < .5F) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return Color.rgb(1.F, this.progress / .5F, 0.F);

            } else {
                return (0xff) << 24 | (0xff) << 16 | (((int) (255 * this.progress - .5F)) & 0xff) << 8 | (0);
            }

        } else {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return Color.rgb(1.F, 1.F, 0.F);

            } else {
                return (0xff) << 24 | (0xff) << 16 | (0xff) << 8 | (0);
            }
        }
    }
}
