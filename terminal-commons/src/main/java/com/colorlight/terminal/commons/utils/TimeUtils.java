package com.colorlight.terminal.commons.utils;


import com.colorlight.terminal.commons.exception.technical.TechErrorCode;
import com.colorlight.terminal.commons.exception.technical.TechnicalException;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间工具类
 */
public class TimeUtils {

    private TimeUtils() {
        throw new TechnicalException(TechErrorCode.INSTANTIATION_IS_PROHIBITED);
    }

    /**
     * 将指定时区时间转成服务器时区查询
     * @param browserTime 浏览器时间
     * @param browserZone 浏览器时区
     * @return
     */
    public static LocalDateTime transTimeToUTC(LocalDateTime browserTime, ZoneId browserZone) {
        ZonedDateTime zonedDateTime = ZonedDateTime.of(browserTime, browserZone);
        ZonedDateTime utcZonedDateTime = zonedDateTime.withZoneSameInstant(ZoneId.of("UTC"));
        return utcZonedDateTime.toLocalDateTime();
    }

    /**
     * 时间戳转UTC LocalDateTime
     * @param timestamp 时间戳
     * @return UTC
     */
    public static LocalDateTime convertTimestampToUtc(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp),
                    ZoneId.of("UTC")
            );
        } catch (Exception e) {
            throw new TechnicalException(TechErrorCode.TIME_FORMAT_TRANSLATE_FAILED);
        }
    }


    /**
     * 将事件时间戳转换为LocalDateTime
     *
     * @param timestamp 时间戳
     * @return localDateTime
     */
    public static LocalDateTime convertTimestampToLocalDateTime(Long timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now();
        }

        try {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp),
                    ZoneId.systemDefault()
            );
        } catch (Exception e) {
            throw new TechnicalException(TechErrorCode.TIME_FORMAT_TRANSLATE_FAILED);
        }
    }

    /**
     * 将 LocalDateTime 转换为毫秒时间戳。
     *
     * @param localDateTime 要转换的 LocalDateTime 对象
     * @return 毫秒时间戳
     */
    public static Long convertLocalDateTimeToTimestamp(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 将指定格式的字符串转换为 LocalDateTime 对象。
     * * @param timeStr 要转换的时间字符串
     * @param pattern 时间字符串的格式，例如 "yyyy-MM-dd HH:mm:ss"
     * @return 转换后的 LocalDateTime 对象
     */
    public static LocalDateTime convertStringToLocalDateTime(String timeStr, String pattern) {
        if (StringUtils.isBlank(timeStr)) {
            return null;
        }
        if (StringUtils.isBlank(pattern)) {
            pattern = "yyyy-MM-dd HH:mm:ss";
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDateTime.parse(timeStr, formatter);
        } catch (Exception e) {
            throw new TechnicalException(TechErrorCode.TIME_FORMAT_TRANSLATE_FAILED, "Failed to parse string to LocalDateTime.");
        }
    }

    /**
     * 将 LocalDateTime 对象格式化为指定格式的字符串。
     *
     * @param localDateTime 要格式化的 LocalDateTime 对象
     * @param pattern 目标格式，例如 "yyyy-MM-dd HH:mm:ss"
     * @return 格式化后的时间字符串
     */
    public static String formatLocalDateTimeToString(LocalDateTime localDateTime, String pattern) {
        if (localDateTime == null || pattern == null || pattern.isEmpty()) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return localDateTime.format(formatter);
    }
}
