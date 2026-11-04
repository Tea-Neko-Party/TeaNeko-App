package org.zexnocs.teanekoagent.component;

import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * 内置业务参数的公共校验基类；第三方组件使用独立参数契约，不受旧 cfg 字段表限制。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
public abstract class BuiltinAgentComponent extends DeclaredAgentComponent {
    /**
     * @param id 组件标识
     * @param defaults 默认参数
     * @param fields 公开表单描述
     */
    protected BuiltinAgentComponent(String id, Map<String, Object> defaults, List<Field> fields) {
        super(id, defaults, fields);
    }

    /**
     * @param parameters 原独立参数
     * @throws IllegalArgumentException 当参数越过业务范围时
     */
    @Override
    public void validate(JsonNode parameters) {
        super.validate(parameters);
        for (var entry : parameters.properties()) {
            String value = entry.getValue().isString() ? entry.getValue().asString() : entry.getValue().toString();
            if (entry.getKey().equals("allowedTools")) {
                if (!value.isEmpty() && (!value.matches("[a-zA-Z0-9_-]{1,64}(,[a-zA-Z0-9_-]{1,64})*")
                        || value.split(",").length > 128)) {
                    throw new IllegalArgumentException("允许的工具须为最多128项工具名称列表。");
                }
                continue;
            }
            String invalid = AgentParameterRules.validate(entry.getKey(), value);
            if (invalid != null) {
                throw new IllegalArgumentException(invalid);
            }
        }
    }
}
