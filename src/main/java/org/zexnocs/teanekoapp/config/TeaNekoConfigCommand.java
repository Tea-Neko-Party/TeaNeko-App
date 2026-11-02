package org.zexnocs.teanekoapp.config;

import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 配置管理指令，提供注册、注销、查询配置的功能
 * <br>4.1.0: 解决了配置 namespaces 固定的问题，将 namespace 与 client 绑定
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.11
 * @version 4.5.0-l
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
    private final List<IConfigCommandFields> extensions;

    /**
     * 组合配置发现、查询和写端口，使确认反馈可以等待真实提交。
     *
     * @param logger 项目日志接口
     * @param configManagerScanner 由装配方提供的 {@code ConfigManagerScanner} 协作依赖
     * @param iConfigDataService 本次装配使用的 {@code IConfigDataService} 服务
     * @param iConfigDataQueryService 本次装配使用的 {@code IConfigDataQueryService} 服务
     */
    public TeaNekoConfigCommand(ILogger logger,
                                ConfigManagerScanner configManagerScanner,
                                IConfigDataService iConfigDataService,
                                IConfigDataQueryService iConfigDataQueryService) {
        this(logger, configManagerScanner, iConfigDataService, iConfigDataQueryService, List.of());
    }

    /**
     * 组合普通 DTO 字段与独立业务字段；装配时拒绝同名扩展，不依赖扫描顺序。
     * @param logger 项目日志
     * @param configManagerScanner 配置发现
     * @param iConfigDataService 普通配置服务
     * @param iConfigDataQueryService 普通配置查询
     * @param extensions 独立业务字段适配器
     * @throws IllegalArgumentException 当扩展重复声明同一字段时
     */
    @Autowired
    public TeaNekoConfigCommand(ILogger logger, ConfigManagerScanner configManagerScanner,
            IConfigDataService iConfigDataService, IConfigDataQueryService iConfigDataQueryService,
            List<IConfigCommandFields> extensions) {
        this.logger = logger;
        this.configManagerScanner = configManagerScanner;
        this.iConfigDataService = iConfigDataService;
        this.iConfigDataQueryService = iConfigDataQueryService;
        this.extensions = List.copyOf(extensions);
        var names = new HashSet<List<String>>();
        for (var extension : extensions) {
            for (var field : extension.fields().keySet()) {
                if (!names.add(List.of(extension.managerName(), field))) { throw new IllegalArgumentException("配置业务字段重复声明。"); }
            }
        }
    }

    /**
     * 查看已经注册的所有/具体的配置；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param ruleName 目标配置名称
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            查看已经注册的所有/具体的配置。
            规格：/cfg <?配置名称>
            如果名称为空，则以转发的形式显示所有配置。""")
    @DefaultCommand
    public CompletionStage<Void> queryCurrentConfig(CommandData<ITeaNekoMessageData> commandData, @DefaultValue("") String ruleName) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                .getEasyBuilder(data);
        var namespaces = buildNamespaces(commandData);
        var scopeId = commandData.getScopeId();
        if(ruleName.isBlank()) {
            try {
                var textList = iConfigDataQueryService.queryAllConfigManagerDetailsInObject(namespaces, scopeId);
                if(textList.isEmpty()) {
                    return messageSender.sendReplyMessageAsync("您没有注册任何配置喵。");
                }
                return details(commandData, ruleName, true, textList).thenCompose(values -> data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                        .getForwardBuilder(data)
                        .addBotAllText(values)
                        .sendByPartAsync(10));
            } catch (IllegalAccessException e) {
                logger.errorWithReport(this.getClass().getName(), "出现配置字段无法访问的情况喵。", e);
            return messageSender.sendReplyMessageAsync("出现配置字段无法访问的情况喵。");
            }
        } else {
            try {
                var configManager = configManagerScanner.getConfigManager(ruleName);
                String text = iConfigDataQueryService.queryOneConfigManagerDetailInObject(configManager, scopeId);
                return details(commandData, ruleName, true, List.of(text))
                        .thenCompose(values -> messageSender.sendTextMessageAsync(String.join("\n", values)));
            } catch (ConfigDataNotFoundException | ConfigManagerNotFoundException e) {
                return messageSender.sendReplyMessageAsync("未注册该配置，或者该配置不存在。");
            } catch (IllegalAccessException e) {
                logger.errorWithReport(this.getClass().getName(), "出现配置字段无法访问的情况喵。", e);
            return messageSender.sendReplyMessageAsync("出现配置字段无法访问的情况喵。");
            }
        }
    }

    /**
     * 查看可以开启的所有/具体的配置；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param ruleName 目标配置名称
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            查看可以开启的所有/具体的配置。
            规格：/cfg all <?配置名称>
            如果配置名称为空，则以转发的形式显示所有配置。""")
    @SubCommand(value = {"all"})
    public CompletionStage<Void> queryExistingConfig(CommandData<ITeaNekoMessageData> commandData,
                                                 @DefaultValue("") String ruleName) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                .getEasyBuilder(data);
        var namespaces = buildNamespaces(commandData);
        if(ruleName.isBlank()) {
            var textList = iConfigDataQueryService.queryAllConfigManagerDetails(namespaces);
            if(textList.isEmpty()) {
                return messageSender.sendReplyMessageAsync("当前没有任何可用的配置喵。");
            }
            return details(commandData, ruleName, false, textList).thenCompose(values -> data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                    .getForwardBuilder(data)
                    .addBotAllText(values)
                    .sendAsync());
        } else {
            try {
                var rule = configManagerScanner.getConfigManager(ruleName);
                var text = iConfigDataQueryService.queryOneConfigManagerDetail(rule);
                return details(commandData, ruleName, false, List.of(text))
                        .thenCompose(values -> messageSender.sendTextMessageAsync(String.join("\n", values)));
            } catch (ConfigManagerNotFoundException e) {
                return messageSender.sendReplyMessageAsync("配置" + ruleName + "不存在喵。");
            }
        }
    }

    /**
     * 注册一个配置；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param ruleName 目标配置名称
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            注册一个配置。
            规格：/cfg reg <配置名称>
            重复注册将重置该配置。""")
    @SubCommand(value = {"reg", "register"})
    public CompletionStage<Void> registerConfig(CommandData<ITeaNekoMessageData> commandData,
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
                return messageSender.sendReplyMessageAsync("配置 " + ruleName + " 不可在当前区域中注册喵。");
            }
            return iConfigDataService.registerConfigAsync(rule, commandData.getScopeId())
                    .thenCompose(ignored -> messageSender.sendReplyMessageAsync("注册配置 " + ruleName + " 成功喵。"));
        } catch (ConfigManagerNotFoundException e) {
            return messageSender.sendReplyMessageAsync("配置" + ruleName + "不存在喵。");
        }
    }

    /**
     * 注销一个配置。)；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param ruleName 目标配置名称
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
           注销一个配置。)
           规格：/cfg unreg <配置名称>
           注销后会删除该配置的所有数据，且不可撤回。""")
    @SubCommand(value = {"unreg"})
    public CompletionStage<Void> unregisterConfig(CommandData<ITeaNekoMessageData> commandData,
                                             String ruleName) {
        var data = commandData.getRawData();
        var sender = data.getMessageSender();
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            return iConfigDataService.unregisterConfigAsync(rule, commandData.getScopeId())
                    .thenCompose(removed -> sender.sendReplyMessageAsync(removed
                            ? "注销配置 " + ruleName + " 成功喵。" : "该配置未注册喵。"));
        } catch (ConfigManagerNotFoundException e) {
            return sender.sendReplyMessageAsync("配置" + ruleName + "不存在喵。");
        }
    }

    /**
     * 修改某一个配置字段的值；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param ruleName 目标配置名称
     * @param configField 需要操作的配置字段
     * @param configValueList 按原始顺序拼接为配置值的命令参数
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            修改某一个配置字段的值。
            规格：/cfg set <配置名称> <配置字段> <配置值>""")
    @SubCommand(value = {"set"})
    public CompletionStage<Void> setConfig(CommandData<ITeaNekoMessageData> commandData,
                                            String ruleName, String configField, List<String> configValueList) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                .getEasyBuilder(data);
        var configValue = String.join(" ", configValueList);
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            for (var extension : extensions) {
                if (!extension.managerName().equals(ruleName) || !extension.fields().containsKey(configField)) { continue; }
                if (hasDtoField(rule.configType(), configField)) {
                    throw new IllegalArgumentException("业务扩展不得覆盖普通配置字段。");
                }
                return extension.set(commandData, configField, configValue)
                        .handle((text, failure) -> failure == null ? text : "业务配置操作未能确认，请核对当前状态；已提交操作不会自动回退。")
                        .thenCompose(messageSender::sendReplyMessageAsync);
            }
            return iConfigDataService.setRuleConfigFieldAsync(rule, commandData.getScopeId(), configField, configValue)
                    .thenCompose(ignored -> messageSender.sendReplyMessageAsync("配置 " + ruleName + " 的配置字段 " + configField + " 设置成功喵"));
        } catch (ConfigManagerNotFoundException e) {
            return messageSender.sendReplyMessageAsync("配置" + ruleName + "不存在喵。");
        } catch (ConfigDataNotFoundException e) {
            return messageSender.sendReplyMessageAsync("您尚未注册配置 " + ruleName + " 喵。");
        } catch (NoSuchFieldException e) {
            return messageSender.sendReplyMessageAsync("配置 " + ruleName + " 中不存在配置字段 " + configField + " 喵。");
        } catch (IllegalArgumentException e) {
            return messageSender.sendReplyMessageAsync("配置字段 " + configField + " 的值不合法喵。");
        } catch (IllegalAccessException e) {
            logger.errorWithReport(this.getClass().getName(), "配置字段 " + configField + " 无法访问喵。", e);
            return messageSender.sendReplyMessageAsync("配置字段 " + configField + " 无法访问喵。");
        } catch (ConfigFieldCheckerFailureException e) {
            return messageSender.sendReplyMessageAsync("配置字段" + configField + "失败喵，原因：" + e.getText());
        }
        catch (Exception e) {
            logger.errorWithReport(this.getClass().getName(), "发生未知错误。", e);
            return messageSender.sendReplyMessageAsync("发生未知错误喵，请联系开发者。");
        }
    }

    /**
     * 按顺序追加业务字段的说明或权威值，不要求同步阻塞等待数据库。
     * @param command 原生配置命令
     * @param manager 筛选管理器，空值表示所有
     * @param current 是否查询当前值
     * @param original 普通 DTO 查询结果
     * @return 独立且不可变的完整展示列表
     */
    private CompletionStage<List<String>> details(CommandData<ITeaNekoMessageData> command, String manager,
            boolean current, List<String> original) {
        CompletionStage<List<String>> result = CompletableFuture.completedStage(new ArrayList<>(original));
        for (var extension : extensions) {
            if (!manager.isBlank() && !manager.equals(extension.managerName())) { continue; }
            result = result.thenCompose(values -> {
                CompletionStage<String> extra;
                try {
                    extra = current ? extension.read(command) : CompletableFuture.completedStage(extension.managerName() + " 业务字段：\n"
                            + extension.fields().entrySet().stream().map(entry -> entry.getKey() + "：" + entry.getValue()).collect(java.util.stream.Collectors.joining("\n")));
                } catch (RuntimeException failure) { extra = CompletableFuture.failedStage(failure); }
                return extra.handle((text, failure) -> {
                    if (failure != null) { values.add("业务配置读取失败，请核对注册状态与存储。"); }
                    else if (!text.isBlank()) { values.add(text); }
                    return values;
                });
            });
        }
        return result.thenApply(List::copyOf);
    }

    /**
     * 沿继承关系检查真实 DTO 字段，防止扩展覆盖父类中的持久化属性。
     * @param type 配置 DTO 类型
     * @param name 待声明业务字段
     * @return 任一父类实际声明该字段时为 true
     */
    private boolean hasDtoField(Class<?> type, String name) {
        for (var current = type; current != null; current = current.getSuperclass()) {
            if (Arrays.stream(current.getDeclaredFields()).anyMatch(field -> field.getName().equals(name))) { return true; }
        }
        return false;
    }

    /**
     * 为某一个配置中的 list 字段添加一个值；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param ruleName 目标配置名称
     * @param configField 需要操作的配置字段
     * @param configValueList 按原始顺序拼接为配置值的命令参数
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            为某一个配置中的 list 字段添加一个值。
            规格：/cfg add <配置名称> <配置字段> <配置值>
            注意：配置字段必须是 list 类型的字段。""")
    @SubCommand(value = {"add"})
    public CompletionStage<Void> addToConfigList(CommandData<ITeaNekoMessageData> commandData,
                                                String ruleName, String configField, List<String> configValueList) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                .getEasyBuilder(data);
        var configValue = String.join(" ", configValueList);
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            return iConfigDataService.addToRuleConfigListFiledAsync(rule, commandData.getScopeId(), configField, configValue)
                    .thenCompose(ignored -> messageSender.sendReplyMessageAsync("配置 " + ruleName + " 的配置字段 " + configField + " 已添加值 " + configValue + " 喵。"));
        } catch (ConfigManagerNotFoundException e) {
            return messageSender.sendReplyMessageAsync("配置" + ruleName + "不存在喵。");
        } catch (ConfigDataNotFoundException e) {
            return messageSender.sendReplyMessageAsync("您尚未注册配置 " + ruleName + " 喵。");
        } catch (NoSuchFieldException e) {
            return messageSender.sendReplyMessageAsync("配置 " + ruleName + " 中不存在配置字段 " + configField + " 喵。");
        } catch (ObjectFieldUtil.FieldNotListException e) {
            return messageSender.sendReplyMessageAsync("该字段不是 list 喵。");
        } catch (IllegalArgumentException e) {
            return messageSender.sendReplyMessageAsync("配置字段 " + configField + " 的值不合法喵。");
        } catch (IllegalAccessException e) {
            logger.errorWithReport(this.getClass().getName(), "配置字段 " + configField + " 无法访问喵。", e);
            return messageSender.sendReplyMessageAsync("配置字段 " + configField + " 无法访问喵。");
        } catch (ConfigFieldCheckerFailureException e) {
            return messageSender.sendReplyMessageAsync("配置字段" + configField + "失败喵，原因：" + e.getText());
        } catch (Exception e) {
            logger.errorWithReport(this.getClass().getName(), "发生未知错误。", e);
            return messageSender.sendReplyMessageAsync("发生未知错误喵，请联系开发者。");
        }
    }

    /**
     * 为某一个配置中的 list 字段删除一个值；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param ruleName 目标配置名称
     * @param configField 需要操作的配置字段
     * @param index 目标项索引或展示页码，按当前入口约定解释
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            为某一个配置中的 list 字段删除一个值。
            规格：/cfg remove <配置名称> <配置字段> <index>
            注意：配置字段必须是 list 类型的字段。""")
    @SubCommand(value = {"remove"})
    public CompletionStage<Void> removeFromConfigList(CommandData<ITeaNekoMessageData> commandData,
                                                   String ruleName, String configField, int index) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                .getEasyBuilder(data);
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            return iConfigDataService.removeFromRuleConfigListFiledAsync(rule, commandData.getScopeId(), configField, index)
                    .thenCompose(ignored -> messageSender.sendReplyMessageAsync("配置 " + ruleName + " 的配置字段 " + configField + " 已删除索引为 " + index + " 的值喵。"));
        } catch (ConfigManagerNotFoundException e) {
            return messageSender.sendReplyMessageAsync("配置" + ruleName + "不存在喵。");
        } catch (ConfigDataNotFoundException e) {
            return messageSender.sendReplyMessageAsync("您尚未注册配置 " + ruleName + " 喵。");
        } catch (NoSuchFieldException e) {
            return messageSender.sendReplyMessageAsync("配置 " + ruleName + " 中不存在配置字段 " + configField + " 喵。");
        } catch (ObjectFieldUtil.FieldNotListException e) {
            return messageSender.sendReplyMessageAsync("该字段不是 list 喵。");
        } catch (IndexOutOfBoundsException e) {
            return messageSender.sendReplyMessageAsync("配置字段 " + configField + " 的索引超出范围喵。");
        } catch (IllegalAccessException e) {
            logger.errorWithReport(this.getClass().getName(), "配置字段 " + configField + " 无法访问喵。", e);
            return messageSender.sendReplyMessageAsync("配置字段 " + configField + " 无法访问喵。");
        } catch (Exception e) {
            logger.errorWithReport(this.getClass().getName(), "发生未知错误。", e);
            return messageSender.sendReplyMessageAsync("发生未知错误喵，请联系开发者。");
        }
    }

    /**
     * 清除某一个配置中的 list 字段；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param ruleName 目标配置名称
     * @param configField 需要操作的配置字段
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            清除某一个配置中的 list 字段。
            规格：/cfg clear <配置名称> <配置字段>
            注意：配置字段必须是 list 类型的字段。""")
    @SubCommand(value = {"clear"})
    public CompletionStage<Void> clearFromConfigList(CommandData<ITeaNekoMessageData> commandData,
                                                  String ruleName, String configField) {
        var data = commandData.getRawData();
        var messageSender = data.getClient().getTeaNekoToolbox().getMessageSenderTools()
                .getEasyBuilder(data);
        try {
            var rule = configManagerScanner.getConfigManager(ruleName);
            return iConfigDataService.clearRuleConfigListFiledAsync(rule, commandData.getScopeId(), configField)
                    .thenCompose(ignored -> messageSender.sendReplyMessageAsync("配置 " + ruleName + " 的配置字段 " + configField + " 已清空喵。"));
        } catch (ConfigManagerNotFoundException e) {
            return messageSender.sendReplyMessageAsync("配置" + ruleName + "不存在喵。");
        } catch (ConfigDataNotFoundException e) {
            return messageSender.sendReplyMessageAsync("您尚未注册配置 " + ruleName + " 喵。");
        } catch (NoSuchFieldException e) {
            return messageSender.sendReplyMessageAsync("配置 " + ruleName + " 中不存在配置字段 " + configField + " 喵。");
        } catch (ObjectFieldUtil.FieldNotListException e) {
            return messageSender.sendReplyMessageAsync("该字段不是 list 喵。");
        } catch (IllegalAccessException e) {
            logger.errorWithReport(this.getClass().getName(), "配置字段 " + configField + " 无法访问喵。", e);
            return messageSender.sendReplyMessageAsync("配置字段 " + configField + " 无法访问喵。");
        } catch (Exception e) {
            logger.errorWithReport(this.getClass().getName(), "发生未知错误。", e);
            return messageSender.sendReplyMessageAsync("发生未知错误喵，请联系开发者。");
        }
    }

    /**
     * 根据 commandData 构造 namespaces
     *
     * @param commandData 指令数据
     * @return 当前命令来源可以访问的配置管理命名空间
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
