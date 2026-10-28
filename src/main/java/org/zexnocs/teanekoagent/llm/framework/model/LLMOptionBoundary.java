package org.zexnocs.teanekoagent.llm.framework.model;

import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;

import java.util.*;

/**
 * 通用选项中拒绝认证字段；不在错误消息中输出字段值。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class LLMOptionBoundary {
    /**
     * 禁止直接实例化此辅助类型，统一复用静态入口。
     */
    private LLMOptionBoundary() {}

    /**
     * 检查普通模型选项中是否夹带认证信息，凭据必须经独立可信端口解析。
     *
     * @param options 本次有效模型选项
     */
    public static void validate(ILLMModelOptions options) {
        if (options != null) {
            options.findMetadata().ifPresent(LLMOptionBoundary::validateMetadata);
        }
    }

    /**
     * 递归检查扩展元数据的认证字段，循环引用通过对象身份集合去重。
     *
     * @param metadata 不允许包含认证信息的扩展元数据
     */
    public static void validateMetadata(Map<String, Object> metadata) {
        inspect(metadata, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /**
     * 遍历映射、集合和数组中的字段，拒绝密钥、令牌等认证键，错误不携带原始值。
     *
     * @param value 待处理的值
     * @param visited 已访问对象或节点集合，用于避免循环展开
     * @throws LLMConfigurationException 当模型路由、凭据或本地配置不满足调用边界时
     */
    private static void inspect(Object value, Set<Object> visited) {
        if (value == null || !visited.add(value)) {
            return;
        }
        switch (value) {
            case Map<?, ?> map -> {
                for (var entry : map.entrySet()) {
                    var key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                    if (key.endsWith("apikey") || key.endsWith("authorization") || key.endsWith("accesstoken")
                            || key.endsWith("clientsecret") || key.endsWith("password") || key.endsWith("credentials")) {
                        throw new LLMConfigurationException("Credentials are forbidden in LLM options; use Spring application configuration.");
                    }
                    inspect(entry.getValue(), visited);
                }
            }
            case Iterable<?> items -> items.forEach(item -> inspect(item, visited));
            case Object[] items -> {
                for (var item : items) {
                    inspect(item, visited);
                }
            }
            default -> {
            }
        }
    }
}
