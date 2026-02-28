package org.zexnocs.teanekoapp.command;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.CommandDescriptionScanner;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.DefaultValue;
import org.zexnocs.teanekocore.command.interfaces.ICommandPermissionManager;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;

import java.util.Comparator;
import java.util.List;

/**
 * 茶猫帮助指令。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Command(value = {"/help"}, scope = CommandScope.ALL)
public class TeaNekoHelpCommand {

    private final CommandDescriptionScanner commandDescriptionScanner;
    private final ICommandPermissionManager commandPermissionManager;
    private final ICommandScopeManager commandScopeManager;

    public TeaNekoHelpCommand(CommandDescriptionScanner commandDescriptionScanner,
                              ICommandPermissionManager commandPermissionManager,
                              ICommandScopeManager commandScopeManager) {
        this.commandDescriptionScanner = commandDescriptionScanner;
        this.commandPermissionManager = commandPermissionManager;
        this.commandScopeManager = commandScopeManager;
    }

    @DefaultCommand
    public void help(CommandData<ITeaNekoMessageData> commandData, @DefaultValue("1") int index) {
        int MAX_NUMBER_PER_PAGE = 10;
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSender(CommandData.getCommandToken());
        // 根据第一个指令体的首字母进行排序，并筛选出 description 不为空的指令
        List<Command> sortedKeys = commandDescriptionScanner.getDescriptionDataKeySet().stream()
                .filter(o -> commandDescriptionScanner.getDescriptionData(o).getCommandDescription() != null &&
                        commandPermissionManager.hasPermission(o, commandData) &&
                        commandScopeManager.inScope(o, commandData))
                .sorted(Comparator.comparing(o -> o.value()[0]))
                .toList();
        // 获取总页数
        int totalPage = (int) Math.ceil((double) sortedKeys.size() / MAX_NUMBER_PER_PAGE);
        // 判断当前页数是否超过总页数
        if (index > totalPage) {
            messageSender.sendAtReplyMessage("当前页数超过总页数。页数范围为[1," + totalPage + "]", data);
            return;
        }
        // 获取当前页数的指令
        int startIndex = (index - 1) * MAX_NUMBER_PER_PAGE;
        int endIndex = Math.min(startIndex + MAX_NUMBER_PER_PAGE, sortedKeys.size());
        // 发送当前页的指令
        var builder = messageSender.getForwardBuilder(data);
        for(int i = startIndex; i < endIndex; i++) {
            var key = sortedKeys.get(i);
            var description = commandDescriptionScanner.getDescriptionData(key).getCommandDescription();
            // 构造 title
            var titleSb = new StringBuilder();
            titleSb.append(i + 1).append(". {");
            for(var value: key.value()) {
                titleSb.append("\"").append(value).append("\", ");
            }
            // 去掉最后一个空格和逗号
            if(titleSb.length() > 2) {
                titleSb.delete(titleSb.length() - 2, titleSb.length());
            }
            titleSb.append("}: ");
            // 构造 message
            builder.addBotText(titleSb + description.value());
        }
        // 发送当前页数和总页数
        builder.addBotText("当前页数: " + index + "/" + totalPage);
        // 提示下一个页数的指令
        if (index < totalPage) {
            builder.addBotText("输入 /help " + (index + 1) + " 查看下一页");
        }
        // 发送
        builder.send();
    }
}
