package org.zexnocs.teanekocore.command.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.bean_scanner.IBeanScanner;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 指令扫描器
 * 扫描指令、默认指令和子指令
 *
 * @author zExNocs
 * @date 2026/02/18
 */
@Service("commandScanner")
public class CommandScanner implements IScanner {
    /// 日志
    private final ILogger logger;

    /// 前缀指令映射表，key为指令名，value为指令数据
    private final Map<String, CommandMapData> prefixCommandMap = new ConcurrentHashMap<>();

    /// 正则指令映射表，key为指令名，value为指令数据
    private final Map<String, CommandMapData> regexpCommandMap = new ConcurrentHashMap<>();
    private final IBeanScanner iBeanScanner;

    private final AtomicBoolean isInit = new AtomicBoolean(false);

    @Autowired
    public CommandScanner(ILogger logger, IBeanScanner iBeanScanner) {
        this.logger = logger;
        this.iBeanScanner = iBeanScanner;
    }

    /**
     * 热重载方法。
     */
    @Override
    public void reload() {
        __scan();
    }

    /**
     * 初始化方法，扫描指令。
     *
     */
    public void init() {
        if(isInit.compareAndSet(false, true)) {
            __scan();
        }
    }

    /**
     * 获取全部前缀指令。
     * @return Map<String, CommandMapData> 前缀指令集合
     */
    protected Map<String, CommandMapData> getPrefixCommandMap() {
        return prefixCommandMap;
    }

    /**
     * 获取前缀指令。
     * 如果指令不存在，返回null。
     * @param command 指令名
     * @return CommandMapData
     */
    public CommandMapData getPrefixCommand(String command) {
        if(command == null || command.isBlank()) {
            return null;
        }
        return prefixCommandMap.get(command);
    }

    /**
     * 获取正则指令所有的 key。
     * @return Collection<String> 正则指令集合
     */
    public Collection<String> getRegexpCommandKeys() {
        return regexpCommandMap.keySet();
    }

    /**
     * 获取正则指令。
     * 如果指令不存在，返回null。
     * @param command 指令名
     * @return CommandMapData
     */
    public CommandMapData getRegexpCommand(String command) {
        if(command == null || command.isBlank()) {
            return null;
        }
        return regexpCommandMap.get(command);
    }

    /**
     * 扫描指令。
     *
     */
    private synchronized void __scan() {
        prefixCommandMap.clear();
        regexpCommandMap.clear();

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
}
