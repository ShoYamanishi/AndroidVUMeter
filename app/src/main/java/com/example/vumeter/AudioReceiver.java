package com.example.vumeter;

import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.MediaRecorder;

import static androidx.constraintlayout.widget.Constraints.TAG;
import static java.lang.Integer.max;
import static java.lang.Math.abs;

import android.util.Log;
import android.os.Handler;
import android.os.Looper;

public class AudioReceiver {

    private static final int RECORDING_RATE = 8000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;


    private AudioRecord recorder;

    private Thread mReceivingThread;

    private static int BUFFER_SIZE;
    short[] buffer;

    int mRMS;
    int mPeak;

    AudioReceiverListener mListener;


    AudioReceiver(AudioReceiverListener listener) {
       mListener = listener;
       receiveAndCalc();
    }

    void receiveAndCalc() {
        BUFFER_SIZE = AudioRecord.getMinBufferSize( RECORDING_RATE, CHANNEL, FORMAT );
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDING_RATE, CHANNEL, FORMAT, BUFFER_SIZE * 10);
        buffer = new short[BUFFER_SIZE];
        recorder.startRecording();

        mReceivingThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Handler uiHandler = new Handler(Looper.getMainLooper());
                    while (true) {
                        int length_read = recorder.read(buffer, 0, buffer.length);
                        calcRMS( buffer, length_read );
                        if (mListener != null) {
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mListener.onUpdateRMS(mRMS, mPeak);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception: " + e);
                }
            }
        });

        mReceivingThread.start();

    }

    void calcRMS( short[] buffer, int length ) {
        double accumAbs = 0.0;
        mPeak = 0;
        for (int i = 0; i < length; i++) {
            int v = abs((int)buffer[i]);
            if ( mPeak < v ) {
                mPeak = v;
            }
            double val = (double) buffer[i];
            accumAbs += (val * val);
        }
        mRMS = (int) Math.sqrt((accumAbs / (double) length));
    }
}
