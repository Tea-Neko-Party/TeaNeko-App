package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

import org.zexnocs.teanekoplugin.contract.agent.AgentComponentFixtures;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.runtime.delivery.AgentReplyPlan.Mode;
import org.zexnocs.teanekoplugin.agent.expression.expression.ExpressionCompileSystem;
import org.zexnocs.teanekoplugin.agent.expression.expression.ExpressionComponent;
import org.zexnocs.teanekoplugin.agent.expression.expression.ExpressionDelivery;
import org.zexnocs.teanekoplugin.agent.expression.expression.ExpressionFeature;
import org.zexnocs.teanekoplugin.agent.expression.expression.ExpressionReplyProtocol;
import org.zexnocs.teanekoplugin.agent.expression.expression.ExpressionRuntime;
import org.zexnocs.teanekoplugin.agent.expression.expression.IExpressionClientAdapter;
import org.zexnocs.teanekoplugin.agent.expression.expression.ImageReplyHandler;
import org.zexnocs.teanekoplugin.agent.expression.expression.JsonReplyProtocol;
import org.zexnocs.teanekoplugin.agent.expression.expression.MediaReplySettings;
import org.zexnocs.teanekoplugin.agent.expression.expression.NativeFaceService;
import org.zexnocs.teanekoplugin.agent.expression.expression.PlainReplyProtocol;
import org.zexnocs.teanekoplugin.agent.expression.expression.ReplyCompileContext;
import org.zexnocs.teanekoagent.runtime.delivery.ReplyCoordinator;
import org.zexnocs.teanekoplugin.agent.expression.expression.ReplyPlans;
import org.zexnocs.teanekoplugin.agent.expression.expression.ReplySendContext;
import org.zexnocs.teanekoplugin.agent.expression.expression.TeaNekoExpressionClient;
import org.zexnocs.teanekoplugin.agent.expression.expression.TextReplyDirective;
import org.zexnocs.teanekoplugin.agent.expression.expression.TextReplyHandler;
import org.zexnocs.teanekoplugin.agent.expression.expression.file_config.AgentExpressionFileConfig;
import org.zexnocs.teanekoplugin.agent.expression.expression.file_config.AgentMediaReplyFileConfig;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageCodec;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationResult;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekChatCompletionMapper;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekChatCompletionResponseData;
import org.zexnocs.teanekoplugin.agent.cognition.memory.adapter.EasyDataDeliveryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.AgentDeliveryService;
import org.zexnocs.teanekoplugin.agent.expression.sticker.EasyDataStickerCatalog;
import org.zexnocs.teanekoplugin.agent.expression.sticker.FaceReplyDirective;
import org.zexnocs.teanekoplugin.agent.expression.sticker.FaceReplyHandler;
import org.zexnocs.teanekoplugin.agent.expression.sticker.IStickerAnalyzer;
import org.zexnocs.teanekoplugin.agent.expression.sticker.IStickerCatalog;
import org.zexnocs.teanekoplugin.agent.expression.sticker.LocalStickerAssets;
import org.zexnocs.teanekoplugin.agent.expression.sticker.MediaReplies;
import org.zexnocs.teanekoplugin.agent.expression.sticker.MediaReplyFeature;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerComponent;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerExtensionRegistry;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerLibrary;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerReplyDirective;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerReplyException;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerReplyHandler;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerReplyService;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerReplyTools;
import org.zexnocs.teanekoplugin.agent.expression.sticker.Stickers;
import org.zexnocs.teanekoplugin.agent.expression.sticker.WeightedStickerAdmission;
import org.zexnocs.teanekoplugin.agent.expression.sticker.file_config.AgentStickerFileConfig;
import org.zexnocs.teanekoagent.integration.message.NativeMessageSources;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.plugin.AgentPluginRuntime;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptTranscript;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.fake_client.FakeClient;
import org.zexnocs.teanekoapp.fake_client.FakeTeaNekoClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.content.IImageTeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.content.IReplyTeaNekoContentPart;
import org.zexnocs.teanekoplugin.onebot.common.agent.OnebotExpressionClient;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.BeanScanner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 复用事务与平台夹具验证从完整目录到独立发送、回执、回避及实际文件占用的闭环。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：验证生产插件令牌往返、哈希碰撞键序和资源防篡改。
 * <br>2026/11/04：原业务夹具显式使用历史装配，继续回归旧行为。
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：空图库明确提供空类别，未启用媒体能力不产生空值异常。
 * <br>2026/11/05：补充组件审查修复的离线回归及初始化夹具。
 * <br>2026/11/05：补充真实工具目录、供应商协议纠正、图片交付及目录失效的离线闭环。
 * <br>2026/11/07：验证事务重试、并发预算、取消、关闭和最终失败的查询合同。
 *
 * <br>2026/11/08：夹具通过真实插件组件契约准备参数并验证改造后的端口。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
@Timeout(20)
class MediaReplyIntegrationTest{
    /**
     * 2026/11/07：不同类别并发只结算可用的一页预算，结果重放不再收费。
     * @throws Exception 当有界图库查询失败时
     */
    @Test
    void concurrentCategoriesShareTheCommittedInputBudget() throws Exception {
        try (var h = new Harness(directory); var control = ExecutionControl.create()) {
            h.seed(7);
            var sad = h.seed(8);
            h.catalog.change(h.gallery.snapshot(), state -> {
                var entry = state.getEntries().get(sad.getHash());
                var old = Objects.requireNonNull(entry.getAnalysis());
                entry.setAnalysis(new Stickers.Analysis(old.kind(), old.confidence(), true,
                    old.visualDescription(), old.usageDescription(), "sad", old.secondaryTags(), old.quality(), old.reasons()));
                return true;
            }).toCompletableFuture().get(5, TimeUnit.SECONDS);
            var calibration = h.request("并发预算");
            var input = h.input(calibration, control);
            long pageCost = 0;
            for (String category : List.of("happy", "sad")) {
                var page = h.service.list(calibration, input, category, control).toCompletableFuture().get(5, TimeUnit.SECONDS);
                var unsigned = new MediaReplies.Page(page.requestId(), page.resourceProof(), page.category(),
                    page.catalogRevision(), page.items(), page.complete(), page.reason(), "");
                pageCost = Math.max(pageCost, h.db.mapper.writeValueAsBytes(unsigned).length + 512L);
            }
            long limit = new LLMMessageCodec(h.db.mapper).signature(input.messages()).bytes() + pageCost + 1024 + 128;
            var original = h.request("并发预算");
            var request = new AgentRunRequest(original.requestId(), original.actor(), original.activity(),
                original.messages(), original.conversation(), original.memoryReference(), original.userText(), limit);
            var actual = h.input(request, control);
            var first = h.service.list(request, actual, "happy", control).toCompletableFuture();
            var second = h.service.list(request, actual, "sad", control).toCompletableFuture();
            CompletableFuture.allOf(first, second).get(5, TimeUnit.SECONDS);
            var pages = List.of(first.join(), second.join());
            assertEquals(1, pages.stream().filter(MediaReplies.Page::complete).count());
            assertEquals(1, pages.stream().filter(page -> page.reason().equals("INPUT_BUDGET")).count());
            for (var page : pages) {
                assertEquals(page, h.service.list(request, actual, page.category(), control).toCompletableFuture().get(5, TimeUnit.SECONDS));
            }
        }
    }

    /**
     * 2026/11/07：取消、宿主关闭与最终提交失败均结束共享查询，不等待不可取消的数据库阶段。
     * @throws Exception 当隔离提交控制失败时
     */
    @Test
    void cancelledClosedAndFailedQueriesAlwaysSettle() throws Exception {
        for (String action : List.of("cancel", "close", "failure")) {
            try (var h = new Harness(directory.resolve(action)); var control = ExecutionControl.create()) {
                h.seed(7);
                var request = h.request("生命周期");
                var input = h.input(request, control);
                var entered = new CountDownLatch(1);
                var release = new CountDownLatch(1);
                h.db.beforeCommit.set(() -> {
                    entered.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("测试提交等待超时。");
                        }
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("测试提交被中断。", failure);
                    }
                    if (action.equals("failure")) {
                        throw new IllegalStateException("测试最终提交失败。");
                    }
                });
                var waiting = h.service.list(request, input, "happy", control).toCompletableFuture();
                try {
                    assertTrue(entered.await(3, TimeUnit.SECONDS));
                    if (action.equals("cancel")) {
                        control.cancel();
                    } else if (action.equals("close")) {
                        h.service.close();
                    } else {
                        release.countDown();
                    }
                    assertThrows(ExecutionException.class, () -> waiting.get(3, TimeUnit.SECONDS));
                } finally {
                    release.countDown();
                    h.db.beforeCommit.set(() -> {
                    });
                }
            }
        }
    }
    /**
     * 2026/11/06：提交冲突重试不重复结算图库输入预算，同类别重放保留原证明。
     * @throws Exception 当隔离测试未能在期限内完成时
     */
    @Test
    void queryRetryMustNotChargeInputBudgetTwice() throws Exception {
        try (var h = new Harness(directory); var control = ExecutionControl.create()) {
            h.seed(7);
            var calibrationRequest = h.request("预算验证");
            var calibrationInput = h.input(calibrationRequest, control);
            var calibration = h.service.list(calibrationRequest, calibrationInput, "happy", control)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(calibration.complete(), "无上限对照必须能完整查询");
            var unsigned = new MediaReplies.Page(calibration.requestId(), calibration.resourceProof(),
                calibration.category(), calibration.catalogRevision(), calibration.items(), true, "", "");
            long cost = h.db.mapper.writeValueAsBytes(unsigned).length + 512;
            var codec = new LLMMessageCodec(h.db.mapper);
            long budget = codec.signature(calibrationInput.messages()).bytes() + cost + 1024 + 96;

            var original = h.request("预算验证");
            var baselineRequest = new AgentRunRequest(original.requestId(), original.actor(), original.activity(),
                original.messages(), original.conversation(), original.memoryReference(), original.userText(), budget);
            var baselineInput = h.input(baselineRequest, control);
            var baseline = h.service.list(baselineRequest, baselineInput, "happy", control)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(baseline.complete(), "相同有限预算下，无数据库重试的对照必须成功");

            var retryOriginal = h.request("预算验证");
            var retryRequest = new AgentRunRequest(retryOriginal.requestId(), retryOriginal.actor(),
                retryOriginal.activity(), retryOriginal.messages(), retryOriginal.conversation(),
                retryOriginal.memoryReference(), retryOriginal.userText(), budget);
            var retryInput = h.input(retryRequest, control);
            var firstCommit = new java.util.concurrent.atomic.AtomicBoolean(true);
            int retriesBefore = h.db.retries.get();
            h.db.beforeCommit.set(() -> {
                if (firstCommit.getAndSet(false)) {
                    throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                        org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyDataObject.class,
                        "审查注入的一次提交冲突");
                }
            });
            var retried = h.service.list(retryRequest, retryInput, "happy", control)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
            h.db.beforeCommit.set(() -> {
            });
            assertEquals(1, h.db.retries.get() - retriesBefore, "必须确实发生一次事务回滚和重试");
            assertEquals(retried, h.service.list(retryRequest, retryInput, "happy", control)
                .toCompletableFuture().get(5, TimeUnit.SECONDS), "同类别重放复用原回执");
            assertTrue(retried.complete(), "一次回滚不得重复扣减进程内目录预算；实际原因=" + retried.reason());
        }
    }

    @TempDir Path directory;

    /** 2026/11/05：空图库与原生表情关闭时仍说明本轮资源状态，缺少表达能力则不注入资料。 */
    @Test
    void explainsEmptyGalleryWithoutInventingResources() {
        try (var h = new Harness(directory); var control = ExecutionControl.create()) {
            AgentComponentFixtures.set(h.scope, "expression", "nativeExpressionsEnabled", false);
            var request = h.request("空图库");
            var input = h.input(request, control);
            var fragment = input.fragments().stream().filter(value -> value.name().equals(StickerReplyService.FRAGMENT))
                    .findFirst().orElseThrow();
            assertTrue(fragment.text().contains("\"categories\":[]"));
            assertTrue(fragment.text().contains("收藏不等于发送"));
            assertFalse(h.service.canList(request, input));
            AgentComponentFixtures.set(h.scope, "expression", "expressionEnabled", false);
            assertTrue(h.service.fragments(request, control).toCompletableFuture().join().isEmpty());
        }
    }
    /** 2026/11/04：资源签名必须能经实际应用的 Jackson 模块往返，普通文字不能被表情资料阻断。 */
    @Test
    void resourcesRoundTripWithApplicationMapper() {
        try (var h = new Harness(directory, true); var control = ExecutionControl.create()) {
            h.scope.setEnabled(true);
            AgentComponentFixtures.set(h.scope, "expression", "nativeExpressionsEnabled", true);
            for (int size = 2; size <= 8; size++) {
                var revisions = new LinkedHashMap<String, Long>();
                for (int index = 0; index < size; index++) {
                    StringBuilder key = new StringBuilder();
                    for (int bit = 0; bit < 3; bit++) {
                        key.append((index & (1 << bit)) == 0 ? "Aa" : "BB");
                    }
                    revisions.put(key.toString(), (long) index);
                }
                h.pluginToken.set(new org.zexnocs.teanekocore.plugin.PluginRegistry.Token(revisions));
                var request = h.request("网页资源验证" + size);
                var input = h.input(request, control);
                assertDoesNotThrow(() -> h.service.verify(request, input, control).toCompletableFuture().join());
                if (size == 2) {
                    // 2026/11/04：规范化只消除键顺序差异，不允许修改目录内容后沿用原签名。
                    var changed = input.fragments().stream().map(fragment -> fragment.name().equals(NativeFaceService.FRAGMENT)
                            ? new AgentPromptFragment(fragment.name(), fragment.text().replace("微笑", "篡改描述"),
                            fragment.expiresAt(), fragment.transientToolResults()) : fragment).toList();
                    var tampered = new AgentPromptInput(request.messages(), changed, 0);
                    assertThrows(IllegalArgumentException.class, () -> h.context.getBean(NativeFaceService.class).verify(request, tampered));
                }
            }
        }
    }
    /**
     * 2026/11/05：通过真实组件选择链验证表达独立提供 FACE，加入工具或图库仍只保留一份目录。
     * @throws Exception 当有界离线发送等待失败时
     */
    @Test
    void expressionAssemblyProvidesNativeFacesWithoutGalleryAccess() throws Exception {
        try (var h = new Harness(directory); var control = ExecutionControl.create()) {
            // 2026/11/05：复用相同组件宿主验证单独表达及完整图库链。
            var host = h.host();
            for (String extras : List.of("", ",\"tool\":{}", ",\"tool\":{},\"sticker\":{}")) {
                var assembly = org.zexnocs.teanekoagent.component.AgentAssembly.parse(
                        "{\"version\":1,\"components\":{\"llm\":{},\"expression\":{}" + extras + "}}");
                var definition = new org.zexnocs.teanekoagent.runtime.AgentDefinition("原生表情",
                        new org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot(null), Set.of(AgentActivity.COMMAND), Set.of(), assembly);
                var request = h.request("独立表情" + extras.length());
                // 未装图库时只要发生图库事务就使测试失败，验证目录和 FACE 发送都不借用该写锁。
                h.db.beforeTask.set(name -> {
                    if (extras.isEmpty() && name.contains("表情包")) { throw new AssertionError("原生表情不应访问图库事务"); }
                });
                var fragments = host.prompts(org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource.empty())
                        .resolve(definition, request, null, control).toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertEquals(1, fragments.stream().filter(fragment -> fragment.name().equals(NativeFaceService.FRAGMENT)).count());
                var input = new AgentPromptInput(request.messages(), fragments, 0);
                String nonce = ExpressionRuntime.nonce(request.requestId());
                var interaction = h.interaction(request, input, null, "⟦TNK:" + nonce + "|FACE|id=14|wait=0⟧");
                var plan = Objects.requireNonNull(h.runtime.compile(request, input, interaction, control).toCompletableFuture().get(5, TimeUnit.SECONDS));
                h.send(request, plan).toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
            assertEquals(3, h.client.getSentMessages().size());
        }
    }

    /**
     * 2026/11/05：从用户样例纠正到真实结构化查询，经过生产协议映射、资料签名和独立图片交付。
     * @throws Exception 当有界离线等待或本地图片编码失败时
     */
    @Test
    void correctsDsmlAndSendsStickerThroughActualComponentTools() throws Exception {
        var settings = LLMInteractionSettings.defaults();
        try (var h = new Harness(directory, true);
             var control = ExecutionControl.withDeadline(settings.limits().newBudget(),
                     Instant.now().plusSeconds(15), Clock.systemUTC(), h.timers)) {
            var sticker = h.seed(7);
            var host = h.host();
            var assembly = org.zexnocs.teanekoagent.component.AgentAssembly.parse(
                    "{\"version\":1,\"components\":{\"llm\":{},\"expression\":{},\"tool\":{},\"sticker\":{}}}");
            var definition = new AgentDefinition("测试表情包", new LLMModelOptionsSnapshot(null),
                    Set.of(AgentActivity.COMMAND, AgentActivity.TOOL), Set.of(), assembly);
            var request = h.request("主动要表情包");
            var runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(null), ModelCapabilities.textOnly(), settings);
            var fragments = host.prompts(org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource.empty())
                    .resolve(definition, request, runtime, control).toCompletableFuture().get(5, TimeUnit.SECONDS);
            var snapshot = EntitySnapshot.of(EntityId.random(), ComponentPatch.builder()
                    .put(AgentPluginRuntime.SELECTION, host.select(definition))
                    .put(AgentComponents.ACTOR, request.actor()).build());
            var input = host.stamp(new AgentPromptInput(request.messages(), fragments, 0), snapshot);
            var source = host.tools((selected, call, state) -> List.of());
            var bindings = source.select(definition, request, runtime, input);
            source.verifySelection(request, input, bindings);
            assertEquals(List.of("sticker_list"), bindings.stream().map(binding -> binding.definition().getName()).toList());
            assertThrows(StickerReplyException.class, () -> source.verifySelection(request, input, List.of()));
            var models = mock(ILLMModelService.class);
            var attempts = new AtomicInteger();
            var mapper = h.context.getBean(tools.jackson.databind.ObjectMapper.class);
            when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(call -> {
                ILLMPrompt prompt = call.getArgument(0);
                var wire = DeepSeekChatCompletionMapper.toRequest(prompt, LLMModelOptions.copyOf(prompt.getOptions()),
                        "https://offline.invalid", "/chat/completions", "离线测试凭据");
                var sent = mapper.valueToTree(wire);
                assertEquals("sticker_list", sent.path("tools").get(0).path("function").path("name").asString());
                var schema = sent.path("tools").get(0).path("function").path("parameters");
                assertTrue(schema.path("properties").has("categoryId"));
                assertEquals("categoryId", schema.path("required").get(0).asString());
                int attempt = attempts.incrementAndGet();
                String finish = attempt == 2 ? "tool_calls" : "stop";
                Map<String, Object> message;
                if (attempt == 1) {
                    assertEquals("auto", sent.path("tool_choice").asString());
                    assertTrue(mapper.writeValueAsString(sent.path("messages")).contains("happy"));
                    message = Map.of("role", "assistant", "content",
                            "<｜｜DSML｜｜ calls>\n<｜｜DSML｜｜ invoke name=\"sticker_list\">\n\n"
                                    + "</｜｜DSML｜｜ invoke>\n</｜｜DSML｜｜ calls>");
                } else if (attempt == 2) {
                    assertEquals("auto", sent.path("tool_choice").asString());
                    message = Map.of("role", "assistant", "content", "", "reasoning_content", "离线透明状态",
                            "tool_calls", List.of(Map.of("id", "real-list", "type", "function", "function",
                                    Map.of("name", "sticker_list", "arguments", "{\"categoryId\":\"happy\"}"))));
                } else {
                    assertEquals(3, attempt);
                    assertEquals("none", sent.path("tool_choice").asString());
                    var tool = prompt.getMessages().stream().filter(value -> "tool".equals(value.getRole().getValue()))
                            .findFirst().orElseThrow();
                    var page = mapper.readValue(assertInstanceOf(TextLLMContentPart.class,
                            tool.getContents().getFirst().getContentPart()).getText(), MediaReplies.Page.class);
                    assertTrue(page.complete());
                    assertEquals(sticker.getId(), page.items().getFirst().id());
                    String head = "⟦TNK:" + ExpressionRuntime.nonce(request.requestId()) + "|";
                    message = Map.of("role", "assistant", "content", head + "BEGIN|protocol=teaneko:expression|v=1⟧\n"
                            + head + "STICKER|id=" + page.items().getFirst().id() + "|mode=reply|wait=0⟧\n" + head + "END⟧");
                }
                var response = mapper.convertValue(Map.of("choices", List.of(
                        Map.of("index", 0, "finish_reason", finish, "message", message))), DeepSeekChatCompletionResponseData.class);
                return new TaskFuture<>(h.logger, "离线表情包模型", CompletableFuture.completedFuture(
                        DeepSeekChatCompletionMapper.toResult(response)));
            });
            var interactions = new LLMInteractionService(models, () -> settings, h.workers, h.timers);
            var result = interactions.interact(new LLMPrompt(input.messages()), new LLMToolSet(mapper, bindings),
                    new ToolInvocationContext(request.requestId(), snapshot, control)).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(LLMInteractionResult.Status.SUCCESS, result.status(), () -> String.valueOf(result.failure()));
            assertEquals(3, result.modelAttempts());
            assertEquals(1, result.tools().size());
            assertEquals(ToolInvocationResult.Status.SUCCESS, result.tools().getFirst().status());
            var plan = Objects.requireNonNull(h.runtime.compile(request, input, result, control)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS));
            h.send(request, plan).toCompletableFuture().get(5, TimeUnit.SECONDS);
            var messages = h.client.getSentMessages();
            assertEquals(1, messages.size());
            assertEquals(2, messages.getFirst().getMessages().size());
            assertEquals(1, messages.getFirst().getMessages().stream()
                    .filter(part -> part.getContentPart() instanceof IReplyTeaNekoContentPart).count());
            assertTrue(messages.getFirst().getMessages().stream().anyMatch(part ->
                    part.getContentPart() instanceof IImageTeaNekoContentPart image && image.getUrl().startsWith("base64://")));
            assertEquals(1, h.state().getEntries().get(sticker.getHash()).getRobotShows());
        }
    }

    /**
     * 2026/11/05：目录变更、资料被改写或工具被过滤都明确报错，不谎报为空图库。
     * @throws Exception 当本地图片编码失败时
     */
    @Test
    void distinguishesUnavailableDirectoryFromEmptyGallery() throws Exception {
        try (var h = new Harness(directory, true); var control = ExecutionControl.create()) {
            h.seed(8);
            var request = h.request("目录检查");
            var input = h.input(request, control);
            assertTrue(h.service.canList(request, input));
            var changed = input.fragments().stream().map(fragment -> fragment.name().equals(StickerReplyService.FRAGMENT)
                    ? new AgentPromptFragment(fragment.name(), fragment.text().replace("\"happy\"", "\"angry\""),
                    fragment.expiresAt(), fragment.transientToolResults()) : fragment).toList();
            var tampered = new AgentPromptInput(request.messages(), changed, 0);
            var failure = assertThrows(StickerReplyException.class, () -> h.service.canList(request, tampered));
            assertTrue(failure.failureReason().contains("并非图库为空"));
            var recheck = assertThrows(CompletionException.class,
                    () -> h.service.verify(request, tampered, control).toCompletableFuture().join());
            assertInstanceOf(StickerReplyException.class, recheck.getCause());
            AgentComponentFixtures.set(h.scope, "sticker", "stickerReplyEnabled", false);
            assertThrows(StickerReplyException.class, () -> h.service.canList(request, input));
            assertFalse(h.service.canList(request, h.input(request, control)));
        }
    }

    /**
     * 测试客户端显式声明媒体能力，实际编码直接借用已注册 OneBot 适配器。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.9.0-l
     */
    @TeaNekoExpressionClient(FakeTeaNekoClient.ID) public static final class ClientAdapter implements IExpressionClientAdapter {
        final OnebotExpressionClient delegate;volatile CompletableFuture<Void> pending;
        /** @param delegate 原容器编码器 */ public ClientAdapter(OnebotExpressionClient delegate){this.delegate=delegate;}
        /**
         * @param data 原目标
         * @return 明确支持图片
         */ @Override public boolean images(ITeaNekoMessageData data){return true;}
        /**
         * @param data 原目标
         * @param settings 设置
         * @return 明确原生目录
         */ @Override public Map<String,String> faces(ITeaNekoMessageData data, MediaReplySettings settings){return Map.of("14","微笑");}
        /**
         * @param data 原目标
         * @param bytes 原图编码
         * @param mode 方式
         * @return 可控真实确认
         */
        @Override public CompletionStage<Void> image(ITeaNekoMessageData data,String bytes,Mode mode){return delegate.image(data,bytes,mode).thenCompose(ignored->pending==null?CompletableFuture.completedStage(null):pending);}
        /**
         * @param data 原目标
         * @param id 原生 ID
         * @param mode 方式
         * @return 实际确认
         */
        @Override public CompletionStage<Void> face(ITeaNekoMessageData data,String id,Mode mode){return delegate.face(data,id,mode);}
    }
/**
 * 局部默认装配，所有文件和目录仲裁真实执行，只有原生来源由受控租约替身提供。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：增加生产序列化与插件令牌夹具，验证资源签名往返。
 * <br>2026/11/04：夹具明确保留历史装配及原业务行为，适配可选组件路径。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：夹具提供现有客户端目录依赖，不连接真实客户端。
 * <br>2026/11/05：复用组件宿主完成模型到图片发送的离线验证。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
    private static final class Harness implements AutoCloseable{
        // 2026/11/04：在单个夹具内切换碰撞键集合，覆盖生产令牌的嵌套 Map。
        final java.util.concurrent.atomic.AtomicReference<org.zexnocs.teanekocore.plugin.PluginRegistry.Token> pluginToken =
                new java.util.concurrent.atomic.AtomicReference<>(new org.zexnocs.teanekocore.plugin.PluginRegistry.Token(Map.of()));
        final NamespaceDatabaseFixture db=new NamespaceDatabaseFixture();final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext();
        final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();final ScheduledExecutorService timers=Executors.newSingleThreadScheduledExecutor();
        final ILogger logger=mock(ILogger.class);final FakeClient client=new FakeClient(logger);
        final AgentStickerFileConfig gallery=new AgentStickerFileConfig();final AgentMediaReplyFileConfig media=new AgentMediaReplyFileConfig();final AgentScopeConfigData scope=new AgentScopeConfigData(null);
        final Map<UUID,ITeaNekoMessageData> sources=new ConcurrentHashMap<>();
        final ExpressionRuntime runtime;final StickerReplyService service;final StickerReplyTools tools;final IStickerCatalog catalog;final LocalStickerAssets assets;
        final ReplyCoordinator queue;final ExpressionDelivery delivery;final EasyDataDeliveryStore ledger;
        /** @param path 测试目录 */
        Harness(Path path){
            this(path, false);
        }
        /**
         * @param path 测试目录
         * @param applicationMapper 是否使用生产序列化模块
         */
        Harness(Path path, boolean applicationMapper){
            gallery.setStorageRoot(path.toString());var expression=new AgentExpressionFileConfig();expression.setDefaultDelay(0);
            var files=mock(IFileConfigService.class);when(files.requireCurrent(AgentStickerFileConfig.class)).thenReturn(gallery);when(files.requireCurrent(AgentMediaReplyFileConfig.class)).thenReturn(media);when(files.requireCurrent(AgentExpressionFileConfig.class)).thenReturn(expression);
            var configs=mock(IConfigDataService.class);doReturn(Optional.of(scope)).when(configs).getConfigData(any(),eq(AgentScopeConfigData.class),anyString());
            var bridge=mock(NativeMessageSources.class);
            when(bridge.source(any())).thenAnswer(call->Optional.ofNullable(sources.get(((AgentRunRequest)call.getArgument(0)).requestId())));
            ledger=new EasyDataDeliveryStore(db.data,db.store,db.mapper);
            // 2026/11/04：复现生产模块与无模块夹具的序列化差异。
            var actualMapper = applicationMapper ? new org.zexnocs.teanekocore._app_config.JacksonConfig().objectMapper() : db.mapper;
            var beans=context.getBeanFactory();beans.registerSingleton("data",db.data);beans.registerSingleton("mapper",actualMapper);beans.registerSingleton("files",files);beans.registerSingleton("configs",configs);beans.registerSingleton("bridge",bridge);
            if (applicationMapper) {
                // 2026/11/04：生产具有包含依赖的插件令牌；哈希冲突键复现序列化往返中的 Map 顺序变化。
                var plugins = mock(org.zexnocs.teanekocore.plugin.PluginRegistry.class);
                var token = new org.zexnocs.teanekocore.plugin.PluginRegistry.Token(Map.of("Aa", 1L, "BB", 2L));
                pluginToken.set(token);
                when(plugins.capture(anyString())).thenAnswer(ignored -> pluginToken.get());
                when(plugins.isEnabled(anyString())).thenReturn(true);
                // 2026/11/08：签名替身仍发布真实安装目录，配置读取不会把全部业务视为未安装。
                when(plugins.list()).thenReturn(new org.zexnocs.teanekocore.plugin.PluginRegistry(List.of(
                        org.zexnocs.teanekocore.plugin.PluginDeclarationScanner.declaration(
                                org.zexnocs.teanekoplugin.agent.expression.ExpressionPlugin.class)::descriptor)).list());
                beans.registerSingleton("plugins", plugins);
            } else {
                // 2026/11/07：普通序列化夹具也保留真实插件归属门禁。
                beans.registerSingleton("plugins", new org.zexnocs.teanekocore.plugin.PluginRegistry(List.of(
                        () -> new org.zexnocs.teanekocore.plugin.PluginDescriptor("teaneko.agent", "1", 1, Set.of()),
                        () -> new org.zexnocs.teanekocore.plugin.PluginDescriptor("agent.expression", "4.9.0-l", 1, Set.of()))));
            }
            beans.registerSingleton("coreTimers", mock(org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService.class));
            // 2026/11/05：素材策略使用已发布客户端目录，独立测试提供该依赖而不启动平台连接。
            beans.registerSingleton("clients", mock(TeaNekoClientScanner.class));
            beans.registerSingleton("manager",new AgentScopeConfigManager());beans.registerSingleton("virtualExecutor",workers);beans.registerSingleton("taskExecutor",timers);beans.registerSingleton("logger",logger);beans.registerSingleton("scanner",new BeanScanner(context,logger));
            beans.registerSingleton("ledger",new AgentDeliveryService(ledger,workers,Clock.systemUTC(),()->Duration.ofDays(7)));
            // 2026/11/08：使用宿主拥有的通用发送队列，表达插件只提供发送策略。
            context.register(org.zexnocs.teanekoagent.runtime.delivery.AgentDeliveryConfiguration.class,
                    org.zexnocs.teanekoplugin.agent.expression.expression.configuration.AgentExpressionConfig.class,org.zexnocs.teanekoplugin.agent.expression.sticker.configuration.AgentStickerConfig.class,
                    EasyDataStickerCatalog.class, LocalStickerAssets.class, WeightedStickerAdmission.class,OnebotExpressionClient.class,ClientAdapter.class,StickerReplyService.class,StickerReplyTools.class,MediaReplyFeature.class,
                    ExpressionReplyProtocol.class,TextReplyDirective.class,PlainReplyProtocol.class,JsonReplyProtocol.class,TextReplyHandler.class,ImageReplyHandler.class,StickerReplyDirective.class,FaceReplyDirective.class,StickerReplyHandler.class,FaceReplyHandler.class,ExpressionDelivery.class,NativeFaceService.class);
            // 2026/11/07：独立测试容器显式启动图库业务插件的发送生命周期。
            AgentComponentFixtures.register(context);
            context.refresh();
            verifyNoInteractions(files, configs, bridge);
            catalog = context.getBean(IStickerCatalog.class);
            assets = context.getBean(LocalStickerAssets.class);
            service = context.getBean(StickerReplyService.class);
            service.start().toCompletableFuture().join();
            tools = context.getBean(StickerReplyTools.class);
            runtime = context.getBean(ExpressionRuntime.class);
            queue = context.getBean(ReplyCoordinator.class);
            delivery = context.getBean(ExpressionDelivery.class);
            db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED,0,0,Instant.now(),null,null),10).toCompletableFuture().join();
        }
        /**
         * 2026/11/05：装配测试所需真实组件及媒体工具，省略无关业务工具。
         * @return 持有独立插件目录的宿主
         */
        AgentPluginRuntime host() {
            var beans = context.getBeanFactory();
            var plugins = new org.zexnocs.teanekocore.plugin.PluginRegistry(List.of(
                    () -> new org.zexnocs.teanekocore.plugin.PluginDescriptor("teaneko.agent", "1", 1, Set.of()),
                    () -> new org.zexnocs.teanekocore.plugin.PluginDescriptor("agent.expression", "4.9.0-l", 1, Set.of())));
            var features = List.<org.zexnocs.teanekoagent.plugin.IAgentFeature>of(
                    new org.zexnocs.teanekoagent.component.AgentComponentFeature("teaneko.agent", "llm",
                            new org.zexnocs.teanekoagent.component.llm.LLMComponent(mock(org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser.class))),
                    new org.zexnocs.teanekoagent.component.AgentComponentFeature("agent.expression", "expression",
                            new ExpressionComponent(new ExpressionCompileSystem(beans.getBeanProvider(ExpressionRuntime.class)))),
                    new org.zexnocs.teanekoagent.component.AgentComponentFeature("teaneko.agent", "tool",
                            new org.zexnocs.teanekoagent.component.tool.ToolComponent()),
                    new org.zexnocs.teanekoagent.component.AgentComponentFeature("agent.expression", "sticker", new StickerComponent()),
                    new ExpressionFeature(runtime, context.getBean(NativeFaceService.class)),
                    context.getBean(MediaReplyFeature.class),
                    new org.zexnocs.teanekoagent.plugin.SourceAgentFeature("teaneko.agent", "builtin-tools", true,
                            (definition, request, state) -> List.of(), org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource.empty()));
            return new AgentPluginRuntime(plugins, features,
                    beans.getBeanProvider(org.zexnocs.teanekocore.event.interfaces.IEventService.class));
        }
        /**
         * @param group 真实区域
         * @return 独立请求及宿主来源绑定
         */
        AgentRunRequest request(String group){
            var data=client.handleGroupMessage(group,"用户","昵称",CommandPermission.MEMBER,"你好").getData();
            var actor=NativeMessageSources.actor(data);
            var request=new AgentRunRequest(UUID.randomUUID(),actor,AgentActivity.COMMAND,List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("你好").build()).build()),null,null);
            sources.put(request.requestId(),data);
            return request;
        }
        /**
         * @param request 当前请求
         * @param control 当前控制
         * @return 原表达与真实资源资料
         */
        AgentPromptInput input(AgentRunRequest request,ExecutionControl control){var fragments=new ArrayList<AgentPromptFragment>();fragments.add(new AgentPromptFragment(ExpressionRuntime.FRAGMENT,runtime.contract(request)));fragments.addAll(context.getBean(NativeFaceService.class).fragments(request));fragments.addAll(service.fragments(request,control).toCompletableFuture().join());return new AgentPromptInput(request.messages(),fragments,0);}
        /**
         * @param value 区分原图的像素
         * @return 发布并激活的真实文件条目
         * @throws Exception 当本地图像编码失败时
         */
        Stickers.Entry seed(int value)throws Exception{
            var image=new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,value);String url;try(var out=new ByteArrayOutputStream()){ImageIO.write(image,"png",out);url="data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray());}
            var job=new Stickers.Job();job.setLease(UUID.randomUUID());job.setSource(new Stickers.Source(new AgentScope("test","来源"),UUID.randomUUID(),"用户","机器人","消息",0,Instant.now(),false,UUID.randomUUID(),url));
            var settings=gallery.snapshot();var identity=assets.identity(settings);try(var control=ExecutionControl.create()){var prepared=assets.prepare(job,settings,control);job.setBlob(prepared.blob());assets.publish(job,settings,control);}
            var e=new Stickers.Entry();e.setId("stk_"+job.getBlob().hash().substring(0,32));e.setHash(job.getBlob().hash());e.setBlob(job.getBlob());e.setState(Stickers.EntryStatus.ACTIVE);e.setCreated(Instant.now());e.setActivatedAt(Instant.now());e.setCachedUntil(Instant.now().plusSeconds(3600));e.setAnalysis(new Stickers.Analysis(Stickers.Kind.STICKER,.99,true,"猫的表情","开心地打招呼","happy",List.of(),.9,"适合聊天"));
            // 2026/11/07：便携描述要求真实分析版本，夹具与正常采集保持一致。
            e.setAnalysisVersion(settings.analysisVersion());
            catalog.change(settings,s->{s.setStorageIdentity(identity);s.getEntries().put(e.getHash(),e);s.setRevision(s.getRevision()+1);return true;}).toCompletableFuture().join();return e;
        }
        /** @return 实际目录副本 */
        Stickers.State state(){return catalog.change(gallery.snapshot(),s->s).toCompletableFuture().join();}
        /**
         * @param request 原请求
         * @param input 原布局
         * @param control 原控制
         * @return 经真实注解参数绑定的完整回执
         */
        ToolInvocationResult list(AgentRunRequest request,AgentPromptInput input,ExecutionControl control){
            var definition=new AgentDefinition("测试",new LLMModelOptionsSnapshot(null),Set.of(AgentActivity.COMMAND,AgentActivity.TOOL));
            var runtime=new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(null),ModelCapabilities.textOnly(),LLMInteractionSettings.defaults());var bindings=context.getBean(MediaReplyFeature.class).tools().select(definition,request,runtime,input);
            var tool=new LLMToolCall("call_1","function","sticker_list","{\"categoryId\":\"happy\"}");return new LLMToolSet(db.mapper,bindings).execute(tool,new ToolInvocationContext(request.requestId(),EntitySnapshot.of(EntityId.random(),ComponentPatch.builder().put(AgentComponents.ACTOR,request.actor()).build()),control)).toCompletableFuture().join();
        }
        /**
         * @param request 原请求
         * @param input 原布局
         * @param result 实际工具回执
         * @param body 最终指令
         * @return 原交互及工具完整配对
         */
        LLMInteractionResult interaction(AgentRunRequest request,AgentPromptInput input,ToolInvocationResult result,String body){var messages= new ArrayList<>(input.messages());if(result!=null){messages.add(LLMAssistantMessage.builder().contents(List.of()).toolCalls(List.of(result.call())).build());messages.add(result.toMessage());}
            var answer=LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText("⟦TNK:"+ExpressionRuntime.nonce(request.requestId())+"|BEGIN|protocol=teaneko:expression|v=1⟧\n"+body+"\n⟦TNK:"+ExpressionRuntime.nonce(request.requestId())+"|END⟧").build()).build();messages.add(answer);return new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS,messages,answer,List.of(),result==null?List.of():List.of(result),Map.of());}
        /**
         * @param request 原请求
         * @param plan 固定计划
         * @return 使用真实持久账本的交付阶段
         */
        CompletionStage<Void> send(AgentRunRequest request,ReplyPlans.Approved plan){var result=mock(AgentRunResult.class);when(result.replyPlan()).thenReturn(plan);when(result.memoryReference()).thenReturn(new MemoryNamespaceReference(request.actor().scope(),MemoryNamespaceId.SHARED,0,0));
            return queue.execute("测试",request.actor(),request.requestId(),"fake-bot",control->delivery.send(sources.get(request.requestId()),request,result,control,()->{control.checkActive();return CompletableFuture.completedStage(null);}));}
        /** 释放原资源和夹具，不连接真实服务。 */
        @Override public void close(){context.close();workers.close();timers.shutdownNow();db.close();}
    }
    /**
     * 注解目录、冻结协议、独立图片/原生表情和真实逐段账本完整接通，重放不重复展示。
     * @throws Exception 当本地编码失败时
     */
    @Test void sendsQueriedStickerAndNativeFaceWithReceipts()throws Exception{
        try(var h=new Harness(directory);var control=ExecutionControl.create()){
            var sticker=h.seed(1);var request=h.request("群甲");var input=h.input(request,control);var listing=h.list(request,input,control);assertEquals(ToolInvocationResult.Status.SUCCESS,listing.status());assertFalse(listing.truncated());
            var page=h.db.mapper.readValue(listing.output(),MediaReplies.Page.class);assertTrue(page.complete());assertEquals(sticker.getId(),page.items().getFirst().id());assertFalse(listing.output().contains("来源"));
            String n=ExpressionRuntime.nonce(request.requestId());var interaction=h.interaction(request,input,listing,"⟦TNK:"+n+"|TEXT|wait=0⟧\n好呀\n⟦TNK:"+n+"|STICKER|id="+sticker.getId()+"|mode=reply|wait=0⟧\n⟦TNK:"+n+"|FACE|id=14|wait=0⟧");
            var plan=Objects.requireNonNull(h.runtime.compile(request,input,interaction,control).toCompletableFuture().join());var transcript=AgentPromptTranscript.createExpression(input,interaction,new LLMMessageCodec(h.db.mapper),plan);String projected=h.db.mapper.writeValueAsString(transcript.messages());assertFalse(projected.contains("TNK:"));assertFalse(projected.contains(page.proof()));
            h.send(request,plan).toCompletableFuture().join();assertEquals(3,h.client.getSentMessages().size());var image=h.client.getSentMessages().get(1).getMessages();assertEquals(2,image.size());assertEquals(1,image.stream().filter(c->c.getContentPart() instanceof IReplyTeaNekoContentPart).count());assertTrue(image.stream().anyMatch(c->c.getContentPart() instanceof IImageTeaNekoContentPart p&&p.getUrl().startsWith("base64://")));
            assertEquals(1,h.client.getSentMessages().get(2).getMessages().size());assertEquals("face",h.client.getSentMessages().get(2).getMessages().getFirst().getType());assertEquals(1,h.state().getEntries().get(sticker.getHash()).getRobotShows());
            h.send(request,plan).toCompletableFuture().join();assertEquals(3,h.client.getSentMessages().size());assertEquals(1,h.state().getEntries().get(sticker.getHash()).getRobotShows());
            var next=h.request("群甲");assertFalse(h.service.canList(next,h.input(next,control)));var other=h.request("群乙");assertTrue(h.service.canList(other,h.input(other,control)));
        }
    }
    /**
     * 历史、截断、改写的目录及未查询 ID 无权发送；版本变化发生在整计划发送前拒绝。
     * @throws Exception 当本地编码失败时
     */
    @Test void rejectsStaleOrUnqueriedResourcesBeforeSending()throws Exception{
        try(var h=new Harness(directory);var control=ExecutionControl.create()){
            var sticker=h.seed(2);var request=h.request("群甲");var input=h.input(request,control);String line="⟦TNK:"+ExpressionRuntime.nonce(request.requestId())+"|STICKER|id="+sticker.getId()+"|wait=0⟧";
            assertThrows(CompletionException.class,()->h.runtime.compile(request,input,h.interaction(request,input,null,line),control).toCompletableFuture().join());
            var real=h.list(request,input,control);var truncated=new ToolInvocationResult(real.call(),real.status(),real.output(),true);assertThrows(CompletionException.class,()->h.runtime.compile(request,input,h.interaction(request,input,truncated,line),control).toCompletableFuture().join());
            var other=h.request("群乙");var otherInput=h.input(other,control);String otherLine="⟦TNK:"+ExpressionRuntime.nonce(other.requestId())+"|STICKER|id="+sticker.getId()+"|wait=0⟧";
            assertThrows(CompletionException.class,()->h.runtime.compile(other,otherInput,h.interaction(other,otherInput,real,otherLine),control).toCompletableFuture().join());
            var forged=new ToolInvocationResult(real.call(),real.status(),real.output().replace("开心地打招呼","另一种用途"),false);assertThrows(CompletionException.class,()->h.runtime.compile(request,input,h.interaction(request,input,forged,line),control).toCompletableFuture().join());
            var plan=Objects.requireNonNull(h.runtime.compile(request,input,h.interaction(request,input,real,line),control).toCompletableFuture().join());h.catalog.change(h.gallery.snapshot(),s->{s.getEntries().get(sticker.getHash()).setRevision(2);return true;}).toCompletableFuture().join();
            assertThrows(CompletionException.class,()->h.send(request,plan).toCompletableFuture().join());assertTrue(h.client.getSentMessages().isEmpty());assertTrue(h.state().getEntries().get(sticker.getHash()).getReaders().isEmpty());
            var fresh=h.request("群甲");var freshInput=h.input(fresh,control);var freshTool=h.list(fresh,freshInput,control);String freshLine="⟦TNK:"+ExpressionRuntime.nonce(fresh.requestId())+"|STICKER|id="+sticker.getId()+"|wait=0⟧";
            var freshPlan=Objects.requireNonNull(h.runtime.compile(fresh,freshInput,h.interaction(fresh,freshInput,freshTool,freshLine),control).toCompletableFuture().join());h.assets.delete(Objects.requireNonNull(sticker.getBlob()),h.gallery.snapshot());
            assertThrows(CompletionException.class,()->h.send(fresh,freshPlan).toCompletableFuture().join());assertTrue(h.client.getSentMessages().isEmpty());assertTrue(h.state().getEntries().get(sticker.getHash()).getReaders().isEmpty());
        }
    }
    /**
     * 取消已开始的发送不提前释放文件；原阶段确认后才记展示和释放，随后才允许物理回收。
     * @throws Exception 当本地编码失败时
     */
    @Test void keepsLeaseUntilPhysicalCompletionAndCountsOnlyConfirmation()throws Exception{
        try(var h=new Harness(directory);var control=ExecutionControl.create()){
            var sticker=h.seed(3);var request=h.request("群甲");var input=h.input(request,control);var tool=h.list(request,input,control);var draft=new ReplyPlans.Draft(new MediaReplies.Sticker(sticker.getId(),null),Mode.PLAIN,0);
            var permit=h.service.sticker(new ReplyCompileContext(request,input,control,List.of(tool),List.of(draft)),sticker.getId()).toCompletableFuture().join();var pending=new CompletableFuture<Void>();h.context.getBean(ClientAdapter.class).pending=pending;
            var sending=h.service.sendSticker(new ReplySendContext(h.sources.get(request.requestId()),request,0,control,()->{control.checkActive();return CompletableFuture.completedStage(null);}),permit,Mode.PLAIN).toCompletableFuture();
            await().atMost(Duration.ofSeconds(5)).until(()->!h.client.getSentMessages().isEmpty());control.cancel();assertFalse(sending.isDone());assertEquals(0,h.state().getEntries().get(sticker.getHash()).getRobotShows());assertFalse(h.state().getEntries().get(sticker.getHash()).getReaders().isEmpty());
            var controls=new ArrayList<ExecutionControl>();var physical=new ArrayList<CompletableFuture<Void>>();
            try{
                for(int i=0;i<8;i++){
                    var own=ExecutionControl.create();controls.add(own);var next=h.request("并发群"+i);var actual=h.input(next,own);var listed=h.list(next,actual,own);
                    var selected=h.service.sticker(new ReplyCompileContext(next,actual,own,List.of(listed),List.of(draft)),sticker.getId()).toCompletableFuture().join();
                    physical.add(h.service.sendSticker(new ReplySendContext(h.sources.get(next.requestId()),next,0,own,()->{own.checkActive();return CompletableFuture.completedStage(null);}),selected,Mode.PLAIN).toCompletableFuture());
                }
                await().atMost(Duration.ofSeconds(5)).until(()->h.client.getSentMessages().size()==8);assertFalse(physical.getLast().isDone());controls.getLast().cancel();assertThrows(CompletionException.class,()->physical.getLast().join());
                assertEquals(8,h.state().getEntries().get(sticker.getHash()).getReaders().size());
                h.catalog.change(h.gallery.snapshot(),s->{s.getEntries().get(sticker.getHash()).setState(Stickers.EntryStatus.RETIRED);return true;}).toCompletableFuture().join();var storage=h.context.getBean(StickerExtensionRegistry.class).storage(h.gallery.snapshot());
                var ports=new StickerExtensionRegistry.Selected(mock(IStickerAnalyzer.class),storage.assets(),h.context.getBean(WeightedStickerAdmission.class),storage.catalog(),storage.tokens());
                assertTrue(h.context.getBean(StickerLibrary.class).garbage(h.gallery.snapshot(),ports).toCompletableFuture().join().isEmpty());pending.complete(null);sending.join();for(int i=0;i<7;i++){physical.get(i).join();}
                assertTrue(h.state().getEntries().get(sticker.getHash()).getReaders().isEmpty());assertEquals(8,h.state().getEntries().get(sticker.getHash()).getRobotShows());
            }finally{pending.complete(null);controls.forEach(ExecutionControl::close);}
        }
    }
    /**
     * 类别不能完整装入预算时不给部分 ID；资源必须独立成条，原生目录变化立即拒绝旧许可。
     * @throws Exception 当本地编码失败时
     */
    @Test void respectsCompleteCategoryBudgetAndMediaSwitches()throws Exception{
        try(var h=new Harness(directory);var control=ExecutionControl.create()){
            h.media.setMaxCategoryCharacters(1024);for(int i=0;i<10;i++){h.seed(10+i);}
            var sad=h.seed(99);var thanks=h.seed(100);h.catalog.change(h.gallery.snapshot(),s->{
                for(var pair:Map.of(sad.getHash(),"sad",thanks.getHash(),"thanks").entrySet()){var e=s.getEntries().get(pair.getKey());var old=Objects.requireNonNull(e.getAnalysis());e.setAnalysis(new Stickers.Analysis(old.kind(),old.confidence(),true,old.visualDescription(),old.usageDescription(),pair.getValue(),old.secondaryTags(),old.quality(),old.reasons()));}return true;
            }).toCompletableFuture().join();
            var request=h.request("群甲");var input=h.input(request,control);var output=h.list(request,input,control);var page=h.db.mapper.readValue(output.output(),MediaReplies.Page.class);assertFalse(page.complete());assertTrue(page.items().isEmpty());assertEquals("CATEGORY_BUDGET",page.reason());
            var validPage=h.service.list(request,input,"sad",control).toCompletableFuture().join();assertTrue(validPage.complete());var validTool=new ToolInvocationResult(new LLMToolCall("call_2","function","sticker_list","{\"categoryId\":\"sad\"}"),ToolInvocationResult.Status.SUCCESS,h.db.mapper.writeValueAsString(validPage),false);
            assertEquals(sad.getId(),h.service.sticker(new ReplyCompileContext(request,input,control,List.of(output,validTool),List.of()),sad.getId()).toCompletableFuture().join().item().id());
            assertThrows(CompletionException.class,()->h.service.list(request,input,"thanks",control).toCompletableFuture().join());
            assertThrows(IllegalArgumentException.class,()->new StickerReplyDirective().parse(new ReplyPlans.Directive("STICKER",Map.of("id","valid_id","mode","at"),"")));
            var faces = h.context.getBean(NativeFaceService.class);
            var permit=faces.face(new ReplyCompileContext(request,input,control),"14");
            AgentComponentFixtures.set(h.scope, "expression", "nativeExpressionsEnabled", false);
            assertThrows(CompletionException.class,()->faces.sendFace(new ReplySendContext(h.sources.get(request.requestId()),request,0,control,()->CompletableFuture.completedStage(null)),permit,Mode.PLAIN).toCompletableFuture().join());assertTrue(h.client.getSentMessages().isEmpty());
        }
    }
}
