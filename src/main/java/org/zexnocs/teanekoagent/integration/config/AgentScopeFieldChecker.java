package org.zexnocs.teanekoagent.integration.config;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekocore.database.configdata.api.IConfigFieldChecker;

/**
 * 校验作用域开关、活动模式及窗口覆盖，拒绝未知字段，不承担调用者权限校验。
 *
 * <br>2026/11/03：沿用原 cfg 权限校验新的表达开关。
 * <br>2026/11/03：素材采集开关执行相同严格布尔校验。
 * <br>2026/11/03：共享图库和原生表情回复开关同样严格校验。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：校验聊天模型路由格式，保留空值继承。
 *
 * <br>2026/11/04：统一组件校验及事务内的秘密保留，标量规则交由组件基础层。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@org.springframework.stereotype.Component
public final class AgentScopeFieldChecker implements IConfigFieldChecker {
    private org.zexnocs.teanekoagent.component.@org.jspecify.annotations.Nullable AgentComponentRegistry components;

    /**
     * 2026/11/04：cfg 与 Web 共用实际注册组件校验，不在字段检查器中复制组件列表。
     * @param components 当前容器组件目录
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void installComponents(org.zexnocs.teanekoagent.component.AgentComponentRegistry components) {
        this.components = components;
    }

    /**
     * 2026/11/04：仅在原事务内处理明确的凭据保留标记，其余值完整校验。
     * @param field 原字段名
     * @param submitted 本次节点
     * @param previous 持锁的原节点
     * @return 合并完毕的装配文本节点
     */
    @Override
    public tools.jackson.databind.JsonNode prepareJson(String field, tools.jackson.databind.JsonNode submitted,
            tools.jackson.databind.@org.jspecify.annotations.Nullable JsonNode previous) {
        if (!field.equals("components")) {
            return submitted;
        }
        if (!submitted.isString()) {
            throw new IllegalArgumentException("组件装配必须使用 JSON 文本。");
        }
        return tools.jackson.databind.node.StringNode.valueOf(java.util.Objects.requireNonNull(components)
                .mergeSecrets(submitted.asString(), previous == null || previous.isNull() ? null : previous.asString()));
    }
    /**
     * 按显式字段列表校验值，权限继续由原 /cfg 命令链负责。
     * 时区与 Skill 解析仅用于验证输入，不替换原始配置值。
     * @param field 目标字段
     * @param value 输入值
     * @return 合法时为 null，否则为固定中文原因
     */
    // 2026/11/04：标量校验已移入组件规则，不再保留原忽略结果警告的压制。
    @Override public String isValid(String field, String value) {
        // 2026/11/04：装配必须是显式版本化 JSON；原文可能含秘密，不输出解析异常。
        if ("components".equals(field)) {
            if (components == null) {
                return "Agent 组件目录尚未装配。";
            }
            try {
                components.validate(value);
                return null;
            } catch (IllegalArgumentException | IllegalStateException failure) {
                return "Agent 组件装配无效，请检查组件标识、参数、依赖及模型配置。";
            }
        }
        return org.zexnocs.teanekoagent.component.AgentParameterRules.validate(field, value);
    }
}
