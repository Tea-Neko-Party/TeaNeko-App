package org.zexnocs.teanekoapp.config;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigDataNotFoundException;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManagerScanner;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.List;

/**
 * 配置管理指令，提供注册、注销、查询配置的功能
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Description("配置管理指令，提供注册、注销、查询配置的功能。群组中只允许群主使用该指令。")
@Command(value = {"/config", "/cfg", "/配置"},
        scope = CommandScope.ALL,
        permission = CommandPermission.OWNER,
        permissionPackage = {"teaNeko-config"})
public class TeaNekoConfigCommand {
    private final TeaNekoPrivateConfigQueryService teaNekoPrivateConfigQueryService;
    private final TeaNekoGroupConfigQueryService teaNekoGroupConfigQueryService;
    private final ILogger logger;
    private final ConfigManagerScanner configManagerScanner;

    public TeaNekoConfigCommand(TeaNekoPrivateConfigQueryService teaNekoPrivateConfigQueryService,
                                TeaNekoGroupConfigQueryService teaNekoGroupConfigQueryService,
                                ILogger logger,
                                ConfigManagerScanner configManagerScanner) {
        this.teaNekoPrivateConfigQueryService = teaNekoPrivateConfigQueryService;
        this.teaNekoGroupConfigQueryService = teaNekoGroupConfigQueryService;
        this.logger = logger;
        this.configManagerScanner = configManagerScanner;
    }

    @Description("""
            查看已经注册的所有/具体的配置。
            规格：/cfg <?配置名称>
            如果名称为空，则以转发的形式显示所有配置。""")
    @DefaultCommand
    public void queryCurrentConfig(CommandData<ITeaNekoMessageData> commandData, @DefaultValue("") String ruleName) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSender(CommandData.getCommandToken());
        if(ruleName.isBlank()) {
            try {
                List<String> textList;
                if(commandData.getScope().equals(CommandScope.PRIVATE)) {
                    textList = teaNekoPrivateConfigQueryService.queryAllConfigManagerDetailsInObject(commandData);
                } else {
                    textList = teaNekoGroupConfigQueryService.queryAllConfigManagerDetailsInObject(commandData);
                }

                if(textList.isEmpty()) {
                    messageSender.sendReplyMessage("您没有注册任何配置喵。", data);
                    return;
                }
                messageSender.getForwardBuilder(data)
                        .addBotAllText(textList)
                        .sendByPart(10);
            } catch (IllegalAccessException e) {
                messageSender.sendReplyMessage("出现配置字段无法访问的情况喵。", data);
                logger.errorWithReport(this.getClass().getName(), "出现配置字段无法访问的情况喵。", e);
            }
        } else {
            try {
                var rule = configManagerScanner.getConfigManager(ruleName);
                String text;
                if(commandData.getScope().equals(CommandScope.PRIVATE)) {
                    text = teaNekoPrivateConfigQueryService.queryOneConfigManagerDetailInObject(rule, commandData);
                } else {
                    text = teaNekoGroupConfigQueryService.queryOneConfigManagerDetailInObject(rule, commandData);
                }
                messageSender.sendTextMessage(text, data);
            } catch (ConfigDataNotFoundException e) {
                messageSender.sendReplyMessage("未注册该配置，或者该配置不存在。", data);
            } catch (IllegalAccessException e) {
                messageSender.sendReplyMessage("出现配置字段无法访问的情况喵。", data);
                logger.errorWithReport(this.getClass().getName(), "出现配置字段无法访问的情况喵。", e);
            }
        }
    }
}
