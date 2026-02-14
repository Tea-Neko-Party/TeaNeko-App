package org.zexnocs.teanekocore.utils;

import org.springframework.scheduling.support.CronExpression;

import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * 中国日期工具类。
 *
 * @author zExNocs
 * @date 2026/02/12
 */
public enum ChinaDateUtil {
    Instance;

    /// 中国时区
    private final ZoneId zoneId = ZoneId.of("Asia/Shanghai");

    /// yyyy-MM-dd 格式的日期格式化器
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /// yyyy-MM-dd HH:mm:ss 格式的日期格式化器
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 将毫秒时间转换为不带有时分秒的中国日期。
     *
     * @param millis 毫秒时间
     * @return 当地日期
     */
    public LocalDate convertToChinaDate(long millis) {
        return Instant.ofEpochMilli(millis)
                .atZone(zoneId)
                .toLocalDate();
    }

    /**
     * 根将毫秒时间转换为带有时分秒的中国日期。
     *
     * @param millis 毫秒时间
     * @return 当地日期时间
     */
    public LocalDateTime convertToChinaDateTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zoneId);
    }


    /**
     * 将毫秒时间转换为带有时分秒的中国日期时间（ZonedDateTime）。
     *
     * @param millis 毫秒时间
     * @return 当地日期时间（ZonedDateTime）
     */
    public ZonedDateTime convertToChinaZonedDateTime(long millis) {
        return Instant.ofEpochMilli(millis)
                .atZone(zoneId);
    }

    /**
     * 将 Date 转换为不带有时分秒的日期
     *
     * @param date 日期
     * @return 日期字符串
     */
    public String convertToString(LocalDate date) {
        return date.format(dateFormatter);
    }

    /**
     * 将LocalDateTime转换为带有时分秒的日期
     *
     * @param date 日期
     * @return 日期字符串
     */
    public String convertToString(LocalDateTime date) {
        return date.format(dateTimeFormatter);
    }

    /**
     * 将 millis 时间转换为不带有时分秒的日期字符串
     * e.g. 2024-06-01
     *
     * @param millis 毫秒时间
     * @return 日期字符串
     */
    public String convertToDateString(long millis) {
        return convertToString(convertToChinaDate(millis));
    }

    /**
     * 将 millis 时间转换为带有时分秒的日期字符串
     * e.g. 2024-06-01 15:30:45
     *
     * @param millis 毫秒时间
     * @return 日期字符串
     */
    public String convertToDateTimeString(long millis) {
        return convertToString(convertToChinaDateTime(millis));
    }

    /**
     * 将现在的时间转换为不带有时分秒的日期
     * e.g. 2024-06-01
     *
     * @return 字符串
     */
    public String getNowDateString() {
        return convertToString(LocalDate.now(zoneId));
    }

    /**
     * 将现在的时间转换为带有时分秒的日期
     * e.g. 2024-06-01 15:30:45
     *
     * @return 字符串
     */
    public String getNowDateTimeString() {
        return convertToString(LocalDateTime.now(zoneId));
    }

    /**
     * 计算给定起始时间（毫秒，中国时区）之后的下一次 Cron 触发时间。
     *
     * @param cron   Cron表达式
     * @param millis 起始毫秒（中国时区的时间戳）
     * @return 下一次触发时间的毫秒数（中国时区），如果没有下一次则返回 -1
     */
    public long getNextTriggerTime(CronExpression cron, long millis) {
        return getNextTriggerTime(cron, convertToChinaZonedDateTime(millis));
    }

    /**
     * 计算给定起始时间之后的下一次 Cron 触发时间。
     *
     * @param cron  Cron表达式
     * @param start 起始日期时间（视为中国时区的本地时间）
     * @return 下一次触发时间的毫秒数（中国时区），如果没有下一次则返回 -1
     */
    public long getNextTriggerTime(CronExpression cron, LocalDateTime start) {
        return getNextTriggerTime(cron, start.atZone(zoneId));
    }

    /**
     * 计算给定起始时间（ZonedDateTime）之后的下一次 Cron 触发时间。
     *
     * @param cron  Cron表达式
     * @param zonedDateTime 起始日期时间（可以是任何时区的 ZonedDateTime，将会被转换为中国时区的时间）
     * @return long
     */
    public long getNextTriggerTime(CronExpression cron, ZonedDateTime zonedDateTime) {
        var next = cron.next(zonedDateTime);
        if (next == null) {
            return -1L;
        }
        return next.toInstant().toEpochMilli();
    }
}