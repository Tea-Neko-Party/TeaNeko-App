package org.zexnocs.teanekoplugin.general;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 让机器人说话指令。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 */
@Description("让机器人说话，可以解析unicode字符。但是注意字符不能用空格隔开。规格：/say <message>")
@Command(value = {"/say"},
        scope = CommandScope.ALL,
        permission = CommandPermission.ALL,
        permissionPackage = "chore.all.say")
public class SayCommand {
    /**
     * 规格：/say &lt;message&gt;；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param messageList 保持原始顺序的消息内容列表
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("规格：/say <message>")
    @DefaultCommand
    public CompletionStage<Void> say(CommandData<ITeaNekoMessageData> commandData, List<String> messageList) {
        var data = commandData.getRawData();
        var senderData = data.getUserData();
        StringBuilder result = new StringBuilder();
        result.append("""
                指令操作者：%s (%s)
                """.formatted(senderData.getNickname(), senderData.getUserIdInPlatform()));
        extracted(messageList, result);
        return data.getMessageSender()
                        .sendTextMessageAsync(result.toString());
    }

    /**
     * debug say。不会显示指令操作者信息；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param messageList 保持原始顺序的消息内容列表
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("debug say。不会显示指令操作者信息。")
    @SubCommand(value = {"debug"}, permission = CommandPermission.DEBUG)
    public CompletionStage<Void> debugSay(CommandData<ITeaNekoMessageData> commandData, List<String> messageList) {
        var data = commandData.getRawData();
        StringBuilder result = new StringBuilder();
        extracted(messageList, result);
        return data.getMessageSender()
                .sendTextMessageAsync(result.toString());
    }

    /**
     * 解析 unicode 字符并构建结果字符串。
     *
     * @param messageList 要说的话列表
     * @param result 结果字符串构建器
     */
    private static void extracted(List<String> messageList, StringBuilder result) {
        for(String message: messageList) {
            // 查找unicode字符
            Pattern pattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
            Matcher matcher = pattern.matcher(message);
            while (matcher.find()) {
                char ch = (char) Integer.parseInt(matcher.group(1), 16);
                matcher.appendReplacement(result, String.valueOf(ch));
            }
            matcher.appendTail(result);
            // 添加空格
            result.append(" ");
        }
        // 去掉最后一个空格
        if (!result.isEmpty()) {
            result.setLength(result.length() - 1);
        }
    }
}
