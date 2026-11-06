package org.zexnocs.teanekoagent.component;

import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;

/**
 * 标记由组件装配拥有的资料服务，避免同时被 Spring 当作宿主全局附加来源。
 * 服务须由组件的 {@link IAgentComponent#prompts()} 提供，仅在该组件选中后进入插件运行链。
 * 此接口只声明归属，不授予访问权；资料解析和复核仍遵守原资料端口契约。
 * <br>2026/11/06：区分可拆卸组件来源与独立宿主来源，防止重复注入及未装配时执行。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 * @see IAgentComponent
 */
public interface IAgentComponentPromptSource extends IAgentPromptSource {
}
