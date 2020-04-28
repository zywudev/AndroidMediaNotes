package com.wuzy.androidmultimedialearning.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.wuzy.androidmultimedialearning.util.ThreadHelper;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 播放录音类
 */
public class AudioTracker {

    private static final String TAG = "AudioTracker";
    // 采样率 44100Hz，所有设备都支持
    private final static int SAMPLE_RATE = 44100;
    // 单声道，所有设备都支持
    private final static int CHANNEL = AudioFormat.CHANNEL_OUT_MONO;
    // 位深 16 位，所有设备都支持
    private final static int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // 缓冲区字节大小
    private int mBufferSizeInBytes = 0;
    // 播放对象
    private AudioTrack mAudioTrack;
    // 文件名
    private String mFilePath;
    // 状态
    private volatile Status mStatus = Status.STATUS_NO_READY;

    private Context mContext;

    public AudioTracker(Context context) {
        mContext = context;
    }

    public void createAudioTrack(String filePath) throws IllegalStateException {
        mFilePath = filePath;
        mBufferSizeInBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL, AUDIO_FORMAT);
        if (mBufferSizeInBytes <= 0) {
            throw new IllegalStateException("AudioTrack is not available " + mBufferSizeInBytes);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAudioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(mBufferSizeInBytes)
                    .build();
        } else {
            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, CHANNEL, AUDIO_FORMAT,
                    mBufferSizeInBytes, AudioTrack.MODE_STREAM);
        }
        mStatus = Status.STATUS_READY;
    }

    /**
     * 开始播放
     */
    public void start() throws IllegalStateException {
        if (mStatus == Status.STATUS_NO_READY || mAudioTrack == null) {
            throw new IllegalStateException("播放器尚未初始化");
        }
        if (mStatus == Status.STATUS_START) {
            throw new IllegalStateException("正在播放...");
        }
        Log.d(TAG, "===start===");

        ThreadHelper.getInstance().execute(() -> {
            try {
                playAudioData();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                ThreadHelper.getInstance().runOnUiThread(() -> {
                    Toast.makeText(mContext, "播放出错", Toast.LENGTH_SHORT).show();
                });
            }
        });

        mStatus = Status.STATUS_START;
    }

    private void playAudioData() throws IOException {
        InputStream dis = null;
        try {
            ThreadHelper.getInstance().runOnUiThread(() -> {
                Toast.makeText(mContext, "播放开始", Toast.LENGTH_SHORT).show();
            });
            dis = new DataInputStream(new BufferedInputStream(new FileInputStream(mFilePath)));
            byte[] bytes = new byte[mBufferSizeInBytes];
            int length;
            mAudioTrack.play();
            // write 是阻塞的方法
            while ((length = dis.read(bytes)) != -1 && mStatus == Status.STATUS_START) {
                mAudioTrack.write(bytes, 0, length);
            }
            ThreadHelper.getInstance().runOnUiThread(() -> {
                Toast.makeText(mContext, "播放结束", Toast.LENGTH_SHORT).show();
            });
        } finally {
            if (dis != null) {
                dis.close();
            }
        }
    }

    public void stop() throws IllegalStateException {
        Log.d(TAG, "===stop===");
        if (mStatus == Status.STATUS_NO_READY || mStatus == Status.STATUS_READY) {
            throw new IllegalStateException("播放尚未开始");
        } else {
            mStatus = Status.STATUS_STOP;
            mAudioTrack.stop();
            release();
        }
    }

    public void release() {
        Log.d(TAG, "==release===");
        mStatus = Status.STATUS_NO_READY;
        if (mAudioTrack != null) {
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }

    /**
     * 播放对象的状态
     */
    public enum Status {
        //未开始
        STATUS_NO_READY,
        //预备
        STATUS_READY,
        //播放
        STATUS_START,
        //停止
        STATUS_STOP
    }

}
