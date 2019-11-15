package com.example.vumeter;

import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;

import static android.opengl.GLES20.GL_FRAMEBUFFER;

import java.nio.IntBuffer;

public class VUMeterRenderer implements GLSurfaceView.Renderer {

    private final Context      mContext;
    private final VUMeterModel mModel;

    private int       mVertexShaderHandle   = 0;
    private int       mFragmentShaderHandle = 0;
    private int       mProgramHandle        = 0;
    private int[]     mTextureHandles       = new int[1];
    private IntBuffer mVertexBuffer         = IntBuffer.allocate(1);
    private IntBuffer mIndexBuffer          = IntBuffer.allocate(1);
    private IntBuffer mColorRenderBuffer    = IntBuffer.allocate(1);
    private IntBuffer mFramebuffer          = IntBuffer.allocate(1);

    private int       mPositionSlot;
    private int       mTexCoordSlot;
    private int       mTextureUniform;

    private int       mWidth;
    private int       mHeight;

    public VUMeterRenderer(Context context)
    {
        mContext = context;
        mModel   = new VUMeterModel(context);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        createShaders();
        createShaderProgram();
        prepareShaders();
        loadTexture();
        setupGL();
    }


    @Override
    public void onDrawFrame(GL10 gl)
    {
        render();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        GLES20.glViewport(0, 0, width, height);
        mWidth  = width;
        mHeight = height;
    }


    private void createShaders() {

        mVertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);

        if (mVertexShaderHandle != 0) {
            GLES20.glShaderSource(mVertexShaderHandle, mModel.mVertexShader);
            GLES20.glCompileShader(mVertexShaderHandle);
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(mVertexShaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
            if (compileStatus[0] == 0) {
                GLES20.glDeleteShader(mVertexShaderHandle);
                mVertexShaderHandle = 0;
            }
        }
        if (mVertexShaderHandle == 0) {
            throw new RuntimeException("Error creating vertex shader.");
        }

        mFragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);

        if (mFragmentShaderHandle != 0) {
            GLES20.glShaderSource(mFragmentShaderHandle, mModel.mFragmentShader);
            GLES20.glCompileShader(mFragmentShaderHandle);
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(mFragmentShaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
            if (compileStatus[0] == 0) {
                GLES20.glDeleteShader(mFragmentShaderHandle);
                mFragmentShaderHandle = 0;
            }
        }
        if (mFragmentShaderHandle == 0) {
            throw new RuntimeException("Error creating fragment shader.");
        }
    }


    private void createShaderProgram() {
        mProgramHandle = GLES20.glCreateProgram();

        if (mProgramHandle != 0) {
            GLES20.glAttachShader(mProgramHandle, mVertexShaderHandle);

            GLES20.glAttachShader(mProgramHandle, mFragmentShaderHandle);
            GLES20.glLinkProgram(mProgramHandle);

            final int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(mProgramHandle, GLES20.GL_LINK_STATUS, linkStatus, 0);

            if (linkStatus[0] == 0) {
                GLES20.glDeleteProgram(mProgramHandle);
                mProgramHandle = 0;
            }
        }
        if (mProgramHandle == 0) {
            throw new RuntimeException("Error creating program.");
        }
    }


    private void prepareShaders() {
        GLES20.glUseProgram(mProgramHandle);

        mPositionSlot = GLES20.glGetAttribLocation(mProgramHandle, "Position");
        mTexCoordSlot = GLES20.glGetAttribLocation(mProgramHandle, "TexCoordIn");

        GLES20.glEnableVertexAttribArray(mPositionSlot);
        GLES20.glEnableVertexAttribArray(mTexCoordSlot);

        mTextureUniform = GLES20.glGetUniformLocation(mProgramHandle, "Texture");
    }


    private void loadTexture() {

        GLES20.glGenTextures(1, mTextureHandles, 0);
        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, mTextureHandles[0]);
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, mModel.mTextureBitmap, 0);

    }


    private void setupGL() {

        GLES20.glGenBuffers        (1, mVertexBuffer      );
        GLES20.glGenBuffers        (1, mIndexBuffer       );
       // GLES20.glGenRenderbuffers  (1, mColorRenderBuffer );
       // GLES20.glGenFramebuffers   (1, mFramebuffer       );

        mVertexBuffer.position      (0);
        mIndexBuffer.position       (0);
        //mColorRenderBuffer.position (0);
        //mFramebuffer.position       (0);

        //GLES20.glBindRenderbuffer( GLES20.GL_RENDERBUFFER, mColorRenderBuffer.get() );
        //mColorRenderBuffer.position(0);

        //GLES20.glBindFramebuffer( GLES20.GL_FRAMEBUFFER, mFramebuffer.get() );
        //mFramebuffer.position(0);
/*
        GLES20.glFramebufferRenderbuffer(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_RENDERBUFFER,
                mColorRenderBuffer.get()
        );
        mColorRenderBuffer.position(0);
*/
    }

    int dummyRMS = 0;

    private void render() {

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVertexBuffer.get());
        mVertexBuffer.position(0);
        GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                mModel.mVerticesByteSize(),
                mModel.mVerticesNative,
                GLES20.GL_STATIC_DRAW
        );

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mIndexBuffer.get());
        mIndexBuffer.position(0);

        GLES20.glBufferData(
                GLES20.GL_ELEMENT_ARRAY_BUFFER,
                mModel.mIndicesByteSize(),
                mModel.mIndicesNative,
                GLES20.GL_STATIC_DRAW
        );

        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_TEXTURE_2D);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glDepthMask(false);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glViewport(0, 0, mWidth, mHeight);

        GLES20.glVertexAttribPointer(mPositionSlot,
                3,
                GLES20.GL_FLOAT,
                false,
                mModel.mVerticesAttribSize(),
                0);

        GLES20.glVertexAttribPointer(mTexCoordSlot,
                2,
                GLES20.GL_FLOAT,
                false,
                mModel.mVerticesAttribSize(),
                4 * 3);

        GLES20.glActiveTexture( GLES20.GL_TEXTURE0 );
        GLES20.glBindTexture  ( GLES20.GL_TEXTURE_2D, mTextureHandles[0] );
        GLES20.glUniform1i    ( mTextureUniform, 0 );

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT,  0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 12);

        if (mModel.peaked()) {
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, 24);
        }
    }
}
