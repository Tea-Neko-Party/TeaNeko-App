package org.zexnocs.teanekoagent.runtime.context;

/**
 * 入站采集后冻结的上下文定位，用于排队及执行前复核同一身份和引用。
 * <br>2026/11/07：将入站宿主与具体历史存储 DTO 分离。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentCapturedContext {
    /** @return 实际采集使用的固定上下文引用 */
    IAgentContextReference reference();
    /** @return 实际采集使用的固定提示上下文 */
    IAgentPromptContext personality();
}
