package org.zexnocs.teanekoagent.component.llm.framework.message.interfaces;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.util.List;
import java.util.Map;

/**
 * 从 LLM 接收或者发送给 LLM 作为上下文的消息。
 * <br>有两种可能，要么是 String 模式的，要么是 Content List 模式。
 * <br>这里默认采取 Content List 模式；如果是 String 模式，则应在 LLM 客户端里手动解析成 Content List
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：修正消息内容返回说明，避免泛型尖括号被误解析为 HTML。
 *
 * @author zExNocs
 * @date 2026/03/19
 * @since 4.4.0
 * @version 4.8.0-l
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
     * 获取 content 列表。
     * 2026/11/05：使用有效链接说明列表元素，不直接拼接 HTML 尖括号。
     *
     * @return 由 {@link ILLMContent} 组成的消息内容列表
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
