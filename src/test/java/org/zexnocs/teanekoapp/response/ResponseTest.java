package org.zexnocs.teanekoapp.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekoapp.fake_client.FakeClient;
import org.zexnocs.teanekoapp.fake_client.FakeSendData;
import org.zexnocs.teanekoapp.sender.api.ISendData;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;

/**
 * 测试 ResponseService 的功能，主要测试注册回调函数并触发事件后是否能够正确接收响应。
 *
 * @author zExNocs
 * @date 2026/02/23
 */
@SpringBootTest
public class ResponseTest {
    @Autowired
    private ISenderService iSenderService;
    @Autowired
    private FakeClient fakeClient;

    /**
     * 测试成功接收响应的情况。发送一个请求并注册回调函数，等待响应并验证结果是否正确。
     */
    @Test
    public void testSuccess() {
        var sendData = new FakeSendData(fakeClient, new ObjectMapper(), 3);
        iSenderService.send(sendData, FakeSendData.class, Duration.ZERO, 3, Duration.ofSeconds(1))
                .thenAccept(r -> {
                    Assertions.assertTrue(r.isSuccess());
                    var result = r.getResult();
                    Assertions.assertNotNull(result);
                    Assertions.assertFalse(result.isEmpty());
                    System.out.println(r.getResult().getFirst().getData());
                }).finish().join();
    }

    /**
     * 测试未能接收响应的情况。发送一个请求但不触发事件，等待响应并验证结果是否为失败。
     */
    @Test
    public void testFailure() {
        var sendData = new FakeSendData(fakeClient, new ObjectMapper(), 10);
        iSenderService.send(sendData, FakeSendData.class, Duration.ZERO, 3, Duration.ofSeconds(1))
                .thenAccept(r -> {
                    Assertions.assertFalse(r.isSuccess());
                    Assertions.assertNotNull(r.getResult());
                    Assertions.assertTrue(r.getResult().isEmpty());
                    System.out.println("测试失败情况成功");
                }).finish().join();
    }

    /**
     * 测试如果 response type 与实际不一致
     */
    @Test
    public void testInvalidResponseType() {
        InvalidSendData sendData = new InvalidSendData(fakeClient, new ObjectMapper(), UUID.randomUUID().toString());
        iSenderService.send(sendData, InvalidSendData.class, Duration.ZERO, 3, Duration.ofSeconds(1))
                .thenAccept(r -> {
                    Assertions.assertTrue(r.isSuccess());
                    Assertions.assertNotNull(r.getResult());
                    Assertions.assertNull(r.getResult().getFirst().invalidField);
                })
                .finish().join();
    }

    @Getter
    @AllArgsConstructor
    public static class InvalidSendData implements ISendData<InvalidResponseType> {
        @JsonIgnore
        private final IClient client;

        @JsonIgnore
        private final ObjectMapper mapper;

        @JsonProperty("echo")
        private final String echo;

        @JsonProperty("count")
        private final int count = 0;

        /**
         * 将数据转化成字符串形式，通常是JSON字符串，以便发送给客户端。
         *
         * @return 转化后的字符串形式数据
         */
        @Override
        public @NonNull String toSendString() {
            return mapper.writeValueAsString(this);
        }

        /**
         * 获取发送数据的响应类型，用于后续处理响应数据。
         * <p>如果为 null 则表示响应数据为 null。
         *
         * @return 响应类型的Class对象
         */
        @Override
        public Class<InvalidResponseType> getResponseType() {
            return InvalidResponseType.class;
        }

        /**
         * 获取发送消息的 token，用于标记不同的消息来源。
         * <p>如果为 null，则表示通用的消息来源。
         * <p>如果要解析成 json，需要标注为 {@link JsonIgnore}，以避免被当做发送数据的一部分。
         */
        @Override
        public @Nullable String getSenderToken() {
            return "";
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvalidResponseType {
        private String invalidField;
    }
}
