package org.zexnocs.teanekocore.command.core;

import lombok.Builder;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.reload.api.IScanner;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 指令描述扫描器。
 *
 * @author zExNocs
 * @date 2026/02/18
 */
@Service("descriptionScanner")
public class CommandDescriptionScanner implements IScanner {
    /// command scanner
    private final CommandScanner commandScanner;

    /// 指令描述数据map
    private final Map<Command, DescriptionMapData> descriptionDataMap = new ConcurrentHashMap<>();

    private final AtomicBoolean isInit = new AtomicBoolean(false);

    @Autowired
    public CommandDescriptionScanner(CommandScanner commandScanner) {
        this.commandScanner = commandScanner;
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

    private synchronized void __scan() {
        descriptionDataMap.clear();
        // 获取所有前缀指令集
        var commandMap = commandScanner.getPrefixCommandMap();
        for(var data: commandMap.values()) {
            var defaultCommandMethod = data.getDefaultCommandMethod();
            var defaultCommandDescription = defaultCommandMethod == null ? null : defaultCommandMethod.getAnnotation(Description.class);
            descriptionDataMap.put(data.getCommandAnnotation(), DescriptionMapData.builder()
                    .commandDescription(data.getCommand().getClass().getAnnotation(Description.class))
                    .subCommandDescriptionMap(getSubCommandDescriptionConcurrentHashMap(data))
                    .defaultCommandDescription(defaultCommandDescription)
                    .build());
        }
    }

    /**
     * 获取指令描述数据
     * @param command 指令
     * @return 指令描述数据
     */
    public DescriptionMapData getDescriptionData(Command command) {
        if(command == null) {
            return null;
        }
        return descriptionDataMap.get(command);
    }

    /**
     * 获取 keySet
     * @return keySet
     */
    public Set<Command> getDescriptionDataKeySet() {
        return descriptionDataMap.keySet();
    }

    /**
     * 构造子指令描述map
     * @param data 指令数据
     * @return 子指令描述map
     */
    private static ConcurrentHashMap<SubCommand, Description> getSubCommandDescriptionConcurrentHashMap(CommandMapData data) {
        var subCommandDescriptionMap = new ConcurrentHashMap<SubCommand, Description>();
        for(var subCommandPair: data.getSubCommandMap().values()) {
            SubCommand subCommand = subCommandPair.getFirst();
            Method method = subCommandPair.getSecond();
            var subCommandDescription = method.getAnnotation(Description.class);
            if (subCommandDescription != null) {
                subCommandDescriptionMap.put(subCommand, subCommandDescription);
            }
        }
        return subCommandDescriptionMap;
    }

    @Builder
    @Getter
    public static class DescriptionMapData {
        // 主指令描述
        private Description commandDescription;

        // 子指令描述
        private Map<SubCommand, Description> subCommandDescriptionMap;

        // 默认子指令描述
        private Description defaultCommandDescription;
    }
}
