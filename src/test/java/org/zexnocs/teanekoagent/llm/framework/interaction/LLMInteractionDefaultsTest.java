package org.zexnocs.teanekoagent.llm.framework.interaction;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
// 2026/11/05：交互测试沿用迁入 configuration 包的真实装配。
import org.zexnocs.teanekoagent.llm.configuration.LLMInteractionConfig;
import org.zexnocs.teanekoagent.llm.configuration.SpringLLMInteractionDefaults;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 不启动应用或数据库，验证配置懒加载、脱敏错误、显式覆盖及借用资源装配。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：适配模型专属 configuration 包，保留原交互配置验证。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * <br>2026/11/08：夹具通过真实插件组件契约准备参数并验证改造后的端口。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
class LLMInteractionDefaultsTest {
    private static final String PREFIX = "tea-neko.llm.interaction.";

    /** 2026/11/07：真实 Spring 工厂可仅用模型端口与 Core 资源装配，不要求 Agent 观察服务。 */
    @Test
    void springAssemblyDoesNotRequireAnAgentObserver() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ILLMModelService.class, () -> mock(ILLMModelService.class));
            context.registerBean(ILLMInteractionDefaults.class, () -> mock(ILLMInteractionDefaults.class));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(org.zexnocs.teanekocore.logger.ILogger.class,
                    () -> mock(org.zexnocs.teanekocore.logger.ILogger.class));
            context.registerBean("virtualExecutor", ExecutorService.class, () -> mock(ExecutorService.class));
            context.registerBean("taskExecutor", ScheduledExecutorService.class, () -> mock(ScheduledExecutorService.class));
            context.register(LLMInteractionConfig.class);
            context.refresh();

            assertInstanceOf(LLMInteractionService.class, context.getBean(ILLMInteractionService.class));
            assertTrue(context.getBeansOfType(ILLMInteractionObserver.class).isEmpty());
        }
    }

    /** 验证缺失字段采用默认值，后续调用读取新配置但不改变旧快照。 */
    @Test void readsDefaultsAndReloadsForFollowingInteractions() {
        var environment = new MockEnvironment();
        var defaults = new SpringLLMInteractionDefaults(environment);
        var original = defaults.resolve();
        assertEquals(LLMInteractionSettings.defaults(), original);
        environment.setProperty(PREFIX + "max-model-calls", "1");
        environment.setProperty(PREFIX + "model-timeout-seconds", "20");
        var changed = defaults.resolve();
        assertEquals(1, changed.limits().maxModelCalls());
        assertEquals(0, changed.limits().maxToolRounds());
        assertEquals(Duration.ofSeconds(20), changed.modelTimeout());
        assertEquals(3, original.limits().maxModelCalls());
    }

    /** 验证错误本地配置不会在构造阶段抛出，调用错误不包含属性值或原始异常链。 */
    @Test void delaysInvalidConfigurationAndRedactsParsingFailure() {
        var environment = new MockEnvironment().withProperty(PREFIX + "max-model-calls", "测试敏感值");
        var defaults = assertDoesNotThrow(() -> new SpringLLMInteractionDefaults(environment));
        var failure = assertThrows(LLMConfigurationException.class, defaults::resolve);
        assertFalse(failure.getMessage().contains("测试敏感值"));
        assertNull(failure.getCause());
        environment.setProperty(PREFIX + "max-model-calls", "3");
        environment.setProperty(PREFIX + "tool-timeout-seconds", "0");
        assertThrows(LLMConfigurationException.class, defaults::resolve);
        environment.setProperty(PREFIX + "tool-timeout-seconds", "15");
        assertEquals(LLMInteractionSettings.defaults(), defaults.resolve());
    }

    /** 验证默认设置失效只结束本次交互，显式覆盖仍可进入受控执行。 */
    @Test void explicitSettingsBypassInvalidLocalDefaults() {
        var runtime = new InteractionRuntime();
        var models = mock(ILLMModelService.class);
        var defaults = mock(ILLMInteractionDefaults.class);
        when(defaults.resolve()).thenThrow(new LLMConfigurationException("测试配置失效。"));
        var service = new LLMInteractionService(models, defaults, runtime, runtime.scheduler);
        try (var parent = runtime.parent(LLMCallLimits.defaults())) {
            var context = new ToolInvocationContext(UUID.randomUUID(),
                    EntitySnapshot.of(EntityId.random(), ComponentPatch.builder().build()), parent);
            var prompt = new LLMPrompt(LLMUserMessage.builder().contents(List.of()).build());
            var tools = new LLMToolSet(new ObjectMapper(), List.of());
            var unavailable = service.interact(prompt, tools, context).toCompletableFuture().join();
            assertEquals(LLMInteractionResult.Status.CONFIGURATION_ERROR, unavailable.status());
            assertEquals(0, unavailable.modelAttempts());
            verifyNoInteractions(models);
            var pending = service.interact(prompt, tools, context, LLMInteractionSettings.defaults());
            assertFalse(pending.toCompletableFuture().isDone());
            verify(defaults, times(1)).resolve();
            parent.cancel();
            assertEquals(LLMInteractionResult.Status.CANCELLED, pending.toCompletableFuture().join().status());
        }
    }

    /** 验证 Spring 工厂只装配既有执行器，不读取默认配置或关闭借用资源。 */
    @Test void assemblyDoesNotReadDefaultsOrOwnThreadPools() {
        var models = mock(ILLMModelService.class);
        var defaults = mock(ILLMInteractionDefaults.class);
        var executor = mock(ExecutorService.class);
        var scheduler = mock(ScheduledExecutorService.class);
        var estimator = mock(ILLMTokenEstimator.class);
        assertInstanceOf(LLMInteractionService.class,
                new LLMInteractionConfig().llmInteractionService(models, defaults, executor, scheduler, estimator));
        verifyNoInteractions(models, defaults, executor, scheduler, estimator);
    }

    /** 验证可选 token 配置支持长整数、零额度和负数拒绝，缺省保持未声明。 */
    @Test void resolvesOptionalLongTokenLimitWithoutTreatingZeroAsUnlimited() {
        var environment = new MockEnvironment();
        var defaults = new SpringLLMInteractionDefaults(environment);
        assertNull(defaults.resolve().limits().maxTotalTokens());
        environment.setProperty(PREFIX + "max-total-tokens", "0");
        assertEquals(0L, defaults.resolve().limits().maxTotalTokens());
        environment.setProperty(PREFIX + "max-total-tokens", Long.toString(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, defaults.resolve().limits().maxTotalTokens());
        environment.setProperty(PREFIX + "max-total-tokens", "-1");
        assertThrows(LLMConfigurationException.class, defaults::resolve);
        environment.setProperty(PREFIX + "max-total-tokens", "9223372036854775808");
        assertThrows(LLMConfigurationException.class, defaults::resolve);
    }
}
