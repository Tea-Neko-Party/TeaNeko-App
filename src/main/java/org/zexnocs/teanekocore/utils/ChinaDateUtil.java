package org.zexnocs.teanekocore.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 中国日期工具类。
 *
 * @author zExNocs
 * @date 2026/02/12
 */
public enum ChinaDateUtil {
    Instance;

    private final ZoneId zoneId = ZoneId.of("Asia/Shanghai");

    /**
     * 将毫秒时间转换为不带有时分秒的中国日期。
     * @param millis 毫秒时间
     * @return 当地日期
     */
    public LocalDate convertToChinaDate(long millis) {
        return new Date(millis).toInstant()
                .atZone(zoneId)
                .toLocalDate();
    }

    /**
     * 根将毫秒时间转换为带有时分秒的中国日期。
     */
    public LocalDateTime convertToChinaDateTime(long millis) {
        return LocalDateTime.ofInstant(new Date(millis).toInstant(), zoneId);
    }

    /**
     * 将Date转换为不带有时分秒的日期
     * @param date 日期
     * @return 日期字符串
     */
    public String convertToString(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return date.format(formatter);
    }

    /**
     * 将LocalDateTime转换为带有时分秒的日期
     * @param date 日期
     * @return 日期字符串
     */
    public String convertToString(LocalDateTime date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return date.format(formatter);
    }

    /**
     * 将现在的时间转换为不带有时分秒的日期
     * e.g. 2024-06-01
     */
    public String getNowToStringWithoutTime() {
        return convertToString(LocalDate.now(zoneId));
    }

    /**
     * 将现在的时间转换为带有时分秒的日期
     * e.g. 2024-06-01 15:30:45
     */
    public String getNowToStringWithTime() {
        return convertToString(LocalDateTime.now(zoneId));
    }
}
