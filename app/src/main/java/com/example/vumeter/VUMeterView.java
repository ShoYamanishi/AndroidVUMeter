package com.example.vumeter;

import android.opengl.GLSurfaceView;

import android.content.Context;
import android.util.AttributeSet;


public class VUMeterView extends GLSurfaceView {


    public VUMeterView(Context context)
    {
        this(context, null);
    }

    public VUMeterView(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    public VUMeterView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs);

        setEGLContextClientVersion(2);

        setRenderer(new VUMeterRenderer(context));
    }

}
