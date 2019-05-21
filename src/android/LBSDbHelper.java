package cn.wiskind.lbs;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 定位服务数据库辅助工具（location based service database helper）
 */
public class LBSDbHelper extends SQLiteOpenHelper {

    /**
     * 单例
     */
    private static LBSDbHelper instance;

    /**
     * 数据库版本（database version）
     */
    private static final int DB_VER = 2;

    /**
     * 数据库名称（database name）
     */
    private static final String DB_NAME = "WiskindLBS.db";

    /**
     * 创建实体的SQL
     */
    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + LBSCont.LocEntry.TN + " (" +
                    LBSCont.LocEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    LBSCont.LocEntry.CN_LNG + " TEXT, " +
                    LBSCont.LocEntry.CN_LAT + " TEXT, " +
                    LBSCont.LocEntry.CN_CDATE + " INTEGER);";

    /**
     * 创建运行日志实体的SQL
     */
    private static final String SQL_CREATE_RUNLOG =
            "CREATE TABLE " + LBSCont.RunLogEntry.TN + " (" +
                    LBSCont.RunLogEntry.CN_TIME + " INTEGER PRIMARY KEY, " +
                    LBSCont.RunLogEntry.CN_SUCCESS + " INTEGER);";

    /**
     * 简化构造方法
     */
    private LBSDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VER);
    }

    /**
     * 获取单例
     *
     * @param context 上下文
     * @return 实例
     */
    public static synchronized LBSDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new LBSDbHelper(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
        db.execSQL(SQL_CREATE_RUNLOG);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion == 2) {
            db.execSQL(SQL_CREATE_RUNLOG);
        }
    }
}
