package org.zexnocs.teanekoplugin.general.truth;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.concurrent.CompletionStage;

/**
 * 真心话游戏指令
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 */
@Description("""
        真心话游戏指令。
        使用 "真心话 开始" 开始游戏;
        使用"/d" 投掷骰子;
        使用 "真心话 结束" 来结算游戏。
        具体规则请看群规喵。""")
@Command(value = {"/truth", "/真心话", "真心话"},
        scope = CommandScope.GROUP,
        permission = CommandPermission.ALL)
public class TruthCommand {
    private final TruthService truthService;

    /**
     * 注入真心话业务服务，使开始、结束和通知按原状态机执行。
     *
     * @param truthService 由装配方提供的 {@code TruthService} 协作依赖
     */
    @Autowired
    public TruthCommand(TruthService truthService) {
        this.truthService = truthService;
    }

    /**
     * 开始游戏。此时需要参与者使用 /d 投掷骰子；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param autoEndTime 用于本次处理的 {@code autoEndTime} 数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            开始游戏。此时需要参与者使用 /d 投掷骰子。
            格式：/真心话 开始 <?自动结束时间，单位秒>
            自动结束时间默认为 35 秒后。""")
    @SubCommand("开始")
    public CompletionStage<Void> start(CommandData<ITeaNekoMessageData> commandData, @DefaultValue("35") int autoEndTime) {
        var data = commandData.getRawData();
        return truthService.start(data, autoEndTime);
    }

    /**
     * 结算游戏。此时自动选择问与答的玩家；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("结算游戏。此时自动选择问与答的玩家。")
    @SubCommand("结束")
    public CompletionStage<Void> end(CommandData<ITeaNekoMessageData> commandData) {
        var data = commandData.getRawData();
        return truthService.end(data, data.getScopeId());
    }
}
