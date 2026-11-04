package org.zexnocs.teanekoagent.component.expression;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * 素材选择与客户端表情的独立预算，不承载下载或模型凭据。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：区分未配置客户端目录与显式空目录，保留管理员的禁用选择。
 *
 * @param enabled 总开关
 * @param maxPerReply 单轮表情包数量
 * @param maxCategories 单轮展开类别数
 * @param cooldownTurns 同区域回避轮数
 * @param maxCategoryCharacters 完整类别回执字符预算
 * @param nativeFaces 按客户端配置的原生表情标识及描述；null 表示使用客户端默认，空表表示禁止
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.0-l
 */
public record MediaReplySettings(boolean enabled, int maxPerReply, int maxCategories, int cooldownTurns,
        int maxCategoryCharacters, @Nullable Map<String, Map<String, String>> nativeFaces) {
    /**
     * 校验并深层固定目录，禁止过量 Prompt 或可变配置泄漏。
     * @param enabled 开关
     * @param maxPerReply 单轮上限
     * @param maxCategories 类别上限
     * @param cooldownTurns 回避轮数
     * @param maxCategoryCharacters 结果预算
     * @param nativeFaces 显式客户端目录；null 表示尚未配置而非空目录
     * @throws IllegalArgumentException 当预算或目录标识、描述超出范围时
     */
    public MediaReplySettings {
        // 2026/11/05：仅缺省目录允许客户端提供默认值，显式空映射仍深层冻结并保持为空。
        if (maxPerReply < 1 || maxPerReply > 4 || maxCategories < 1 || maxCategories > 2
                || cooldownTurns < 0 || cooldownTurns > 16 || maxCategoryCharacters < 1024
                || maxCategoryCharacters > 7800 || nativeFaces != null && nativeFaces.size() > 32) {
            throw new IllegalArgumentException("媒体回复数量或目录预算无效。");
        }
        if (nativeFaces != null) {
            var copy = new TreeMap<String, Map<String, String>>();
            nativeFaces.forEach((client, faces) -> {
                if (client.isBlank() || client.length() > 64 || faces.size() > 64
                        || faces.entrySet().stream().anyMatch(entry ->
                                !entry.getKey().matches("[a-zA-Z0-9_.:-]{1,64}")
                                        || entry.getValue().isBlank() || entry.getValue().length() > 80)) {
                    throw new IllegalArgumentException("原生表情目录标识或描述无效。");
                }
                copy.put(client, Collections.unmodifiableMap(new TreeMap<>(faces)));
            });
            nativeFaces = Collections.unmodifiableMap(copy);
        }
    }

    /**
     * 2026/11/05：仅整个配置字段缺省时使用客户端默认；显式目录没有该客户端时返回空目录。
     * @param clientId 客户端稳定标识
     * @param defaults 由该客户端提供的只读默认目录
     * @return 显式目录或缺省时的客户端默认目录
     */
    public Map<String, String> nativeFacesFor(String clientId, Map<String, String> defaults) {
        return nativeFaces == null ? defaults : nativeFaces.getOrDefault(clientId, Map.of());
    }
}
