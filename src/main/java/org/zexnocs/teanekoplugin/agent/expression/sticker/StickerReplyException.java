package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;

/**
 * 表情包目录与发送依赖的固定失败原因，不把资料失效当成空图库。
 * <br>2026/11/05：区分目录校验失败和最终工具被过滤，不公开原资料或签名。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
public final class StickerReplyException extends IllegalStateException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 报告目录存在但必要的表达合同或工具已被筛掉。
     */
    public StickerReplyException() {
        super("本轮存在可用表情包，但缺少表达合同或 sticker_list 工具；"
                + "请检查 expression/tool 装配、allowedTools、插件筛选和客户端范围。");
    }

    /**
     * 为目录校验失败补充安全上下文，原异常仅保留类型及代码位置供诊断。
     * @param cause 原目录校验失败
     */
    public StickerReplyException(RuntimeException cause) {
        super("表情包目录校验失败，并非图库为空；请检查配置、目录签名、原目标或插件代次。", cause);
    }

    /** @return 不含图库正文、来源和签名的固定原因 */
    @Override
    public String failureReason() {
        return getMessage();
    }
}
