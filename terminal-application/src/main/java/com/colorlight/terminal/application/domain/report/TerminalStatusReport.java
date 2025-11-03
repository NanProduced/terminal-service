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
 * 对应原项目中的 _led_status JSON 数据结构
 *
 * @author Nan
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TerminalStatusReport {

    /**
     * 终端基本信息（名称、描述等）
     */
    private Terminal terminal;

    /**
     * websocket连接状态
     */
    @JsonProperty("WebSocketStatus")
    private WebsocketStatus websocketStatus;

    /**
     * 电源状态信息
     */
    private PowerStatus powerstatus;

    /**
     * 终端详细信息包装器（版本、序列号、型号、运行时间、内存、存储、播放状态等）
     */
    private InfoWrapper info;

    /**
     * 版本和内容信息（内容列表、播放状态等）
     */
    private Vsns vsns;

    /**
     * 屏幕尺寸和显示参数（分辨率、帧率、时钟频率等）
     */
    private Dimension dimension;

    /**
     * 音量设置信息
     */
    private Volume volume;

    /**
     * 输入模式信息
     */
    private InputMode inputmode;

    /**
     * 网络接口状态信息（以太网、WiFi、4G等网络连接状态）
     */
    @JsonProperty("ifstatus")
    private IfStatus ifStatus;

    /**
     * 亮度和色温设置信息
     */
    private BrightnessAndColorTemp brightnessandcolortemp;

    /**
     * 上报时间间隔设置（GPS、传感器、BER等上报间隔）
     */
    private ReportTime reporttime;

    /**
     * 指令执行间隔设置
     */
    private CmdInterval cmdinterval;

    /**
     * 各种上报开关设置（日志、截图、版本信息等上报开关）
     */
    private ReportSwitch reportswitch;

    /**
     * 内容上报状态设置
     */
    private ContentReport contentreport;

    /**
     * 内容上报间隔和大小设置
     */
    private ContentReportInterval contentreportinterval;

    /**
     * 新版RTC时间设置（时间、时区、自动时间等）
     */
    private NewRtc newrtc;

    /**
     * 旧版RTC时间设置
     */
    private Rtc rtc;

    /**
     * 本地化信息（语言、国家等）
     */
    private LocaleInfo locale;

    /**
     * 节目同步模式设置（GPS、NTP、LAN、音频同步等）
     */
    private SyncProgramMode sync_program_mode;

    /**
     * 入站防火墙设置
     */
    private InboundFirewall inboundfirewall;

    /**
     * 亮度曲线设置（自动亮度调节相关参数）
     */
    private BrightCurve brightcurve;

    /**
     * 亮度版本信息
     */
    private BrightnessVersion brightnessversion;

    /**
     * 所有亮度相关信息（实时亮度、保存亮度、传感器状态等）
     */
    private AllBrightnessInfo allbrightnessinfo;

    /**
     * 摄像头配置信息
     */
    private CameraConfig cameraconfig;

    /**
     * 屏幕方向设置
     */
    private ScreenOrientation screen_orientation;

    /**
     * 4G网络信息
     */
    @JsonProperty("4ginfo")
    private Map<String, Object> _4ginfo;


    /**
     * 终端基本信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Terminal {
        /**
         * 终端名称
         */
        private String name;

        /**
         * LED屏描述信息
         */
        private String leddescription;

        /**
         * 上报时间戳
         */
        @JsonProperty("_report_time")
        private long reportTime;
    }

    /**
     * 电源状态信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PowerStatus {
        /**
         * 电源状态（0-关闭，1-开启）
         */
        private int powerstatus;

        /**
         * 上报时间戳
         */
        @JsonProperty("_report_time")
        private long reportTime;
    }

    /**
     * 终端详细信息包装器
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InfoWrapper {
        /**
         * 终端详细信息
         */
        private Info info;

        /**
         * 上报时间戳
         */
        @JsonProperty("_report_time")
        private long reportTime;
    }

    /**
     * 终端详细信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Info {
        /**
         * 版本名称
         */
        private String vername;

        /**
         * 序列号
         */
        private String serialno;

        /**
         * 设备型号
         */
        private String model;

        /**
         * 运行时间（秒）
         */
        private long up;

        /**
         * 内存信息
         */
        private Mem mem;

        /**
         * 存储信息
         */
        private Storage storage;

        /**
         * 当前播放状态
         */
        private Playing playing;
    }

    /**
     * 内存信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Mem {
        /**
         * 总内存大小（字节）
         */
        private long total;

        /**
         * 可用内存大小（字节）
         */
        private long free;
    }

    /**
     * 存储信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Storage {
        /**
         * 总存储空间（字节）
         */
        private long total;

        /**
         * 可用存储空间（字节）
         */
        private long free;
    }

    /**
     * 播放状态信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Playing {
        /**
         * 播放内容名称
         */
        private String name;

        /**
         * 播放内容路径
         */
        private String path;

        /**
         * 播放内容来源
         */
        private String source;

        /**
         * 播放类型
         */
        private String type;
    }

    /**
     * 版本和内容信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Vsns {
        /**
         * 内容组列表
         */
        private List<ContentGroup> contents;

        /**
         * 当前播放状态
         */
        private Playing playing;

        /**
         * 上报时间戳
         */
        @JsonProperty("_report_time")
        private long reportTime;

        /**
         * 内容组信息
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ContentGroup {
            /**
             * 内容类型
             */
            private String type;

            /**
             * 资源大小（字节）
             */
            private long ressize;

            /**
             * 未使用空间（字节）
             */
            private long unused;

            /**
             * 内容项列表
             */
            private List<ContentItem> content;
        }

        /**
         * 内容项信息
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ContentItem {
            /**
             * 内容MD5值
             */
            private String md5;

            /**
             * 内容名称
             */
            private String name;

            /**
             * 发布的MD5值
             */
            private String publishedmd5;

            /**
             * 内容大小（字节）
             */
            private long size;
        }
    }

    /**
     * 屏幕尺寸和显示参数
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Dimension {
        /**
         * 显示时钟频率（Hz）
         */
        private long dclk;

        /**
         * 帧率（FPS）
         */
        private int fps;

        /**
         * 屏幕高度（像素）
         */
        private int height;

        /**
         * 水平同步信号
         */
        private int hsync;

        /**
         * 实际显示时钟频率（Hz）
         */
        private long real_dclk;

        /**
         * 实际屏幕高度（像素）
         */
        private int real_height;

        /**
         * 实际屏幕宽度（像素）
         */
        private int real_width;

        /**
         * 屏幕宽度（像素）
         */
        private int width;

        /**
         * 上报时间戳
         */
        @JsonProperty("_report_time")
        private long reportTime;
    }

    /**
     * 音量设置信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Volume {
        /**
         * 音乐音量（0-100）
         */
        private int musicvolume;

        /**
         * 上报时间戳
         */
        @JsonProperty("_report_time")
        private long reportTime;
    }

    /**
     * 输入模式信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InputMode {
        /**
         * 上报时间戳
         */
        @JsonProperty("_report_time")
        private long reportTime;
    }

    /**
     * 网络接口状态信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IfStatus {
        /**
         * 网络接口类型列表
         */
        private List<NetInterface> types;

        /**
         * 网络接口详细信息
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class NetInterface {
            /**
             * 接口类型（eth-以太网，wifi-WiFi，4g-4G网络等）
             */
            private String type;

            /**
             * 是否启用（0-禁用，1-启用）
             */
            private int enabled;

            /**
             * 是否连接（0-未连接，1-已连接）
             */
            private int connected;

            /**
             * 操作状态（up-启动，down-关闭等）
             */
            private String operstate;

            /**
             * 连接模式
             */
            private String mode;

            /**
             * MAC地址
             */
            private String mac;

            /**
             * IP地址信息
             */
            private NetIps ips;

            /**
             * WiFi SSID名称
             */
            @JsonProperty("SSID")
            private String SSID;

            /**
             * WiFi密码
             */
            private String pass;

            /**
             * 连接速度（Mbps）
             */
            private int speed;

            /**
             * 载波状态（0-无载波，1-有载波）
             */
            private int carrier;

            /**
             * 连接状态
             */
            private String state;

            /**
             * 当前接入点
             */
            private String currentap;

            /**
             * 可用SSID列表
             */
            private List<SsidEntry> ssids;

            /**
             * WiFi信道
             */
            private Integer channel;

            private List<Peer> peers;

            private boolean weakPassword;

            private Integer strength;

        }

        /**
         * WiFi SSID条目
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class Peer {
            private String ip;
            private String mac;
        }

        /**
         * 网络IP配置信息
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class NetIps {
            /**
             * IP地址
             */
            private String ip;

            /**
             * 子网掩码
             */
            private String mask;

            /**
             * 广播地址
             */
            private String broadcast;

            /**
             * 网关地址
             */
            private String gateway;

            /**
             * 主DNS服务器
             */
            private String dns1;

            /**
             * 备用DNS服务器
             */
            private String dns2;
        }

        /**
         * WiFi SSID配置项
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class SsidEntry {
            /**
             * WiFi网络名称
             */
            private String SSID;

            /**
             * WiFi密码
             */
            private String pass;

            /**
             * 连接优先级
             */
            private double priority;
        }
    }

    /**
     * 亮度和色温设置信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrightnessAndColorTemp {
        /**
         * 亮度值（0-255）
         */
        private int brightness;

        /**
         * 色温值（单位：K）
         */
        private int colortemperature;

        /**
         * 上报时间戳
         */
        @JsonProperty("_report_time")
        private long reportTime;
    }

    /**
     * 上报时间间隔设置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReportTime {
        /**
         * GPS上报间隔（秒）
         */
        private double gps_report_interval;

        /**
         * 传感器上报间隔（秒）
         */
        private double sensor_report_interval;

        /**
         * BER（误码率）上报间隔（秒）
         */
        private double ber_report_interval;

        /**
         * gps采样上报时间间隔信息 0是关闭，大于0是打开并配置时间间隔。
         */
        private Integer gps_sample_interval;
    }

    /**
     * 指令执行间隔设置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CmdInterval {
        /**
         * 指令执行间隔（毫秒）
         */
        private int command_interval;
    }

    /**
     * 各种上报开关设置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReportSwitch {
        /**
         * 日志上报开关（"on"/"off"）
         */
        private String log_report;

        /**
         * 完整屏幕状态上报开关（"on"/"off"）
         */
        private String complete_screen_status_report;

        /**
         * 非旋转节目截图上报开关（"on"/"off"）
         */
        private String not_rotate_program_screenshot_report;

        /**
         * 旋转节目截图上报开关（"on"/"off"）
         */
        private String rotate_program_screenshot_report;

        /**
         * 指令截图上报开关（"on"/"off"）
         */
        private String command_screenshot_report;

        /**
         * 自动版本信息上报开关（"on"/"off"）
         */
        private String auto_vsns_report;

        /**
         * 手动版本信息上报开关（"on"/"off"）
         */
        private String manual_vsns_report;

        /**
         * 旋转节目版本信息上报开关（"on"/"off"）
         */
        private String rotate_program_vsns_report;

        /**
         * 自动设备信息上报开关（"on"/"off"）
         */
        private String auto_info_report;

        /**
         * 手动设备信息上报开关（"on"/"off"）
         */
        private String manual_info_report;
    }

    /**
     * 内容上报状态设置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentReport {
        /**
         * 内容上报状态（0-关闭，1-开启）
         */
        private int content_report_status;

        /**
         * 节目上报状态（0-关闭，1-开启）
         */
        private int program_report_status;


    }

    /**
     * 内容上报间隔和大小设置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentReportInterval {
        /**
         * 内容上报间隔（秒）
         */
        private int content_report_interval;

        /**
         * 内容上报大小限制（字节）
         */
        private int content_report_size;
    }

    /**
     * 新版RTC时间设置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NewRtc {
        /**
         * 当前时间（格式：yyyy-MM-dd HH:mm:ss）
         */
        private String time;

        /**
         * 时区ID（如：Asia/Shanghai）
         */
        private String timezoneId;

        /**
         * 时区偏移量（小时）
         */
        private double timezone;

        /**
         * 是否自动时间同步（0-手动，1-自动）
         */
        private int isautotime;

        /**
         * 上报时间戳
         */
        @JsonProperty("_report_time")
        private long reportTime;
    }

    /**
     * 旧版RTC时间设置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rtc {
        /**
         * 当前时间
         */
        private String time;

        /**
         * 时区设置
         */
        private String timezone;

        /**
         * 是否自动时区（0-手动，1-自动）
         */
        private int isautotimezone;

        /**
         * 是否自动时间同步（0-手动，1-自动）
         */
        private int isautotime;

        /**
         * 上报时间戳
         */
        @JsonProperty("_report_time")
        private long reportTime;
    }

    /**
     * 本地化信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocaleInfo {
        /**
         * 语言设置（如：zh、en）
         */
        private String language;

        /**
         * 国家设置（如：CN、US）
         */
        private String country;
    }

    /**
     * 节目同步模式设置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SyncProgramMode {
        /**
         * GPS同步启用状态（0-禁用，1-启用）
         */
        private int sync_program_gps_enable;

        /**
         * NTP同步启用状态（0-禁用，1-启用）
         */
        private int sync_program_ntp_enable;

        /**
         * NTP服务器地址
         */
        private String sync_program_ntp_server;

        /**
         * NTP同步间隔（秒）
         */
        private int sync_program_ntp_interval;

        /**
         * NTP同步阈值（毫秒）
         */
        private int sync_program_ntp_threshold;

        /**
         * LAN同步启用状态（0-禁用，1-启用）
         */
        private int sync_program_lan_enable;

        /**
         * LAN同步角色（master-主控，slave-从控）
         */
        private String sync_program_lan_role;

        /**
         * 音频同步启用状态（0-禁用，1-启用）
         */
        private int sync_program_audio_enable;
    }

    /**
     * 入站防火墙设置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InboundFirewall {
        /**
         * 防火墙状态（on-开启，off-关闭）
         */
        private String status;
    }

    /**
     * 亮度曲线设置（自动亮度调节相关参数）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrightCurve {
        /**
         * 非反向伽马值列表
         */
        private List<Integer> noneReverseGammaValues;

        /**
         * 反向伽马值列表
         */
        private List<Integer> reverseGammaValues;

        /**
         * 自动亮度模式（0-手动，1-自动）
         */
        private int auto;

        /**
         * 是否为新亮度算法（0-旧算法，1-新算法）
         */
        private int isNewBrightness;

        /**
         * 最大调节值
         */
        private int maxAdjustValue;

        /**
         * 最大原始值
         */
        private int maxOriginalValue;

        /**
         * 最大百分比
         */
        private int maxPercent;

        /**
         * 调节方法
         */
        private int method;

        /**
         * 中间调节值
         */
        private int midAdjustValue;

        /**
         * 中间百分比
         */
        private int midPercent;

        /**
         * 最小调节值
         */
        private int minAdjustValue;

        /**
         * 最小原始值
         */
        private int minOriginalValue;

        /**
         * 最小百分比
         */
        private int minPercent;

        /**
         * 保存设置（0-不保存，1-保存）
         */
        private int save;

        /**
         * 传感器灵敏度
         */
        private int sensitivity;

        /**
         * 传感器错误时的默认值
         */
        private int sensorErrorDefaultValue;

        /**
         * 485传感器源（0-禁用，1-启用）
         */
        private int sensorSource485;

        /**
         * 多功能卡传感器源（0-禁用，1-启用）
         */
        private int sensorSourceMultifunctionCard;
    }

    /**
     * 亮度版本信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrightnessVersion {
        /**
         * 是否为新亮度算法（0-旧算法，1-新算法）
         */
        private int isNewBrightness;
    }

    /**
     * 所有亮度相关信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AllBrightnessInfo {
        /**
         * 实时亮度值（0-255）
         */
        private int realTimeBrightValue;

        /**
         * 保存的亮度值（0-255）
         */
        private int savedBrightValue;

        /**
         * 亮度显示是否开启
         */
        private boolean isbShowOn;

        /**
         * 是否有亮度传感器
         */
        private boolean isHasSensor;

        /**
         * 传感器检测到的亮度值
         */
        private int sensorBright;

        /**
         * 亮度和色温调节类型
         */
        private int briAndClrTAdjustType;

        /**
         * 485传感器源（0-禁用，1-启用）
         */
        private int sensorSource485;

        /**
         * 多功能卡传感器源（0-禁用，1-启用）
         */
        private int sensorSourceMultifunctionCard;
    }

    /**
     * 摄像头配置信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CameraConfig {
        /**
         * 响应代码
         */
        private double code;

        /**
         * 摄像头配置数据
         */
        private CameraData data;

        /**
         * 响应消息
         */
        private String msg;

        /**
         * 摄像头配置详细数据
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class CameraData {
            /**
             * 自动上传（0-禁用，1-启用）
             */
            private int auto_upload;

            /**
             * 曝光设置
             */
            private int exposure;

            /**
             * 拍照间隔（秒）
             */
            private int interval;

            /**
             * 图片质量（0-100）
             */
            private int quality;

            /**
             * 图片尺寸（如：1920x1080）
             */
            private String size;

            /**
             * 白平衡设置（auto-自动，manual-手动等）
             */
            private String whitebalance;
        }
    }

    /**
     * 屏幕方向设置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScreenOrientation {
        /**
         * 屏幕方向（portrait-竖屏，landscape-横屏，auto-自动）
         */
        private String orientation;
    }

    /**
     * websocket连接状态
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebsocketStatus {

        private Integer status;
    }
}
