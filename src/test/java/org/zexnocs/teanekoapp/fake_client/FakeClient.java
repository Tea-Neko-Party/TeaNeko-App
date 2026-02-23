package org.zexnocs.teanekoapp.fake_client;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekoapp.response.ResponseData;
import org.zexnocs.teanekoapp.response.ResponseEvent;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * 一个用于测试的假客户端实现。
 *
 * @author zExNocs
 * @date 2026/02/23
 */
@Component
public class FakeClient implements IClient {
    private final ObjectMapper mapper = new ObjectMapper();
    private final IEventService iEventService;
    private final ITaskService iTaskService;

    public FakeClient(IEventService iEventService, ITaskService iTaskService) {
        this.iEventService = iEventService;
        this.iTaskService = iTaskService;
    }

    /**
     * 发送消息到服务器。
     *
     * @param message 要发送的消息
     */
    @Override
    public void send(String message) {
        // 异步 handle
        iTaskService.subscribe("FakeClient 处理接收信息",
                () -> {
                    handle(message);
                    return EmptyTaskResult.INSTANCE;
                },
                EmptyTaskResult.getResultType());
    }

    /**
     * 从客户端接收消息并处理。
     * 一般就是将 message 解析后包装成事件对象并推送到事件总线中。
     * 一般分为两个主要事件：
     * 1. 消息事件 MessageEvent：用于处理用户发送的消息
     * 2. 响应事件 ResponseEvent：用于处理客户端的响应信息
     *
     * @param message 接收到的消息
     */
    @Override
    public void handle(String message) {
        // 将 message 转化成 map
        var data = mapper.readValue(message, Map.class);
        // 获取 echo 字段
        var echo = (String) data.get("echo");
        if(echo == null) {
            throw new IllegalArgumentException("Message must contain an 'echo' field");
        }
        // 获取 count 字段
        var count = (Integer) data.get("count");
        if(count == null) {
            throw new IllegalArgumentException("Message must contain a 'count' field");
        }
        // 构造响应消息
        ResponseData responseData;
        if(count == 0) {
            var map = new HashMap<String, String>();
            map.put("data", "Hello, this is a response from FakeClient!");
            responseData = new ResponseData(true, echo, mapper.writeValueAsString(map));
        } else {
            responseData = new ResponseData(false, echo, null);
        }
        var responseEvent = new ResponseEvent(responseData, ResponseData.class);
        iEventService.pushEvent(responseEvent);
    }
}
