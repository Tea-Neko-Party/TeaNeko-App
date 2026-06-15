package org.zexnocs.teanekoagent.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.zexnocs.teanekoagent.memory.AgentMemoryQueryService;
import org.zexnocs.teanekoagent.memory.AgentMemoryRecord;
import org.zexnocs.teanekoagent.response.AgentResponseData;
import org.zexnocs.teanekoagent.response.AgentResponseEvent;
import org.zexnocs.teanekoagent.sender.AgentConversationSender;
import org.zexnocs.teanekoagent.sender.AgentMemoryWriteSendData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * TeaNeko Agent 内置客户端契约测试。
 * <br>验证响应事件类型、Toolbox 能力边界、事件推送数量和 Sender 异步返回类型。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
class TeaNekoAgentClientContractTest {
    /**
     * 验证内置客户端处理 Agent 响应 JSON 时只创建 {@link AgentResponseEvent}。
     */
    @Test
    void handleOnlyCreatesAgentResponseEvent() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var client = new TeaNekoAgentInternalClient(
                null, null, null, null, null, null, mapper, Mockito.mock(ILogger.class)
        );
        var json = mapper.writeValueAsString(AgentResponseData.builder()
                .success(true)
                .echo("echo")
                .rawData(List.of())
                .build());

        var event = client.handle(json);

        Assertions.assertInstanceOf(AgentResponseEvent.class, event);
    }

    /**
     * 验证 Agent Toolbox 对无关平台工具明确抛出不支持异常。
     */
    @Test
    void toolboxRejectsUnrelatedPlatformTools() {
        var toolbox = new TeaNekoAgentToolbox(null, null, null, Mockito.mock(ILogger.class));

        Assertions.assertThrows(UnsupportedOperationException.class, toolbox::getMessageSenderTools);
        Assertions.assertThrows(UnsupportedOperationException.class, toolbox::getGroupMemberInfoSender);
        Assertions.assertThrows(UnsupportedOperationException.class, toolbox::getGroupMemberListSender);
    }

    /**
     * 验证结构化 Agent 操作只向事件服务推送一个 Agent 响应事件。
     */
    @Test
    void structuredOperationPublishesOnlyAgentResponseEvent() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var eventService = Mockito.mock(IEventService.class);
        var memoryService = Mockito.mock(AgentMemoryQueryService.class);
        var client = new TeaNekoAgentInternalClient(
                null, null, null, memoryService, null, eventService, mapper, Mockito.mock(ILogger.class)
        );
        var record = new AgentMemoryRecord();
        record.setContent("remember this");
        var sendData = new AgentMemoryWriteSendData(
                client, mapper, "scope", "agent", "user", record
        );

        client.send(sendData);

        var eventCaptor = ArgumentCaptor.forClass(IEvent.class);
        Mockito.verify(eventService).pushEvent(eventCaptor.capture());
        Assertions.assertInstanceOf(AgentResponseEvent.class, eventCaptor.getValue());
        Mockito.verifyNoMoreInteractions(eventService);
    }

    /**
     * 验证对话 Sender 返回 {@code TaskFuture<ITaskResult<?>>}，符合应用 Sender 异步契约。
     *
     * @throws NoSuchMethodException 当对话发送方法不存在时抛出
     */
    @Test
    void conversationSenderReturnsTaskFutureOfTaskResult() throws Exception {
        var method = AgentConversationSender.class.getMethod("send", ITeaNekoMessageData.class);

        Assertions.assertEquals(TaskFuture.class, method.getReturnType());
        Assertions.assertTrue(method.getGenericReturnType().getTypeName().contains("ITaskResult"));
    }
}
