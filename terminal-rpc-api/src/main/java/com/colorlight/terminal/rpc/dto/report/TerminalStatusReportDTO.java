package com.colorlight.terminal.rpc.dto.report;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 终端状态报告RPC传输对象
 * 
 * @author Demon
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalStatusReportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 终端基本信息
     */
    private TerminalDTO terminal;

    /**
     * 电源状态信息
     */
    private PowerStatusDTO powerstatus;

    /**
     * 终端详细信息
     */
    private InfoWrapperDTO info;

    /**
     * 版本和内容信息
     */
    private VsnsDTO vsns;

    /**
     * 屏幕尺寸和显示参数
     */
    private DimensionDTO dimension;

    /**
     * 音量设置信息
     */
    private VolumeDTO volume;

    /**
     * 输入模式信息
     */
    private InputModeDTO inputmode;

    /**
     * 网络接口状态信息
     */
    private IfStatusDTO ifStatus;

    /**
     * 亮度和色温设置信息
     */
    private BrightnessAndColorTempDTO brightnessandcolortemp;

    /**
     * 新版RTC时间设置
     */
    private NewRtcDTO newrtc;

    /**
     * 本地化信息
     */
    private LocaleInfoDTO locale;

    /**
     * 所有亮度相关信息
     */
    private AllBrightnessInfoDTO allbrightnessinfo;

    /**
     * 4G网络信息
     */
    private Map<String, Object> _4ginfo;

    /**
     * 终端基本信息DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TerminalDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String name;
        private String leddescription;
        private long reportTime;
    }

    /**
     * 电源状态DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PowerStatusDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int powerstatus;
        private long reportTime;
    }

    /**
     * 终端详细信息包装器DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InfoWrapperDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private InfoDTO info;
        private long reportTime;
    }

    /**
     * 终端详细信息DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String vername;
        private String serialno;
        private String model;
        private long up;
        private MemDTO mem;
        private StorageDTO storage;
        private PlayingDTO playing;
    }

    /**
     * 内存信息DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private long total;
        private long free;
    }

    /**
     * 存储信息DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private long total;
        private long free;
    }

    /**
     * 播放状态DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayingDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String name;
        private String path;
        private String source;
    }

    /**
     * 版本和内容信息DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VsnsDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private List<Object> contents; // 简化处理
        private PlayingDTO playing;
        private long reportTime;
    }

    /**
     * 屏幕尺寸和显示参数DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private long dclk;
        private int fps;
        private int height;
        private int width;
        private int real_height;
        private int real_width;
        private long reportTime;
    }

    /**
     * 音量设置DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private int musicvolume;
        private long reportTime;
    }

    /**
     * 输入模式DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InputModeDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private long reportTime;
    }

    /**
     * 网络接口状态DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IfStatusDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private List<NetInterfaceDTO> types;

        /**
         * 网络接口详细信息DTO
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class NetInterfaceDTO implements Serializable {
            private static final long serialVersionUID = 1L;
            
            private String type;
            private int enabled;
            private int connected;
            private String operstate;
            private String mac;
            private String SSID;
            private int speed;
            private String state;
        }
    }

    /**
     * 亮度和色温设置DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrightnessAndColorTempDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int brightness;
        private int colortemperature;
        private long reportTime;
    }

    /**
     * 新版RTC时间设置DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewRtcDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String time;
        private String timezoneId;
        private double timezone;
        private int isautotime;
        private long reportTime;
    }

    /**
     * 本地化信息DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocaleInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String language;
        private String country;
    }

    /**
     * 所有亮度相关信息DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllBrightnessInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private int realTimeBrightValue;
        private int savedBrightValue;
        private boolean isbShowOn;
        private boolean isHasSensor;
        private int sensorBright;
    }
}
