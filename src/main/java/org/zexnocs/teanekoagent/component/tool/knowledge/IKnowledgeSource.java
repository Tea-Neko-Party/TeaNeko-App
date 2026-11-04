package org.zexnocs.teanekoagent.component.tool.knowledge;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.tool.extension.AgentExtensionContext;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 知识源/分块/检索扩展，不内置 PDF、网页、embedding 或向量数据库。
 * 后端根据真实上下文过滤范围并标记完整性，模型不能另传数据库或任意物理路径。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@FunctionalInterface
public interface IKnowledgeSource {
    /**
     * 在真实授权范围内检索有来源的原始分块。
     * @param query 有界查询和结果预算
     * @param context 可信身份及执行控制
     * @return 明确来源、版本和扫描完整性的证据
     */
    CompletionStage<Page> search(Query query, AgentExtensionContext context);
    /**
     * 独立于后端索引实现的检索预算。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param text 查询原文
     * @param maxChunks 最大分块数
     * @param maxCharacters 正文总字符上限
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    record Query(String text, int maxChunks, int maxCharacters) {
        /**
         * 校验查询硬上限，不接受无界全库读取。
         * @param text 查询原文
         * @param maxChunks 最大分块数
         * @param maxCharacters 正文上限
         * @throws IllegalArgumentException 当查询或预算非法时
         */
        public Query {
            if (text == null || text.isBlank() || text.length() > 512 || maxChunks < 1 || maxChunks > 4 || maxCharacters < 1 || maxCharacters > 4096) {
                throw new IllegalArgumentException("知识查询或结果预算无效。");
            }
        }
    }
    /**
     * 不可变知识证据页，不完整的空页不能解释为确定无资料。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param chunks 原始证据
     * @param complete 本次查询是否完整覆盖其声明范围
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    record Page(List<KnowledgeChunk> chunks, boolean complete) {
        /**
         * 冻结有界证据，不丢失原始来源。
         * @param chunks 原始证据
         * @param complete 完整性
         * @throws IllegalArgumentException 当结果数量超过硬上限时
         */
        public Page { chunks = List.copyOf(chunks); if (chunks.size() > 4) { throw new IllegalArgumentException("知识证据页最多四项。"); } }
    }
}
