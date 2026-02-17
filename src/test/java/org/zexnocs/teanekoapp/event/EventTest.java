package org.zexnocs.teanekoapp.event;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekoapp.event.object.AEvent;
import org.zexnocs.teanekoapp.event.object.BEvent;
import org.zexnocs.teanekocore.event.interfaces.IEventService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 事件测试
 *
 * @author zExNocs
 * @date 2026/02/17
 */
@SpringBootTest
public class EventTest {
    @Autowired
    private IEventService iEventService;

    /**
     * 测试事件继承触发
     * 假设有 父事件 A 和 子事件 B，B 继承 A：
     * 1. 事件 A 不会触发监听器 B，但会触发监听器 A；
     * 2. 事件 B 会触发监听器 A 和 B
     */
    @Test
    public void testEventInheritance() {
        // 测试事件 A，理应该触发监听器 A，但不触发监听器 B
        AtomicInteger counterA = new AtomicInteger(0);
        var futureA = iEventService.pushEventWithFuture(new AEvent(counterA)).finish();

        // 测试事件 B，理应该触发监听器 A 和 B，且先触发监听器 A
        AtomicInteger counterB = new AtomicInteger(0);
        var futureB = iEventService.pushEventWithFuture(new BEvent(counterB)).finish();

        // 等待事件处理完成
        CompletableFuture.allOf(futureA, futureB).join();
        Assertions.assertEquals(1, counterA.get());
        Assertions.assertEquals(2, counterB.get());
    }
}
