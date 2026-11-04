package org.zexnocs.teanekoagent.component.llm.framework.model;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.zexnocs.teanekoagent.component.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.IDefaultModelResolver;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.LLMModel;
import org.zexnocs.teanekoagent.component.llm.instance.deepseek.DeepSeekChatModel;
import org.zexnocs.teanekoagent.component.llm.instance.kimi.KimiChatModel;
import org.zexnocs.teanekoagent.component.llm.instance.openai.completions.OpenAIChatCompletionsModel;
import org.zexnocs.teanekoagent.component.llm.instance.openai.responses.OpenAIResponsesModel;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.Map;

/**
 * LLM 模型注解注册测试。
 * <br>验证模型服务只扫描带有 {@link LLMModel} 注解的接口实现，并使用注解 ID 注册模型。
 *
 * <br>2026/11/04：为配置别名查询声明空目录，保留未知模型拒绝断言。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.1
 * @version 4.8.0-l
 */
class LLMModelServiceRegistrationTest {
    /**
     * 验证注册 ID 完全来自注解，且不再执行纯接口扫描。
     */
    @Test
    void registersAnnotatedModelByAnnotationIdOnly() {
        var beanScanner = Mockito.mock(IBeanScanner.class);
        var fileConfigService = Mockito.mock(ILLMFileConfigService.class);
        // 2026/11/04：未注册标识还会查询已配置别名，夹具明确声明空配置目录。
        Mockito.when(fileConfigService.getConfig()).thenReturn(new org.zexnocs.teanekoagent.component.llm.file_config.LLMModelFileConfig());
        var defaultModelResolver = Mockito.mock(IDefaultModelResolver.class);
        var annotation = Mockito.mock(LLMModel.class);
        var model = Mockito.mock(ILLMModel.class);
        Mockito.when(annotation.id()).thenReturn("annotation-model");
        Mockito.when(model.getProvider()).thenReturn("different-provider");
        Mockito.when(beanScanner.getBeansWithAnnotationAndInterface(LLMModel.class, ILLMModel.class))
                .thenReturn(Map.of("modelBean", IndependentPair.of(annotation, model)));

        var service = new LLMModelService(beanScanner, fileConfigService, defaultModelResolver);
        service.init();

        Assertions.assertSame(model, service.getModel(LLMModelId.of("annotation-model")));
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.getModel(LLMModelId.of("different-provider"))
        );
        Mockito.verify(beanScanner).getBeansWithAnnotationAndInterface(LLMModel.class, ILLMModel.class);
        Mockito.verify(beanScanner, Mockito.never()).getBeansOfType(ILLMModel.class);
    }

    /**
     * 验证实际模型的注解注册契约，并确保 OpenAI Completions 通用实现不注册。
     */
    @Test
    void declaresAnnotationsOnlyOnRoutableModels() {
        Assertions.assertEquals(
                DeepSeekChatModel.PROVIDER,
                DeepSeekChatModel.class.getAnnotation(LLMModel.class).id()
        );
        Assertions.assertEquals(
                OpenAIResponsesModel.PROVIDER,
                OpenAIResponsesModel.class.getAnnotation(LLMModel.class).id()
        );
        Assertions.assertEquals(
                KimiChatModel.PROVIDER,
                KimiChatModel.class.getAnnotation(LLMModel.class).id()
        );
        Assertions.assertNull(OpenAIChatCompletionsModel.class.getAnnotation(LLMModel.class));
    }
}
