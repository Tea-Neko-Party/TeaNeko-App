package org.zexnocs.teanekoagent.llm.framework.model.interfaces;

import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;

import java.util.Optional;

/**
 * 默认模型路由端口；配置布局由装配适配器决定。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface IDefaultModelResolver {
    /**
     * 由外部配置端口提供默认路由；未配置可以返回空，底层 LLM 不依赖 Agent 配置实现。
     *
     * @return 符合当前定位与类型约束的值；未配置或不存在时返回空
     */
    Optional<LLMModelId> findDefaultModelId();
}
