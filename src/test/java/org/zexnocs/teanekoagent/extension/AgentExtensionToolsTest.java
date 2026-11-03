package org.zexnocs.teanekoagent.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.adapter.extension.AgentExtensionTools;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.knowledge.IKnowledgeSource;
import org.zexnocs.teanekoagent.knowledge.KnowledgeChunk;
import org.zexnocs.teanekoagent.llm.framework.interaction.InteractionRuntime;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationResult;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.Entity;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用内存后端示例验证实际 LLM 工具契约、可信来源、授权撤销和业务回执，不访问宿主文件或执行代码。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
@Timeout(10)
class AgentExtensionToolsTest {
    /**
     * 复用原工具集、组件和手动执行器，后端只作可运行的内存示例。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    private static final class Harness implements AutoCloseable {
        final InteractionRuntime runtime = new InteractionRuntime();
        final ObjectMapper mapper = new ObjectMapper();
        final ExecutionControl control = ExecutionControl.create(LLMCallLimits.defaults().newBudget());
        final AtomicBoolean allowed = new AtomicBoolean(true);
        final AtomicBoolean failWrite = new AtomicBoolean();
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger commits = new AtomicInteger();
        final AtomicReference<String> body = new AtomicReference<>("原文件");
        final AtomicReference<String> version = new AtomicReference<>("v1");
        final Map<String, IAgentFileAccess.WriteResult> receipts = new HashMap<>();
        final Map<String, String> commands = new HashMap<>();
        final AtomicReference<CompletableFuture<IAgentCodeExecutor.Result>> codeReply = new AtomicReference<>();
        final AgentActorContext actor = new AgentActorContext(new AgentScope("客户端", "群"), AgentActorContext.Origin.USER,
                Optional.of(UUID.randomUUID()), "真实用户", "真实消息", "真实目标");
        final AgentRunRequest request = new AgentRunRequest(UUID.randomUUID(), actor, AgentActivity.SERVICE,
                List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("明确宿主输入").build()).build()));
        final AgentDefinition definition = new AgentDefinition("extensions", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values()));
        final AgentRuntimeSnapshot settings = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(null), ModelCapabilities.textOnly(), LLMInteractionSettings.defaults());
        final AgentExtensionTools extensions;
        final IAgentPromptSource prompts;
        final IAgentToolSource tools;
        final AgentPromptInput input;
        final LLMToolSet set;
        final ToolInvocationContext trusted;
        /** 构造内存文件/表达式和知识源，不为默认应用安装实际执行器。 */
        Harness() {
            extensions = new AgentExtensionTools((capability, context) -> {
                context.control().checkActive(); assertEquals(actor, context.actor());
                return allowed.get() ? CompletableFuture.completedStage(null) : CompletableFuture.failedStage(new IllegalStateException("不能泄露的授权细节"));
            }, mapper, runtime);
            var files = new IAgentFileAccess() {
                /**
                 * 返回有版本的内存正文。
                 * @param path 已校验相对路径
                 * @param maximum 正文上限
                 * @param context 可信调用
                 * @return 实际读取
                 */
                @Override public CompletionStage<ReadResult> read(String path, int maximum, AgentExtensionContext context) {
                    assertEquals(AgentExtensionContext.Phase.EXECUTION, context.phase());
                    reads.incrementAndGet(); assertEquals("notes/a.txt", path); return CompletableFuture.completedStage(new ReadResult(true, version.get(), body.get(), true));
                }
                /**
                 * 以实际上下文防重并按原版本修改内存正文。
                 * @param path 相对路径
                 * @param text 新正文
                 * @param expected 原版本
                 * @param context 可信防重标识
                 * @return 原提交回执
                 */
                @Override public CompletionStage<WriteResult> write(String path, String text, String expected, AgentExtensionContext context) {
                    if (failWrite.get()) { return CompletableFuture.failedStage(new IllegalStateException("不得泄露的后端凭据")); }
                    var key = context.requestId() + ":" + context.callId(); var command = mapper.writeValueAsString(List.of(path, text, expected));
                    if (commands.containsKey(key) && !commands.get(key).equals(command)) { return CompletableFuture.completedStage(new WriteResult(AgentExtensionStatus.REJECTED, null)); }
                    var receipt = receipts.computeIfAbsent(key, ignored -> {
                        commands.put(key, command);
                        if (!version.get().equals(expected)) { return new WriteResult(AgentExtensionStatus.REJECTED, null); }
                        body.set(text); version.set("v" + (commits.incrementAndGet() + 1)); return new WriteResult(AgentExtensionStatus.COMPLETED, version.get());
                    });
                    return CompletableFuture.completedStage(receipt);
                }
            };
            var code = new IAgentCodeExecutor() {
                /** @return 本示例明确支持的表达式名称 */
                @Override public Set<String> languages() { return Set.of("expr"); }
                /**
                 * 只返回内存结果，不运行用户代码或任何宿主进程。
                 * @param language 已声明名称
                 * @param text 候选正文
                 * @param context 可信调用
                 * @return 可控制的实际观察
                 */
                @Override public CompletionStage<Result> execute(String language, String text, AgentExtensionContext context) {
                    var pending = codeReply.get(); return pending == null ? CompletableFuture.completedStage(new Result(AgentExtensionStatus.COMPLETED, "示例结果", true)) : pending;
                }
            };
            IKnowledgeSource knowledge = (query, context) -> CompletableFuture.completedStage(new IKnowledgeSource.Page(
                    List.of(new KnowledgeChunk("内存示例", "文档甲", "片段一", "r1", "带来源的知识证据")), false));
            prompts = extensions.prompts(IAgentPromptSource.empty(), EnumSet.allOf(AgentExtensionCapability.class));
            var loading = prompts.resolve(definition, request, settings, control).toCompletableFuture(); runtime.runAll();
            input = new AgentPromptInput(request.messages(), loading.join(), 0);
            tools = extensions.tools((agent, message, config) -> List.of(), List.of(extensions.fileRead(files), extensions.fileWrite(files),
                    extensions.codeExecute(code), extensions.knowledgeSearch(knowledge)));
            set = new LLMToolSet(mapper, tools.select(definition, request, settings, input));
            trusted = new ToolInvocationContext(request.requestId(), new Entity(EntityId.random(), ComponentPatch.builder().put(AgentComponents.REQUEST, request)
                    .put(AgentComponents.ACTOR, actor).put(AgentComponents.PROMPT_INPUT, input).build()).snapshot(), control);
        }
        /**
         * 通过原工具集执行并推进业务队列。
         * @param name 真实工具名称
         * @param id 原调用 ID
         * @param json 业务参数
         * @return 实际工具回执
         */
        ToolInvocationResult invoke(String name, String id, String json) {
            var result = set.execute(LLMToolCall.builder().name(name).id(id).arguments(json).build(), trusted).toCompletableFuture(); runtime.runAll(); return result.join();
        }
        /** 只关闭自己拥有的控制，借用后端不持有线程或宿主资源。 */
        @Override public void close() { control.close(); runtime.runAll(); assertEquals(0, runtime.pendingTimers()); }
    }
    /** 验证实际读写/防重、知识来源和代码回执，参数不能伪造权限或越界路径。 */
    @Test void explicitBackendsKeepIdentityVersionsAndBusinessReceipts() {
        try (var h = new Harness()) {
            var read = h.invoke("file_read", "read", "{\"path\":\"notes/a.txt\"}"); assertEquals(ToolInvocationResult.Status.SUCCESS, read.status()); assertTrue(read.output().contains("v1"));
            h.invoke("file_read", "escape", "{\"path\":\"../private.txt\"}");
            h.invoke("file_read", "spoof", "{\"path\":\"notes/a.txt\",\"actor\":\"伪造\"}"); assertEquals(1, h.reads.get());
            var write = "{\"path\":\"notes/a.txt\",\"text\":\"新文件\",\"expectedRevision\":\"v1\"}";
            assertEquals(h.invoke("file_write", "write", write).output(), h.invoke("file_write", "write", write).output()); assertEquals(1, h.commits.get());
            assertTrue(h.invoke("file_write", "write", write.replace("新文件", "不同正文")).output().contains("REJECTED"));
            assertTrue(h.invoke("file_write", "old-version", write).output().contains("REJECTED"));
            var page = h.invoke("knowledge_search", "knowledge", "{\"query\":\"资料\"}").output();
            assertTrue(page.contains("文档甲")); assertTrue(page.contains("r1")); assertTrue(page.contains("\"complete\":false"));
            assertTrue(h.invoke("code_execute", "code", "{\"language\":\"expr\",\"code\":\"示例\"}").output().contains("COMPLETED"));
            assertTrue(h.tools.select(h.definition, h.request, h.settings, new AgentPromptInput(h.request.messages(), List.of(), 1)).isEmpty());
            h.failWrite.set(true); var unknown = h.invoke("file_write", "unknown", write).output();
            assertTrue(unknown.contains("INDETERMINATE")); assertFalse(unknown.contains("后端凭据")); h.control.checkActive();
        }
    }
    /** 验证后端开始后撤销授权不公开敏感结果，旧完成重放也不能绕过当前资格。 */
    @Test void revokedAuthorizationWithholdsLateOutputAndReplay() {
        try (var h = new Harness()) {
            var backend = new CompletableFuture<IAgentCodeExecutor.Result>(); h.codeReply.set(backend);
            var result = h.set.execute(LLMToolCall.builder().name("code_execute").id("late").arguments("{\"language\":\"expr\",\"code\":\"示例\"}").build(), h.trusted).toCompletableFuture();
            h.runtime.runAll(); assertFalse(result.isDone()); h.allowed.set(false);
            backend.complete(new IAgentCodeExecutor.Result(AgentExtensionStatus.COMPLETED, "不再允许公开的输出", true)); h.runtime.runAll();
            assertTrue(result.join().output().contains("INDETERMINATE")); assertFalse(result.join().output().contains("不再允许公开的输出"));
            var replay = h.prompts.verifyReplay(h.request, h.input, h.control).toCompletableFuture(); h.runtime.runAll();
            assertThrows(CompletionException.class, replay::join); h.control.checkActive();
        }
    }
}
