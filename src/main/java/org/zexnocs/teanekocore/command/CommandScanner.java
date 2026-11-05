package org.zexnocs.teanekocore.command;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 指令扫描器
 * 扫描指令、默认指令和子指令
 *
 * 修改记录：<br>
 * 2026/11/05：扫描时保存合并的类型说明，完整前缀和正则目录一次发布，失败重扫保留旧缓存。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.8.2-l
 * @see Command
 * @see DefaultCommand
 * @see SubCommand
 */
@Service("commandScanner")
public class CommandScanner implements IScanner {
    /// 日志
    private final ILogger logger;

    // 2026/11/05：前缀和正则目录共同发布，不先清空运行中请求可见的数据。
    private volatile Commands commands = new Commands(Map.of(), Map.of());
    private boolean initialized;
    private final IBeanScanner iBeanScanner;

    /**
     * 注入组件发现与日志服务，扫描时统一验证命令方法的公开性和异步契约。
     *
     * @param logger 项目日志接口
     * @param iBeanScanner 本次装配使用的 {@code IBeanScanner} 服务
     */
    @Autowired
    public CommandScanner(ILogger logger, IBeanScanner iBeanScanner) {
        this.logger = logger;
        this.iBeanScanner = iBeanScanner;
    }

    /**
     * 获取全部前缀指令。
     *
     * @return 前缀与注册命令数据的映射，类型为 {@code Map<String, CommandMapData>}
     */
    protected Map<String, CommandMapData> getPrefixCommandMap() {
        // 2026/11/05：读取完整发现快照，不向调用方暴露可变注册表。
        return commands.prefix();
    }

    /**
     * 获取前缀指令。
     * 如果指令不存在，返回null。
     *
     * @param command 指令名
     * @return 匹配的前缀命令数据，未匹配时为 null
     */
    public CommandMapData getPrefixCommand(String command) {
        if(command == null || command.isBlank()) {
            return null;
        }
        // 2026/11/05：键查询只访问当前已发布快照。
        return commands.prefix().get(command);
    }

    /**
     * 获取正则指令所有的 key。
     *
     * @return 正则命令标识集合，类型为 {@code Collection<String>}
     */
    public Collection<String> getRegexpCommandKeys() {
        // 2026/11/05：旧快照游标在显式重扫期间仍可安全遍历。
        return commands.regexp().keySet();
    }

    /**
     * 获取正则指令。
     * 如果指令不存在，返回null。
     *
     * @param command 指令名
     * @return 匹配的正则命令数据，未匹配时为 null
     */
    public CommandMapData getRegexpCommand(String command) {
        if(command == null || command.isBlank()) {
            return null;
        }
        // 2026/11/05：键查询只访问当前已发布快照。
        return commands.regexp().get(command);
    }

    /**
     * 2026/11/05：按同一发现快照列出命令，并去除前缀与正则别名重复项。
     * @return 完整快照中的真实注册命令
     */
    protected Collection<CommandMapData> getRegisteredCommands() {
        var current = commands;
        var found = new LinkedHashSet<>(current.prefix().values());
        found.addAll(current.regexp().values());
        return List.copyOf(found);
    }

    /** 2026/11/05：首次初始化成功后不重复发现，失败由显式重扫修复。 */
    @Override
    public synchronized void init() {
        if (!initialized) {
            _scan();
        }
    }

    /** 2026/11/05：重新发现不清理旧目录，完整校验后再替换。 */
    @Override
    public synchronized void reload() {
        _scan();
    }

    /**
     * 扫描指令。
     * 2026/11/05：同时固定类型说明，全部扫描成功后一次发布两类目录。
     * @throws IllegalArgumentException 当命令方法不符合公开异步契约时
     */
    protected synchronized void _scan() {
        var prefixCommandMap = new LinkedHashMap<String, CommandMapData>();
        var regexpCommandMap = new LinkedHashMap<String, CommandMapData>();
        var beanPairs = iBeanScanner.getBeansWithAnnotation(Command.class);
        for(var pair: beanPairs.values()) {
            var annotation = pair.first();
            var bean = pair.second();
            var clazz = iBeanScanner.getBeanClass(bean);
            var commandNames = annotation.value();
            if(commandNames.length == 0) {
                logger.errorWithReport("CommandScanner", "指令 %s 没有指定指令名".formatted(clazz.getName()));
                continue;
            }
            // 获取指令模式相应的Map
            var commandMap = switch (annotation.mode()) {
                case PREFIX -> prefixCommandMap;
                case REGEX -> regexpCommandMap;
            };
            // 获取默认和子指令方法
            Method defaultCommandMethod = null;
            DefaultCommand defaultCommandAnnotation = null;
            var subCommandMap = new LinkedHashMap<String, Pair<SubCommand, Method>>();
            for(var method: clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(DefaultCommand.class) || method.isAnnotationPresent(SubCommand.class)) {
                    CommandMethodContract.requireAsync(method);
                }
                // 如果是默认指令方法
                if(method.isAnnotationPresent(DefaultCommand.class)) {
                    if(defaultCommandMethod != null) {
                        logger.errorWithReport("CommandScanner", "指令 " + clazz.getName() + " 有多个默认指令方法");
                    } else {
                        defaultCommandMethod = method;
                        defaultCommandAnnotation = method.getAnnotation(DefaultCommand.class);
                    }
                } else if (method.isAnnotationPresent(SubCommand.class)) {
                    // 如果是子指令方法
                    var subCommandAnnotation = method.getAnnotation(SubCommand.class);
                    for(var subCommandName: subCommandAnnotation.value()) {
                        var __checkSubCommand = subCommandMap.get(subCommandName);
                        if(__checkSubCommand != null){
                            logger.errorWithReport("CommandScanner", String.format("""
                                    指令 %s 有多个子指令 %s 方法，分别是 %s 和 %s""",
                                    clazz.getName(),
                                    subCommandName,
                                    __checkSubCommand.getSecond().getName(),
                                    method.getName()));
                            continue;
                        }
                        subCommandMap.put(subCommandName, Pair.of(subCommandAnnotation, method));
                    }
                }
            }
            // 存储
            var commandMapData = CommandMapData.builder()
                    .command(bean)
                    .commandAnnotation(annotation)
                    // 2026/11/05：目标类型与合并注解只在发现阶段解析。
                    .description(AnnotatedElementUtils.findMergedAnnotation(clazz, Description.class))
                    .defaultCommandMethod(defaultCommandMethod)
                    .defaultCommandAnnotation(defaultCommandAnnotation)
                    .subCommandMap(Map.copyOf(subCommandMap))
                    .build();
            for(var command: commandNames) {
                var __checkCommand = commandMap.get(command);
                if(__checkCommand != null) {
                    logger.errorWithReport("CommandScanner", String.format("""
                            指令 %s 已经注册，分别是 %s 和 %s""",
                            command,
                            __checkCommand.getCommand().getClass().getName(),
                            commandMapData.getCommand().getClass().getName()));
                    continue;
                }
                commandMap.put(command, commandMapData);
            }
        }
        commands = new Commands(Map.copyOf(prefixCommandMap), Map.copyOf(regexpCommandMap));
        initialized = true;
    }

    /**
     * 一次发布的完整命令发现结果，不包含动态权限或插件启用结果。
     *
     * @param prefix 前缀目录
     * @param regexp 正则目录
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.2-l
     * @version 4.8.2-l
     */
    private record Commands(Map<String, CommandMapData> prefix, Map<String, CommandMapData> regexp) {}
}
