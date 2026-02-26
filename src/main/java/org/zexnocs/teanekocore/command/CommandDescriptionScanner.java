package org.zexnocs.teanekocore.command;

import lombok.Builder;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.reload.AbstractScanner;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指令描述扫描器。
 *
 * @see Command
 * @see Description
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
@Service("descriptionScanner")
public class CommandDescriptionScanner extends AbstractScanner {
    /// command scanner
    private final CommandScanner commandScanner;

    /// 指令描述数据map
    private final Map<Command, DescriptionMapData> descriptionDataMap = new ConcurrentHashMap<>();

    @Autowired
    public CommandDescriptionScanner(CommandScanner commandScanner) {
        this.commandScanner = commandScanner;
    }

    @Override
    protected synchronized void _scan() {
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
