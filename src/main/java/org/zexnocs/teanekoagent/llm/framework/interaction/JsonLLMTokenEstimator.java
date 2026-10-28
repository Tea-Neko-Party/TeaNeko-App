package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import tools.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 按消息、工具定义及透明状态的 UTF-8 JSON 字节数加固定开销进行保守本地估算。
 * 这不是具体模型的 tokenizer，图片计费和供应商隐藏开销尤其不能据此保证精确上界。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class JsonLLMTokenEstimator implements ILLMTokenEstimator {
    private final ObjectMapper mapper;

    /**
     * 借用 JSON 转换器，不建立网络、线程或外部计费资源。
     * @param mapper 应用或离线使用方提供的转换器
     */
    public JsonLLMTokenEstimator(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "JSON 转换器不能为空。");
    }

    /**
     * 流式计数字节，避免为大图片额外构造完整 JSON 字节数组；不返回或记录原始内容。
     * @param prompt 已固定输入，工具必须为只读定义
     * @return 标记为 utf8-json-v1 的输入估算
     */
    @Override public LLMTokenEstimate estimate(ILLMPrompt prompt) {
        var options = prompt.getOptions();
        var tools = options == null ? List.of() : options.findTools().orElse(List.of());
        var metadata = options == null ? Map.of() : options.getMetadata();
        var output = new CountingOutput();
        mapper.writeValue(output, Map.of("messages", prompt.getMessages(), "tools", tools, "metadata", metadata));
        long overhead = 256L + 64L * prompt.getMessages().size() + 256L * tools.size();
        long tokens = output.count > Long.MAX_VALUE - overhead ? Long.MAX_VALUE : output.count + overhead;
        return new LLMTokenEstimate(tokens, "utf8-json-v1");
    }

    /**
     * 只统计序列化长度的内存输出端，不持有任何正文副本。
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class CountingOutput extends OutputStream {
        private long count;

        /**
         * 计入一个输出字节，忽略具体数据值。
         * @param value 当前序列化字节
         */
        @Override public void write(int value) { add(1); }

        /**
         * 按字节批次计数，不保留或读取数据内容。
         * @param bytes 当前序列化缓冲区
         * @param offset 有效数据起点
         * @param length 有效数据长度
         * @throws IndexOutOfBoundsException 当序列化器提供的缓冲区范围非法时
         */
        @Override public void write(byte[] bytes, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            add(length);
        }

        /**
         * 饱和累加长度，禁止溢出后得到较小的预算成本。
         * @param length 本批非负字节数
         */
        private void add(long length) { count = length > Long.MAX_VALUE - count ? Long.MAX_VALUE : count + length; }
    }
}
