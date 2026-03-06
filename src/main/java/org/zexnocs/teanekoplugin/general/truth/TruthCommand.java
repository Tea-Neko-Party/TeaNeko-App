package org.zexnocs.teanekoplugin.general.truth;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 真心话游戏指令
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
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

    @Autowired
    public TruthCommand(TruthService truthService) {
        this.truthService = truthService;
    }

    @Description("""
            开始游戏。此时需要参与者使用 /d 投掷骰子。
            格式：/真心话 开始 <?自动结束时间，单位秒>
            自动结束时间默认为 35 秒后。""")
    @SubCommand("开始")
    public void start(CommandData<ITeaNekoMessageData> commandData, @DefaultValue("35") int autoEndTime) {
        var data = commandData.getRawData();
        truthService.start(data, autoEndTime);
    }

    @Description("结算游戏。此时自动选择问与答的玩家。")
    @SubCommand("结束")
    public void end(CommandData<ITeaNekoMessageData> commandData) {
        var data = commandData.getRawData();
        truthService.end(data, data.getScopeId());
    }
}
