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
 * <br>2026/11/06：按独立开关和日志级别输出精简文字汇总，去重并继续脱敏。
 *
 * <br>2026/11/08：摘要不携带正文，调试预览与独立滚动诊断分别配置。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.9.0-l
 */
@Getter
@Setter
@NoArgsConstructor
@FileConfig(value = "observability", path = "agent", type = FileConfigType.YAML)
@Description("每条请求链输出一份汇总；上下文可能包含聊天隐私，请限制日志访问。密钥、签名链接和图片内容会隐藏。")
public class AgentObservationFileConfig implements IFileConfigData {
    @Description("独立 LLM 调用汇总；Agent 内部调用由 Agent 统一报告。")
    private boolean llmInfo = true;
    @Description("Agent 请求汇总；关闭时同时关闭该请求内的 LLM 汇总，不影响网页时间线。")
    private boolean agentInfo = true;

    // 2026/11/06：默认精简文字汇总；保留旧正文总开关，新增字段不自动开放敏感诊断。
    @Description("摘要级别：INFO 单行日常统计，DEBUG 仅调试可见，NONE 关闭摘要及预览；独立诊断另设开关。")
    private Visibility visibility = Visibility.INFO;
    @Description("DEBUG 正文预览总开关；不控制独立诊断文件，推理预览开关独立。")
    private boolean includeContext = true;
    @Description("DEBUG 预览首轮应用选择的 USER 对象；后台任务对象可能不是用户原话。")
    private boolean includeUser = true;
    @Description("输出本次最终回复，工具续轮的中间正文不重复输出。")
    private boolean includeFinalOutput = true;
    @Description("输出去重后的 system 指令；JSON 结构说明由 include-schema 单独控制。")
    private boolean includeSystem;
    @Description("输出去重后的 JSON Schema 与工具协议声明，可能包含较长描述。")
    private boolean includeSchema;
    @Description("输出每个工具调用的参数、状态及结果，各一次。")
    private boolean includeTools;
    @Description("汇总包含供应商实际返回的推理字段或推理摘要；未返回则标明未提供。")
    private boolean includeReasoning;
    @Description("DEBUG 预览总字符预算，默认1024，旧配置仍可读取；实际最大2048，每个字段最多384码点。")
    private int maxContentCharacters = 1024;

    @Description("完整统一协议请求与响应写入独立滚动诊断文件，继续隐藏凭据、链接和媒体；不进入控制台或网页日志。")
    private boolean diagnosticLog = true;

    /**
     * 请求汇总的可见级别，正文开关不改变错误诊断。
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.6-l
     * @version 4.8.6-l
     */
    public enum Visibility {
        /** 在常规运行级别展示。 */
        INFO,
        /** 仅在调试级别展示。 */
        DEBUG,
        /** 关闭请求汇总，不改变业务异常。 */
        NONE
    }
}
