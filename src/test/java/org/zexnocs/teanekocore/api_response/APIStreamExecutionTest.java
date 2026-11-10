package org.zexnocs.teanekocore.api_response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.api_response.api.APIRequestData;
import org.zexnocs.teanekocore.api_response.api.IAPIRequestData;
import org.zexnocs.teanekocore.api_response.api.IAPIResponseData;
import org.zexnocs.teanekocore.api_response.exception.APIRequestTimeoutException;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseAccumulator;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;
import org.zexnocs.teanekocore.logger.ILogger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.server.HttpServer;

import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 使用真实 SSE 解码器及本地 TCP 验证聚合、取消、时限和无隐式重发。
 * <br>2026/11/10：验证任意字节分帧，断流不交付部分结果，不访问外部网络。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
@Timeout(10)
class APIStreamExecutionTest {
    private final ManualScheduler timer = new ManualScheduler();
    private final AtomicInteger exchanges = new AtomicInteger();

    /**
     * 无重试的合成 POST。
     * @param text 合成输入
     * @author zExNocs
     * @date 2026/11/10
     * @since 4.9.1-l
     * @version 4.9.1-l
     */
    @APIRequestData(baseUrl = "http://offline.invalid", path = "/stream", method = "POST", retryCount = 0)
    record Request(String text) implements IAPIRequestData { }

    /**
     * 最小合成事件与结果。
     * @param text 正文
     * @author zExNocs
     * @date 2026/11/10
     * @since 4.9.1-l
     * @version 4.9.1-l
     */
    public record Reply(String text) implements IAPIResponseData { }

    /**
     * @param body 可控的响应字节源
     * @param contentType 响应声明类型
     * @return 使用真实编解码及原生命周期的 HTTP 服务
     */
    private APIResponseService service(Flux<DataBuffer> body, String contentType) {
        return new APIResponseService(Schedulers.immediate(), timer, mock(ICacheService.class), mock(ILogger.class),
                ignored -> WebClient.builder().exchangeFunction(request -> {
                    exchanges.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", contentType).body(body).build());
                })
                        .build());
    }

    /**
     * @param text 合成 SSE 数据
     * @return 分割到单个字节的 UTF-8 缓冲，包括中文字符中间边界
     */
    private Flux<DataBuffer> bytes(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return Flux.range(0, bytes.length)
                .map(index -> DefaultDataBufferFactory.sharedInstance.wrap(new byte[]{bytes[index]}));
    }

    /**
     * @param finishes 完整结束次数
     * @return 本次订阅独立的合成聚合器工厂
     */
    private Supplier<IAPIResponseAccumulator<Reply, Reply>> collector(AtomicInteger finishes) {
        return () -> new IAPIResponseAccumulator<>() {
            private final StringBuilder text = new StringBuilder();

            /** @param data 当前有界合成事件 */
            @Override
            public void accept(Reply data) {
                text.append(data.text());
                if (text.length() > 100) {
                    throw new IllegalArgumentException("合成响应超出测试容量。");
                }
            }

            /** @return 仅正常结束时返回的合成完整结果 */
            @Override
            public Reply finish() {
                finishes.incrementAndGet();
                return new Reply(text.toString());
            }
        };
    }

    /** 字节和中文边界的分割不改变 SSE 正文，只有 EOF 才交付聚合。 */
    @Test
    void fragmentedEventsWaitForEof() {
        var tail = Sinks.many().unicast().<DataBuffer>onBackpressureBuffer();
        var finishes = new AtomicInteger();
        var service = service(bytes(": heartbeat\n\ndata: {\"text\":\"你\"}\n\ndata: {\"text\":\"好\"}\n\n")
                .concatWith(tail.asFlux()), "text/event-stream");
        var result = service.addStreamTask(new Request("合成"), Reply.class, collector(finishes),
                ExecutionPolicy.withTimeout(Duration.ofSeconds(3))).asStage().toCompletableFuture();
        assertFalse(result.isDone());
        assertEquals(0, finishes.get());
        tail.tryEmitComplete().orThrow();
        assertEquals("你好", result.join().text());
        assertEquals(1, finishes.get());
        assertEquals(0, timer.pendingTasks());
    }

    /** 父取消和整个阶段时限都会释放真实订阅，迟到正文不触发完成回调。 */
    @Test
    void cancellationAndTimeoutDiscardAccumulatedData() {
        for (boolean cancel : new boolean[]{true, false}) {
            var disposed = new AtomicBoolean();
            var finishes = new AtomicInteger();
            var body = bytes("data: {\"text\":\"未完成\"}\n\n")
                    .concatWith(Flux.never()).doOnCancel(() -> disposed.set(true));
            try (var control = ExecutionControl.create()) {
                var pending = service(body, "text/event-stream").addStreamTask(new Request("合成"), Reply.class,
                        collector(finishes), ExecutionPolicy.controlled(Duration.ofSeconds(2), control))
                        .asStage().toCompletableFuture();
                if (cancel) {
                    control.cancel();
                } else {
                    timer.advance(Duration.ofSeconds(2));
                }
                var failure = assertThrows(CompletionException.class, pending::join).getCause();
                if (cancel) {
                    assertInstanceOf(ExecutionStoppedException.class, failure);
                } else {
                    assertInstanceOf(APIRequestTimeoutException.class, failure);
                }
                assertTrue(disposed.get());
                assertEquals(0, finishes.get());
                assertEquals(0, timer.pendingTasks());
            }
        }
    }

    /** 已收到部分正文后连接重置必须失败，不能用累计结果掩盖异常。 */
    @Test
    void transportFailureDoesNotFinishPartialResponse() {
        var finishes = new AtomicInteger();
        var body = bytes("data: {\"text\":\"部分\"}\n\n")
                .concatWith(Flux.error(new SocketException("Connection reset")));
        var pending = service(body, "text/event-stream").addStreamTask(new Request("合成"), Reply.class,
                collector(finishes), ExecutionPolicy.defaults()).asStage().toCompletableFuture();
        assertThrows(CompletionException.class, pending::join);
        // 2026/11/10：释放响应体可能再次订阅清理，网络重发以实际 exchange 次数判断。
        assertEquals(1, exchanges.get());
        assertEquals(0, finishes.get());
        assertEquals(0, timer.pendingTasks());
    }

    /** 非 SSE 响应明确失败，不将代理的普通 JSON 或错误页面当成成功流。 */
    @Test
    void wrongContentTypeIsRejected() {
        var finishes = new AtomicInteger();
        var pending = service(bytes("{\"text\":\"OK\"}"), "application/json")
                .addStreamTask(new Request("合成"), Reply.class, collector(finishes), ExecutionPolicy.defaults())
                .asStage().toCompletableFuture();
        var failure = assertThrows(CompletionException.class, pending::join);
        assertTrue(FailureDiagnostic.from(failure).toString().contains("要求 Content-Type=text/event-stream"));
        assertEquals(0, finishes.get());
    }

    /**
     * 本地服务收到 POST 后立即断开，生产客户端不能在底层重新提交。
     * @throws Exception 当有界网络等待失败时
     */
    @Test
    void tcpAbortDoesNotResubmitPost() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) -> {
            requests.incrementAndGet();
            return request.receive().then(Mono.fromRunnable(() -> response.withConnection(connection -> connection.dispose())));
        }).bindNow(Duration.ofSeconds(3));
        try {
            var service = new APIResponseService(Schedulers.immediate(), Schedulers.parallel(),
                    mock(ICacheService.class), mock(ILogger.class),
                    ignored -> APIResponseService.createClient("http://127.0.0.1:" + server.port()));
            var pending = service.addTask(new Request("只发送一次"), Reply.class, true, true,
                    ExecutionPolicy.withTimeout(Duration.ofSeconds(3))).asStage().toCompletableFuture();
            assertThrows(java.util.concurrent.ExecutionException.class, () -> pending.get(4, TimeUnit.SECONDS));
            assertEquals(1, requests.get());
        } finally {
            server.disposeNow(Duration.ofSeconds(3));
        }
    }
}
