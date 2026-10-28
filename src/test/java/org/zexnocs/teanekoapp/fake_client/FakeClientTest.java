package org.zexnocs.teanekoapp.fake_client;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.message.TeaNekoMessageReceiveEvent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekoapp.response.ResponseEvent;
import org.zexnocs.teanekocore.command.api.CommandPermission;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证测试客户端能沿原消息和响应事件链记录收发行为。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class FakeClientTest {
    /**
     * 验证FakeClient 群消息生成带真实来源结构的入站事件。
     */
    @Test
    void handleGroupMessageCreatesTeaNekoEvent() {
        var client = new FakeClient();

        var event = client.handleGroupMessage(
                "group-1",
                "user-1",
                "Tester",
                CommandPermission.ADMIN,
                "/ping now"
        );

        var data = event.getData();
        assertEquals(TeaNekoMessageType.GROUP, data.getMessageType());
        assertEquals("fake-client-group@group-1", data.getScopeId());
        assertEquals("user-1", data.getUserData().getUserIdInPlatform());
        assertEquals(CommandPermission.ADMIN, data.getUserData().getRole());
        assertEquals("/ping now", data.getMessages().getFirst().getContentPart().toRawString());
    }

    /**
     * 验证测试发送器记录可观察的目标与消息内容。
     */
    @Test
    void messageSenderRecordsFakeSendData() {
        var client = new FakeClient();
        var event = client.handleGroupMessage("group-1", "user-1", "hello");

        event.getData()
                .getMessageSender()
                .addReplyTextMessage("world")
                .send();

        var sent = client.getLastSentMessage();
        assertNotNull(sent);
        assertEquals(FakeSendData.TargetType.GROUP, sent.getTargetType());
        assertEquals("group-1", sent.getTargetId());
        assertEquals(2, sent.getMessages().size());
        assertEquals("world", sent.getMessages().get(1).getContentPart().toRawString());
        assertEquals(1, client.getSentRawMessages().size());
    }

    /**
     * 验证FakeClient 响应 JSON 转换为对应的响应事件。
     */
    @Test
    void handleResponseJsonCreatesResponseEvent() {
        var client = new FakeClient();

        var event = client.handle("""
                {"echo":"abc","status":"ok","data":{"message_id":"fake-sent-1"}}
                """);

        var responseEvent = assertInstanceOf(ResponseEvent.class, event);
        assertTrue(responseEvent.getData().isSuccess());
        assertEquals("abc", responseEvent.getData().getEcho());
        assertEquals("fake-sent-1", responseEvent.getData().getRawData().getFirst().get("message_id"));
    }

    /**
     * 验证无额外结构的纯文本按私聊输入处理。
     */
    @Test
    void handlePlainTextFallsBackToPrivateMessage() {
        var client = new FakeClient();

        var event = assertInstanceOf(TeaNekoMessageReceiveEvent.class, client.handle("plain text"));
        var data = (ITeaNekoMessageData) event.getData();

        assertEquals(TeaNekoMessageType.PRIVATE, data.getMessageType());
        assertEquals("plain text", data.getMessages().getFirst().getContentPart().toRawString());
    }
}
