package org.zexnocs.teanekoagent.llm_api.api.message.interfaces;

/**
 * LLM Content 数据
 * <br>有两种可能，要么是 String 模式的，要么是 Content List 模式。
 * <br>这里默认采取 Content List 模式；如果是 String 模式，则应在 LLM 客户端里手动解析成 Content List
 *
 * @see ILLMContentItem
 * @author zExNocs
 * @date 2026/03/19
 * @since 4.4.0
 */
public interface ILLMContent {
    /**
     * content item 的类型
     *
     * @return {@link String }
     */
    String getType();
}
