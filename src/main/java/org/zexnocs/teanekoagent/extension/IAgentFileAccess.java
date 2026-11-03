package org.zexnocs.teanekoagent.extension;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * 受控文件后端扩展，默认应用不提供宿主文件实现。
 * 实现负责根目录/链接边界、实际授权、原版本写入及 requestId+callId 防重；同键改参数须拒绝，不自动重试写入。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
public interface IAgentFileAccess {
    /**
     * 读取明确相对路径，返回源版本及是否完整，不把截断解释为文件结束。
     * @param path 已规范化的相对路径，后端仍须防止根目录及链接越界
     * @param maxCharacters 本次正文上限
     * @param context 真实调用上下文
     * @return 实际读取结果
     */
    CompletionStage<ReadResult> read(String path, int maxCharacters, AgentExtensionContext context);
    /**
     * 按明确原版本写入，NEW 表示只允许创建尚不存在的目标。
     * @param path 受控相对路径
     * @param text 完整候选正文
     * @param expectedRevision 原读取版本或 NEW，不能无保护覆盖
     * @param context 真实调用及后端防重标识
     * @return 已提交、拒绝或未知的真实回执
     */
    CompletionStage<WriteResult> write(String path, String text, String expectedRevision, AgentExtensionContext context);
    /**
     * 有来源版本的文件正文，内容由后端先完成脱敏和范围限制。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param exists 是否已确认目标存在；不存在时正文为空、版本为空且 complete 为 true
     * @param revision 实际源版本
     * @param text 正文
     * @param complete 是否已完整读取
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    record ReadResult(boolean exists, @Nullable String revision, String text, boolean complete) {}
    /**
     * 文件修改的业务回执，不返回任意后端对象。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param status 实际状态
     * @param revision 已确认的新版本，未知或拒绝时可为空
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    record WriteResult(AgentExtensionStatus status, @Nullable String revision) {}
}
