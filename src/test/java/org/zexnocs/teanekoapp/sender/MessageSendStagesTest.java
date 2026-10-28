package org.zexnocs.teanekoapp.sender;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证公共分段发送适配的顺序、等待、列表快照及失败收束。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class MessageSendStagesTest {
    /**
     * 验证分段按顺序发送并等待最后一段回执。
     */
    @Test
    void sendsPartsSequentiallyAndAwaitsTheLastPart() {
        var first = new CompletableFuture<Void>();
        var last = new CompletableFuture<Void>();
        var sent = new ArrayList<List<Integer>>();
        var stage = MessageSendStages.sendByPart(List.of(1, 2, 3), 2, part -> {
            sent.add(part);
            return sent.size() == 1 ? first : last;
        });

        assertEquals(List.of(List.of(1, 2)), sent);
        assertFalse(stage.toCompletableFuture().isDone());
        first.complete(null);
        assertEquals(List.of(List.of(1, 2), List.of(3)), sent);
        assertFalse(stage.toCompletableFuture().isDone());
        last.complete(null);
        assertNull(stage.toCompletableFuture().join());
    }

    /**
     * 验证延迟发送使用固定列表快照，不读取后续变更。
     */
    @Test
    void delayedPartsUseImmutableListSnapshot() {
        var source = new ArrayList<>(List.of(1, 2, 3));
        var gate = new CompletableFuture<Void>();
        var sent = new ArrayList<List<Integer>>();
        var stage = MessageSendStages.sendByPart(source, 2, part -> {
            sent.add(part);
            return sent.size() == 1 ? gate : CompletableFuture.completedStage(null);
        });
        source.clear();
        source.add(999);
        assertThrows(UnsupportedOperationException.class, () -> sent.getFirst().set(0, 999));

        gate.complete(null);

        stage.toCompletableFuture().join();
        assertEquals(List.of(List.of(1, 2), List.of(3)), sent);
    }

    /**
     * 验证发送失败停止剩余分段并保留原始异常。
     */
    @Test
    void failureStopsRemainingPartsAndPreservesTheCause() {
        var gate = new CompletableFuture<Void>();
        var calls = new AtomicInteger();
        var stage = MessageSendStages.sendByPart(List.of(1, 2, 3), 1, part -> {
            calls.incrementAndGet();
            return gate;
        });
        var failure = new IllegalStateException("send failed");

        gate.completeExceptionally(failure);

        var error = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());
        assertSame(failure, error.getCause());
        assertEquals(1, calls.get());
    }

    /**
     * 验证发送器同步抛错或缺少阶段时也能结束等待。
     */
    @Test
    void synchronousSenderFailureAndMissingStageAlsoSettleTheChain() {
        var failure = new IllegalStateException("sender unavailable");
        var stage = MessageSendStages.sendByPart(List.of(1, 2), 1, part -> { throw failure; });
        assertSame(failure, assertThrows(CompletionException.class,
                () -> stage.toCompletableFuture().join()).getCause());
        var missing = MessageSendStages.sendByPart(List.of(1), 1, part -> null);
        assertInstanceOf(NullPointerException.class, assertThrows(CompletionException.class,
                () -> missing.toCompletableFuture().join()).getCause());
    }

    /**
     * 验证空输入不发送，非法分段大小明确拒绝。
     */
    @Test
    void emptyInputDoesNotSendAndInvalidPartSizeIsRejected() {
        var calls = new AtomicInteger();
        var stage = MessageSendStages.sendByPart(List.of(), 10, part -> {
            calls.incrementAndGet();
            return CompletableFuture.completedStage(null);
        });
        assertNull(stage.toCompletableFuture().join());
        assertEquals(0, calls.get());
        assertThrows(IllegalArgumentException.class,
                () -> MessageSendStages.sendByPart(List.of(), 0, part -> null));
        assertThrows(IllegalArgumentException.class,
                () -> MessageSendStages.sendByPart(List.of(1), -1, part -> null));
    }

    /**
     * 验证超大分段大小不会溢出，有限内容只发送一段。
     */
    @Test
    void hugePartSizeDoesNotOverflowAndSendsOnePart() {
        var sent = new ArrayList<List<Integer>>();
        MessageSendStages.sendByPart(List.of(1, 2), Integer.MAX_VALUE, part -> {
            sent.add(part);
            return CompletableFuture.completedStage(null);
        }).toCompletableFuture().join();
        assertEquals(List.of(List.of(1, 2)), sent);
    }
}
