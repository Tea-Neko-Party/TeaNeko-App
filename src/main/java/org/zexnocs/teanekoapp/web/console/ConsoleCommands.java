package org.zexnocs.teanekoapp.web.console;

import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 原命令目录中的指令台入口，展示独立管理来源与明确发送行为的开发方式。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Command(value = "/console", permission = CommandPermission.ALL, scope = CommandScope.ALL)
@Description("指令台管理命令。/console 查看当前目标；/console say 文本 向所选真实聊天发送文本。仅接受受信管理来源。")
public final class ConsoleCommands {
    /**
     * @param command 原管理调用，不是平台成员
     * @return 本地输出已记录
     */
    @DefaultCommand
    @ManagementCommand
    @Description("查看当前已固定的目标。示例：/console。只在指令台输出，不向聊天发送。")
    public CompletionStage<Void> status(CommandData<ConsoleInvocation> command) {
        return command.getRawData().print("当前目标：" + command.getRawData().targetName()
                + "。执行者是网页管理操作者，未冒充群成员。账号与连接在实际发送前再次校验。");
    }

    /**
     * @param command 原管理调用
     * @param text 使用原参数绑定器解析的文本
     * @return 实际平台消息已确认后结束
     */
    @SubCommand("say")
    @ManagementCommand(sendsMessages = true)
    @Description("向所选真实聊天发送一条纯文本。示例：/console say 大家好；含空格可使用引号。发送不会自动重试，请先确认目标。")
    public CompletionStage<Void> say(CommandData<ConsoleInvocation> command, List<String> text) {
        return command.getRawData().send(String.join(" ", text));
    }
}
