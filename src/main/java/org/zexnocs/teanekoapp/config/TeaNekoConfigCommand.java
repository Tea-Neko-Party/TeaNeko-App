package org.zexnocs.teanekoapp.config;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigDataNotFoundException;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigManagerNotFoundException;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManagerScanner;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.ObjectFieldUtil;

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
    private final IConfigDataService iConfigDataService;

    public TeaNekoConfigCommand(TeaNekoPrivateConfigQueryService teaNekoPrivateConfigQueryService,
                                TeaNekoGroupConfigQueryService teaNekoGroupConfigQueryService,
                                ILogger logger,
                                ConfigManagerScanner configManagerScanner, IConfigDataService iConfigDataService) {
        this.teaNekoPrivateConfigQueryService = teaNekoPrivateConfigQueryService;
        this.teaNekoGroupConfigQueryService = teaNekoGroupConfigQueryService;
        this.logger = logger;
        this.configManagerScanner = configManagerScanner;
        this.iConfigDataService = iConfigDataService;
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
                var textList = commandData.getScope().equals(CommandScope.PRIVATE) ?
                        teaNekoPrivateConfigQueryService.queryAllConfigManagerDetailsInObject(commandData) :
                        teaNekoGroupConfigQueryService.queryAllConfigManagerDetailsInObject(commandData);
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
                String text = commandData.getScope().equals(CommandScope.PRIVATE) ?
                        teaNekoPrivateConfigQueryService.queryOneConfigManagerDetailInObject(rule, commandData) :
                        teaNekoGroupConfigQueryService.queryOneConfigManagerDetailInObject(rule, commandData);
                messageSender.sendTextMessage(text, data);
            } catch (ConfigDataNotFoundException | ConfigManagerNotFoundException e) {
                messageSender.sendReplyMessage("未注册该配置，或者该配置不存在。", data);
            } catch (IllegalAccessException e) {
                messageSender.sendReplyMessage("出现配置字段无法访问的情况喵。", data);
                logger.errorWithReport(this.getClass().getName(), "出现配置字段无法访问的情况喵。", e);
            }
        }
    }

    @Description("""
            查看可以开启的所有/具体的配置。
            规格：/cfg all <?配置名称>
            如果配置名称为空，则以转发的形式显示所有配置。""")
    @SubCommand(value = {"all"})
    public void queryExistingPrivateManagerRules(CommandData<ITeaNekoMessageData> commandData,
                                                 @DefaultValue("") String ruleName) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSender(CommandData.getCommandToken());
        if(ruleName.isBlank()) {
            var textList = commandData.getScope().equals(CommandScope.PRIVATE) ?
                    teaNekoPrivateConfigQueryService.queryAllConfigManagerDetails() :
                    teaNekoGroupConfigQueryService.queryAllConfigManagerDetails();
            if(textList.isEmpty()) {
                messageSender.sendReplyMessage("当前没有任何可用的配置喵。", data);
                return;
            }
            messageSender.getForwardBuilder(data)
                    .addBotAllText(textList)
                    .send();
        } else {
            try {
                var rule = configManagerScanner.getConfigManager(ruleName);
                var text = commandData.getScope().equals(CommandScope.PRIVATE) ?
                        teaNekoPrivateConfigQueryService.queryOneConfigManagerDetail(rule) :
                        teaNekoGroupConfigQueryService.queryOneConfigManagerDetail(rule);
                messageSender.sendTextMessage(text, data);
            } catch (ConfigManagerNotFoundException e) {
                messageSender.sendReplyMessage("配置" + ruleName + "不存在喵。", data);
            }
        }
    }

    @Description("""
            注册一个配置。
            规格：/pm reg <配置名称>
            重复注册将重置该配置。""")
    @SubCommand(value = {"reg", "register"})
    public void registerPrivateManagerRule(CommandData<ITeaNekoMessageData> commandData,
                                           String ruleName) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSender(CommandData.getCommandToken());
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            iConfigDataService.registerConfig(rule, commandData.getScopeId());
            messageSender.sendReplyMessage("注册配置 " + ruleName + " 成功喵。", data);
        } catch (ConfigManagerNotFoundException e) {
            messageSender.sendReplyMessage("配置" + ruleName + "不存在喵。", data);
        }
    }

    @Description("""
           注销一个配置。)
           规格：/pm unreg <配置名称>
           注销后会删除该配置的所有数据，且不可撤回。""")
    @SubCommand(value = {"unreg"})
    public void unregisterPrivateManagerRule(CommandData<ITeaNekoMessageData> commandData,
                                             String ruleName) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSender(CommandData.getCommandToken());
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            if (iConfigDataService.unregisterConfig(rule, commandData.getScopeId())) {
                messageSender.sendReplyMessage("注销配置 " + ruleName + " 成功喵。", data);
            } else {
                messageSender.sendReplyMessage("该配置未注册喵。", data);
            }
        } catch (ConfigManagerNotFoundException e) {
            messageSender.sendReplyMessage("配置" + ruleName + "不存在喵。", data);
        }
    }

    @Description("""
            修改某一个配置字段的值。
            规格：/pm set <配置名称> <配置字段> <配置值>""")
    @SubCommand(value = {"set"})
    public void setPrivateManagerRuleConfig(CommandData<ITeaNekoMessageData> commandData,
                                            String ruleName, String configField, List<String> configValueList) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSender(CommandData.getCommandToken());
        var configValue = String.join(" ", configValueList);
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            iConfigDataService.setRuleConfigField(rule, commandData.getScopeId(), configField, configValue);
            messageSender.sendReplyMessage("配置 " + ruleName + " 的配置字段 " + configField + " 设置成功喵", data);
        } catch (ConfigManagerNotFoundException e) {
            messageSender.sendReplyMessage("配置" + ruleName + "不存在喵。", data);
        } catch (ConfigDataNotFoundException e) {
            messageSender.sendReplyMessage("您尚未注册配置 " + ruleName + " 喵。", data);
        } catch (NoSuchFieldException e) {
            messageSender.sendReplyMessage("配置 " + ruleName + " 中不存在配置字段 " + configField + " 喵。", data);
        } catch (IllegalArgumentException e) {
            messageSender.sendReplyMessage("配置字段 " + configField + " 的值不合法喵。", data);
        } catch (IllegalAccessException e) {
            messageSender.sendReplyMessage("配置字段 " + configField + " 无法访问喵。", data);
            logger.errorWithReport(this.getClass().getName(), "配置字段 " + configField + " 无法访问喵。", e);
        } catch (Exception e) {
            messageSender.sendReplyMessage("发生未知错误喵，请联系开发者。", data);
            logger.errorWithReport(this.getClass().getName(), "发生未知错误。", e);
        }
    }

    @Description("""
            为某一个配置中的 list 字段添加一个值。
            规格：/pm add <配置名称> <配置字段> <配置值>
            注意：配置字段必须是 list 类型的字段。""")
    @SubCommand(value = {"add"})
    public void addPrivateManagerRuleConfigList(CommandData<ITeaNekoMessageData> commandData,
                                                String ruleName, String configField, List<String> configValueList) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSender(CommandData.getCommandToken());
        var configValue = String.join(" ", configValueList);
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            iConfigDataService.addToRuleConfigListFiled(rule, commandData.getScopeId(), configField, configValue);
            messageSender.sendReplyMessage("配置 " + ruleName + " 的配置字段 " + configField + " 已添加值 " + configValue + " 喵。", data);
        } catch (ConfigManagerNotFoundException e) {
            messageSender.sendReplyMessage("配置" + ruleName + "不存在喵。", data);
        } catch (ConfigDataNotFoundException e) {
            messageSender.sendReplyMessage("您尚未注册配置 " + ruleName + " 喵。", data);
        } catch (NoSuchFieldException e) {
            messageSender.sendReplyMessage("配置 " + ruleName + " 中不存在配置字段 " + configField + " 喵。", data);
        } catch (ObjectFieldUtil.FieldNotListException e) {
            messageSender.sendReplyMessage("该字段不是 list 喵。", data);
        } catch (IllegalArgumentException e) {
            messageSender.sendReplyMessage("配置字段 " + configField + " 的值不合法喵。", data);
        } catch (IllegalAccessException e) {
            messageSender.sendReplyMessage("配置字段 " + configField + " 无法访问喵。", data);
            logger.errorWithReport(this.getClass().getName(), "配置字段 " + configField + " 无法访问喵。", e);
        } catch (Exception e) {
            messageSender.sendReplyMessage("发生未知错误喵，请联系开发者。", data);
            logger.errorWithReport(this.getClass().getName(), "发生未知错误。", e);
        }
    }

    @Description("""
            为某一个配置中的 list 字段删除一个值。
            规格：/pm remove <配置名称> <配置字段> <index>
            注意：配置字段必须是 list 类型的字段。""")
    @SubCommand(value = {"remove"})
    public void removePrivateManagerRuleConfigList(CommandData<ITeaNekoMessageData> commandData,
                                                   String ruleName, String configField, int index) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSender(CommandData.getCommandToken());
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            iConfigDataService.removeFromRuleConfigListFiled(rule, commandData.getScopeId(), configField, index);
            messageSender.sendReplyMessage("配置 " + ruleName + " 的配置字段 " + configField + " 已删除索引为 " + index + " 的值喵。", data);
        } catch (ConfigManagerNotFoundException e) {
            messageSender.sendReplyMessage("配置" + ruleName + "不存在喵。", data);
        } catch (ConfigDataNotFoundException e) {
            messageSender.sendReplyMessage("您尚未注册配置 " + ruleName + " 喵。", data);
        } catch (NoSuchFieldException e) {
            messageSender.sendReplyMessage("配置 " + ruleName + " 中不存在配置字段 " + configField + " 喵。", data);
        } catch (ObjectFieldUtil.FieldNotListException e) {
            messageSender.sendReplyMessage("该字段不是 list 喵。", data);
        } catch (IndexOutOfBoundsException e) {
            messageSender.sendReplyMessage("配置字段 " + configField + " 的索引超出范围喵。", data);
        } catch (IllegalAccessException e) {
            messageSender.sendReplyMessage("配置字段 " + configField + " 无法访问喵。", data);
            logger.errorWithReport(this.getClass().getName(), "配置字段 " + configField + " 无法访问喵。", e);
        } catch (Exception e) {
            messageSender.sendReplyMessage("发生未知错误喵，请联系开发者。", data);
            logger.errorWithReport(this.getClass().getName(), "发生未知错误。", e);
        }
    }

    @Description("""
            清除某一个配置中的 list 字段。
            规格：/pm clear <配置名称> <配置字段>
            注意：配置字段必须是 list 类型的字段。""")
    @SubCommand(value = {"clear"})
    public void clearPrivateManagerRuleConfigList(CommandData<ITeaNekoMessageData> commandData,
                                                  String ruleName, String configField) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSender(CommandData.getCommandToken());
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            iConfigDataService.clearRuleConfigListFiled(rule, commandData.getScopeId(), configField);
            messageSender.sendReplyMessage("配置 " + ruleName + " 的配置字段 " + configField + " 已清空喵。", data);
        } catch (ConfigManagerNotFoundException e) {
            messageSender.sendReplyMessage("配置" + ruleName + "不存在喵。", data);
        } catch (ConfigDataNotFoundException e) {
            messageSender.sendReplyMessage("您尚未注册配置 " + ruleName + " 喵。", data);
        } catch (NoSuchFieldException e) {
            messageSender.sendReplyMessage("配置 " + ruleName + " 中不存在配置字段 " + configField + " 喵。", data);
        } catch (ObjectFieldUtil.FieldNotListException e) {
            messageSender.sendReplyMessage("该字段不是 list 喵。", data);
        } catch (IllegalAccessException e) {
            messageSender.sendReplyMessage("配置字段 " + configField + " 无法访问喵。", data);
            logger.errorWithReport(this.getClass().getName(), "配置字段 " + configField + " 无法访问喵。", e);
        } catch (Exception e) {
            messageSender.sendReplyMessage("发生未知错误喵，请联系开发者。", data);
            logger.errorWithReport(this.getClass().getName(), "发生未知错误。", e);
        }
    }
}
