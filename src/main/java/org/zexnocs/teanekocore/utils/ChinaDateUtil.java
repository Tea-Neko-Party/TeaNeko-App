package org.zexnocs.teanekocore.utils;

import lombok.Getter;
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
    @Getter
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
     * 将时间点转换为中国时区的日期。
     *
     * @param instant 时间点
     * @return 中国时区日期
     */
    public LocalDate convertToChinaDate(Instant instant) {
        return instant.atZone(zoneId).toLocalDate();
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
     * 将时间点转换为中国时区的本地日期时间，仅用于展示和日历计算。
     *
     * @param instant 时间点
     * @return 中国时区本地日期时间
     */
    public LocalDateTime convertToChinaDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, zoneId);
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
     * 将时间点格式化为中国时区的人类可读日期时间。
     *
     * @param instant 时间点
     * @return {@code yyyy-MM-dd HH:mm:ss} 格式字符串
     */
    public String convertToDateTimeString(Instant instant) {
        return convertToString(convertToChinaDateTime(instant));
    }

    /**
     * 获取当前时间的 {@link LocalDate}
     *
     * @return 当前日期
     */
    public LocalDate getNowDate() {
        return LocalDate.now(zoneId);
    }

    /**
     * 获取当前时间的 {@link LocalDateTime}
     *
     * @return 当前日期时间
     */
    public LocalDateTime getNowDateTime() {
        return LocalDateTime.now(zoneId);
    }

    /**
     * 获取当前时间的 {@link ZonedDateTime}
     *
     * @return 当前域日期时间
     */
    public ZonedDateTime getNowZonedDateTime() {
        return ZonedDateTime.now(zoneId);
    }

    /**
     * 将现在的时间转换为不带有时分秒的日期
     * e.g. 2024-06-01
     *
     * @return 字符串
     */
    public String getNowDateString() {
        return convertToString(getNowDate());
    }

    /**
     * 将现在的时间转换为带有时分秒的日期
     * e.g. 2024-06-01 15:30:45
     *
     * @return 字符串
     */
    public String getNowDateTimeString() {
        return convertToString(getNowDateTime());
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
     * 计算指定时间点之后的下一次 Cron 触发时间。
     *
     * @param cron Cron 表达式
     * @param instant 起始时间点
     * @return 下一次触发时间；不存在后续触发时间时返回 {@link Instant#MAX}
     */
    public Instant getNextTriggerTime(CronExpression cron, Instant instant) {
        var next = cron.next(instant.atZone(zoneId));
        return next == null ? Instant.MAX : next.toInstant();
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
