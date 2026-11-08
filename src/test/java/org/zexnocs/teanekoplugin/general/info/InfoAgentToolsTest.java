package org.zexnocs.teanekoplugin.general.info;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.tool.*;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolMethods;
import org.zexnocs.teanekoagent.integration.message.NativeMessageSources;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.message.DefaultTeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.message.TeaNekoMessageData;
import org.zexnocs.teanekoapp.message.TeaNekoUserData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserCoinService;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDTO;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekocore.utils.RandomUtil;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekoplugin.general.info.messageboard.MessageBoardInfoData;
import org.zexnocs.teanekoplugin.general.info.messageboard.MessageBoardInfoService;
import org.zexnocs.teanekoplugin.general.info.personal.PersonalInfoService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 覆盖实际工具集中的 UUID 查询、留言分页及权限撤销，不访问平台或数据库。
 * <br>2026/11/02：注解绑定提前拒绝类型溢出，权限撤销停止交互。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 * <br>2026/11/05：经独立工具入口验证原资料及留言权限。
 * <br>2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
 * <br>2026/11/05：补充组件审查修复的离线回归及初始化夹具。
 * <br>2026/11/07：资料服务显式装配插件访问端口，保持原只读工具验证。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.9.0-l
 */
class InfoAgentToolsTest {
    private final UUID target = UUID.randomUUID();
    private final ObjectMapper mapper = new ObjectMapper();
    private final PersonalInfoService personal = mock(PersonalInfoService.class);
    private final MessageBoardInfoService messages = mock(MessageBoardInfoService.class);
    private final TeaNekoUserData actor = TeaNekoUserData.builder().uuid(UUID.randomUUID()).userIdInPlatform("user")
            .nickname("用户").groupId("group").role(CommandPermission.MEMBER).build();
    private final AgentDefinition definition = new AgentDefinition("资料工具", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values()));
    private final AgentRunRequest request;
    private final InfoAgentTools tools;

    /**
     * 装配真实公开资料服务与宿主来源，不建立新的业务或存储实现。
     * 2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
     */
    InfoAgentToolsTest() {
        var sources = mock(NativeMessageSources.class);
        var client = mock(ITeaNekoClient.class); when(client.getClientId()).thenReturn("fake");
        var data = TeaNekoMessageData.builder().client(client).userData(actor).scopeId("scope").messageId("message")
                .messageType(TeaNekoMessageType.GROUP).time(Instant.parse("2026-11-02T00:00:00Z"))
                .messages(new DefaultTeaNekoContentListBuilder().addText("查询资料").build()).build();
        request = new AgentRunRequest(UUID.randomUUID(), NativeMessageSources.actor(data), AgentActivity.COMMAND, List.of());
        when(sources.source(request)).thenReturn(Optional.of(data));
        var coins = mock(ITeaUserCoinService.class); IItemDataDTO<?> coin = mock(IItemDataDTO.class);
        when(coin.getCount()).thenReturn(42); doReturn(coin).when(coins).getCoin(target);
        when(personal.getPersonInfo(target)).thenReturn("喜欢花茶"); when(messages.getMessageList(target)).thenReturn(List.of(
                new MessageBoardInfoData("较早留言", UUID.randomUUID(), Instant.parse("2026-11-01T00:00:00Z")),
                new MessageBoardInfoData("最新留言", UUID.randomUUID(), Instant.parse("2026-11-02T00:00:00Z"))));
        // 2026/11/07：此用例只读资料，发送路径所需的插件门禁由独立回归验证。
        var info = new InfoService(mock(RandomUtil.class), personal, messages, coins, mock(PluginAccess.class));
        tools = new InfoAgentTools(info, messages, sources, mapper, Runnable::run);
    }

    /**
     * 2026/11/05：组合单工具提供者，不改变原用例的真实来源和业务权限。
     * 2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
     * @return 当前请求可使用的工具绑定
     */
    private List<LLMToolBinding> select() {
        var providers = List.of(tools, new InfoUserMessagesTool(tools));
        providers.forEach(TeaNekoAgentToolMethods::prepare);
        return providers.stream()
                .flatMap(provider -> TeaNekoAgentToolMethods.select(
                        provider, definition, request, null, null, mapper, Runnable::run).stream())
                .toList();
    }

    /**
     * 通过实际 schema 校验及可信上下文执行工具，控制由此方法关闭。
     * @param selected 已固定的原工具白名单
     * @param name 工具名
     * @param arguments 模型业务参数
     * @return 2026/11/02：实际工具集状态，保留参数转换失败的区分
     */
    private ToolInvocationResult invoke(List<LLMToolBinding> selected, String name, String arguments) {
        try (var control = ExecutionControl.create()) {
            var context = new ToolInvocationContext(request.requestId(), EntitySnapshot.of(EntityId.random(), ComponentPatch.builder()
                    .put(AgentComponents.ACTOR, request.actor()).put(AgentComponents.REQUEST, request).build()), control);
            var result = new LLMToolSet(mapper, selected).execute(LLMToolCall.builder().id(UUID.randomUUID().toString()).name(name).arguments(arguments).build(), context)
                    .toCompletableFuture().join();
            assertFalse(result.truncated()); return result;
        }
    }

    /**
     * 读取成功业务信封，失败状态由需要检查拒绝的用例直接观察。
     * @param selected 固定工具
     * @param name 工具名称
     * @param arguments 业务参数
     * @return 成功业务结果
     */
    private JsonNode execute(List<LLMToolBinding> selected, String name, String arguments) {
        var result = invoke(selected, name, arguments);
        assertEquals(ToolInvocationResult.Status.SUCCESS, result.status()); return mapper.readTree(result.output());
    }

    /**
     * 2026/11/02：权限撤销必须停止整个交互，不能向模型交付已读取的留言。
     * @param selected 已固定旧绑定
     * @param arguments 业务参数
     */
    private void assertRevoked(List<LLMToolBinding> selected, String arguments) {
        var failure = assertThrows(java.util.concurrent.CompletionException.class, () -> invoke(selected, InfoAgentTools.MESSAGES, arguments));
        assertInstanceOf(org.zexnocs.teanekoagent.llm.framework.LLMOperationRejectedException.class, failure.getCause());
    }

    /** 普通用户能按 UUID 查询现有公开资料，不获得完整留言列表或平台写入能力。 */
    @Test void publicInfoReadsExistingProfileAndCoinsByUuid() {
        // 2026/11/05：使用组合后的真实注解入口。
        var selected = select();
        assertEquals(List.of(InfoAgentTools.INFO), selected.stream().map(value -> value.definition().getName()).toList());
        assertTrue(selected.getFirst().supportsClient("其他客户端"));
        var result = execute(selected, InfoAgentTools.INFO, "{\"uuid\":\"" + target + "\"}");
        assertEquals(target.toString(), result.path("uuid").stringValue()); assertEquals("喜欢花茶", result.path("personalInfo").stringValue());
        assertEquals(42, result.path("coinCount").intValue()); assertEquals("较早留言", result.path("randomMessage").stringValue());
    }

    /** 2026/11/02：留言查询使用稳定分页，读取过程中撤销权限后不得交付已经取到的正文。 */
    @Test void debugMessagePagesAreBoundedAndRecheckPermissionAfterRead() {
        // 2026/11/05：独立留言工具仍按真实 DEBUG 来源选入。
        actor.setRole(CommandPermission.DEBUG);
        var selected = select();
        var args = "{\"uuid\":\"" + target + "\",\"limit\":1}";
        var result = execute(selected, InfoAgentTools.MESSAGES, args);
        assertEquals("最新留言", result.path("messages").get(0).path("message").stringValue());
        assertEquals(1, result.path("nextOffset").intValue()); assertTrue(result.path("hasMore").booleanValue());
        when(messages.getMessageList(target)).thenAnswer(ignored -> {
            actor.setRole(CommandPermission.MEMBER); return List.of(new MessageBoardInfoData("不得泄漏", UUID.randomUUID(), Instant.now()));
        });
        assertRevoked(selected, args);
    }

    /** 2026/11/02：旧绑定不能绕过权限变化，非法 UUID 和溢出分页在读取业务前拒绝。 */
    @Test void revokedPermissionAndInvalidArgumentsNeverReachMessageService() {
        // 2026/11/05：撤权覆盖独立入口保留的执行复核。
        actor.setRole(CommandPermission.DEBUG);
        var selected = select();
        clearInvocations(messages, personal);
        var invalid = invoke(selected, InfoAgentTools.MESSAGES, "{\"uuid\":\"" + target + "\",\"offset\":4294967296}");
        assertEquals(ToolInvocationResult.Status.FAILED, invalid.status());
        assertFalse(execute(selected, InfoAgentTools.INFO, "{\"uuid\":\"1-1-1-1-1\"}").path("available").booleanValue());
        actor.setRole(CommandPermission.MEMBER);
        assertRevoked(selected, "{\"uuid\":\"" + target + "\"}");
        verifyNoInteractions(messages, personal);
    }
}
