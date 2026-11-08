package org.zexnocs.teanekoplugin.support;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证模型服务停止取消自有工作，排空完成前不释放资源或重启。
 * <br>2026/11/07：覆盖迟到物理结果及借用父控制的所有权。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class PluginModelScopeTest {
    /** 停止只取消子控制，真实阶段尚未结束时保持资源并拒绝重新启用。 */
    @Test void drainsOriginalWorkBeforeReleasingAndRestarting() {
        var scope = new PluginModelScope();
        var physical = new CompletableFuture<String>();
        var child = new AtomicReference<ExecutionControl>();
        var released = new AtomicBoolean();
        try (var parent = ExecutionControl.create()) {
            scope.start();
            var work = scope.execute(parent, control -> {
                child.set(control);
                return physical;
            });
            var cancelledObservation = work.toCompletableFuture();
            assertTrue(cancelledObservation.cancel(true));
            var stopped = scope.stop(() -> released.set(true)).toCompletableFuture();
            assertThrows(RuntimeException.class, () -> child.get().checkActive());
            parent.checkActive();
            assertFalse(stopped.isDone());
            assertFalse(released.get());
            assertThrows(IllegalStateException.class, scope::start);
            assertThrows(CompletionException.class,
                    () -> scope.execute(parent, ignored -> CompletableFuture.completedStage("迟到调用"))
                            .toCompletableFuture().join());
            physical.complete("真实完成");
            assertEquals("真实完成", work.toCompletableFuture().join());
            stopped.join();
            assertTrue(released.get());
            scope.start();
            assertEquals("新代次", scope.execute(parent, ignored -> CompletableFuture.completedStage("新代次"))
                    .toCompletableFuture().join());
            scope.stop(() -> {}).toCompletableFuture().join();
        } finally {
            physical.complete("测试清理");
        }
    }
}
