package org.zexnocs.teanekoagent.llm.framework.model;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfigParameter;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAIChatCompletionModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIModelOptions;
import org.zexnocs.teanekocore.actuator.execution.BudgetExceededException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证模型容量、默认输入比例及参数快照在供应商转换期间保持一致。
 *
 * 修改记录：<br>
 * 2026/11/04：覆盖比例边界、完整输出预留、配置缺失及上下文参数复制。
 * <br>2026/11/04：校正当前实现的版本归属为 v4.7.2-l。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：根据原提交职责恢复编码损坏的历史注释。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.2-l
 * @version 4.9.0-l
 */
class LLMContextWindowTest {
    /**
     * 输入同时受比例与剩余上下文约束，边界相等放行，超出一项即拒绝。
     * @param tokens 总上下文容量
     * @param ratio 输入比例
     * @param output 完整输出额度
     * @param allowed 预期可用输入额度
     */
    @ParameterizedTest
    @CsvSource({"10000,0.8,1000,8000", "10000,0.8,3000,7000", "10000,1,1000,9000", "101,0.8,1,80"})
    void preservesFullOutputWhileApplyingInputRatio(long tokens, double ratio, int output, long allowed) {
        var window = new LLMContextWindow(tokens, ratio);
        assertEquals(allowed, window.maxInputTokens(output));
        assertDoesNotThrow(() -> window.requireFits(allowed, output));
        var rejected = assertThrows(BudgetExceededException.class, () -> window.requireFits(allowed + 1, output));
        assertEquals(LLMContextWindow.INPUT_TOKENS, rejected.key());
    }

    /** 缺失容量与非法额度必须明确失败，百分比未填写时使用百分之八十。 */
    @Test
    void requiresExplicitModelCapacityAndDefaultsRatio() {
        var entry = new LLMModelFileConfigParameter();
        entry.setId("test");
        entry.setModel("离线模型");
        entry.setMaxTokens(4000);
        assertThrows(LLMConfigurationException.class, () -> entry.toOptions(null));
        entry.setContextWindowTokens(20000L);
        var options = entry.toOptions(null);
        assertEquals(4000, options.getMaxTokens());
        assertEquals(new LLMContextWindow(20000, 0.8), options.findContextWindow().orElseThrow());
        entry.setContextInputRatio(0.6);
        assertEquals(12000, entry.toOptions(null).findContextWindow().orElseThrow().maxInputTokens(4000));
        entry.setContextInputRatio(1.1);
        assertThrows(LLMConfigurationException.class, () -> entry.toOptions(null));
        assertThrows(LLMConfigurationException.class, () -> new LLMContextWindow(1000, 0.8).maxInputTokens(1001));
    }

    /** 参数快照与供应商转换必须保留模型容量，不把后续原对象修改传播到旧请求。 */
    @Test
    void preservesContextAcrossSnapshotsAndProviderCopies() {
        var context = new LLMContextWindow(131072, 0.8);
        var original = LLMModelOptions.builder().provider("test").model("离线模型").maxTokens(16000)
                .contextWindow(context).build();
        var snapshot = new LLMModelOptionsSnapshot(original);
        original.setContextWindow(new LLMContextWindow(10000, 0.5));
        var captured = snapshot.toOptions();
        assertEquals(context, captured.findContextWindow().orElseThrow());
        assertEquals(context, LLMModelOptions.merge(captured, LLMModelOptions.empty()).findContextWindow().orElseThrow());
        assertEquals(context, DeepSeekModelOptions.copyOf(captured).findContextWindow().orElseThrow());
        assertEquals(context, OpenAIModelOptions.copyOf(captured).findContextWindow().orElseThrow());
        assertEquals(context, OpenAIChatCompletionModelOptions.copyOf(captured).findContextWindow().orElseThrow());
        assertEquals(16000, captured.getMaxTokens());
    }
}
