package org.zexnocs.teanekocore.command;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指令扫描器
 * 扫描指令、默认指令和子指令
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 * @see Command
 * @see DefaultCommand
 * @see SubCommand
 */
@Service("commandScanner")
public class CommandScanner extends AbstractScanner {
    /// 日志
    private final ILogger logger;

    /// 前缀指令映射表，key为指令名，value为指令数据
    private final Map<String, CommandMapData> prefixCommandMap = new ConcurrentHashMap<>();

    /// 正则指令映射表，key为指令名，value为指令数据
    private final Map<String, CommandMapData> regexpCommandMap = new ConcurrentHashMap<>();
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
        return prefixCommandMap;
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
        return prefixCommandMap.get(command);
    }

    /**
     * 获取正则指令所有的 key。
     *
     * @return 正则命令标识集合，类型为 {@code Collection<String>}
     */
    public Collection<String> getRegexpCommandKeys() {
        return regexpCommandMap.keySet();
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
        return regexpCommandMap.get(command);
    }

    /**
     * 扫描指令。
     */
    protected synchronized void _scan() {
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
            var subCommandMap = new ConcurrentHashMap<String, Pair<SubCommand, Method>>();
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
                    .defaultCommandMethod(defaultCommandMethod)
                    .defaultCommandAnnotation(defaultCommandAnnotation)
                    .subCommandMap(subCommandMap)
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
    }

    /**
     * 清理原始数据的方法。
     */
    @Override
    protected void _clear() {
        prefixCommandMap.clear();
        regexpCommandMap.clear();
    }
}
