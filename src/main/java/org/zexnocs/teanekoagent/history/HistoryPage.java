package org.zexnocs.teanekoagent.history;

import java.util.List;

/**
 * 有界历史页面，省略和扫描上限不伪装成确定没有历史。
 * @param messages 本页完整文本消息，按保存序号倒序
 * @param nextBefore 下一页游标；零表示本次已扫描至末尾
 * @param scanned 已检查行数
 * @param omittedCharacters 是否因字符不足省略完整消息
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record HistoryPage(List<HistoryMessage> messages, long nextBefore, int scanned, boolean omittedCharacters) {
    /**
     * 冻结已检查消息集合。
     * @param messages 完整消息
     * @param nextBefore 后续游标
     * @param scanned 检查量
     * @param omittedCharacters 字符省略标志
     */
    public HistoryPage { messages = List.copyOf(messages); }
}
