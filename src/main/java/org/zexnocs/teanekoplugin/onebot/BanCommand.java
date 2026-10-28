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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 群禁言命令。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.1
 * @version 4.5.0-l
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

    /**
     * 装配平台禁言与 DEBUG 身份服务，沿原消息和权限链执行管理动作。
     *
     * @param groupBanSender 由装配方提供的 {@code GroupBanSender} 协作依赖
     * @param onebotDebuggerService 由装配方提供的 {@code OnebotDebuggerService} 协作依赖
     */
    @Autowired
    public BanCommand(GroupBanSender groupBanSender, OnebotDebuggerService onebotDebuggerService) {
        this.groupBanSender = groupBanSender;
        this.onebotDebuggerService = onebotDebuggerService;
    }

    /**
     * 禁言某人；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param targetId 原业务规则允许操作的目标标识
     * @param time 事件发生时间
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            禁言某人。
            格式：/ban <目标ID> <?时间>
            时间格式：?d?h?m?s。时间单位不可以省略。
            默认解除禁言。""")
    @DefaultCommand
    public CompletionStage<Void> ban(CommandData<OnebotMessageData> commandData, long targetId, @DefaultValue("") String time) {
        var data = commandData.getRawData();
        var onebotData = data.getOnebotRawMessageData();
        if (onebotDebuggerService.isDebugger(targetId)) {
            return CompletableFuture.completedStage(null); // 不允许禁言主调试账号
        }
        // 最大禁言时间为一个月 - 1 秒
        long maxBanTime = Duration.ofDays(30).minusSeconds(1).getSeconds();
        long timeInSeconds = parseTimeToSeconds(time);
        return groupBanSender.banAsync(onebotData.getGroupId(),
                targetId, Math.min(maxBanTime, timeInSeconds));
    }

    /**
     * 将命令时间表达转换为平台需要的秒数，非法输入保持原校验失败语义。
     *
     * @param time 事件发生时间
     * @return 按当前输入与状态计算的数值
     */
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
