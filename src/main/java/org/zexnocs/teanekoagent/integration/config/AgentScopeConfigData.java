package org.zexnocs.teanekoagent.integration.config;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.AgentAssembly;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.description.Mask;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 新 Agent 的作用域注册数据，只有真实登记记录才允许使用默认启用值。
 * 私有模型连接随组件原文受保护保存，普通查询脱敏，不提供补正 key 写入入口。
 * <br>2026/11/03：新 scope 默认允许自动长期学习，已有显式关闭配置继续生效。
 * <br>2026/11/03：登记独立拟人表达开关。
 * <br>2026/11/03：登记独立素材采集开关，不绑定记忆学习。
 * <br>2026/11/03：分别控制共享图库回复和客户端原生表情。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：新增聊天模型路由，空值继承文件默认。
 * <br>2026/11/04：新登记只安装 LLM，旧记录保持原行为，业务参数从独立组件读取。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：新增默认开启的失败原因回复配置。
 * <br>2026/11/06：以字段注解声明人格与表达长文本编辑器。
 * <br>2026/11/07：接入插件归属与独立贡献，保留原权限及执行契约。
 * <br>2026/11/07：基础数据仅保留通用运行字段，业务旧节点原样保留并交插件迁移。
 * <br>2026/11/07：删除临时业务访问器及固定组件分支，原始扩展节点不参与 Bean 属性识别。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Getter
@Setter
public class AgentScopeConfigData implements IConfigData {
    // 2026/11/07：业务参数只存在于受保护组件文本或未迁移的原节点，不再生成业务访问器。
    @Mask("组件装配已隐藏，请在受保护的网页表单中查看和修改。")
    @Description("版本化 Agent 组件 JSON；必需 llm，其余组件显式安装。")
    private @Nullable String components = AgentAssembly.MINIMAL;

    @Description("是否启用当前作用域的 Agent 功能。")
    private boolean enabled = true;
    @Description("Agent 失败时是否通过原发送器回复公开原因，默认开启。")
    private boolean failureReplyEnabled = true;
    @Description("使用的模型配置路由；留空继承 model.yml 默认模型。")
    private String modelRoute = "";
    @Description("活动模式：PASSIVE_BASIC、PASSIVE_CONTEXTUAL 或 ACTIVE。")
    private String mode = AgentScopePolicy.Mode.PASSIVE_BASIC.name();
    @Description("自动入站的明确机器人称呼，英文逗号分隔，最多八项，每项1至32字符。")
    private String triggerNames = "";
    @Description("通用会话窗口上限，0 关闭窗口，-1 使用本地默认值；不拆开完整工具轮次。")
    private int historyMaxMessages = -1;
    @Description("统一协议 JSON 输入字节上限，必须为正，-1 使用本地默认值。")
    private long inputMaxBytes = -1;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    @Mask("历史扩展参数仅供受保护配置迁移使用。")
    private final Map<String, JsonNode> retainedFields = new LinkedHashMap<>();

    /** 新登记只安装 LLM，不默认拥有业务插件能力。 */
    public AgentScopeConfigData() {
    }

    /**
     * 旧配置缺少装配时保留历史标记，具体业务由已安装插件的迁移器解释。
     * @param components 原受保护装配文本，历史记录可缺失
     */
    @JsonCreator
    public AgentScopeConfigData(@JsonProperty("components") @Nullable String components) {
        this.components = components;
    }

    /**
     * 2026/11/07：保留未知顶层节点的精确 JSON 类型，不解释业务字段或为缺省值补真。
     * @param field 历史或扩展字段
     * @param value 原节点，允许 JSON 空值
     */
    @JsonAnySetter
    public void retain(String field, @Nullable JsonNode value) {
        retainedFields.put(field, value == null ? NullNode.getInstance() : value.deepCopy());
    }

    /**
     * 2026/11/07：受保护的完整配置序列化沿用原顶层键，普通配置投影不公开此存储。
     * @return 独立的原节点副本，不生成额外包装字段
     */
    @JsonAnyGetter
    public Map<String, JsonNode> retainedFields() {
        var result = new LinkedHashMap<String, JsonNode>();
        retainedFields.forEach((key, value) -> result.put(key, value.deepCopy()));
        return result;
    }

    /** @return 无秘密的运行装配，历史标记由插件兼容入口处理 */
    @JsonIgnore
    public AgentAssembly assembly() {
        return components == null ? AgentAssembly.legacyDefaults() : AgentAssembly.parse(components);
    }

    /** @return 原生 LLM 组件所选路由，私有配置仍由受保护端口解析 */
    public String getModelRoute() {
        return assembly().legacy() ? modelRoute : assembly().settings("llm").path("route").asString("");
    }

    /**
     * 为真实登记记录建立通用准入策略，非法模式不自动改为启用。
     * @return 已登记的作用域策略
     * @throws IllegalArgumentException 当模式或模型路由无效时
     */
    public AgentScopePolicy toPolicy() {
        try {
            return new AgentScopePolicy(true, enabled, AgentScopePolicy.Mode.valueOf(mode), getModelRoute(), assembly());
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IllegalArgumentException("Agent 活动模式或模型路由配置无效，请检查对应字段。");
        }
    }
}
