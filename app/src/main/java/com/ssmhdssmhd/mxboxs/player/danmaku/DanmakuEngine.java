package com.ssmhdssmhd.mxboxs.player.danmaku;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DanmakuEngine {

    public static final int TYPE_SCROLL = 0;
    public static final int TYPE_TOP = 1;
    public static final int TYPE_BOTTOM = 2;

    public static final int COLOR_WHITE = 0xFFFFFF;

    private final List<DanmakuItem> items = new ArrayList<>();
    private final List<DanmakuItem> consumedItems = new ArrayList<>();
    private final List<DanmakuRenderer> activeRenderers = new ArrayList<>();
    private long currentPosition;
    private long lastPosition;
    private boolean visible;
    private float scrollAreaRatio = 0.5f;
    private long durationMs = 8000L;
    private int maxScrollLines = 0;
    private int maxTopLines = 0;
    private int maxBottomLines = 0;
    private float textScale = 1.0f;
    private float transparency = 0.0f;
    private int styleMode = 2;
    private int colorMode = 0;
    private long timeOffsetMs = 0L;

    public static class DanmakuItem {
        public final long time;
        public final String text;
        public final int type;
        public final int color;
        public final float textSize;
        public final boolean bold;
        public boolean consumed;

        public DanmakuItem(long time, String text, int type, int color, float textSize, boolean bold) {
            this.time = time;
            this.text = text;
            this.type = type;
            this.color = color;
            this.textSize = textSize;
            this.bold = bold;
        }
    }

    public static class DanmakuRenderer {
        public final DanmakuItem item;
        public long startTime;
        public float x;
        public float y;
        public float speed;
        public boolean active;
        public int lineIndex;

        public DanmakuRenderer(DanmakuItem item, long startTime) {
            this.item = item;
            this.startTime = startTime;
            this.x = 0;
            this.y = 0;
            this.speed = 0;
            this.active = true;
            this.lineIndex = -1;
        }
    }

    public void addItem(DanmakuItem item) {
        item.consumed = false;
        items.add(item);
    }

    public void clearItems() {
        items.clear();
        consumedItems.clear();
        activeRenderers.clear();
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) activeRenderers.clear();
    }

    public boolean isVisible() {
        return visible;
    }

    public void setScrollAreaRatio(float ratio) {
        this.scrollAreaRatio = ratio;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public void setMaxScrollLines(int maxScrollLines) {
        this.maxScrollLines = maxScrollLines;
    }

    public void setMaxTopLines(int maxTopLines) {
        this.maxTopLines = maxTopLines;
    }

    public void setMaxBottomLines(int maxBottomLines) {
        this.maxBottomLines = maxBottomLines;
    }

    public void setTextScale(float textScale) {
        this.textScale = textScale;
    }

    public void setTransparency(float transparency) {
        this.transparency = transparency;
    }

    public void setStyleMode(int styleMode) {
        this.styleMode = styleMode;
    }

    public void setColorMode(int colorMode) {
        this.colorMode = colorMode;
    }

    public void setTimeOffsetMs(long timeOffsetMs) {
        this.timeOffsetMs = timeOffsetMs;
    }

    public void update(long position, long duration, int viewWidth, int viewHeight) {
        if (!visible) return;
        lastPosition = currentPosition;
        currentPosition = position + timeOffsetMs;

        synchronized (items) {
            Iterator<DanmakuItem> itemIterator = items.iterator();
            while (itemIterator.hasNext()) {
                DanmakuItem item = itemIterator.next();
                if (!item.consumed && item.time <= currentPosition && item.time > lastPosition - 200) {
                    addRenderer(item, viewWidth, viewHeight);
                    item.consumed = true;
                    consumedItems.add(item);
                    itemIterator.remove();
                } else if (item.time < currentPosition - 10000) {
                    itemIterator.remove();
                }
            }
        }

        synchronized (activeRenderers) {
            Iterator<DanmakuRenderer> rendererIterator = activeRenderers.iterator();
            while (rendererIterator.hasNext()) {
                DanmakuRenderer renderer = rendererIterator.next();
                long elapsed = currentPosition - renderer.startTime;
                if (renderer.item.type == TYPE_SCROLL) {
                    float distance = (elapsed / (float) durationMs) * (viewWidth + 200);
                    renderer.x = viewWidth + 100 - distance;
                    if (renderer.x < -viewWidth) {
                        rendererIterator.remove();
                    }
                } else {
                    if (elapsed > durationMs) {
                        rendererIterator.remove();
                    }
                }
            }
        }
    }

    private void addRenderer(DanmakuItem item, int viewWidth, int viewHeight) {
        DanmakuRenderer renderer = new DanmakuRenderer(item, currentPosition);
        if (item.type == TYPE_SCROLL) {
            renderer.speed = (viewWidth + 200) / (float) durationMs;
            renderer.lineIndex = findScrollLine(viewWidth, viewHeight);
            float lineHeight = getLineHeight(viewHeight);
            renderer.y = renderer.lineIndex * lineHeight + lineHeight;
            renderer.x = viewWidth + 100;
        } else if (item.type == TYPE_TOP) {
            renderer.lineIndex = findTopLine();
            float lineHeight = getLineHeight(viewHeight);
            renderer.y = renderer.lineIndex * lineHeight + lineHeight;
            renderer.x = viewWidth / 2;
        } else if (item.type == TYPE_BOTTOM) {
            renderer.lineIndex = findBottomLine(viewHeight);
            float lineHeight = getLineHeight(viewHeight);
            renderer.y = viewHeight - renderer.lineIndex * lineHeight;
            renderer.x = viewWidth / 2;
        }
        activeRenderers.add(renderer);
    }

    private int findScrollLine(int viewWidth, int viewHeight) {
        float lineHeight = getLineHeight(viewHeight);
        int availableLines = (int) ((viewHeight * scrollAreaRatio) / lineHeight);
        if (maxScrollLines > 0) availableLines = Math.min(availableLines, maxScrollLines);
        if (availableLines <= 0) availableLines = 1;

        boolean[] occupied = new boolean[availableLines];
        for (DanmakuRenderer r : activeRenderers) {
            if (r.item.type == TYPE_SCROLL && r.lineIndex >= 0 && r.lineIndex < availableLines) {
                float textWidth = r.item.text.length() * 20;
                float remainingDistance = (durationMs - (currentPosition - r.startTime)) * r.speed;
                if (remainingDistance < textWidth + 50) {
                    occupied[r.lineIndex] = true;
                }
            }
        }
        for (int i = 0; i < availableLines; i++) {
            if (!occupied[i]) return i;
        }
        return 0;
    }

    private int findTopLine() {
        int maxLines = maxTopLines > 0 ? maxTopLines : 3;
        for (int i = 0; i < maxLines; i++) {
            boolean occupied = false;
            for (DanmakuRenderer r : activeRenderers) {
                if (r.item.type == TYPE_TOP && r.lineIndex == i) {
                    occupied = true;
                    break;
                }
            }
            if (!occupied) return i;
        }
        return 0;
    }

    private int findBottomLine(int viewHeight) {
        int maxLines = maxBottomLines > 0 ? maxBottomLines : 3;
        for (int i = 0; i < maxLines; i++) {
            boolean occupied = false;
            for (DanmakuRenderer r : activeRenderers) {
                if (r.item.type == TYPE_BOTTOM && r.lineIndex == i) {
                    occupied = true;
                    break;
                }
            }
            if (!occupied) return i;
        }
        return 0;
    }

    private float getLineHeight(int viewHeight) {
        return Math.max(viewHeight * 0.05f, 24f) * textScale;
    }

    public void draw(Canvas canvas, Paint paint) {
        if (!visible) return;
        synchronized (activeRenderers) {
            for (DanmakuRenderer renderer : activeRenderers) {
                drawItem(canvas, paint, renderer);
            }
        }
    }

    private void drawItem(Canvas canvas, Paint paint, DanmakuRenderer renderer) {
        DanmakuItem item = renderer.item;
        float alpha = 1.0f - transparency;
        int color = item.color == COLOR_WHITE && colorMode == 0 ? Color.WHITE : item.color;
        paint.setTextSize(32 * textScale);

        if (styleMode == 1) {
            paint.setShadowLayer(4, 2, 2, adjustAlpha(Color.BLACK, alpha));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(adjustAlpha(color, alpha));
            drawText(canvas, paint, renderer);
        } else if (styleMode == 2) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setColor(adjustAlpha(Color.BLACK, alpha));
            drawText(canvas, paint, renderer);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(adjustAlpha(color, alpha));
            drawText(canvas, paint, renderer);
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(adjustAlpha(color, alpha));
            drawText(canvas, paint, renderer);
        }
        paint.setShadowLayer(0, 0, 0, 0);
    }

    private void drawText(Canvas canvas, Paint paint, DanmakuRenderer renderer) {
        DanmakuItem item = renderer.item;
        if (item.type == TYPE_SCROLL) {
            canvas.drawText(item.text, renderer.x, renderer.y, paint);
        } else {
            float textWidth = paint.measureText(item.text);
            canvas.drawText(item.text, renderer.x - textWidth / 2, renderer.y, paint);
        }
    }

    private int adjustAlpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * alpha);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return Color.argb(a, r, g, b);
    }

    public void seekTo(long position) {
        synchronized (activeRenderers) {
            activeRenderers.clear();
        }
        synchronized (items) {
            synchronized (consumedItems) {
                for (DanmakuItem item : consumedItems) {
                    if (item.time >= position - 100) {
                        item.consumed = false;
                        items.add(item);
                    }
                }
                consumedItems.clear();
            }
            Iterator<DanmakuItem> itemIterator = items.iterator();
            while (itemIterator.hasNext()) {
                DanmakuItem item = itemIterator.next();
                if (item.time < position - 100) {
                    itemIterator.remove();
                }
            }
        }
        currentPosition = position + timeOffsetMs;
        lastPosition = currentPosition;
    }
}
