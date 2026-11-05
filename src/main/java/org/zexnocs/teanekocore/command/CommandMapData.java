package org.zexnocs.teanekocore.command;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.util.Pair;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.framework.description.Description;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 指令映射数据类，用于存储指令相关的信息，包括指令本体、指令注解、默认指令方法、默认指令注解以及子指令集合。
 *
 * 修改记录：<br>
 * 2026/11/05：保存类型说明并提供已注册方法投影，命令目录查询不再解析 Bean 类型或重新发现方法。
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 * @version 4.8.2-l
 */
@Builder
public class CommandMapData {
    /// 指令本体
    @Getter
    private final Object command;

    /// 指令注解
    @Getter
    private final Command commandAnnotation;

    // 2026/11/05：类型说明在命令扫描阶段解析，页面查询直接读取原声明。
    @Getter
    private final @Nullable Description description;

    /// 默认指令方法
    @Getter
    private final Method defaultCommandMethod;

    /// 默认指令注解
    @Getter
    private final DefaultCommand defaultCommandAnnotation;

    /// 子指令集合
    private final Map<String, Pair<SubCommand, Method>> subCommandMap;

    /**
     * 获取已扫描的子指令及其别名映射。
     *
     * @return 子指令注册数据
     */
    protected Map<String, Pair<SubCommand, Method>> getSubCommandMap() {
        return subCommandMap;
    }

    /**
     * 从注册数据投影默认方法与子指令方法，不重新扫描类型或反射枚举方法。
     * 2026/11/05：指令台只展示真实登记的方法，并按方法去除别名重复项。
     *
     * @return 不含重复方法的只读列表
     */
    public List<Method> getRegisteredMethods() {
        var methods = new LinkedHashSet<Method>();
        if (defaultCommandMethod != null) {
            methods.add(defaultCommandMethod);
        }
        subCommandMap.values().forEach(pair -> methods.add(pair.getSecond()));
        return List.copyOf(methods);
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
