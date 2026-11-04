package org.zexnocs.teanekoagent.integration.config;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.component.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.component.llm.file_config.LLMModelFileConfigParameter;
import org.zexnocs.teanekoagent.component.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoUserData;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.database.configdata.ConfigEditService;
import org.zexnocs.teanekocore.database.configdata.ConfigEdits;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证模型切换的 OWNER 约束、已配置路由要求和版本事务，不调用数据库或模型网络。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.7-l
 * @version 4.8.0-l
 */
class AgentModelSelectionTest {
    /** 2026/11/04：无权者和未知路由不提交，合法切换保留读取版本并在提交前复核。 */
    @Test
    void switchesOnlyConfiguredRoutesThroughAuthorizedVersionedEdit() {
        var files = mock(ILLMFileConfigService.class);
        var models = mock(ILLMModelService.class);
        var edits = mock(ConfigEditService.class);
        var entry = new LLMModelFileConfigParameter();
        entry.setId("deepseek-chat");
        entry.setAdapter("deepseek");
        entry.setModel("deepseek-flash");
        var config = new LLMModelFileConfig();
        config.setModels(List.of(entry));
        when(files.getConfig()).thenReturn(config);
        when(models.getDefaultOptions(any())).thenReturn(LLMModelOptions.builder()
                .provider("deepseek-chat").model("deepseek-flash").maxTokens(2048).build());
        var message = mock(ITeaNekoMessageData.class);
        var user = mock(ITeaNekoUserData.class);
        var client = mock(ITeaNekoClient.class);
        when(message.getUserData()).thenReturn(user);
        when(message.getClient()).thenReturn(client);
        when(client.getClientId()).thenReturn("web");
        when(user.getUuid()).thenReturn(UUID.randomUUID());
        when(user.getRole()).thenReturn(CommandPermission.MEMBER);
        when(message.getScopeId()).thenReturn("private:fixture");
        when(message.getMessageId()).thenReturn("message-id");
        var selection = new AgentModelSelection(files, models, edits);
        assertThrows(IllegalStateException.class, () -> selection.select(message, "deepseek-chat", null));
        verifyNoInteractions(edits);
        when(user.getRole()).thenReturn(CommandPermission.OWNER);
        assertThrows(IllegalArgumentException.class, () -> selection.select(message, "unknown", null));
        verifyNoInteractions(edits);
        when(edits.read(any(), anyString(), any())).thenReturn(CompletableFuture.completedStage(
                new ConfigEdits.Snapshot(true, "original-version", "{}")));
        when(edits.change(any(), anyString(), anyString(), any(), any())).thenAnswer(call -> {
            call.<Runnable>getArgument(4).run();
            ConfigEdits.Change change = call.getArgument(3);
            assertEquals("original-version", change.expectedRevision());
            assertEquals(Map.of("modelRoute", "deepseek-chat"), change.fields());
            return CompletableFuture.completedStage(new ConfigEdits.Receipt(change.operationId(), "target", "new-version",
                    true, change.action(), List.of("modelRoute"), java.time.Instant.now(), false));
        });
        assertTrue(selection.select(message, "deepseek-chat", null).toCompletableFuture().join().contains("deepseek-chat"));
        var actor = user.getUuid().toString();
        verify(edits).change(any(), eq("private:fixture"), eq(actor), any(), any());
    }
}
