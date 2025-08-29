package com.colorlight.terminal.application.domain.report;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 素材播放记录上报
 *
 * @author Nan
 */
@Data
public class MediaPlayRecordReport {

    /**
     * 节目VSN名
     */
    private String programName;

    /**
     * 素材名
     */
    private String resOriginName;

    /**
     * 素材Md5
     */
    private String resMd5Name;

    /**
     * 素材所在节目页名
     */
    private String pageName;

    /**
     * 素材所在节目页索引
     */
    private Integer pageIndex;

    /**
     * 素材所在播放区域名称
     */
    private String regionName;

    /**
     * 素材所在播放区域索引
     */
    private Integer regionIndex;

    /**
     * 播放开始UTC时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startUtcTime;

    /**
     * 播放开始本地时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startLocalTime;

    /**
     * 播放结束UTC时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endUtcTime;

    /**
     * 播放结束本地时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endLocalTime;

    /**
     * 播放时长（秒）
     */
    private Long duration;

    /**
     * 素材类型
     */
    private String itemType;

    /**
     * 业务字段（校准后的开始播放时间）
     * <li>1.根据是否开启校准来判断是否校准开始时间</li>
     * <li>2.使用这个字段作为查询的时间字段</li>
     */
    private LocalDateTime adjustStartTime;

    /**
     * 反序列化时先将adjustStartTime设置为startUtcTime
     * @param startUtcTime 开始播放时间(UTC)
     */
    @JsonSetter("startUtcTime")
    public void setStartUtcTimeAndAdjustStartTime(LocalDateTime startUtcTime) {
        this.startUtcTime = startUtcTime;
        this.adjustStartTime = startUtcTime;
    }
}
