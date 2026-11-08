package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;

/**
 * 图片格式及解码资源校验的安全原因，只接收固定说明及已验证的数字，不携带下载地址。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
final class StickerImageException extends IllegalArgumentException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;

    /** @param reason 固定说明或图片元数据统计，不允许传入模型输出或远端异常正文 */
    StickerImageException(String reason) {
        super(reason);
    }

    /** @return 可公开的格式或解码预算原因 */
    @Override
    public String failureReason() {
        return "素材图片解码失败：" + getMessage();
    }
}
