package org.zexnocs.teanekoplugin.agent.expression.sticker;

import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;

/**
 * 已开始素材作业的资格失效，携带固定原因供持久终态及安全诊断使用。
 * <br>2026/11/10：资格撤销不再隐藏为普通运行状态异常。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
public final class StickerAccessException extends IllegalStateException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;

    private final StickerAccess access;

    /** @param access 本次拒绝资格的固定分类 */
    public StickerAccessException(StickerAccess access) {
        super(access.description());
        this.access = access;
    }

    /** @return 本次资格失效的类型 */
    public StickerAccess access() {
        return access;
    }

    /** @return 不包含来源和配置值的固定说明 */
    @Override
    public String failureReason() {
        return access.description();
    }
}
