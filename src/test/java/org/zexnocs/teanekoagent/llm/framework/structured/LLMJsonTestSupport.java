package org.zexnocs.teanekoagent.llm.framework.structured;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryLayers;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityLearningOutput;
import org.zexnocs.teanekoplugin.agent.cognition.personality.StyleObservations;
import org.zexnocs.teanekoplugin.agent.expression.expression.LegacyReplyOutput;
import org.zexnocs.teanekoplugin.agent.expression.sticker.Stickers;
import org.zexnocs.teanekoplugin.agent.social.proactive.adapter.ProactiveOutput;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 业务回归共用真实结构化服务，只替换类型发现与模型传输。
 * <br>2026/11/06：不通过模拟目标对象跳过描述、提示和严格绑定。
 * <br>2026/11/06：将长度、数量或枚举约束纳入协议声明，并在本地落实校验。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 * <br>2026/11/10：允许夹具注入真实截止调度器，普通同步用例借用无线程的调度替身。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.9.1-l
 */
public final class LLMJsonTestSupport {
    /** 不实例化测试工具。 */
    private LLMJsonTestSupport() { }

    /**
     * @param interactions 可控交互端口
     * @param mapper 原测试转换器
     * @return 包含真实生产合同的服务，不写本地原文
     */
    public static LLMJsonService create(ILLMInteractionService interactions, ObjectMapper mapper) {
        // 2026/11/10：旧同步用例不创建线程；验证真实截止时间时使用具名重载。
        return create(interactions, mapper, mock(ScheduledExecutorService.class));
    }

    /**
     * 2026/11/10：时间边界用例借用自己的调度器并负责关闭。
     * @param interactions 原交互端口
     * @param mapper 原转换器
     * @param scheduler 调用方拥有的截止调度器
     * @return 使用真实结构合同的生成服务
     */
    public static LLMJsonService create(ILLMInteractionService interactions, ObjectMapper mapper,
            ScheduledExecutorService scheduler) {
        return new LLMJsonService(schemas(Stickers.Analysis.class, MemoryLayers.Digest.class, StyleObservations.Analysis.class,
                PersonalityLearningOutput.class, ProactiveOutput.class, LegacyReplyOutput.class), interactions, mapper,
                new LLMJsonDiagnostics(false, "未启用的测试诊断"),
                // 2026/11/06：离线端口显式保留输入，供应商能力准备由协议集成测试覆盖。
                mock(org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService.class,
                        org.mockito.Mockito.CALLS_REAL_METHODS), scheduler);
    }

    /**
     * 在测试初始化时构造目录，实际读写仍经过扫描器的原子发布。
     * @param types 已注解类型
     * @return 初始化后的目录
     */
    public static LLMJsonSchemas schemas(Class<?>... types) {
        var scanner = mock(IClassScanner.class);
        var declarations = new LinkedHashMap<Class<?>, LLMJsonType>();
        for (var type : types) { declarations.put(type, type.getAnnotation(LLMJsonType.class)); }
        when(scanner.getClassesWithAnnotation(LLMJsonType.class)).thenReturn(declarations);
        var schemas = new LLMJsonSchemas(scanner);
        schemas.init();
        return schemas;
    }
}
