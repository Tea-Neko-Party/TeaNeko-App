package org.zexnocs.teanekoagent.llm_api_framework.interfaces;

import java.util.List;

/**
 * LLM 获取的结果接口。
 *
 * @author zExNocs
 * @date 2026/03/24
 * @since 4.4.0
 */
public interface ILLMResult {
    /**
     * 获取对话的唯一标识符
     *
     * @return 对话的唯一标识符
     */
    String getId();

    /**
     * 模型生成的 completion
     *
     * @return {@link List }<{@link ILLMChoice }>
     */
    List<ILLMChoice> getChoices();

    /**
     * 对象类型。
     * <br>一般为 "chat.completion"
     *
     * @return {@link String }
     */
    String getObject();

    /**
     * 时间戳，单位为秒
     *
     * @return long
     */
    long getCreated();

    /**
     * 生成该 completion 所使用的模型
     *
     * @return {@link String }
     */
    String getModel();

    /**
     * 请求的用量信息
     *
     * @return {@link ILLMUsage }
     */
    ILLMUsage getUsage();
}
