package org.zexnocs.teanekoagent.schedule;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 从完整当前原文识别有界任务表达，时间相对原生消息时刻计算，重试不会随服务器时钟漂移。
 * 不解释引文、历史或模型提供的时间，未识别的普通对话交回原 Agent。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
public final class NaturalTaskParser {
    private static final String ID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern INSPECT = Pattern.compile("^请?(查看|查询|取消|删除)(?:提醒)?任务\\s+(" + ID + ")$");
    private static final Pattern LIST = Pattern.compile("^请?(?:查看|查询|列出|看看)(?:我的)?(?:提醒)?任务(?:列表)?$");
    private static final Pattern NEXT = Pattern.compile("^请?查看任务下一页\\s+(" + ID + ")$");
    private static final Pattern CREATE = Pattern.compile("^请?(.{1,96}?)提醒我[，,\\s]*(.{1,2000})$", Pattern.DOTALL);
    private static final Pattern DURATION = Pattern.compile("^(每)?([1-9]\\d{0,5})(秒|分钟|小时|天)(后)?$");
    private static final Pattern DAY = Pattern.compile("^(今天|明天|后天)([0-2]\\d:[0-5]\\d)$");

    /** 工具类不创建实例。 */
    private NaturalTaskParser() { }

    /**
     * 完整匹配后才产生候选；像“有人说……”的引用不会变成已授权的时间表达。
     * @param original 当前未改写原文
     * @param sentAt 平台原始发送时刻
     * @param configuredZone 明确配置的时区，空值表示日历时间需要澄清
     * @return 候选；普通对话为空
     */
    public static Optional<TaskIntent> parse(String original, Instant sentAt, String configuredZone) {
        var text = original.strip();
        var inspect = INSPECT.matcher(text);
        if (inspect.matches()) { return Optional.of(new TaskIntent.Inspect(UUID.fromString(inspect.group(2)),
                inspect.group(1).equals("取消") || inspect.group(1).equals("删除"))); }
        if (LIST.matcher(text).matches()) { return Optional.of(new TaskIntent.ListTasks("", text.contains("我的"))); }
        var next = NEXT.matcher(text);
        if (next.matches()) { return Optional.of(new TaskIntent.ListTasks(UUID.fromString(next.group(1)).toString(), false)); }
        if (text.matches("^请?(?:查看|查询|取消|删除)(?:提醒)?任务.*$")) {
            return Optional.of(new TaskIntent.Clarify("请提供完整任务 UUID；可先发送“查看我的任务”查找，再明确查看或取消的目标。"));
        }
        var create = CREATE.matcher(text);
        if (!create.matches()) {
            return text.startsWith("提醒我") || text.startsWith("请提醒我")
                    ? Optional.of(new TaskIntent.Clarify("请明确提醒时间和正文，例如：10分钟后提醒我喝水；日历时间还需明确时区。")) : Optional.empty();
        }
        try { return Optional.of(time(create.group(1).strip(), create.group(2).strip(), sentAt, configuredZone)); }
        catch (DateTimeException | ArithmeticException invalid) {
            return Optional.of(new TaskIntent.Clarify("提醒时间无效或存在夏令时歧义，请提供明确的日期、时间、偏移及其时区。"));
        }
    }

    /**
     * 相对时长无需本地日历，明确以 UTC 展示；日历表达只能使用原文或配置中的时区。
     * @param expression 原文中的时间表达
     * @param body 字面正文
     * @param sentAt 固定消息时刻
     * @param configuredZone 已配置时区
     * @return 创建或澄清候选
     */
    private static TaskIntent time(String expression, String body, Instant sentAt, String configuredZone) {
        var duration = DURATION.matcher(expression);
        if (duration.matches() && (duration.group(1) != null ^ duration.group(4) != null)) {
            long unit = switch (duration.group(3)) { case "秒" -> 1; case "分钟" -> 60; case "小时" -> 3600; default -> 86400; };
            long seconds = Math.multiplyExact(Long.parseLong(duration.group(2)), unit);
            if (seconds > 31_536_000 || duration.group(1) != null && seconds < 60) {
                return new TaskIntent.Clarify("相对提醒最多一年；固定重复间隔至少六十秒。");
            }
            return new TaskIntent.Create(body, sentAt.plusSeconds(seconds), "UTC", duration.group(1) == null ? 0 : seconds);
        }
        String value = expression; String zoneName = configuredZone; boolean explicitZone = false;
        int split = value.lastIndexOf(' ');
        if (split > 0) {
            try { zoneName = ZoneId.of(value.substring(split + 1)).getId(); value = value.substring(0, split); explicitZone = true; }
            catch (DateTimeException ignored) { /* 日期与时间间的空格不是时区分隔。 */ }
        }
        try {
            var offset = OffsetDateTime.parse(value);
            var zone = explicitZone ? ZoneId.of(zoneName) : offset.getOffset();
            if (!zone.getRules().getValidOffsets(offset.toLocalDateTime()).contains(offset.getOffset())) { throw new DateTimeException("偏移与时区不一致。"); }
            return new TaskIntent.Create(body, offset.toInstant(), zone.getId(), 0);
        } catch (java.time.format.DateTimeParseException ignored) { /* 继续识别明确的本地日历表达。 */ }
        if (zoneName.isBlank()) { return new TaskIntent.Clarify("该提醒需要明确时区；可在时间后写 Asia/Shanghai，或由 OWNER 配置 taskTimeZone。"); }
        var zone = ZoneId.of(zoneName); var day = DAY.matcher(value); LocalDateTime local;
        if (day.matches()) {
            int days = switch (day.group(1)) { case "今天" -> 0; case "明天" -> 1; default -> 2; };
            local = sentAt.atZone(zone).toLocalDate().plusDays(days).atTime(LocalTime.parse(day.group(2)));
        } else {
            local = LocalDateTime.parse(value.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        var offsets = zone.getRules().getValidOffsets(local);
        if (offsets.size() != 1) { throw new DateTimeException("本地时间不存在或重复。"); }
        return new TaskIntent.Create(body, local.toInstant(offsets.getFirst()), zone.getId(), 0);
    }
}
