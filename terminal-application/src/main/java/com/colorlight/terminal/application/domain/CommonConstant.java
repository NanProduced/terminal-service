package com.colorlight.terminal.application.domain;

/**
 * 设备通用常量
 *
 * @author Nan
 */
public final class CommonConstant {

    private CommonConstant() {
    }

    public static final class Device {

        private Device() {
        }

        public static final String DEVICE_ID = "deviceId";

        public static final String LAST_REPORT_TIME = "lastReportTime";

        public static final String LAST_REPORT_SOURCE = "lastReportSource";

        public static final String STATUS = "status";

        public static final String STATUS_CHANGE_TIME = "statusChangeTime";

        public static final String ONLINE_START_TIME = "onlineStartTime";

        public static final String CLIENT_IP = "clientIp";

        public static final String VERSION = "version";


    }

    public static final class Media {

        private Media() {
        }

        public static final String PROGRAM_STATUS = "programStatus";

        public static final String PROGRAM_UPDATE_TIME = "programUpdateTime";

        public static final String UPGRADE_STATUS = "upgradeStatus";

        public static final String UPGRADE_UPDATE_TIME = "upgradeUpdateTime";


    }


}
