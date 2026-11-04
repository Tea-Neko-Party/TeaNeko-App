package org.zexnocs.teanekoagent.component.tool.knowledge;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 知识检索的有界原始分块，来源、文档、分块和版本分别保留，不冒充用户个人事实。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param source 来源名称
 * @param documentId 文档定位
 * @param chunkId 文档内分块定位
 * @param revision 原内容版本
 * @param text 明确证据正文
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public record KnowledgeChunk(String source, String documentId, String chunkId, String revision, String text) {
    /**
     * 保留完整来源，拒绝无界正文和无版本证据。
     * @param source 来源
     * @param documentId 文档标识
     * @param chunkId 分块标识
     * @param revision 内容版本
     * @param text 证据正文
     * @throws IllegalArgumentException 当定位、版本或正文无效时
     */
    public KnowledgeChunk {
        if (java.util.stream.Stream.of(source, documentId, chunkId, revision).anyMatch(value -> value == null || value.isBlank() || value.length() > 128)
                || text == null || text.isBlank() || text.length() > 4096) { throw new IllegalArgumentException("知识分块的来源、版本或正文无效。"); }
    }
}
