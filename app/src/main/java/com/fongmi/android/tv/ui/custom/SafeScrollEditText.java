package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import com.google.android.material.textfield.TextInputEditText;

public class SafeScrollEditText extends TextInputEditText {

    private final Paint trackPaint;
    private final Paint thumbPaint;
    private final RectF rect;
    private final int barSize;
    private final int touchSize;
    private final int minThumb;
    private final int inset;
    private int dragMode;

    public SafeScrollEditText(Context context) {
        this(context, null);
    }

    public SafeScrollEditText(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.editTextStyle);
    }

    public SafeScrollEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rect = new RectF();
        barSize = dp(6);
        touchSize = dp(24);
        minThumb = dp(34);
        inset = dp(3);
        init();
    }

    private void init() {
        setHorizontalScrollBarEnabled(false);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        setWillNotDraw(false);
        trackPaint.setColor(Color.parseColor("#DADCE0"));
        thumbPaint.setColor(Color.parseColor("#185ABC"));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawVerticalBar(canvas);
        drawHorizontalBar(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (handleScrollBarTouch(event)) return true;
        return super.onTouchEvent(event);
    }

    @Override
    public void scrollTo(int x, int y) {
        super.scrollTo(clampInt(x, 0, maxScrollX()), clampInt(y, 0, maxScrollY()));
    }

    @Override
    protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
        invalidate();
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int before, int count) {
        super.onTextChanged(text, start, before, count);
        invalidate();
    }

    private void drawVerticalBar(Canvas canvas) {
        int maxScroll = maxScrollY();
        int extent = Math.max(1, getHeight());
        int range = maxScroll + extent;
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float viewportTop = getScrollY();
        float viewportBottom = viewportTop + getHeight();
        float left = getScrollX() + getWidth() - inset - barSize;
        float top = viewportTop + inset;
        float bottom = viewportBottom - inset - barSize - inset;
        float track = bottom - top;
        if (track <= 0) return;
        float thumb = maxScroll <= 0 ? track : Math.max(minThumb, track * extent / range);
        float maxTop = Math.max(top, bottom - thumb);
        float thumbTop = top + (maxTop - top) * getScrollY() / Math.max(1, maxScroll);
        drawRound(canvas, left, top, left + barSize, bottom, trackPaint);
        drawRound(canvas, left, thumbTop, left + barSize, thumbTop + thumb, thumbPaint);
    }

    private void drawHorizontalBar(Canvas canvas) {
        int maxScroll = maxScrollX();
        int extent = Math.max(1, getWidth());
        int range = maxScroll + extent;
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float viewportLeft = getScrollX();
        float viewportRight = viewportLeft + getWidth();
        float top = getScrollY() + getHeight() - inset - barSize;
        float left = viewportLeft + inset;
        float right = viewportRight - inset - barSize - inset;
        float track = right - left;
        if (track <= 0) return;
        float thumb = maxScroll <= 0 ? track : Math.max(minThumb, track * extent / range);
        float maxLeft = Math.max(left, right - thumb);
        float thumbLeft = left + (maxLeft - left) * getScrollX() / Math.max(1, maxScroll);
        drawRound(canvas, left, top, right, top + barSize, trackPaint);
        drawRound(canvas, thumbLeft, top, thumbLeft + thumb, top + barSize, thumbPaint);
    }

    private boolean handleScrollBarTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            dragMode = hitMode(event.getX(), event.getY());
            if (dragMode == 0) return false;
            requestParentIntercept(false);
            scrollFromTouch(event.getX(), event.getY());
            return true;
        }
        if (dragMode == 0) return false;
        if (action == MotionEvent.ACTION_MOVE) {
            scrollFromTouch(event.getX(), event.getY());
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            dragMode = 0;
            requestParentIntercept(true);
            return true;
        }
        return true;
    }

    private int hitMode(float x, float y) {
        if (x >= getWidth() - touchSize) return 1;
        if (y >= getHeight() - touchSize) return 2;
        return 0;
    }

    private void scrollFromTouch(float x, float y) {
        if (dragMode == 1) {
            int maxScroll = maxScrollY();
            if (maxScroll <= 0) return;
            float top = inset;
            float bottom = getHeight() - inset - barSize - inset;
            float ratio = clamp((y - top) / Math.max(1f, bottom - top));
            scrollTo(getScrollX(), Math.round(maxScroll * ratio));
        } else if (dragMode == 2) {
            int maxScroll = maxScrollX();
            if (maxScroll <= 0) return;
            float left = inset;
            float right = getWidth() - inset - barSize - inset;
            float ratio = clamp((x - left) / Math.max(1f, right - left));
            scrollTo(Math.round(maxScroll * ratio), getScrollY());
        }
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int maxScrollX() {
        return Math.max(0, contentWidth() - getWidth());
    }

    private int maxScrollY() {
        return Math.max(0, contentHeight() - getHeight());
    }

    private int contentWidth() {
        int textWidth = 0;
        Layout layout = getLayout();
        if (layout != null) {
            for (int i = 0; i < layout.getLineCount(); i++) {
                textWidth = Math.max(textWidth, (int) Math.ceil(layout.getLineWidth(i)));
            }
        } else if (getText() != null) {
            textWidth = (int) Math.ceil(getPaint().measureText(getText().toString()));
        }
        return Math.max(getWidth(), textWidth + getTotalPaddingLeft() + getTotalPaddingRight());
    }

    private int contentHeight() {
        Layout layout = getLayout();
        int textHeight = layout == null ? getLineHeight() : layout.getHeight();
        return Math.max(getHeight(), textHeight + getTotalPaddingTop() + getTotalPaddingBottom());
    }

    private void requestParentIntercept(boolean allow) {
        ViewParent parent = getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(!allow);
            parent = parent.getParent();
        }
    }

    private void drawRound(Canvas canvas, float left, float top, float right, float bottom, Paint paint) {
        rect.set(left, top, right, bottom);
        canvas.drawRoundRect(rect, barSize, barSize, paint);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
