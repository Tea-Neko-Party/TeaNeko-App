package org.zexnocs.teanekoagent.component.sticker;

import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;

/**
 * 素材下载的可公开失败原因，不把图片签名地址或远端响应正文带入日志。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.8.3-l
 */
final class StickerDownloadException extends IllegalArgumentException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String reason;

    /**
     * @param reason 下载器生成的固定说明，不能传入完整链接或外部异常消息
     */
    StickerDownloadException(String reason) {
        super(reason);
        this.reason = reason;
    }

    /**
     * @param reason 下载器生成的固定说明
     * @param cause 仅供结构化诊断读取类型和位置的原始异常
     */
    StickerDownloadException(String reason, Throwable cause) {
        super(reason, cause);
        this.reason = reason;
    }

    /** @return 不含签名参数或远端正文的下载失败说明 */
    @Override
    public String failureReason() {
        return reason;
    }
}
