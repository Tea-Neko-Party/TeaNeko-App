package org.zexnocs.teanekoapp.config;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigDataNotFoundException;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigFieldCheckerFailureException;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigManagerNotFoundException;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataQueryService;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManagerScanner;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.ObjectFieldUtil;

import java.util.*;

/**
 * 配置管理指令，提供注册、注销、查询配置的功能
 * <br>4.1.0: 解决了配置 namespaces 固定的问题，将 namespace 与 client 绑定
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 * @version 4.1.0
 */
@Description("配置管理指令，提供注册、注销、查询配置的功能。群组中只允许群主使用该指令。")
@Command(value = {"/config", "/cfg", "/配置"},
        scope = CommandScope.ALL,
        permission = CommandPermission.OWNER,
        permissionPackage = {"teaNeko-config"})
public class TeaNekoConfigCommand {
    private final ILogger logger;
    private final ConfigManagerScanner configManagerScanner;
    private final IConfigDataService iConfigDataService;
    private final IConfigDataQueryService iConfigDataQueryService;

    public TeaNekoConfigCommand(ILogger logger,
                                ConfigManagerScanner configManagerScanner,
                                IConfigDataService iConfigDataService,
                                IConfigDataQueryService iConfigDataQueryService) {
        this.logger = logger;
        this.configManagerScanner = configManagerScanner;
        this.iConfigDataService = iConfigDataService;
        this.iConfigDataQueryService = iConfigDataQueryService;
    }

    @Description("""
            查看已经注册的所有/具体的配置。
            规格：/cfg <?配置名称>
            如果名称为空，则以转发的形式显示所有配置。""")
    @DefaultCommand
    public void queryCurrentConfig(CommandData<ITeaNekoMessageData> commandData, @DefaultValue("") String ruleName) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                .getEasyBuilder(data);
        var namespaces = buildNamespaces(commandData);
        var scopeId = commandData.getScopeId();
        if(ruleName.isBlank()) {
            try {
                var textList = iConfigDataQueryService.queryAllConfigManagerDetailsInObject(namespaces, scopeId);
                if(textList.isEmpty()) {
                    messageSender.sendReplyMessage("您没有注册任何配置喵。");
                    return;
                }
                data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                        .getForwardBuilder(data)
                        .addBotAllText(textList)
                        .sendByPart(10);
            } catch (IllegalAccessException e) {
                messageSender.sendReplyMessage("出现配置字段无法访问的情况喵。");
                logger.errorWithReport(this.getClass().getName(), "出现配置字段无法访问的情况喵。", e);
            }
        } else {
            try {
                var configManager = configManagerScanner.getConfigManager(ruleName);
                String text = iConfigDataQueryService.queryOneConfigManagerDetailInObject(configManager, scopeId);
                messageSender.sendTextMessage(text);
            } catch (ConfigDataNotFoundException | ConfigManagerNotFoundException e) {
                messageSender.sendReplyMessage("未注册该配置，或者该配置不存在。");
            } catch (IllegalAccessException e) {
                messageSender.sendReplyMessage("出现配置字段无法访问的情况喵。");
                logger.errorWithReport(this.getClass().getName(), "出现配置字段无法访问的情况喵。", e);
            }
        }
    }

    @Description("""
            查看可以开启的所有/具体的配置。
            规格：/cfg all <?配置名称>
            如果配置名称为空，则以转发的形式显示所有配置。""")
    @SubCommand(value = {"all"})
    public void queryExistingConfig(CommandData<ITeaNekoMessageData> commandData,
                                                 @DefaultValue("") String ruleName) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                .getEasyBuilder(data);
        var namespaces = buildNamespaces(commandData);
        if(ruleName.isBlank()) {
            var textList = iConfigDataQueryService.queryAllConfigManagerDetails(namespaces);
            if(textList.isEmpty()) {
                messageSender.sendReplyMessage("当前没有任何可用的配置喵。");
                return;
            }
            data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                    .getForwardBuilder(data)
                    .addBotAllText(textList)
                    .send();
        } else {
            try {
                var rule = configManagerScanner.getConfigManager(ruleName);
                var text = iConfigDataQueryService.queryOneConfigManagerDetail(rule);
                messageSender.sendTextMessage(text);
            } catch (ConfigManagerNotFoundException e) {
                messageSender.sendReplyMessage("配置" + ruleName + "不存在喵。");
            }
        }
    }

    @Description("""
            注册一个配置。
            规格：/cfg reg <配置名称>
            重复注册将重置该配置。""")
    @SubCommand(value = {"reg", "register"})
    public void registerConfig(CommandData<ITeaNekoMessageData> commandData,
                                           String ruleName) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                .getEasyBuilder(data);
        var namespaces = buildNamespaces(commandData);
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            // 判断 namespaces 是否与 rule 的 namespaces 有交集，如果没有交集则说明该配置不可用
            var ruleNamespaces = rule.namespaces();
            if(Collections.disjoint(namespaces, Arrays.asList(ruleNamespaces))) {
                messageSender.sendReplyMessage("配置 " + ruleName + " 不可在当前区域中注册喵。");
                return;
            }
            iConfigDataService.registerConfig(rule, commandData.getScopeId());
            messageSender.sendReplyMessage("注册配置 " + ruleName + " 成功喵。");
        } catch (ConfigManagerNotFoundException e) {
            messageSender.sendReplyMessage("配置" + ruleName + "不存在喵。");
        }
    }

    @Description("""
           注销一个配置。)
           规格：/cfg unreg <配置名称>
           注销后会删除该配置的所有数据，且不可撤回。""")
    @SubCommand(value = {"unreg"})
    public void unregisterConfig(CommandData<ITeaNekoMessageData> commandData,
                                             String ruleName) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                .getEasyBuilder(data);
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            if (iConfigDataService.unregisterConfig(rule, commandData.getScopeId())) {
                messageSender.sendReplyMessage("注销配置 " + ruleName + " 成功喵。");
            } else {
                messageSender.sendReplyMessage("该配置未注册喵。");
            }
        } catch (ConfigManagerNotFoundException e) {
            messageSender.sendReplyMessage("配置" + ruleName + "不存在喵。");
        }
    }

    @Description("""
            修改某一个配置字段的值。
            规格：/cfg set <配置名称> <配置字段> <配置值>""")
    @SubCommand(value = {"set"})
    public void setConfig(CommandData<ITeaNekoMessageData> commandData,
                                            String ruleName, String configField, List<String> configValueList) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                .getEasyBuilder(data);
        var configValue = String.join(" ", configValueList);
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            iConfigDataService.setRuleConfigField(rule, commandData.getScopeId(), configField, configValue);
            messageSender.sendReplyMessage("配置 " + ruleName + " 的配置字段 " + configField + " 设置成功喵");
        } catch (ConfigManagerNotFoundException e) {
            messageSender.sendReplyMessage("配置" + ruleName + "不存在喵。");
        } catch (ConfigDataNotFoundException e) {
            messageSender.sendReplyMessage("您尚未注册配置 " + ruleName + " 喵。");
        } catch (NoSuchFieldException e) {
            messageSender.sendReplyMessage("配置 " + ruleName + " 中不存在配置字段 " + configField + " 喵。");
        } catch (IllegalArgumentException e) {
            messageSender.sendReplyMessage("配置字段 " + configField + " 的值不合法喵。");
        } catch (IllegalAccessException e) {
            messageSender.sendReplyMessage("配置字段 " + configField + " 无法访问喵。");
            logger.errorWithReport(this.getClass().getName(), "配置字段 " + configField + " 无法访问喵。", e);
        } catch (ConfigFieldCheckerFailureException e) {
            messageSender.sendReplyMessage("配置字段" + configField + "失败喵，原因：" + e.getText());
        }
        catch (Exception e) {
            messageSender.sendReplyMessage("发生未知错误喵，请联系开发者。");
            logger.errorWithReport(this.getClass().getName(), "发生未知错误。", e);
        }
    }

    @Description("""
            为某一个配置中的 list 字段添加一个值。
            规格：/cfg add <配置名称> <配置字段> <配置值>
            注意：配置字段必须是 list 类型的字段。""")
    @SubCommand(value = {"add"})
    public void addToConfigList(CommandData<ITeaNekoMessageData> commandData,
                                                String ruleName, String configField, List<String> configValueList) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                .getEasyBuilder(data);
        var configValue = String.join(" ", configValueList);
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            iConfigDataService.addToRuleConfigListFiled(rule, commandData.getScopeId(), configField, configValue);
            messageSender.sendReplyMessage("配置 " + ruleName + " 的配置字段 " + configField + " 已添加值 " + configValue + " 喵。");
        } catch (ConfigManagerNotFoundException e) {
            messageSender.sendReplyMessage("配置" + ruleName + "不存在喵。");
        } catch (ConfigDataNotFoundException e) {
            messageSender.sendReplyMessage("您尚未注册配置 " + ruleName + " 喵。");
        } catch (NoSuchFieldException e) {
            messageSender.sendReplyMessage("配置 " + ruleName + " 中不存在配置字段 " + configField + " 喵。");
        } catch (ObjectFieldUtil.FieldNotListException e) {
            messageSender.sendReplyMessage("该字段不是 list 喵。");
        } catch (IllegalArgumentException e) {
            messageSender.sendReplyMessage("配置字段 " + configField + " 的值不合法喵。");
        } catch (IllegalAccessException e) {
            messageSender.sendReplyMessage("配置字段 " + configField + " 无法访问喵。");
            logger.errorWithReport(this.getClass().getName(), "配置字段 " + configField + " 无法访问喵。", e);
        } catch (ConfigFieldCheckerFailureException e) {
            messageSender.sendReplyMessage("配置字段" + configField + "失败喵，原因：" + e.getText());
        } catch (Exception e) {
            messageSender.sendReplyMessage("发生未知错误喵，请联系开发者。");
            logger.errorWithReport(this.getClass().getName(), "发生未知错误。", e);
        }
    }

    @Description("""
            为某一个配置中的 list 字段删除一个值。
            规格：/cfg remove <配置名称> <配置字段> <index>
            注意：配置字段必须是 list 类型的字段。""")
    @SubCommand(value = {"remove"})
    public void removeFromConfigList(CommandData<ITeaNekoMessageData> commandData,
                                                   String ruleName, String configField, int index) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                .getEasyBuilder(data);
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            iConfigDataService.removeFromRuleConfigListFiled(rule, commandData.getScopeId(), configField, index);
            messageSender.sendReplyMessage("配置 " + ruleName + " 的配置字段 " + configField + " 已删除索引为 " + index + " 的值喵。");
        } catch (ConfigManagerNotFoundException e) {
            messageSender.sendReplyMessage("配置" + ruleName + "不存在喵。");
        } catch (ConfigDataNotFoundException e) {
            messageSender.sendReplyMessage("您尚未注册配置 " + ruleName + " 喵。");
        } catch (NoSuchFieldException e) {
            messageSender.sendReplyMessage("配置 " + ruleName + " 中不存在配置字段 " + configField + " 喵。");
        } catch (ObjectFieldUtil.FieldNotListException e) {
            messageSender.sendReplyMessage("该字段不是 list 喵。");
        } catch (IndexOutOfBoundsException e) {
            messageSender.sendReplyMessage("配置字段 " + configField + " 的索引超出范围喵。");
        } catch (IllegalAccessException e) {
            messageSender.sendReplyMessage("配置字段 " + configField + " 无法访问喵。");
            logger.errorWithReport(this.getClass().getName(), "配置字段 " + configField + " 无法访问喵。", e);
        } catch (Exception e) {
            messageSender.sendReplyMessage("发生未知错误喵，请联系开发者。");
            logger.errorWithReport(this.getClass().getName(), "发生未知错误。", e);
        }
    }

    @Description("""
            清除某一个配置中的 list 字段。
            规格：/cfg clear <配置名称> <配置字段>
            注意：配置字段必须是 list 类型的字段。""")
    @SubCommand(value = {"clear"})
    public void clearFromConfigList(CommandData<ITeaNekoMessageData> commandData,
                                                  String ruleName, String configField) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                .getEasyBuilder(data);
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            iConfigDataService.clearRuleConfigListFiled(rule, commandData.getScopeId(), configField);
            messageSender.sendReplyMessage("配置 " + ruleName + " 的配置字段 " + configField + " 已清空喵。");
        } catch (ConfigManagerNotFoundException e) {
            messageSender.sendReplyMessage("配置" + ruleName + "不存在喵。");
        } catch (ConfigDataNotFoundException e) {
            messageSender.sendReplyMessage("您尚未注册配置 " + ruleName + " 喵。");
        } catch (NoSuchFieldException e) {
            messageSender.sendReplyMessage("配置 " + ruleName + " 中不存在配置字段 " + configField + " 喵。");
        } catch (ObjectFieldUtil.FieldNotListException e) {
            messageSender.sendReplyMessage("该字段不是 list 喵。");
        } catch (IllegalAccessException e) {
            messageSender.sendReplyMessage("配置字段 " + configField + " 无法访问喵。");
            logger.errorWithReport(this.getClass().getName(), "配置字段 " + configField + " 无法访问喵。", e);
        } catch (Exception e) {
            messageSender.sendReplyMessage("发生未知错误喵，请联系开发者。");
            logger.errorWithReport(this.getClass().getName(), "发生未知错误。", e);
        }
    }

    /**
     * 根据 commandData 构造 namespaces
     *
     * @param commandData 指令数据
     * @return namespaces
     */
    private List<String> buildNamespaces(CommandData<ITeaNekoMessageData> commandData) {
        var result = new HashSet<String>();
        var annotation = commandData.getRawData().getClient().getClass().getAnnotation(ConfigNamespace.class);
        // 加入 general namespace
        result.add(TeaNekoConfigNamespaces.GENERAL);
        if(annotation != null) {
            result.addAll(Arrays.asList(annotation.generalNamespace()));
        }
        // 根据作用域加入对应的 namespace
        if(commandData.getScope().equals(CommandScope.PRIVATE)) {
            result.add(TeaNekoConfigNamespaces.PRIVATE);
            if(annotation != null) {
                result.addAll(Arrays.asList(annotation.privateNamespace()));
            }
        } else if (commandData.getScope().equals(CommandScope.GROUP)) {
            result.add(TeaNekoConfigNamespaces.GROUP);
            if(annotation != null) {
                result.addAll(Arrays.asList(annotation.groupNamespace()));
            }
        }
        return new ArrayList<>(result);
    }
}
