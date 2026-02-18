package org.zexnocs.teanekocore.command.core;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.interfaces.ICommandParser;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.bean_scanner.IBeanScanner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 指令解析器扫描器。
 *
 * @author zExNocs
 * @date 2026/02/18
 */
@Service("commandParserScanner")
public class CommandParserScanner implements IScanner {

    /// 日志
    private final ILogger logger;

    /// 指令解析器 map
    private final Map<String, ICommandParser<?>> commandParsers = new ConcurrentHashMap<>();

    /// bean 扫描器
    private final IBeanScanner beanScanner;

    private final AtomicBoolean isInit = new AtomicBoolean(false);

    @Autowired
    public CommandParserScanner(ILogger logger, IBeanScanner beanScanner) {
        this.logger = logger;
        this.beanScanner = beanScanner;
    }

    /**
     * 扫描所有指令解析器
     */
    private synchronized void __scan() {
        commandParsers.clear();
        var beanPairs = beanScanner.getBeansWithAnnotationAndInterface(CommandParser.class, ICommandParser.class);
        for(var beanPair: beanPairs.values()) {
            var bean = beanPair.second();
            var annotation = beanPair.first();
            if (commandParsers.putIfAbsent(annotation.value(), bean) != null) {
                logger.errorWithReport("CommandParserScanner", String.format("""
                指令解析器的名称 "%s" 冲突：1. %s; 2. %s""",
                        annotation.value(),
                        commandParsers.get(annotation.value()).getClass().getName(),
                        bean.getClass().getName()));
            }
        }
    }

    /**
     * 根据指令名称获取指令解析器
     * @param name 指令名称
     * @return 指令解析器
     */
    @Nullable
    public ICommandParser<?> getCommandParser(String name) {
        if(name == null || name.isBlank()) {
            return null;
        }
        return commandParsers.get(name);
    }

    /**
     * 热重载方法。
     */
    @Override
    public void reload() {
        __scan();
    }

    /**
     * 初始化方法。
     * 用于防止第一次重复加载。
     */
    @Override
    public void init() {
        if(isInit.compareAndSet(false, true)) {
            __scan();
        }
    }
}
