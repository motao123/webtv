package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.fongmi.android.tv.R;

public class CustomNestedScrollView extends NestedScrollView {

    private int minHeight;
    private int maxHeight;

    public CustomNestedScrollView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public CustomNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CustomNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        setOverScrollMode(OVER_SCROLL_NEVER);
        if (attrs == null) return;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CustomNestedScrollView);
        minHeight = a.getDimensionPixelSize(R.styleable.CustomNestedScrollView_android_minHeight, 0);
        maxHeight = a.getDimensionPixelSize(R.styleable.CustomNestedScrollView_maxHeight, 0);
        a.recycle();
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeight > 0) heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (minHeight > 0 && getMeasuredHeight() < minHeight) setMeasuredDimension(getMeasuredWidth(), minHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        try {
            super.onSizeChanged(w, h, oldw, oldh);
        } catch (IllegalArgumentException e) {
            View focus = findFocus();
            if (isDescendant(focus)) throw e;
            if (focus != null) focus.clearFocus();
        }
    }

    private boolean isDescendant(View view) {
        if (view == null || view == this) return true;
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (parent == this) return true;
            parent = parent.getParent();
        }
        return false;
    }
}
