package cn.wiskind.lbs;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.widget.Toast;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class WiskindLBS extends CordovaPlugin {

    /**
     * 地球半径
     */
    private static final double ER = 6370996.81;

    /**
     * 角度换算弧度比率
     */
    private static final double AR = Math.PI / 180;

    /**
     * 权限请求码
     */
    private static final int PERMISSION_REQUEST_CODE = 10001;

    /**
     * 通知标识
     */
    private static final int NOTIFICATION_ID = 1001;

    /**
     * 时间过滤键名:int（秒）
     */
    private static final String KEY_TIME_FILTER = "timeFilter";

    /**
     * 距离过滤键名:int（米）
     */
    private static final String KEY_DISTANCE_FILTER = "distanceFilter";

    /**
     * 正在定位键名:boolean
     */
    private static final String KEY_WLBS_LOCATING = "wlbs_locating";

    /**
     * 定位服务时间过滤键名:int（秒）
     */
    private static final String KEY_WLBS_TIME_FILTER = "wlbs_time_filter";

    /**
     * 定位服务距离过滤键名:int（米）
     */
    private static final String KEY_WLBS_DISTANCE_FILTER = "wlbs_distance_filter";

    /**
     * 定位服务静音模式（不进行语音提醒）:boolean
     */
    private static final String KEY_WLBS_MUTE = "wlbs_mute";

    /**
     * 上下文
     */
    private Context context;

    /**
     * 应用名称
     */
    private CharSequence appName;

    /**
     * 偏好设置
     */
    private SharedPreferences preferences;

    /**
     * 百度连续定位客户端
     */
    private static LocationClient continuousLocationClient;

    /**
     * 百度单次定位客户端
     */
    private static LocationClient singleLocationClient;

    /**
     * 百度定位客户端配置
     */
    private LocationClientOption locationClientOption;

    /**
     * 最后一次存储的纬度
     */
    private double lastStoredLatitude;

    /**
     * 最后一次存储的经度
     */
    private double lastStoredLongitude;

    /**
     * 距离过滤
     */
    private int distanceFilter;

    /**
     * 定位回调上下文
     */
    private CallbackContext locateCallbackContext;

    /**
     * 异步操作
     */
    private String asyncAction;

    /**
     * 异步参数
     */
    private JSONArray asyncArgs;

    /**
     * 异步回调上下文
     */
    private CallbackContext asyncCallbackContext;

    /**
     * 通知工具
     */
    private NotificationUtils notificationUtils;

    /**
     * 声音工具
     */
    private SoundUtils soundUtils;

    /**
     * 数据库工具
     */
    private LBSDbHelper dbHelper;

    /**
     * 运行日志定时器
     */
    private Timer runlogTimer;

    /**
     * 百度连续定位监听
     */
    private BDAbstractLocationListener continuousLocationListener = new BDAbstractLocationListener() {
        @Override
        public void onReceiveLocation(BDLocation bdLocation) {
            // 此处的BDLocation为定位结果信息类，通过它的各种get方法可获取定位相关的全部结果
            // 以下只列举部分获取经纬度相关（常用）的结果信息
            // 更多结果信息获取说明，请参照类参考中BDLocation类中的说明

            // 获取定位类型、定位错误返回码，具体信息可参照类参考中BDLocation类中的说明
            int errorCode = bdLocation.getLocType();

            // 61	GPS定位结果，GPS定位成功
            // 161	网络定位结果，网络定位成功
            // 68	网络连接失败时，查找本地离线定位时对应的返回结果

            if (61 == errorCode) {
                double latitude = bdLocation.getLatitude();
                double longitude = bdLocation.getLongitude();
                if (validDistance(latitude, longitude)) {
                    store(latitude, longitude);
                    lastStoredLatitude = latitude;
                    lastStoredLongitude = longitude;
                }
            } else {
                boolean mute = preferences.getBoolean(KEY_WLBS_MUTE, false);
                if (!mute) {
                    soundUtils.warn();
                }
                Toast.makeText(context, appName + "GPS信号弱", Toast.LENGTH_SHORT).show();
            }
        }
    };

    /**
     * 百度单次定位监听
     */
    private BDAbstractLocationListener singleLocationListener = new BDAbstractLocationListener() {
        @Override
        public void onReceiveLocation(BDLocation bdLocation) {
            // 此处的BDLocation为定位结果信息类，通过它的各种get方法可获取定位相关的全部结果
            // 以下只列举部分获取经纬度相关（常用）的结果信息
            // 更多结果信息获取说明，请参照类参考中BDLocation类中的说明

            // 获取定位类型、定位错误返回码，具体信息可参照类参考中BDLocation类中的说明
            int errorCode = bdLocation.getLocType();

            // 61	GPS定位结果，GPS定位成功
            // 161	网络定位结果，网络定位成功
            // 68	网络连接失败时，查找本地离线定位时对应的返回结果

            if (61 == errorCode || 161 == errorCode || 68 == errorCode) {
                double latitude = bdLocation.getLatitude();
                double longitude = bdLocation.getLongitude();
                callbackLocate(locateCallbackContext, latitude, longitude);
            } else {
                locateCallbackContext.error("定位数据错误");
            }
            singleLocationClient.stop();
        }
    };

    /**
     * 计算两点之间的直线距离，单位：米
     *
     * @param lat1 点1纬度
     * @param lng1 点1经度
     * @param lat2 点2纬度
     * @param lng2 点2经度
     * @return 距离，单位：米
     */
    private static double getDistance(double lat1, double lng1, double lat2, double lng2) {
        return ER * Math.acos(Math.cos(lat1 * AR) * Math.cos(lat2 * AR) * Math.cos(lng1 * AR - lng2 * AR) + Math.sin(lat1 * AR) * Math.sin(lat2 * AR));
    }

    /**
     * 插件初始化
     */
    @Override
    protected void pluginInitialize() {
        // 获取上下文
        context = cordova.getActivity();
        // 应用信息
        ApplicationInfo info = context.getApplicationInfo();
        // 获取应用名称
        appName = info.loadLabel(context.getPackageManager());
        // 初始化通知工具
        notificationUtils = NotificationUtils.getInstance(context);
        // 初始化声音工具
        soundUtils = SoundUtils.getInstance(context);
        // 初始化数据库工具
        dbHelper = LBSDbHelper.getInstance(context);
        // 计算偏好设置键名
        String preferenceKey = info.packageName + ".PREFERENCE_WISKIND_LBS_KEY";
        // 获取偏好设置
        preferences = cordova.getActivity().getSharedPreferences(preferenceKey, Context.MODE_PRIVATE);

        // 删除30天前的运行日志
        try { deleteRunlog(); } catch (Exception e) { }
        // 启动运行日志定时器
        if (runlogTimer == null) {
            runlogTimer = new Timer();
            runlogTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    storeRunlog();
                }
            }, 60000, 60000);
        }

        // 获取正在定位状态
        boolean locating = preferences.getBoolean(KEY_WLBS_LOCATING, false);

        if (locating) {
            int timeFilter = preferences.getInt(KEY_WLBS_TIME_FILTER, -1);
            int distanceFilter = preferences.getInt(KEY_WLBS_DISTANCE_FILTER, -1);
            if (timeFilter > 0 && distanceFilter > 0) {
                this.distanceFilter = distanceFilter;
                if (isUninitialized()) {
                    initContinuousLocationClient();
                }
                if (!isStarted()) {
                    begin(timeFilter);
                }
            } else {
                SharedPreferences.Editor editor = preferences.edit();
                editor.remove(KEY_WLBS_LOCATING);
                editor.remove(KEY_WLBS_TIME_FILTER);
                editor.remove(KEY_WLBS_DISTANCE_FILTER);
                editor.apply();
            }
        }
    }

    /**
     * 执行
     *
     * @param action          操作
     * @param args            参数
     * @param callbackContext 回调上下文
     * @return 是否有效操作
     * @throws JSONException JSON异常
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (!",start,stop,isStarted,locate,mute,ismute,".contains(action)) {
            return false;
        }
        if (action.equals("mute")) {
            if (args.isNull(0)) {
                callbackContext.error("缺少参数");
                return true;
            }
            boolean mute = args.getBoolean(0);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(KEY_WLBS_MUTE, mute);
            if (editor.commit()) {
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("mute", mute);
                callbackContext.success(new JSONObject(map));
            } else {
                callbackContext.error("静音模式切换失败");
            }
            return true;
        }
        if (action.equals("ismute")) {
            boolean mute = preferences.getBoolean(KEY_WLBS_MUTE, false);
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("mute", mute);
            callbackContext.success(new JSONObject(map));
            return true;
        }
        if (needsToAlertForRuntimePermission()) {
            asyncAction = action;
            asyncArgs = args;
            asyncCallbackContext = callbackContext;
            requestPermission();
        } else {
            asyncExecute(action, args, callbackContext);
        }
        return true;
    }

    @Override
    public void onDestroy() {
        runlogTimer.cancel();
        runlogTimer = null;
    }

    /**
     * 异步执行
     *
     * @param action          操作
     * @param args            参数
     * @param callbackContext 回调上下文
     * @throws JSONException JSON异常
     */
    private void asyncExecute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (isUninitialized()) {
            initContinuousLocationClient();
        }
        if (action.equals("start")) {
            JSONObject json = args.getJSONObject(0);
            int timeFilter = json.getInt(KEY_TIME_FILTER);
            distanceFilter = json.getInt(KEY_DISTANCE_FILTER);
            this.actionStart(timeFilter, callbackContext);
        } else if (action.equals("stop")) {
            this.actionStop(callbackContext);
        } else if (action.equals("isStarted")) {
            this.actionIsStarted(callbackContext);
        } else if (action.equals("locate")) {
            this.actionLocate(callbackContext);
        }
    }

    /**
     * 启动操作
     *
     * @param timeFilter      时间过滤，单位：秒
     * @param callbackContext 回调上下文
     */
    private void actionStart(int timeFilter, CallbackContext callbackContext) {
        try {
            ToggleResult toggleResult = start(timeFilter);
            switch (toggleResult) {
                case success:
                    callbackContext.success();
                    break;
                case already:
                    callbackContext.error("已经启动了，无法再次执行");
                    break;
                case unsaved:
                    callbackContext.error("保存启动状态失败");
                    break;
            }
        } catch (Exception e) {
            parseException(e, callbackContext);
        }
    }

    /**
     * 停止操作
     *
     * @param callbackContext 回调上下文
     */
    private void actionStop(CallbackContext callbackContext) {
        try {
            ToggleResult toggleResult = stop();
            switch (toggleResult) {
                case success:
                    callbackContext.success();
                    break;
                case already:
                    callbackContext.error("已经停止了，无法再次执行");
                    break;
                case unsaved:
                    callbackContext.error("保存停止状态失败");
                    break;
            }
        } catch (Exception e) {
            parseException(e, callbackContext);
        }
    }

    /**
     * 是否已经启动
     *
     * @param callbackContext 回调上下文
     */
    private void actionIsStarted(CallbackContext callbackContext) {
        boolean isStarted = continuousLocationClient != null && continuousLocationClient.isStarted();
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("isStarted", isStarted);
        callbackContext.success(new JSONObject(map));
    }

    /**
     * 定位操作
     *
     * @param callbackContext 回调上下文
     */
    private void actionLocate(CallbackContext callbackContext) {
        try {
            locate(callbackContext);
        } catch (Exception e) {
            parseException(e, callbackContext);
        }
    }

    /**
     * 初始化百度连续定位客户端
     */
    private void initContinuousLocationClient() {
        // 创建百度定位客户端
        continuousLocationClient = new LocationClient(context);
        // 注册百度定位监听
        continuousLocationClient.registerLocationListener(continuousLocationListener);
    }

    /**
     * 初始化设置百度单次定位客户端
     */
    private void initSingleLocationClient() {
        // 初始化示例
        singleLocationClient = new LocationClient(context);
        // 注册百度定位监听
        singleLocationClient.registerLocationListener(singleLocationListener);

        LocationClientOption option = new LocationClientOption();

        // 可选，设置定位模式，默认高精度
        // LocationMode.Hight_Accuracy：高精度；
        // LocationMode. Battery_Saving：低功耗；
        // LocationMode. Device_Sensors：仅使用设备；
        // option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);

        // 可选，设置返回经纬度坐标类型，默认gcj02
        // gcj02：国测局坐标；
        // bd09ll：百度经纬度坐标；
        // bd09：百度墨卡托坐标；
        // 海外地区定位，无需设置坐标类型，统一返回wgs84类型坐标
        option.setCoorType("bd09ll");

        // 可选，设置发起定位请求的间隔，int类型，单位ms
        // 如果设置为0，则代表单次定位，即仅定位一次，默认为0
        // 如果设置非0，需设置1000ms以上才有效
        // option.setScanSpan(1000);

        // 可选，设置是否使用gps，默认false
        // 使用高精度和仅用设备两种定位模式的，参数必须设置为true
        // option.setOpenGps(true);

        // 可选，设置是否当GPS有效时按照1S/1次频率输出GPS结果，默认false
        // option.setLocationNotify(true);

        // 可选，定位SDK内部是一个service，并放到了独立进程。
        // 设置是否在stop的时候杀死这个进程，默认（建议）不杀死，即setIgnoreKillProcess(true)
        // option.setIgnoreKillProcess(false);

        // 可选，设置是否收集Crash信息，默认收集，即参数为false
        // option.SetIgnoreCacheException(false);

        // 可选，7.2版本新增能力
        // 如果设置了该接口，首次启动定位时，会先判断当前WiFi是否超出有效期，若超出有效期，会先重新扫描WiFi，然后定位
        option.setWifiCacheTimeOut(300000); // 5 * 60 * 1000

        // 可选，设置是否需要过滤GPS仿真结果，默认需要，即参数为false
        // option.setEnableSimulateGps(false);

        singleLocationClient.setLocOption(option);
    }

    /**
     * 启动
     *
     * @param timeFilter 时间过滤，单位：秒
     * @return 启停切换结果
     */
    private ToggleResult start(int timeFilter) {
        if (isStarted()) {
            return ToggleResult.already;
        }
        begin(timeFilter);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(KEY_WLBS_LOCATING, true);
        editor.putInt(KEY_WLBS_TIME_FILTER, timeFilter);
        editor.putInt(KEY_WLBS_DISTANCE_FILTER, distanceFilter);
        if (editor.commit()) {
            return ToggleResult.success;
        } else {
            end();
            return ToggleResult.unsaved;
        }
    }

    /**
     * 停止
     *
     * @return 启停切换结果
     */
    private ToggleResult stop() {
        if (isStarted()) {
            end();
            SharedPreferences.Editor editor = preferences.edit();
            editor.remove(KEY_WLBS_LOCATING);
            editor.remove(KEY_WLBS_TIME_FILTER);
            editor.remove(KEY_WLBS_DISTANCE_FILTER);
            if (editor.commit()) {
                return ToggleResult.success;
            } else {
                begin();
                return ToggleResult.unsaved;
            }
        } else {
            return ToggleResult.already;
        }
    }

    /**
     * 定位，获取当前位置
     *
     * @param callbackContext 回调上下文
     */
    private void locate(CallbackContext callbackContext) {
        if (singleLocationClient == null) {
            initSingleLocationClient();
        }
        if (singleLocationClient.isStarted()) {
            callbackContext.error("定位被占用，请稍后再试");
        } else {
            singleLocationClient.start();
            locateCallbackContext = callbackContext;
        }
    }

    /**
     * 开始
     */
    private void begin() {
        continuousLocationClient.start();
        continuousLocationClient.enableLocInForeground(NOTIFICATION_ID, notificationUtils.getNotification());
        soundUtils.start();
    }

    /**
     * 开始
     *
     * @param timeFilter 时间过滤，单位：秒
     */
    private void begin(int timeFilter) {
        setLocationClientOption(timeFilter * 1000);
        begin();
    }

    /**
     * 结束
     */
    private void end() {
        soundUtils.pause();
        continuousLocationClient.disableLocInForeground(true);
        continuousLocationClient.stop();
    }

    /**
     * 存储
     *
     * @param latitude  纬度
     * @param longitude 经度
     */
    private void store(double latitude, double longitude) {
        SQLiteDatabase wdb = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(LBSCont.LocEntry.CN_LAT, latitude);
        values.put(LBSCont.LocEntry.CN_LNG, longitude);
        values.put(LBSCont.LocEntry.CN_CDATE, System.currentTimeMillis());
        wdb.insert(LBSCont.LocEntry.TN, null, values);
    }

    /**
     * 存储运行日志
     */
    private void storeRunlog() {
        SQLiteDatabase rdb = dbHelper.getReadableDatabase();
        Cursor cursor = rdb.query(
                LBSCont.LocEntry.TN,
                new String[] { "COUNT(*) > 0" },
                LBSCont.LocEntry.CN_CDATE + ">?",
                new String[]{ String.valueOf(System.currentTimeMillis() - 60000) },
                null, null, null
        );
        cursor.moveToFirst();
        int success = cursor.getInt(0);

        SQLiteDatabase wdb = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(LBSCont.RunLogEntry.CN_TIME, System.currentTimeMillis());
        values.put(LBSCont.RunLogEntry.CN_SUCCESS, success);
        wdb.insert(LBSCont.RunLogEntry.TN, null, values);
    }

    /**
     * 删除30天前的运行日志
     */
    private void deleteRunlog() {
        SQLiteDatabase wdb = dbHelper.getWritableDatabase();
        wdb.delete(
                LBSCont.RunLogEntry.TN,
                LBSCont.RunLogEntry.CN_TIME + " < ?",
                new String[]{ String.valueOf(System.currentTimeMillis() - 2592000000L) }
        );
    }

    /**
     * 是否需要提示请求运行时权限
     *
     * @return 真，如果需要权限
     */
    private boolean needsToAlertForRuntimePermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !(
                cordova.hasPermission(Manifest.permission.READ_PHONE_STATE)
                        && cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        && cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        && cordova.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                        && cordova.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        );
    }

    /**
     * 请求权限
     */
    private void requestPermission() {
        ArrayList<String> permissions = new ArrayList<String>();

        if (!cordova.hasPermission(Manifest.permission.READ_PHONE_STATE))
            permissions.add(Manifest.permission.READ_PHONE_STATE);

        if (!cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (!cordova.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);

        if (!cordova.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE))
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);

        if (!cordova.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        String[] permissionArray = new String[permissions.size()];
        permissionArray = permissions.toArray(permissionArray);
        cordova.requestPermissions(this, PERMISSION_REQUEST_CODE, permissionArray);
    }

    /**
     * 请求权限结果
     *
     * @param requestCode  请求码
     * @param permissions  权限数组
     * @param grantResults 授权结果
     * @throws JSONException JSON异常
     */
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (requestCode != PERMISSION_REQUEST_CODE) {
            return;
        }
        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                asyncCallbackContext.error("应用权限被拒绝");
                return;
            }
        }
        asyncExecute(asyncAction, asyncArgs, asyncCallbackContext);
    }

    /**
     * 获取百度定位客户端配置
     *
     * @param scanSpan 发起定位请求的间隔，单位：毫秒
     * @return 百度定位客户端配置
     */
    private LocationClientOption getLocationClientOption(int scanSpan) {

        if (locationClientOption != null) {
            locationClientOption.setScanSpan(scanSpan);
            return locationClientOption;
        }

        locationClientOption = new LocationClientOption();

        // 可选，设置定位模式，默认高精度
        // LocationMode.Hight_Accuracy：高精度；
        // LocationMode. Battery_Saving：低功耗；
        // LocationMode. Device_Sensors：仅使用设备；
        // option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);

        // 可选，设置返回经纬度坐标类型，默认gcj02
        // gcj02：国测局坐标；
        // bd09ll：百度经纬度坐标；
        // bd09：百度墨卡托坐标；
        // 海外地区定位，无需设置坐标类型，统一返回wgs84类型坐标
        locationClientOption.setCoorType("bd09ll");

        // 可选，设置发起定位请求的间隔，int类型，单位ms
        // 如果设置为0，则代表单次定位，即仅定位一次，默认为0
        // 如果设置非0，需设置1000ms以上才有效
        locationClientOption.setScanSpan(scanSpan);

        // 可选，设置是否使用gps，默认false
        // 使用高精度和仅用设备两种定位模式的，参数必须设置为true
        locationClientOption.setOpenGps(true);

        // 可选，设置是否当GPS有效时按照1S/1次频率输出GPS结果，默认false
        // option.setLocationNotify(true);

        // 可选，定位SDK内部是一个service，并放到了独立进程。
        // 设置是否在stop的时候杀死这个进程，默认（建议）不杀死，即setIgnoreKillProcess(true)
        // option.setIgnoreKillProcess(false);

        // 可选，设置是否收集Crash信息，默认收集，即参数为false
        // option.SetIgnoreCacheException(false);

        // 可选，7.2版本新增能力
        // 如果设置了该接口，首次启动定位时，会先判断当前WiFi是否超出有效期，若超出有效期，会先重新扫描WiFi，然后定位
        locationClientOption.setWifiCacheTimeOut(300000); // 5 * 60 * 1000

        // 可选，设置是否需要过滤GPS仿真结果，默认需要，即参数为false
        // option.setEnableSimulateGps(false);

        return locationClientOption;
    }

    /**
     * 设置百度定位客户端配置
     *
     * @param scanSpan 发起定位请求的间隔，单位：毫秒
     */
    private void setLocationClientOption(int scanSpan) {
        continuousLocationClient.setLocOption(getLocationClientOption(scanSpan));
    }

    /**
     * 是否未初始化百度定位客户端
     *
     * @return 真，如果未初始化
     */
    private boolean isUninitialized() {
        return continuousLocationClient == null;
    }

    /**
     * 是否启动了百度定位客户端
     *
     * @return 真，如果启动了
     */
    private boolean isStarted() {
        return continuousLocationClient.isStarted();
    }

    /**
     * 对异常进行解析
     *
     * @param exception       异常
     * @param callbackContext 回调上下文
     */
    private void parseException(Exception exception, CallbackContext callbackContext) {
        String message = exception.getMessage() == null ? "未知异常" : exception.getMessage();
        callbackContext.error(message);
    }

    /**
     * 回调定位
     *
     * @param callbackContext 回调上下文
     * @param latitude        纬度
     * @param longitude       经度
     */
    private void callbackLocate(CallbackContext callbackContext, double latitude, double longitude) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("lat", latitude);
        map.put("lng", longitude);
        callbackContext.success(new JSONObject(map));
    }

    /**
     * 是否有效距离
     *
     * @param latitude  纬度
     * @param longitude 经度
     * @return 真，如果有效距离
     */
    private boolean validDistance(double latitude, double longitude) {
        return getDistance(lastStoredLatitude, lastStoredLongitude, latitude, longitude) > distanceFilter;
    }

    /**
     * 启停切换结果
     */
    private enum ToggleResult {
        /**
         * 成功
         */
        success,
        /**
         * 已经是目标状态，无法再次执行
         */
        already,
        /**
         * 无法保存状态，提交偏好设置失败
         */
        unsaved,
    }
}
