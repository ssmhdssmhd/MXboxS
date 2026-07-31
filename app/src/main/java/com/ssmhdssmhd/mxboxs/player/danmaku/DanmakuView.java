package com.ssmhdssmhd.mxboxs.player.danmaku;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class DanmakuView extends View {

    private final DanmakuEngine engine;
    private final Paint paint;

    public DanmakuView(Context context) {
        this(context, null);
    }

    public DanmakuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DanmakuView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        engine = new DanmakuEngine();
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setSubpixelText(true);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public DanmakuEngine getEngine() {
        return engine;
    }

    public void setDanmakuEnabled(boolean enabled) {
        engine.setVisible(enabled);
        if (enabled) {
            setVisibility(VISIBLE);
        } else {
            setVisibility(GONE);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        engine.draw(canvas, paint);
    }

    public void updateDanmaku(long position, long duration) {
        int width = getWidth();
        int height = getHeight();
        if (width > 0 && height > 0) {
            engine.update(position, duration, width, height);
            postInvalidate();
        }
    }

    public void seekTo(long position) {
        engine.seekTo(position);
        postInvalidate();
    }
}
