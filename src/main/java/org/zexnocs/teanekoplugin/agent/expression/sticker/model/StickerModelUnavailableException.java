package org.zexnocs.teanekoplugin.agent.expression.sticker.model;

import org.zexnocs.teanekocore.logger.IFailureReason;
import java.io.Serial;

/**
 * 可由备用视觉路由接管的模型不可用，区别于权限及生命周期撤销。
 * <br>2026/11/10：只携带固定安全原因，不包含凭据或模型原文。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
public final class StickerModelUnavailableException extends IllegalStateException implements IFailureReason {
    @Serial private static final long serialVersionUID = 1L;

    /** @param message 调用方固定的安全说明 */
    public StickerModelUnavailableException(String message) {
        super(message);
    }

    /** @return 固定的模型不可用原因 */
    @Override public String failureReason() {
        return getMessage();
    }
}
