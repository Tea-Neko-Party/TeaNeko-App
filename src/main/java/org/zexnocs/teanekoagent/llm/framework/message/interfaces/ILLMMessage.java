package org.zexnocs.teanekoagent.llm.framework.message.interfaces;

import java.util.List;
import java.util.Map;

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
     * 获取 message 参与者名称
     *
     * @return {@link String }
     */
    default String getName() {
        return "";
    }

    /**
     * 获取 content 列表
     *
     * @return {@link List }<{@link ILLMContent }>
     */
    List<ILLMContent> getContents();

    /**
     * 获取供应商适配器需要在多轮调用中保留的透明 metadata。
     * <br>该数据不属于用户可见消息正文，也不应直接序列化到通用消息 JSON。
     *
     * @return 供应商 metadata
     * @since 4.4.1
     */
    default Map<String, Object> getProviderMetadata() {
        return Map.of();
    }
}
