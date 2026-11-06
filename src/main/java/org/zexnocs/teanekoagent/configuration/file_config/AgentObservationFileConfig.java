package org.zexnocs.teanekoagent.configuration.file_config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 可热更新的请求汇总开关；正文及供应商返回的推理字段必须脱敏，不能记录凭据。
 * <br>2026/11/06：模型调用链与 Agent 决策阶段分别控制 INFO 日志。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.5-l
 */
@Getter
@Setter
@NoArgsConstructor
@FileConfig(value = "observability", path = "agent", type = FileConfigType.YAML)
@Description("每条请求链输出一份汇总；上下文可能包含聊天隐私，请限制日志访问。密钥、签名链接和图片内容会隐藏。")
public class AgentObservationFileConfig implements IFileConfigData {
    @Description("独立 LLM 调用的 INFO 汇总；Agent 内部调用由 Agent 统一报告。")
    private boolean llmInfo = true;
    @Description("Agent 请求 INFO 汇总；关闭时同时关闭该请求内的 LLM 汇总，不影响网页时间线。")
    private boolean agentInfo = true;

    // 2026/11/06：按用户要求提供完整上下文观察，明确隐私边界与输出上限。
    @Description("汇总包含每次调用实际提交的消息、工具定义和响应；关闭后只保留状态及用量。")
    private boolean includeContext = true;
    @Description("汇总包含供应商实际返回的推理字段或推理摘要；未返回则标明未提供。")
    private boolean includeReasoning = true;
    @Description("单条汇总正文字符上限，范围 1024–2000000，默认 262144；截断会明确标注。统计字段不受截断影响。")
    private int maxContentCharacters = 262144;
}
