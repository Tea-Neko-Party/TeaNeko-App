package org.zexnocs.teanekoagent_old.memory;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMFunctionParameterSchemaFactory;
import org.zexnocs.teanekoagent.llm.framework.tool.parameter.LLMObjectFunctionParameter;
import org.zexnocs.teanekoagent_old.agent.prompt.AgentRequestContext;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 记忆时间查询工具测试。
 * <br>验证时间参数转换、非法时间输入处理和 LLM Tool 参数 schema。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
class AgentMemoryToolProviderTimeTest {
    /**
     * 验证 ISO 日期会按本地自然日转换为起止时间，再交给记忆查询服务。
     */
    @Test
    void convertsIsoDateToLocalDayRangeBeforeQuerying() {
        var capturedStart = new AtomicReference<Instant>();
        var capturedEnd = new AtomicReference<Instant>();
        var provider = getAgentMemoryToolProvider(capturedStart, capturedEnd);

        var result = provider.queryMemoryByTime(
                "scope",
                "agent",
                "user",
                "2026-06-04",
                null,
                null,
                5
        );

        Assertions.assertNotNull(capturedStart.get());
        Assertions.assertNotNull(capturedEnd.get());
        Assertions.assertTrue(capturedEnd.get().isAfter(capturedStart.get()));
        Assertions.assertTrue(result.contains("memory"));
    }

    /**
     * 创建一个带有模拟查询服务的 {@link AgentMemoryToolProvider}，
     * 该模拟服务会将传入的时间范围参数捕获到 {@link AtomicReference} 中供测试断言使用。
     *
     * @param capturedStart 用于捕获查询起始时间的容器
     * @param capturedEnd   用于捕获查询结束时间的容器
     * @return 配置了模拟查询服务的 {@link AgentMemoryToolProvider} 实例
     */
    private static @NonNull AgentMemoryToolProvider getAgentMemoryToolProvider(AtomicReference<Instant> capturedStart,
                                                                               AtomicReference<Instant> capturedEnd) {
        var queryService = new AgentMemoryQueryService() {
            @Override
            public List<AgentMemoryRecord> findRelevant(AgentRequestContext context,
                                                        Instant start,
                                                        Instant end,
                                                        int limit) {
                capturedStart.set(start);
                capturedEnd.set(end);
                var record = new AgentMemoryRecord();
                record.setContent("memory");
                record.setEventTime(MemoryTimeRange.exact(start));
                return List.of(record);
            }
        };
        return new AgentMemoryToolProvider(queryService);
    }

    /**
     * 验证工具拒绝未解析的自然语言时间，并向模型返回 ISO-8601 参数要求。
     */
    @Test
    void rejectsNaturalLanguageTimeBoundary() {
        var provider = new AgentMemoryToolProvider(new AgentMemoryQueryService());

        var result = provider.queryMemoryByTime(
                "scope",
                "agent",
                "user",
                "一周前",
                null,
                null,
                5
        );

        Assertions.assertTrue(result.contains("ISO-8601"));
    }

    /**
     * 验证时间点和时间范围参数会暴露给模型，但不会被错误标记为必填参数。
     *
     * @throws NoSuchMethodException 当待检查的工具方法不存在时抛出
     */
    @Test
    void exposesTimePointAndRangeAsOptionalToolParameters() throws NoSuchMethodException {
        var method = AgentMemoryToolProvider.class.getDeclaredMethod(
                "queryMemoryByTime",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Integer.class
        );
        var schema = Assertions.assertInstanceOf(
                LLMObjectFunctionParameter.class,
                LLMFunctionParameterSchemaFactory.fromMethod(method)
        );

        Assertions.assertEquals(List.of("scopeId", "agentId", "userId"), schema.getRequired());
        Assertions.assertTrue(schema.getProperties().containsKey("timePoint"));
        Assertions.assertTrue(schema.getProperties().containsKey("rangeStart"));
        Assertions.assertTrue(schema.getProperties().containsKey("rangeEnd"));
    }
}
