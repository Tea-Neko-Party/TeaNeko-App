package org.zexnocs.teanekoplugin.agent.expression.sticker;

import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;
import java.util.Objects;

/**
 * 素材超过文件大小策略的正常拒绝，只公开受控测量来源与数值。
 * <br>2026/11/10：区分响应声明、流式下界及内联编码，避免把跳过大图当作模型故障。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
final class StickerSizeLimitException extends IllegalArgumentException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 2026/11/10：数值来源固定，不接收 URL、图片内容或外部错误文本。
     * @param measurement 大小的测量方式
     * @param actual 对应测量方式下观察到的大小
     * @param maxFileBytes 允许的原文件字节上限
     */
    StickerSizeLimitException(Measurement measurement, long actual, int maxFileBytes) {
        super("已跳过超限素材：" + measurement.label + "=" + actual
                + "，允许上限=" + (measurement == Measurement.BASE64_CHARACTERS
                ? 4 * ((maxFileBytes + 2L) / 3) : maxFileBytes)
                + "；config/agent/stickers.yml 的 max-file-bytes=" + maxFileBytes + " 字节。");
    }

    /** @return 不含来源内容的文件大小对照 */
    @Override
    public String failureReason() {
        return Objects.requireNonNull(super.getMessage());
    }

    /**
     * 大小证据的固定来源；流式中断只知道已读取下界，不能冒充完整文件大小。
     * <br>2026/11/10：为大小报告提供固定单位和证据类型。
     *
     * @author zExNocs
     * @date 2026/11/10
     * @since 4.9.1-l
     * @version 4.9.1-l
     */
    enum Measurement {
        CONTENT_LENGTH("响应声明字节数"),
        STREAM_BYTES("已读取字节数（至少）"),
        DECODED_BYTES("解码字节数"),
        BASE64_CHARACTERS("Base64 字符数");

        private final String label;

        /**
         * 固定诊断名称，不接受运行时输入。
         * @param label 对应测量方式的中文名称与单位
         */
        Measurement(String label) {
            this.label = label;
        }
    }
}
