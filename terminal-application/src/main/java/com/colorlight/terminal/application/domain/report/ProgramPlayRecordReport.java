package com.colorlight.terminal.application.domain.report;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedList;

/**
 * 代表节目播放记录的报告类。该类主要用于收集和表示关于特定节目的播放数据，包括但不限于播放次数、播放时长等。
 * 支持LAN和Internet两种类型的节目，并能够通过不同的字段来适应这两种类型的数据上报需求。
 *
 * @author Nan
 */
@Data
public class ProgramPlayRecordReport {

    /*=================== 设备上报字段 ===================*/

    /**
     * 节目VSN名称
     */
    private String programVsn;

    /**
     * 节目名称
     */
    private String programName;

    /**
     * 需要适配不同节目类型的上报值
     * <li>LAN节目：programId(String)</li>
     * <li>Internet节目：authorId(Integer)</li>
     */
    @JsonProperty("programId")
    private String programIdStr;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LinkedList<LocalDateTime> startLocalTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LinkedList<LocalDateTime> startUtcTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LinkedList<LocalDateTime> endLocalTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LinkedList<LocalDateTime> endUtcTime;

    /**
     * 播放次数
     */
    @JsonProperty("times")
    private Integer playTimes;

    /**
     * 节目时长
     */
    private Long singleDuration;

    /**
     * 播放时长
     */
    @JsonProperty("duration")
    private Long playDuration;

    /*=================== 业务逻辑添加字段 ===================*/

    /**
     * 设备Id
     */
    @JsonIgnore
    private Long deviceId;

    /**
     * 节目创建者Id
     */
    @JsonIgnore
    private Integer authorId;

    /**
     * 节目Id
     */
    @JsonIgnore
    private Integer programId;

}
