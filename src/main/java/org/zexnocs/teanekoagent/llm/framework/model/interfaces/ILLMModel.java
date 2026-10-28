package org.zexnocs.teanekoagent.llm.framework.model.interfaces;

import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;

import java.util.List;

/**
 * 大语言模型的客户端接口。
 * <br>新的模型供应商需要实现该接口并使用 {@link LLMModel} 标注，才能注册到 TeaNeko Agent。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.5.0-l
 */
public interface ILLMModel {
    /**
     * 获取模型调用使用的供应商 ID。
     * <br>该值用于 base options、能力校验和供应商日志，不参与模型注册；注册 ID 由 {@link LLMModel#id()} 提供。
     *
     * @return 模型供应商 ID
     */
    String getProvider();

    /**
     * 获取默认模型名称。
     * <br>该值不是模型注册 ID 的一部分，仅作为未显式传入 {@link ILLMModelOptions#getModel()} 时的默认模型名。
     *
     * @return 默认模型名称
     */
    String getModel();

    /**
     * 获取模型代码自带的基础配置。
     * <br>基础配置会写入供应商 ID 和基础模型名称，文件默认 options 和本次调用 options 可以继续覆盖未固定的调用参数。
     *
     * @return 模型基础配置
     */
    default ILLMModelOptions getBaseOptions() {
        return LLMModelOptions.builder()
                .provider(getProvider())
                .model(getModel())
                .build();
    }

    /**
     * 是否支持 Options
     *
     * @param options 模型 option
     * @return 当前适配器是否接受本次有效模型选项
     */
    default boolean supports(ILLMModelOptions options) {
        return true;
    }

    /**
     * 未声明的模型只接受文本；新供应商在自己的适配器中声明具体模型能力。
     *
     * @param model 本次实际选中的具体模型名称
     * @return 按实际模型或显式能力集合构造的不可变能力声明
     */
    default ModelCapabilities getCapabilities(String model) {
        return ModelCapabilities.textOnly();
    }

    /**
     * 使用一个 prompt 来获取模型结果。
     *
     * @param prompt 提示词
     * @return 模型调用结果 Future
     */
    TaskFuture<ILLMResult> call(ILLMPrompt prompt);

    /**
     * 显式执行策略；旧适配器未实现时拒绝，不能只给 Future 包超时而保留实际请求。
     *
     * @param prompt 经过宿主装配的消息与本次模型选项
     * @param execution 显式阶段策略，包含可选时限和借用执行控制
     * @return 承载实际任务完成信号及结果的 TaskFuture；本入口不阻塞等待
     * @throws UnsupportedOperationException 当该入口或适配器尚不支持请求的执行方式时
     */
    default TaskFuture<ILLMResult> call(ILLMPrompt prompt, ExecutionPolicy execution) {
        throw new UnsupportedOperationException("LLM adapter does not support execution policies.");
    }

    /**
     * 直接使用 {@link ILLMMessage} list 来获取模型结果，使用模型基础配置。
     *
     * @param messages 保持原始顺序的统一消息列表
     * @return 模型调用结果 Future
     */
    default TaskFuture<ILLMResult> call(List<ILLMMessage> messages) {
        return call(new LLMPrompt(messages, getBaseOptions()));
    }

    /**
     * 使用{@link ILLMMessage} list 和指定的 options 来获取模型结果。
     *
     * @param messages 保持原始顺序的统一消息列表
     * @param options 本次有效模型选项
     * @return 模型调用结果 Future
     */
    default TaskFuture<ILLMResult> call(List<ILLMMessage> messages, ILLMModelOptions options) {
        return call(new LLMPrompt(messages, options));
    }
}
