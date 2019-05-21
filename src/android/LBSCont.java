package cn.wiskind.lbs;

import android.provider.BaseColumns;

/**
 * 定位服务数据约定（location based service contract）
 */
public final class LBSCont {

    /**
     * 私有化构造方法
     */
    private LBSCont() {
    }

    /**
     * 位置实体（location entry）
     */
    public static class LocEntry implements BaseColumns {

        /**
         * 表名（table name）
         */
        public static final String TN = "location";

        /**
         * 经度（longitude）列名（column name）
         */
        public static final String CN_LNG = "lng";

        /**
         * 纬度（latitude）列名（column name）
         */
        public static final String CN_LAT = "lat";

        /**
         * 创建时间（create date）列名（column name）
         */
        public static final String CN_CDATE = "cdate";
    }

    /**
     * 运行日志实体（run log entry）
     */
    public static class RunLogEntry implements BaseColumns {

        /**
         * 表名（table name）
         */
        public static final String TN = "runlog";

        /**
         * 日志时间（time）列名（column name）
         */
        public static final String CN_TIME = "time";

        /**
         * 是否定位成功（success）列名（column name）
         */
        public static final String CN_SUCCESS = "success";
    }

}
