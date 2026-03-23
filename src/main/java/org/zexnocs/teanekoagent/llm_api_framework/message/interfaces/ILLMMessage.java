package org.zexnocs.teanekoagent.llm_api_framework.message.interfaces;

import java.util.List;

/**
 * 从 LLM 接收或者发送给 LLM 作为上下文的消息。
 * <br>有两种可能，要么是 String 模式的，要么是 Content List 模式。
 * <br>这里默认采取 Content List 模式；如果是 String 模式，则应在 LLM 客户端里手动解析成 Content List
 *
 * @author zExNocs
 * @date 2026/03/19
 * @since 4.4.0
 */
public interface ILLMMessage {
    /**
     * 获取 message 类型。
     *
     * @see LLMMessageRole
     * @return {@link LLMMessageRole }
     */
    LLMMessageRole getRole();

    /**
     * 获取 content 列表
     *
     * @return {@link List }<{@link ILLMContent }>
     */
    List<ILLMContent> getContents();
}
