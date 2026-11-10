package org.zexnocs.teanekoagent.component;

import org.zexnocs.teanekocore.framework.composition.ComponentKey;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 为声明式组件复用参数类型校验和实体初始化，不拥有业务服务或全局可变参数。
 * 插件可以直接实现接口，或继承本类贡献独立系统及进一步的参数校验。
 *
 * <br>2026/11/10：公开可选择模型的字段契约及安全配置投影。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.1-l
 */
public abstract class DeclaredAgentComponent implements IAgentComponent {
    private final String id;
    private final Map<String, Object> defaults;
    private final List<Field> fields;
    private final ComponentKey<ComponentParameters> key;

    /**
     * @param id 稳定组件标识
     * @param defaults 公开参数初值
     * @param fields 参数目录
     */
    protected DeclaredAgentComponent(String id, Map<String, Object> defaults, List<Field> fields) {
        this.id = id;
        this.defaults = Map.copyOf(defaults);
        this.fields = List.copyOf(fields);
        key = ComponentKey.of("agent.component." + id + ".parameters", ComponentParameters.class);
    }

    /** @return 本组件专有参数键，系统通过此键取得本轮固定值。 */
    public ComponentKey<ComponentParameters> key() {
        return key;
    }

    /** @return 默认公开参数。 */
    @Override
    public Map<String, Object> defaults() {
        return defaults;
    }

    /** @return 不含实际用户值的字段目录。 */
    @Override
    public List<Field> fields() {
        return fields;
    }

    /**
     * @param parameters 显式组件参数
     * @throws IllegalArgumentException 当字段未声明、类型错误或文本超长时
     */
    @Override
    public void validate(JsonNode parameters) {
        for (var entry : parameters.properties()) {
            var field = fields.stream().filter(value -> value.name().equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("组件 " + id + " 包含未声明参数。"));
            var value = entry.getValue();
            boolean valid = switch (field.kind()) {
                case "BOOLEAN" -> value.isBoolean();
                case "NUMBER" -> value.isIntegralNumber();
                // 2026/11/10：模型选择是独立对象，具体来源和配置由声明组件验证。
                case "MODEL" -> value.isObject();
                case "JSON" -> value.isObject() || value.isArray();
                default -> value.isString() && value.asString().length() <= 8192;
            };
            if (!valid) {
                throw new IllegalArgumentException("组件 " + id + " 的参数 " + field.name() + " 类型或长度无效。");
            }
        }
    }

    /** @return 本组件唯一的数据拥有边界。 */
    @Override
    public Set<ComponentKey<?>> keys() {
        return Set.of(key);
    }

    /**
     * @param assembly 本轮已冻结装配
     * @return 只写本组件键的数据补丁
     */
    @Override
    public ComponentPatch initialize(AgentAssembly assembly) {
        var json = new tools.jackson.databind.ObjectMapper().valueToTree(defaults);
        var values = (tools.jackson.databind.node.ObjectNode) json;
        assembly.settings(id).properties().forEach(entry -> values.set(entry.getKey(), entry.getValue()));
        return ComponentPatch.builder().put(key, new ComponentParameters(values.toString())).build();
    }
}
