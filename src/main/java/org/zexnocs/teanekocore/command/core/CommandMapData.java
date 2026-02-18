package org.zexnocs.teanekocore.command.core;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.util.Pair;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 指令映射数据类，用于存储指令相关的信息，包括指令本体、指令注解、默认指令方法、默认指令注解以及子指令集合。
 *
 * @author zExNocs
 * @date 2026/02/18
 */
@Builder
public class CommandMapData {
    /// 指令本体
    @Getter
    private final Object command;

    /// 指令注解
    @Getter
    private final Command commandAnnotation;

    /// 默认指令方法
    @Getter
    private final Method defaultCommandMethod;

    /// 默认指令注解
    @Getter
    private final DefaultCommand defaultCommandAnnotation;

    /// 子指令集合
    private final Map<String, Pair<SubCommand, Method>> subCommandMap;

    /// 子指令集合
    protected Map<String, Pair<SubCommand, Method>> getSubCommandMap() {
        return subCommandMap;
    }

    /**
     * 从子指令集合中获取子指令。
     * @param key 子指令名称
     * @return 子指令
     */
    public Pair<SubCommand, Method> getSubCommand(String key) {
        if(key == null || key.isBlank()) {
            return null;
        }
        return subCommandMap.get(key);
    }
}
