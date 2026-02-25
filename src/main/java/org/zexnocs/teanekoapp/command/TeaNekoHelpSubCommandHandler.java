package org.zexnocs.teanekoapp.command;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.CommandDescriptionScanner;
import org.zexnocs.teanekocore.command.CommandMapData;
import org.zexnocs.teanekocore.command.interfaces.ICommandPermissionManager;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;
import org.zexnocs.teanekocore.command.interfaces.IHelpSubCommandHandler;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.Collections;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * TeaNeko 的帮助子指令处理器，
 *
 * @author zExNocs
 * @date 2026/02/26
 */
@Service
public class TeaNekoHelpSubCommandHandler implements IHelpSubCommandHandler {
    /// 日志记录器
    private final ILogger logger;

    /// 指令描述扫描器
    private final CommandDescriptionScanner commandDescriptionScanner;

    /// 指令权限管理器
    private final ICommandPermissionManager iCommandPermissionManager;

    /// 指令作用域管理器
    private final ICommandScopeManager iCommandScopeManager;

    @Autowired
    public TeaNekoHelpSubCommandHandler(ILogger logger,
                                        CommandDescriptionScanner commandDescriptionScanner,
                                        ICommandPermissionManager iCommandPermissionManager,
                                        ICommandScopeManager iCommandScopeManager) {
        this.logger = logger;
        this.commandDescriptionScanner = commandDescriptionScanner;
        this.iCommandPermissionManager = iCommandPermissionManager;
        this.iCommandScopeManager = iCommandScopeManager;
    }

    /**
     * 处理帮助指令
     *
     * @param commandData 指令数据
     * @param mapData     指令映射数据
     * @param args        剩下的指令参数
     */
    @Override
    public void handleSubHelp(CommandData<?> commandData, CommandMapData mapData, String[] args) {
        ITeaNekoMessageData data;
        try{
            data = (ITeaNekoMessageData) commandData.getRawData();
        } catch (ClassCastException e) {
            logger.errorWithReport("OneBotHelpSubCommandHandler",
                    "该指令的 rawData 不是 MessageReceiveData 类型，无法处理帮助指令", e);
            return;
        }
        // 处理帮助指令
        var messageSender = data.getClient()
                .getTeaNekoToolbox()
                .getMessageSender(CommandData.getCommandToken());
        var commandAnnotation = mapData.getCommandAnnotation();
        var descriptionData = commandDescriptionScanner.getDescriptionData(commandAnnotation);
        if(descriptionData == null ||
                (descriptionData.getCommandDescription() == null &&
                 descriptionData.getSubCommandDescriptionMap().isEmpty() &&
                 descriptionData.getDefaultCommandDescription() == null)) {
            messageSender.sendReplyMessage("该指令没有帮助信息", data);
            return;
        }
        var builder = messageSender.getForwardBuilder(data);
        StringBuilder mainTitle = new StringBuilder();
        mainTitle.append("指令{");
        for(var name: commandAnnotation.value()) {
            mainTitle.append("\"").append(name).append("\", ");
        }
        // 删除最后的逗号和空格
        if(mainTitle.length() > 2) {
            mainTitle.delete(mainTitle.length() - 2, mainTitle.length());
        }
        mainTitle.append("}的帮助信息：");
        builder.addBotText(mainTitle.toString());
        // 添加指令介绍
        if(descriptionData.getCommandDescription() != null) {
            builder.addBotText("==>指令介绍：" + descriptionData.getCommandDescription().value());
        }

        // 添加对权限组的介绍
        var permissionSb = new StringBuilder();
        permissionSb.append("==>权限组：").append(commandAnnotation.permission().name()).append('\n');
        for(var permission: commandAnnotation.permissionPackage()) {
            permissionSb.append(permission).append(", ");
        }
        if(commandAnnotation.permissionPackage().length > 0) {
            // 删除最后的逗号和空格
            if(permissionSb.length() > 2) {
                permissionSb.delete(permissionSb.length() - 2, permissionSb.length());
            }
        } else {
            // 删除最后的换行
            if(permissionSb.length() > 1) {
                permissionSb.deleteCharAt(permissionSb.length() - 1);
            }
        }
        builder.addBotText(permissionSb.toString());

        // 添加默认指令介绍
        var defaultCommandAnnotation = mapData.getDefaultCommandAnnotation();
        if(descriptionData.getDefaultCommandDescription() != null
                && iCommandPermissionManager.hasPermission(commandAnnotation, defaultCommandAnnotation, commandData)
                && iCommandScopeManager.inScope(commandAnnotation, defaultCommandAnnotation, commandData)) {
            builder.addBotText("==>默认指令介绍：" + descriptionData.getDefaultCommandDescription().value());
        }

        // 添加子指令介绍
        var subCommandDescriptions = Optional.ofNullable(descriptionData.getSubCommandDescriptionMap())
                .orElseGet(Collections::emptyMap)
                .entrySet().stream()
                .filter(entry ->
                        entry.getValue() != null && entry.getKey() != null &&
                        iCommandPermissionManager.hasPermission(commandAnnotation, entry.getKey(), commandData) &&
                        iCommandScopeManager.inScope(commandAnnotation, entry.getKey(), commandData)
                )
                .toList();
        if(!subCommandDescriptions.isEmpty()) {
            builder.addBotText("==>子指令介绍：");

            int index = 1;
            for (var entry : subCommandDescriptions) {
                var keyNames = entry.getKey().value();
                var description = entry.getValue().value();

                // 拼接 { "xxx", "yyy" }
                var nameJoiner = new StringJoiner("\", \"", "{\"", "\"}");
                for (String name : keyNames) {
                    nameJoiner.add(name);
                }

                // 构建并输出
                String fullLine = String.format("%d. %s: %s", index++, nameJoiner, description);
                builder.addBotText(fullLine);
            }
        }

        // 发送
        builder.send();
    }
}
