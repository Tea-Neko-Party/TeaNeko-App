package org.zexnocs.teanekoplugin.onebot;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoclient.onebot.core.OnebotDebuggerService;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupBanSender;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 群禁言命令。
 *
 * @author zExNocs
 * @date 2026/03/08
 * @since 4.1.1
 */
@Description("给群猫猫戴上口球！")
@Command(value = {"/ban", "/禁言", "/口球"},
        permission = CommandPermission.OWNER,
        scope = CommandScope.GROUP,
        permissionPackage = "permission.group.ban",
        supportedClients = {OnebotTeaNekoClient.class})
public class BanCommand {
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([dhms])");

    private final GroupBanSender groupBanSender;
    private final OnebotDebuggerService onebotDebuggerService;

    @Autowired
    public BanCommand(GroupBanSender groupBanSender, OnebotDebuggerService onebotDebuggerService) {
        this.groupBanSender = groupBanSender;
        this.onebotDebuggerService = onebotDebuggerService;
    }

    @Description("""
            禁言某人。
            格式：/ban <目标ID> <?时间>
            时间格式：?d?h?m?s。时间单位不可以省略。
            默认解除禁言。""")
    @DefaultCommand
    public void ban(CommandData<OnebotMessageData> commandData, long targetId, @DefaultValue("") String time) {
        var data = commandData.getRawData();
        var onebotData = data.getOnebotRawMessageData();
        if (onebotDebuggerService.isDebugger(targetId)) {
            return; // 不允许禁言主调试账号
        }
        // 最大禁言时间为一个月 - 1 秒
        long maxBanTime = Duration.ofDays(30).minusSeconds(1).getSeconds();
        long timeInSeconds = parseTimeToSeconds(time);
        groupBanSender.ban(onebotData.getGroupId(),
                targetId, Math.min(maxBanTime, timeInSeconds));
    }

    private long parseTimeToSeconds(String time) {
        if (time == null || time.isEmpty()) {
            return 0;
        }

        Matcher matcher = TIME_PATTERN.matcher(time);
        long totalSeconds = 0;

        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            char unit = matcher.group(2).charAt(0);

            switch (unit) {
                case 'd' -> totalSeconds += value * 86400;
                case 'h' -> totalSeconds += value * 3600;
                case 'm' -> totalSeconds += value * 60;
                case 's' -> totalSeconds += value;
            }
        }

        return totalSeconds;
    }
}
