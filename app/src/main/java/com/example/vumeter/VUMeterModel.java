package com.example.vumeter;

import java.lang.Math;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.ByteOrder;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.util.Log;


/** @brief Positions in pixel in vu_meter_texture.png
 * The positions are in the xy integer coordinates
 * of the PNG file where Y's positive direction is
 * downward.
 *
 *           PNG Texture Coordinates
 *           -----------------------
 *
 *  (0,0)                          (TextureWidth, 0)
 *    +---------------------------------------->X
 *    |
 *    |
 *    |  [ Pixels are in this rectangular area ]
 *    |
 *  Y\|/
 *   (0,TextureHeight)
 *
 *
 *  Normalized Texture Coordinates used by OpenGL
 *  ---------------------------------------------
 *
 *                  (0,1)
 *                     ^
 *                     |
 *                     |
 *                     |
 *                     |
 *  (-1,0)        (0,0)|                (1,0)
 *    -----------------+------------------>
 *                     |
 *                     |
 *                     |
 *                     |
 *                     |
 *                   (0,-1)
 */

public class VUMeterModel implements AudioReceiverListener {

    private static final String TAG = VUMeterModel.class.getSimpleName();

    float       mTheta;
    float       mVelocity;
    float       mAccel;
    double      mPrevTime;

    int         mRMS;
    int         mPeak;

    /** @brief Following are points in PNG texture coordinates. */
    Bitmap mTextureBitmap = null;

    final float TextureWidth             = 512.0f;
    final float TextureHeight            = 512.0f;

    final float BaseWidth                = 512.0f;
    final float BaseHeight               = 300.0f;

    final float HandTopLeftX             =   8.0f;
    final float HandTopLeftY             = 313.0f;
    final float HandBottomRightX         = 187.0f;
    final float HandBottomRightY         = 316.0f;
    final float HandRotatingCenterX      = 251.0f;
    final float HandRotatingCenterY      = 288.0f;
    final float HandBottomUprightOnBaseY = 240.0f;

    final float LEDTopLeftOnBaseX        = 414.0f;
    final float LEDTopLeftOnBaseY        = 116.0f;
    final float LEDTopLeftX              = 198.0f;
    final float LEDTopLeftY              = 304.0f;
    final float LEDBottomRightX          = 231.0f;
    final float LEDBottomRightY          = 339.0f;

    final float HandAngularLimitLeft     = (float)Math.PI * 3.0f/4.0f;
    final float HandAngularLimitRight    = (float)Math.PI * 1.0f/4.0f;

    final float AccelerationCoefficient  = 100.0f;
    final float FrictionCoefficient      =  10.0f;
    final float AmplitudeRef             = ((float)Short.MAX_VALUE) / 1.4142135623f;


    final float DynamicRangeFloorDB      = -96.0f;

    // Following three parameters depend on the microphone and the amplifier.
    final short OverloadThreshold        =  32767 -10000;
    final float MicGainCalibFloorDB      = -55.0f;
    final float MicGainCalibPeakDB       =  -0.0f;


    float[] mVertices = new float[ 3 * 4 * 5 ];
    short[] mIndices  = new short[18];

    public FloatBuffer mVerticesNative;
    public ShortBuffer mIndicesNative;
    public int mVerticesByteSize()  { return  3 /*rects*/ * 4 /*points*/ * 5 /*elements*/ * 4/*float*/ ; }
    public int mIndicesByteSize()   { return 18 * 2/*short*/; }
    public int mVerticesAttribSize(){ return  5 * 4; }
    Context     mContext;
    AudioReceiver mReceiver;


    VUMeterModel (Context context) {
        mContext = context;

        mTextureBitmap = BitmapFactory.decodeResource( mContext.getResources(), R.drawable.vu_meter_texture );

        makeInitialVertexCoordinates();
        resetPhysics();

        mReceiver = new AudioReceiver(this);

    }


    public void onUpdateRMS( int rms, int peak )
    {
        mRMS = rms;
        //Log.i(TAG, String.valueOf(mRMS));
        mPeak = peak;

        updatePhysics();
        makeHandVertices();
    }

    boolean peaked() { return mPeak > OverloadThreshold; }


    /**  @brief Converting from PNG texture Coord to Normalized Coord */
    float fromTexCoordToNormCoordX(float x)
    {
        return ( x / BaseWidth ) * 2.0f - 1.0f;
    }


    /** @brief Converting from PNG texture Coord to Normalized Coord */
    float fromTexCoordToNormCoordYInverted(float y)
    {
        return ( y / BaseHeight ) * -2.0f + 1.0f;
    }


    /** @brief Construct the vertices, texture points, and the indices for OpenGL.
     *         Called only once at initialization.
     */

    void makeInitialVertexCoordinates()
    {
        // VU Meter base.
        mVertices[  0 *  5 +  0 ] = fromTexCoordToNormCoordX( BaseWidth );

        mVertices[  0 *  5 +  1 ] = fromTexCoordToNormCoordYInverted( BaseHeight );

        mVertices[  0 *  5 +  2 ] = 0.0f;

        mVertices[  1 *  5 +  0 ] = fromTexCoordToNormCoordX( BaseWidth );

        mVertices[  1 *  5 +  1 ] = fromTexCoordToNormCoordYInverted(0.0f );

        mVertices[  1 *  5 +  2 ] = 0.0f;

        mVertices[  2 *  5 +  0 ] = fromTexCoordToNormCoordX(0.0f );

        mVertices[  2 *  5 +  1 ] = fromTexCoordToNormCoordYInverted( 0.0f );

        mVertices[  2 *  5 +  2 ] = 0.0f;

        mVertices[  3 *  5 +  0 ] = fromTexCoordToNormCoordX(0.0f);

        mVertices[  3 *  5 +  1 ] = fromTexCoordToNormCoordYInverted( BaseHeight );

        mVertices[  3 *  5 +  2 ] = 0.0f;

        // LED
        float LEDWidth  = LEDBottomRightX - LEDTopLeftX;
        float LEDHeight = LEDBottomRightY - LEDTopLeftY;

        //Log.i("VUMETER", "LED: " + String.valueOf(LEDWidth) + "," + String.valueOf(LEDHeight) );

        mVertices[  8 *  5 +  0 ] = fromTexCoordToNormCoordX( LEDTopLeftOnBaseX + LEDWidth );

        mVertices[  8 *  5 +  1 ] = fromTexCoordToNormCoordYInverted(LEDTopLeftOnBaseY + LEDHeight );

        mVertices[  8 *  5 +  2 ] = 0.0f;

        mVertices[  9 *  5 +  0 ] = fromTexCoordToNormCoordX( LEDTopLeftOnBaseX + LEDWidth );

        mVertices[  9 *  5 +  1 ] = fromTexCoordToNormCoordYInverted( LEDTopLeftOnBaseY  );

        mVertices[  9 *  5 +  2 ] = 0.0f;

        mVertices[ 10 *  5 +  0 ] = fromTexCoordToNormCoordX( LEDTopLeftOnBaseX );

        mVertices[ 10 *  5 +  1 ] = fromTexCoordToNormCoordYInverted( LEDTopLeftOnBaseY );

        mVertices[ 10 *  5 +  2 ] = 0.0f;

        mVertices[ 11 *  5 +  0 ] = fromTexCoordToNormCoordX( LEDTopLeftOnBaseX );

        mVertices[ 11 *  5 +  1 ] = fromTexCoordToNormCoordYInverted(LEDTopLeftOnBaseY + LEDHeight );

        mVertices[ 11 *  5 +  2 ] = 0.0f;

        // Base
        mVertices[  0 *  5  + 3 ] = BaseWidth  / TextureWidth;
        mVertices[  0 *  5  + 4 ] = BaseHeight / TextureHeight;
        mVertices[  1 *  5  + 3 ] = BaseWidth  / TextureWidth;
        mVertices[  1 *  5  + 4 ] = 0.0f;
        mVertices[  2 *  5  + 3 ] = 0.0f;
        mVertices[  2 *  5  + 4 ] = 0.0f;
        mVertices[  3 *  5  + 3 ] = 0.0f;
        mVertices[  3 *  5  + 4 ] = BaseHeight / TextureHeight;

        // Indicator
        mVertices[  4 *  5  + 3 ] = HandBottomRightX / TextureWidth;
        mVertices[  4 *  5  + 4 ] = HandBottomRightY / TextureHeight;
        mVertices[  5 *  5  + 3 ] = HandBottomRightX / TextureWidth;
        mVertices[  5 *  5  + 4 ] = HandTopLeftY     / TextureHeight;
        mVertices[  6 *  5  + 3 ] = HandTopLeftX     / TextureWidth;
        mVertices[  6 *  5  + 4 ] = HandTopLeftY     / TextureHeight;
        mVertices[  7 *  5  + 3 ] = HandTopLeftX     / TextureWidth;
        mVertices[  7 *  5  + 4 ] = HandBottomRightY / TextureHeight;

        // LED
        mVertices[  8 *  5  + 3 ] = LEDBottomRightX / TextureWidth;
        mVertices[  8 *  5  + 4 ] = LEDBottomRightY / TextureHeight;
        mVertices[  9 *  5  + 3 ] = LEDBottomRightX / TextureWidth;
        mVertices[  9 *  5  + 4 ] = LEDTopLeftY     / TextureHeight;
        mVertices[ 10 *  5  + 3 ] = LEDTopLeftX     / TextureWidth;
        mVertices[ 10 *  5  + 4 ] = LEDTopLeftY     / TextureHeight;
        mVertices[ 11 *  5  + 3 ] = LEDTopLeftX     / TextureWidth;
        mVertices[ 11 *  5  + 4 ] = LEDBottomRightY / TextureHeight;

        // Indices
        mIndices[ 0] =  0;
        mIndices[ 1] =  1;
        mIndices[ 2] =  2;
        mIndices[ 3] =  2;
        mIndices[ 4] =  3;
        mIndices[ 5] =  0;
        mIndices[ 6] =  4;
        mIndices[ 7] =  5;
        mIndices[ 8] =  6;
        mIndices[ 9] =  6;
        mIndices[10] =  7;
        mIndices[11] =  4;
        mIndices[12] =  8;
        mIndices[13] =  9;
        mIndices[14] = 10;
        mIndices[15] = 10;
        mIndices[16] = 11;
        mIndices[17] =  8;

        ByteBuffer bb1 = ByteBuffer.allocateDirect( mVertices.length * 4 );
        bb1.order(ByteOrder.nativeOrder());
        mVerticesNative = bb1.asFloatBuffer();
        // mVerticesNative.put(mVertices);
        // mVerticesNative.position(0);

        makeHandVertices();

        ByteBuffer bb2 = ByteBuffer.allocateDirect( mIndices.length * 2 );
        bb2.order(ByteOrder.nativeOrder());
        mIndicesNative = bb2.asShortBuffer();
        mIndicesNative.put(mIndices);
        mIndicesNative.position(0);

    }

    /** @brief Construct/Update the vertices, texture points, and the indices
     *         of the hand of the VU meter for OpenGL.
     *         Called at every screen update (at frame rate).
     *         It depends on mTheta, the angle of the hand.
     */
    void makeHandVertices()
    {
        float radiusShort   = HandRotatingCenterY - HandBottomUprightOnBaseY;

        float radiusLong    = radiusShort + HandBottomRightX - HandTopLeftX;

        float handHalfWidth = ( HandBottomRightY - HandTopLeftY ) * 0.5f;

        float cosTheta = (float)Math.cos( mTheta );
        float sinTheta = (float)Math.sin( mTheta );

        float posBottomCenterX = HandRotatingCenterX + cosTheta * radiusShort;
        float posBottomCenterY = HandRotatingCenterY - sinTheta * radiusShort;
        float posTopCenterX    = HandRotatingCenterX + cosTheta * radiusLong;
        float posTopCenterY    = HandRotatingCenterY - sinTheta * radiusLong;

        float offsetFromCenterToTopLeftX = handHalfWidth * -1.0f * sinTheta;
        float offsetFromCenterToTopLeftY = handHalfWidth * cosTheta;

        mVertices[  4 *  5 +  0 ] = fromTexCoordToNormCoordX(posTopCenterX + offsetFromCenterToTopLeftX );

        mVertices[  4 *  5 +  1 ] = fromTexCoordToNormCoordYInverted(posTopCenterY - offsetFromCenterToTopLeftY );

        mVertices[  4 *  5 +  2 ] = 0.0f;

        mVertices[  5 *  5 +  0 ] = fromTexCoordToNormCoordX( posTopCenterX - offsetFromCenterToTopLeftX );

        mVertices[  5 *  5 +  1 ] = fromTexCoordToNormCoordYInverted( posTopCenterY + offsetFromCenterToTopLeftY );

        mVertices[  5 *  5 +  2 ] = 0.0f;

        mVertices[  6 *  5 +  0 ] = fromTexCoordToNormCoordX(posBottomCenterX + offsetFromCenterToTopLeftX );

        mVertices[  6 *  5 +  1 ] = fromTexCoordToNormCoordYInverted(posBottomCenterY - offsetFromCenterToTopLeftY );

        mVertices[  6 *  5 +  2 ] = 0.0f;

        mVertices[  7 *  5 +  0 ] = fromTexCoordToNormCoordX(posBottomCenterX - offsetFromCenterToTopLeftX  );

        mVertices[  7 *  5 +  1 ] = fromTexCoordToNormCoordYInverted( posBottomCenterY + offsetFromCenterToTopLeftY );

        mVertices[  7 *  5 +  2 ] = 0.0f;

        mVerticesNative.put(mVertices);
        mVerticesNative.position(0);

    }

    void resetPhysics()
    {
        mRMS      = 0;
        mPeak     = 0;
        mTheta    = HandAngularLimitLeft;
        mVelocity = 0.0f;
        mAccel    = 0.0f;
        mPrevTime = 0.0f;
    }


    void updatePhysics()
    {
        double currentTime = ((double)System.currentTimeMillis()) / 1000.0;
        //Log.i("VUMETER", "cuurrent time: " + String.valueOf(currentTime) + " " + String.valueOf(mPrevTime));
        double dt;

        if ( mPrevTime == 0.0 ) {

            dt = 0.01;
        }
        else{

            dt = currentTime - mPrevTime;
        }

        mPrevTime = currentTime;

        if ( mRMS < 1 ) {
            mRMS = 1;
        }

        // The range of micDB is expected to be in
        // [ MicGainCalibFloorDB ,MicGainCalibPeakDB ].

        float micDB = (float)(20.0 * Math.log10( ( (float)mRMS ) / AmplitudeRef ));
        //Log.i("VUMETER", "micDB: " + String.valueOf(micDB));
        float targetTheta = HandAngularLimitLeft
                + ( HandAngularLimitRight - HandAngularLimitLeft )
                * ( micDB - MicGainCalibFloorDB )
                / ( MicGainCalibPeakDB - MicGainCalibFloorDB );


        mAccel = AccelerationCoefficient * ( targetTheta - mTheta )
                - FrictionCoefficient * mVelocity;

        mVelocity = mVelocity + mAccel * (float)dt;
        mTheta    = mTheta + mVelocity * (float)dt;
        if ( mTheta > HandAngularLimitLeft ) {

            mTheta = HandAngularLimitLeft;
            mVelocity = 0.0f;
        }

        if ( mTheta < HandAngularLimitRight ) {

            mTheta = HandAngularLimitRight;
            mVelocity = 0.0f;
        }
    }

    final String mVertexShader =
          "attribute vec4 Position;\n"
        + "attribute vec2 TexCoordIn;\n"
        + "varying   vec2 TexCoordOut;\n"
        + "\n"
        + "void main (void) {\n"
        + "\n"
        + "    gl_Position = Position;\n"
        + "    TexCoordOut = TexCoordIn;\n"
        + "\n"
        + "}\n";

    final String mFragmentShader =
          "varying lowp vec2 TexCoordOut;\n"
        + "uniform sampler2D Texture;\n"
        + "\n"
        + "void main (void) {\n"
        + "\n"
        + "    gl_FragColor = texture2D( Texture, TexCoordOut );\n"
        + "}\n";


}
