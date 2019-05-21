package cn.wiskind.lbs;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.media.MediaPlayer;

/**
 * 声音工具
 */
public class SoundUtils extends ContextWrapper {

    /**
     * 单例
     */
    private static SoundUtils instance;

    /**
     * 定位数据错误播放器
     */
    private MediaPlayer gpsWeakPlayer;

    /**
     * 静默播放器
     */
    private MediaPlayer silencePlayer;

    /**
     * 构造方法
     *
     * @param base 基础上下文
     */
    private SoundUtils(Context base) {
        super(base);

        Context context = getApplicationContext();
        String packageName = context.getPackageName();
        Resources resources = context.getResources();

        int silenceId = resources.getIdentifier("silence", "raw", packageName);
        int gpsWeakId = resources.getIdentifier("gps_weak", "raw", packageName);

        silencePlayer = MediaPlayer.create(getApplicationContext(), silenceId);
        silencePlayer.setLooping(true);
        silencePlayer.setVolume(0, 0);

        gpsWeakPlayer = MediaPlayer.create(getApplicationContext(), gpsWeakId);
        gpsWeakPlayer.setVolume(1, 1);
    }

    /**
     * 获取单例
     *
     * @param context 上下文
     * @return 实例
     */
    public static synchronized SoundUtils getInstance(Context context) {
        if (instance == null) {
            instance = new SoundUtils(context);
        }
        return instance;
    }

    public void start() {
        if (!silencePlayer.isPlaying()) {
            silencePlayer.start();
        }
    }

    public void pause() {
        if (silencePlayer.isPlaying()) {
            silencePlayer.pause();
        }
    }

    public void warn() {
        gpsWeakPlayer.start();
    }
}
