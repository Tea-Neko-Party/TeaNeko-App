package org.zexnocs.teanekocore.command;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

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
 * <br>2026/11/07：接入插件归属与独立贡献，保留原权限及执行契约。
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 * @version 4.9.0-l
 */
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
    private final @Nullable Method defaultCommandMethod;

    /// 默认指令注解
    @Getter
    private final @Nullable DefaultCommand defaultCommandAnnotation;

    /// 子指令集合
    private final Map<String, Pair<SubCommand, Method>> subCommandMap;
    // 2026/11/07：扩展子命令保存真实容器实例，执行时不从宿主类重找业务方法。
    private final Map<Method, Object> methodTargets;

    /**
     * 2026/11/07：完整映射构造时固定各方法静态合同，运行时不补扫描。
     * @param command 根命令实例
     * @param commandAnnotation 根声明
     * @param description 根说明
     * @param defaultCommandMethod 默认方法
     * @param defaultCommandAnnotation 默认声明
     * @param subCommandMap 子方法与别名
     * @param methodTargets 扩展方法的真实目标，为空时全部使用根实例
     */
    @Builder
    public CommandMapData(Object command, Command commandAnnotation, @Nullable Description description,
            @Nullable Method defaultCommandMethod, @Nullable DefaultCommand defaultCommandAnnotation,
            Map<String, Pair<SubCommand, Method>> subCommandMap, @Nullable Map<Method, Object> methodTargets) {
        this.command = command;
        this.commandAnnotation = commandAnnotation;
        this.description = description;
        this.defaultCommandMethod = defaultCommandMethod;
        this.defaultCommandAnnotation = defaultCommandAnnotation;
        this.subCommandMap = Map.copyOf(subCommandMap);
        this.methodTargets = methodTargets == null ? Map.of() : Map.copyOf(methodTargets);
        getRegisteredMethods().forEach(CommandMethodContract::register);
    }

    /**
     * @param method 已扫描的处理方法
     * @return 真实处理 Bean，原根方法仍由根实例执行
     */
    public Object target(Method method) {
        return methodTargets.getOrDefault(method, command);
    }

    /**
     * 2026/11/07：构建包含新贡献的独立快照，保留根权限及全部旧目标。
     * @param additions 新子命令和别名
     * @param targets 新方法的真实 Spring 实例
     * @return 合并后的完整根目录
     * @throws IllegalArgumentException 当子命令与已有声明重名时
     */
    public CommandMapData extend(Map<String, Pair<SubCommand, Method>> additions, Map<Method, Object> targets) {
        var children = new java.util.LinkedHashMap<>(subCommandMap);
        additions.forEach((name, contribution) -> {
            var previous = children.putIfAbsent(name, contribution);
            if (previous != null) {
                throw new IllegalArgumentException("子命令标识冲突：" + name + "，来源="
                        + previous.getSecond() + "、" + contribution.getSecond());
            }
        });
        var owners = new java.util.HashMap<>(methodTargets);
        owners.putAll(targets);
        return builder().command(command).commandAnnotation(commandAnnotation).description(description)
                .defaultCommandMethod(defaultCommandMethod).defaultCommandAnnotation(defaultCommandAnnotation)
                .subCommandMap(Map.copyOf(children)).methodTargets(Map.copyOf(owners)).build();
    }

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
