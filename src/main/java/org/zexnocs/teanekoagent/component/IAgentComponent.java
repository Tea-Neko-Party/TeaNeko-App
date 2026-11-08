package org.zexnocs.teanekoagent.component;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.plugin.IAgentFeature;
import org.zexnocs.teanekocore.framework.composition.ComponentKey;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组件类型的参数、数据和系统贡献契约；服务由 Spring 持有，实体只保存不可变参数。
 *
 * 修改记录：<br>
 * 2026/11/04：提供可由插件扩展的声明式装配接口，前后端复用同一字段目录。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
public interface IAgentComponent {
    /** @return 默认参数，只包含可公开的模板值。 */
    Map<String, Object> defaults();
    /** @return 参数名、中文名称及说明。 */
    List<Field> fields();
    /**
     * @param parameters 本次独立参数树
     * @throws IllegalArgumentException 当组件参数无效时
     */
    void validate(JsonNode parameters);
    /** @return 本组件拥有的类型化实体数据键。 */
    Set<ComponentKey<?>> keys();
    /**
     * @param assembly 已校验且脱敏的固定装配
     * @return 仅包含本组件数据的初始化补丁
     */
    ComponentPatch initialize(AgentAssembly assembly);
    /** @return 需要启用的已有能力贡献标识；不绕过其插件及业务权限。 */
    default Set<String> features() {
        return Set.of();
    }
    /** @return 在明确阶段执行的 ECS 系统，读取本组件参数并返回受控补丁。 */
    default List<IAgentFeature.SystemContribution> systems() {
        return List.of();
    }

    /** @return 组件提供的工具；未安装 tool 组件时宿主不选择任何工具。 */
    default org.zexnocs.teanekoagent.runtime.IAgentToolSource tools() {
        return (definition, request, runtime) -> List.of();
    }

    /** @return 可等待的资料来源，默认不注入资料。 */
    default org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource prompts() {
        return org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource.empty();
    }

    /** @return 本组件独占的资料来源名，仍受宿主预算及版本检查。 */
    default Set<String> promptNames() {
        return Set.of();
    }

    /**
     * 一个组件字段的公开表单描述。
     *
     * @param name 序列化参数名
     * @param label 中文名称
     * @param description 用途与限制
     * @param kind BOOLEAN、NUMBER、TEXT、LONG_TEXT、JSON 或 SECRET
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.8.0-l
     * @version 4.8.0-l
     */
    record Field(String name, String label, String description, String kind) {}
}
