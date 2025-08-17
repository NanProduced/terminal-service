package com.colorlight.terminal.application.domain.report;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 终端上报信息（主要是led_status）
 *
 * @author Nan
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TerminalStatusReport {
    private Terminal terminal;
    private PowerStatus powerstatus;
    private InfoWrapper info;
    private Vsns vsns;
    private Dimension dimension;
    private Volume volume;
    private InputMode inputmode;

    @JsonProperty("ifstatus")
    private IfStatus ifStatus;

    private BrightnessAndColorTemp brightnessandcolortemp;
    private ReportTime reporttime;
    private CmdInterval cmdinterval;
    private ReportSwitch reportswitch;
    private ContentReport contentreport;
    private ContentReportInterval contentreportinterval;
    private NewRtc newrtc;
    private Rtc rtc;
    private LocaleInfo locale;

    private SyncProgramMode sync_program_mode;
    private InboundFirewall inboundfirewall;
    private BrightCurve brightcurve;
    private BrightnessVersion brightnessversion;
    private AllBrightnessInfo allbrightnessinfo;

    private CameraConfig cameraconfig;
    private ScreenOrientation screen_orientation;

    @JsonProperty("4ginfo")
    private Map<String, Object> _4ginfo;


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Terminal {
        private String name;
        private String leddescription;
        @JsonProperty("_report_time")
        private long reportTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PowerStatus {
        private int powerstatus;
        @JsonProperty("_report_time")
        private long reportTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InfoWrapper {
        private Info info;
        @JsonProperty("_report_time")
        private long reportTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Info {
        private String vername;
        private String serialno;
        private String model;
        private long up;
        private Mem mem;
        private Storage storage;
        private Playing playing;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Mem {
        private long total;
        private long free;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Storage {
        private long total;
        private long free;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Playing {
        private String name;
        private String path;
        private String source;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Vsns {
        private List<ContentGroup> contents;
        private Playing playing;
        @JsonProperty("_report_time")
        private long reportTime;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ContentGroup {
            private String type;
            private long ressize;
            private long unused;
            private List<ContentItem> content;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ContentItem {
            private String md5;
            private String name;
            private String publishedmd5;
            private long size;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Dimension {
        private long dclk;
        private int fps;
        private int height;
        private int hsync;
        private long real_dclk;
        private int real_height;
        private int real_width;
        private int width;
        @JsonProperty("_report_time")
        private long reportTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Volume {
        private int musicvolume;
        @JsonProperty("_report_time")
        private long reportTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InputMode {
        @JsonProperty("_report_time")
        private long reportTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IfStatus {
        private List<NetInterface> types;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class NetInterface {
            private String type;
            private int enabled;
            private int connected;
            private String operstate;
            private String mode;
            private String mac;
            private NetIps ips;
            private String SSID;
            private String pass;
            private int speed;
            private int carrier;
            private String state;
            private String currentap;
            private List<SsidEntry> ssids;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class NetIps {
            private String ip;
            private String mask;
            private String broadcast;
            private String gateway;
            private String dns1;
            private String dns2;
        }


        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class SsidEntry {
            private String SSID;
            private String pass;
            private double priority;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrightnessAndColorTemp {
        private int brightness;
        private int colortemperature;
        @JsonProperty("_report_time")
        private long reportTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReportTime {
        private double gps_report_interval;
        private double sensor_report_interval;
        private double ber_report_interval;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CmdInterval {
        private int command_interval;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReportSwitch {
        private String log_report;
        private String complete_screen_status_report;
        private String not_rotate_program_screenshot_report;
        private String rotate_program_screenshot_report;
        private String command_screenshot_report;
        private String auto_vsns_report;
        private String manual_vsns_report;
        private String rotate_program_vsns_report;
        private String auto_info_report;
        private String manual_info_report;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentReport {
        private int content_report_status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentReportInterval {
        private int content_report_interval;
        private int content_report_size;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NewRtc {
        private String time;
        private String timezoneId;
        private double timezone;
        private int isautotime;
        @JsonProperty("_report_time")
        private long reportTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rtc {
        private String time;
        private String timezone;
        private int isautotimezone;
        private int isautotime;
        @JsonProperty("_report_time")
        private long reportTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocaleInfo {
        private String language;
        private String country;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SyncProgramMode {
        private int sync_program_gps_enable;
        private int sync_program_ntp_enable;
        private String sync_program_ntp_server;
        private int sync_program_ntp_interval;
        private int sync_program_ntp_threshold;
        private int sync_program_lan_enable;
        private String sync_program_lan_role;
        private int sync_program_audio_enable;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InboundFirewall {
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrightCurve {
        private List<Integer> noneReverseGammaValues;
        private List<Integer> reverseGammaValues;
        private int auto;
        private int isNewBrightness;
        private int maxAdjustValue;
        private int maxOriginalValue;
        private int maxPercent;
        private int method;
        private int midAdjustValue;
        private int midPercent;
        private int minAdjustValue;
        private int minOriginalValue;
        private int minPercent;
        private int save;
        private int sensitivity;
        private int sensorErrorDefaultValue;
        private int sensorSource485;
        private int sensorSourceMultifunctionCard;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrightnessVersion {
        private int isNewBrightness;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AllBrightnessInfo {
        private int realTimeBrightValue;
        private int savedBrightValue;
        private boolean isbShowOn;
        private boolean isHasSensor;
        private int sensorBright;
        private int briAndClrTAdjustType;
        private int sensorSource485;
        private int sensorSourceMultifunctionCard;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CameraConfig {
        private double code;
        private CameraData data;
        private String msg;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class CameraData {
            private int auto_upload;
            private int exposure;
            private int interval;
            private int quality;
            private String size;
            private String whitebalance;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScreenOrientation {
        private String orientation;
    }
}
