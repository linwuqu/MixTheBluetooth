package com.hc.basiclibrary.titleBasic;

import android.content.Context;
import android.util.AttributeSet;


import androidx.annotation.Nullable;

public class RotateTextView extends androidx.appcompat.widget.AppCompatTextView {
    public RotateTextView(Context context) {
        super(context);
    }

    public RotateTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public RotateTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean isFocused() {
        // TODO Auto-generated method stub
        return true;
    }

}
