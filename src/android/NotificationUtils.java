package cn.wiskind.lbs;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Build;

/**
 * 通知工具
 */
public class NotificationUtils extends ContextWrapper {

    /**
     * 单例
     */
    private static NotificationUtils instance;

    /**
     * Android 渠道名称
     */
    public static final String ANDROID_CHANNEL_NAME = "ANDROID CHANNEL";

    /**
     * Android 渠道 ID
     */
    public static String ANDROID_CHANNEL_ID;

    /**
     * 应用名称
     */
    public static CharSequence APP_NAME;

    /**
     * 通知管理器
     */
    private NotificationManager mManager;

    /**
     * 构造方法
     *
     * @param base 基础上下文
     */
    private NotificationUtils(Context base) {
        super(base);
        ApplicationInfo info = getApplicationInfo();
        // 获取 Android 渠道 ID
        ANDROID_CHANNEL_ID = info.packageName;
        // 获取应用名称
        APP_NAME = info.loadLabel(getPackageManager());
        // 创建渠道
        createChannels();
    }

    /**
     * 获取单例
     *
     * @param context 上下文
     * @return 实例
     */
    public static synchronized NotificationUtils getInstance(Context context) {
        if (instance == null) {
            instance = new NotificationUtils(context);
        }
        return instance;
    }

    /**
     * 创建渠道
     */
    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { // Android 8.0
            return;
        }
        // create android channel
        NotificationChannel androidChannel = new NotificationChannel(ANDROID_CHANNEL_ID, ANDROID_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        // Sets whether notifications posted to this channel should display notification lights
        androidChannel.enableLights(true);
        // Sets whether notification posted to this channel should vibrate.
        androidChannel.enableVibration(true);
        // Sets the notification light color for notifications posted to this channel
        androidChannel.setLightColor(Color.GREEN);
        // Sets whether notifications posted to this channel appear on the lockscreen or not
        androidChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        getManager().createNotificationChannel(androidChannel);
    }

    /**
     * 获取通知管理器
     */
    private NotificationManager getManager() {
        if (mManager == null) {
            mManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return mManager;
    }

    /**
     * 获取通知
     */
    public Notification getNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android 8.0
            builder = new Notification.Builder(getApplicationContext(), ANDROID_CHANNEL_ID);
        } else {
            //noinspection deprecation
            builder = new Notification.Builder(getApplicationContext());
        }
        Intent nfIntent = new Intent(getApplicationContext(), getBaseContext().getClass());
        builder.setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, nfIntent, 0))
                .setContentTitle("正在定位")
                .setContentText(APP_NAME)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setWhen(System.currentTimeMillis());
        return builder.build();
    }
}
