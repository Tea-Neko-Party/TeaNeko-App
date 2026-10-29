package org.zexnocs.teanekoagent.llm.framework.message;

import org.zexnocs.teanekoagent.llm.framework.message.content.ImageLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMToolMessage;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 统一消息的确定性 JSON 度量和指纹，显式包含图像、工具协议和供应商透明状态。
 * 只在内存流中计数和摘要，不保存额外完整正文；字节数不代表模型 token 或费用。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class LLMMessageCodec {
    private final ObjectMapper mapper;

    /**
     * 借用线程安全的 JSON 转换器，不修改其设置。
     * @param mapper 本地 JSON 转换器
     * @throws NullPointerException 当转换器为空时
     */
    public LLMMessageCodec(ObjectMapper mapper) { this.mapper = Objects.requireNonNull(mapper, "消息转换器不能为空。"); }

    /**
     * 对已冻结消息计算完整协议指纹，对象键排序而列表顺序保持不变。
     * @param messages 已冻结统一消息
     * @return UTF-8 字节数及 SHA-256 指纹
     * @throws IllegalArgumentException 当正文类型不受支持或数据无法编码时
     */
    public Signature signature(List<ILLMMessage> messages) {
        return encode(messages.stream().map(this::messageValue).toList());
    }

    /**
     * 度量宿主的不可变诊断对象，不返回其正文；调用必须在业务锁外。
     * @param value 只含数据的宿主诊断
     * @return 实际 JSON 序列化字节数
     * @throws IllegalArgumentException 当诊断无法序列化时
     */
    public long serializedBytes(Object value) { return encode(value).bytes(); }

    /**
     * 显式保留协议字段，避免通用序列化忽略透明状态或把文本内容压成普通字符串。
     * @param message 已冻结消息
     * @return 内部使用的规范化 JSON 数据
     * @throws IllegalArgumentException 当正文类型不受支持时
     */
    private Object messageValue(ILLMMessage message) {
        var value = new TreeMap<String, Object>();
        value.put("role", message.getRole().name());
        value.put("name", Objects.toString(message.getName(), ""));
        value.put("providerMetadata", ordered(message.getProviderMetadata()));
        value.put("contents", message.getContents().stream().map(content -> {
            var item = new TreeMap<String, Object>();
            item.put("type", content.getType());
            switch (content.getContentPart()) {
                case TextLLMContentPart text -> item.put("text", text.getText());
                case ImageLLMContentPart image -> { item.put("url", image.getUrl()); item.put("detail", image.getDetail().name()); }
                default -> throw new IllegalArgumentException("消息度量不支持该正文类型。");
            }
            return item;
        }).toList());
        if (message instanceof ILLMAssistantMessage assistant) {
            value.put("calls", assistant.getToolCalls().stream().map(call -> {
                var item = new TreeMap<String, Object>();
                item.put("id", call.getId()); item.put("type", call.getType());
                item.put("name", call.getName()); item.put("arguments", call.getArguments());
                return item;
            }).toList());
        }
        if (message instanceof ILLMToolMessage tool) { value.put("callId", tool.getToolCallId()); }
        return value;
    }

    /**
     * 对已冻结的 JSON 透明状态递归排序，不改变列表或数值表示。
     * @param value 已校验的 JSON 值
     * @return 规范化 JSON 值
     */
    private static Object ordered(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((key, item) -> result.put((String) key, ordered(item)));
            return result;
        }
        if (value instanceof List<?> list) { return list.stream().map(LLMMessageCodec::ordered).toList(); }
        return value;
    }

    /**
     * 流式编码，不在异常链中导出正文；每次调用拥有独立计数器和摘要器。
     * @param value 待编码数据
     * @return 字节度量与指纹
     * @throws IllegalArgumentException 当 JSON 编码失败时
     */
    private Signature encode(Object value) {
        try (var output = new DigestOutput()) {
            mapper.writeValue(output, value);
            return new Signature(output.count, HexFormat.of().formatHex(output.digest.digest()));
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("统一协议数据无法完成本地编码。");
        }
    }

    /**
     * 只保存计量及单向指纹的数据值。
     * @param bytes UTF-8 字节数
     * @param sha256 规范化 JSON 的 SHA-256
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record Signature(long bytes, String sha256) {}

    /**
     * 不保留正文的输出端，长度饱和累加，摘要器只由当前线程使用。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class DigestOutput extends OutputStream {
        private long count;
        private final MessageDigest digest;

        /**
         * 建立本次编码独享的标准摘要器。
         * @throws IllegalStateException 当运行环境缺少标准 SHA-256 时
         */
        DigestOutput() {
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("运行环境缺少标准消息摘要器。"); }
        }

        /**
         * 累加一个字节并更新摘要，不保存原值。
         * @param value 当前字节
         */
        @Override public void write(int value) { digest.update((byte) value); count = Math.min(Long.MAX_VALUE - 1, count) + 1; }

        /**
         * 累加一段序列化缓冲，避免逐字节调用产生额外开销。
         * @param bytes 缓冲区
         * @param offset 有效起点
         * @param length 有效长度
         * @throws IndexOutOfBoundsException 当缓冲区范围非法时
         */
        @Override public void write(byte[] bytes, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            digest.update(bytes, offset, length);
            count = length > Long.MAX_VALUE - count ? Long.MAX_VALUE : count + length;
        }
    }
}
