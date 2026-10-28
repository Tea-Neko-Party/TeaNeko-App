package org.zexnocs.teanekocore.framework.composition;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;

import java.time.Clock;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 System 装配顺序、步骤提交、取消、失败及实体租约边界。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class SystemPipelineTest {
    private static final ComponentKey<Integer> COUNT = ComponentKey.of("count", Integer.class);
    private static final ComponentKey<String> LABEL = ComponentKey.of("label", String.class);

    /**
     * 验证编译顺序遵循依赖，无依赖步骤保持稳定排列。
     */
    @Test
    void compiledOrderFollowsDependenciesAndIsStableForIndependentSteps() {
        var seed = step("z-seed", Set.of(), Set.of(COUNT), Set.of(), context ->
                done(SystemResult.continueWith(ComponentPatch.builder().put(COUNT, 4).build())));
        var consumer = step("a-consumer", Set.of(COUNT), Set.of(LABEL), Set.of("z-seed"), context ->
                done(SystemResult.continueWith(ComponentPatch.builder()
                        .put(LABEL, "count=" + context.snapshot().require(COUNT)).build())));
        var independent = step("m-independent", Set.of(), Set.of(), Set.of(), context ->
                done(SystemResult.continueWith(ComponentPatch.empty())));
        var pipeline = SystemPipeline.compile(List.of(consumer, seed, independent));

        try (var control = ExecutionControl.create()) {
            var result = pipeline.execute(new Entity(new EntityId("ordered")), control).toCompletableFuture().join();
            assertEquals(List.of("m-independent", "z-seed", "a-consumer"), result.executedSystems());
            assertEquals(result.executedSystems(), pipeline.systemIds());
            assertEquals("count=4", result.snapshot().require(LABEL));
        }
    }

    /**
     * 验证装配拒绝重复标识、缺失依赖与依赖环。
     */
    @Test
    void compilationRejectsDuplicateMissingAndCyclicDependencies() {
        var a = emptyStep("a", Set.of());
        assertThrows(IllegalArgumentException.class, () -> SystemPipeline.compile(List.of(a, a)));
        assertThrows(IllegalArgumentException.class,
                () -> SystemPipeline.compile(List.of(emptyStep("a", Set.of("missing")))));
        assertThrows(IllegalArgumentException.class,
                () -> SystemPipeline.compile(List.of(emptyStep("a", Set.of("b")), emptyStep("b", Set.of("a")))));
        assertThrows(IllegalArgumentException.class,
                () -> SystemPipeline.compile(List.of(emptyStep("self", Set.of("self")))));
    }

    /**
     * 验证多个写入者需要明确顺序且所有 System 同意组件类型。
     */
    @Test
    void writersNeedExplicitOrderingAndAllSystemsMustAgreeOnComponentTypes() {
        var first = step("first", Set.of(), Set.of(COUNT), Set.of(), context -> done(SystemResult.skip()));
        var second = step("second", Set.of(), Set.of(COUNT), Set.of(), context -> done(SystemResult.skip()));
        assertThrows(IllegalArgumentException.class, () -> SystemPipeline.compile(List.of(first, second)));

        var wrongType = step("wrong", Set.of(ComponentKey.of("count", String.class)), Set.of(),
                Set.of("first"), context -> done(SystemResult.skip()));
        assertThrows(IllegalArgumentException.class, () -> SystemPipeline.compile(List.of(first, wrongType)));
    }

    /**
     * 验证缺少必需输入时失败，只有显式可选步骤允许跳过。
     */
    @Test
    void requiredInputsFailButExplicitlyOptionalStepCanSkip() {
        var invoked = new AtomicBoolean();
        var required = step("required", Set.of(COUNT), Set.of(), Set.of(), context -> {
            invoked.set(true);
            return done(SystemResult.skip());
        });
        var entity = new Entity(new EntityId("missing"));
        try (var control = ExecutionControl.create()) {
            assertStageFailure(IllegalStateException.class, SystemPipeline.compile(List.of(required)).execute(entity, control));
            assertFalse(invoked.get());
        }

        var optional = new TestSystem(new SystemDescriptor("optional", Set.of(COUNT), Set.of(), Set.of(), true), context -> {
            invoked.set(true);
            return done(SystemResult.skip());
        });
        try (var control = ExecutionControl.create()) {
            var result = SystemPipeline.compile(List.of(optional)).execute(entity, control).toCompletableFuture().join();
            assertEquals(List.of("optional"), result.skippedSystems());
            assertTrue(result.executedSystems().isEmpty());
            assertFalse(invoked.get());
        }
    }

    /**
     * 验证异步步骤完成并提交后，后续步骤才能读取结果。
     */
    @Test
    void asynchronousStepMustFinishBeforeFollowingStepCanReadItsResult() {
        var pending = new CompletableFuture<SystemResult>();
        var observed = new AtomicBoolean();
        var first = step("first", Set.of(), Set.of(COUNT), Set.of(), context -> pending);
        var next = step("next", Set.of(COUNT), Set.of(), Set.of("first"), context -> {
            assertEquals(7, context.snapshot().require(COUNT));
            observed.set(true);
            return done(SystemResult.continueWith(ComponentPatch.empty()));
        });
        try (var control = ExecutionControl.create()) {
            var result = SystemPipeline.compile(List.of(next, first))
                    .execute(new Entity(new EntityId("async")), control).toCompletableFuture();
            assertFalse(result.isDone());
            assertFalse(observed.get());

            pending.complete(SystemResult.continueWith(ComponentPatch.builder().put(COUNT, 7).build()));

            assertTrue(observed.get());
            assertEquals(List.of("first", "next"), result.join().executedSystems());
        }
    }

    /**
     * 验证未声明写入在补丁的任何部分提交前失败。
     */
    @Test
    void undeclaredWriteFailsBeforeCommittingAnyOfThePatch() {
        var bad = step("bad", Set.of(), Set.of(COUNT), Set.of(), context -> done(SystemResult.continueWith(
                ComponentPatch.builder().put(COUNT, 1).put(LABEL, "not declared").build())));
        var entity = new Entity(new EntityId("writes"));
        try (var control = ExecutionControl.create()) {
            assertStageFailure(IllegalStateException.class, SystemPipeline.compile(List.of(bad)).execute(entity, control));
            assertEquals(0, entity.snapshot().revision());
            assertFalse(entity.snapshot().contains(COUNT));
        }
    }

    /**
     * 验证停止决定提交当前补丁但不执行后续步骤。
     */
    @Test
    void stopCommitsItsPatchButDoesNotRunFollowingSystem() {
        var reached = new AtomicBoolean();
        var stop = step("stop", Set.of(), Set.of(COUNT), Set.of(), context ->
                done(SystemResult.stop(ComponentPatch.builder().put(COUNT, 2).build())));
        var next = step("next", Set.of(), Set.of(), Set.of("stop"), context -> {
            reached.set(true);
            return done(SystemResult.skip());
        });
        try (var control = ExecutionControl.create()) {
            var result = SystemPipeline.compile(List.of(next, stop))
                    .execute(new Entity(new EntityId("stop")), control).toCompletableFuture().join();
            assertTrue(result.stopped());
            assertEquals(2, result.snapshot().require(COUNT));
            assertEquals(List.of("stop"), result.executedSystems());
            assertFalse(reached.get());
        }
    }

    /**
     * 验证失败保留已完成步骤并释放实体执行租约。
     */
    @Test
    void failurePreservesCompletedStepsAndReleasesEntityLease() {
        var expected = new IllegalArgumentException("step failed");
        var first = step("first", Set.of(), Set.of(COUNT), Set.of(), context -> done(SystemResult.continueWith(
                ComponentPatch.builder().put(COUNT, 1).build())));
        var fail = step("fail", Set.of(), Set.of(), Set.of("first"), context -> {
            throw expected;
        });
        var entity = new Entity(new EntityId("failure"));
        try (var control = ExecutionControl.create()) {
            assertSame(expected, assertStageFailure(IllegalArgumentException.class,
                    SystemPipeline.compile(List.of(fail, first)).execute(entity, control)));
            assertEquals(1, entity.snapshot().require(COUNT));
        }
        try (var control = ExecutionControl.create()) {
            assertEquals(1, SystemPipeline.compile(List.of()).execute(entity, control)
                    .toCompletableFuture().join().snapshot().require(COUNT));
        }
    }

    /**
     * 验证取消在途步骤释放租约，迟到结果不能提交。
     */
    @Test
    void cancelledPendingStepReleasesLeaseAndCannotCommitItsLateResult() {
        var pending = new CompletableFuture<SystemResult>();
        var slow = step("slow", Set.of(), Set.of(COUNT), Set.of(), context -> pending);
        var entity = new Entity(new EntityId("cancelled"));
        try (var control = ExecutionControl.create()) {
            var result = SystemPipeline.compile(List.of(slow)).execute(entity, control);
            control.cancel();
            assertEquals(ExecutionControl.StopReason.CANCELLED,
                    assertStageFailure(ExecutionStoppedException.class, result).reason());

            try (var nextControl = ExecutionControl.create()) {
                SystemPipeline.compile(List.of()).execute(entity, nextControl).toCompletableFuture().join();
            }
            pending.complete(SystemResult.continueWith(ComponentPatch.builder().put(COUNT, 99).build()));
            assertFalse(entity.snapshot().contains(COUNT));
            assertEquals(0, entity.snapshot().revision());
        }
    }

    /**
     * 验证源阶段永不完成时截止时间仍能结束管线。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void deadlineCompletesPipelineEvenIfSourceNeverReturns() throws Exception {
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        var entity = new Entity(new EntityId("deadline"));
        var slow = step("slow", Set.of(), Set.of(), Set.of(), context -> new CompletableFuture<>());
        try (var control = ExecutionControl.withDeadline(new ResourceBudget(Map.of()),
                Instant.now().plusMillis(80), Clock.systemUTC(), scheduler)) {
            var result = SystemPipeline.compile(List.of(slow)).execute(entity, control).toCompletableFuture();
            var failure = assertThrows(ExecutionException.class, () -> result.get(3, TimeUnit.SECONDS)).getCause();
            assertEquals(ExecutionControl.StopReason.DEADLINE_EXCEEDED,
                    assertInstanceOf(ExecutionStoppedException.class, failure).reason());
            try (var nextControl = ExecutionControl.create()) {
                SystemPipeline.compile(List.of()).execute(entity, nextControl).toCompletableFuture().join();
            }
        } finally {
            scheduler.shutdownNow();
        }
    }

    /**
     * 验证同一实体禁止重复在途执行，不同实体保持独立。
     */
    @Test
    void sameEntityCannotRunTwiceWhileDifferentEntitiesRemainIndependent() {
        var firstEntity = new Entity(new EntityId("first"));
        var secondEntity = new Entity(new EntityId("second"));
        var firstStage = new CompletableFuture<SystemResult>();
        var secondStage = new CompletableFuture<SystemResult>();
        var slow = step("slow", Set.of(), Set.of(COUNT), Set.of(), context ->
                context.snapshot().id().equals(firstEntity.snapshot().id()) ? firstStage : secondStage);
        var pipeline = SystemPipeline.compile(List.of(slow));
        try (var first = ExecutionControl.create(); var duplicate = ExecutionControl.create();
             var second = ExecutionControl.create()) {
            var firstResult = pipeline.execute(firstEntity, first).toCompletableFuture();
            assertStageFailure(IllegalStateException.class, pipeline.execute(firstEntity, duplicate));
            var secondResult = pipeline.execute(secondEntity, second).toCompletableFuture();

            firstStage.complete(SystemResult.continueWith(ComponentPatch.builder().put(COUNT, 1).build()));
            assertEquals(1, firstResult.join().snapshot().require(COUNT));
            assertFalse(secondResult.isDone());
            secondStage.complete(SystemResult.continueWith(ComponentPatch.builder().put(COUNT, 2).build()));
            assertEquals(2, secondResult.join().snapshot().require(COUNT));
        }
    }

    /**
     * 验证外部版本变化会拒绝迟到异步补丁。
     */
    @Test
    void externallyChangedRevisionRejectsAStaleAsynchronousPatch() {
        var entity = new Entity(new EntityId("stale"));
        var pending = new CompletableFuture<SystemResult>();
        var slow = step("slow", Set.of(), Set.of(COUNT), Set.of(), context -> pending);
        try (var control = ExecutionControl.create()) {
            var result = SystemPipeline.compile(List.of(slow)).execute(entity, control);
            entity.commit(0, ComponentPatch.builder().put(COUNT, 50).build());

            pending.complete(SystemResult.continueWith(ComponentPatch.builder().put(COUNT, 1).build()));

            assertStageFailure(ConcurrentModificationException.class, result);
            assertEquals(50, entity.snapshot().require(COUNT));
        }
    }

    /**
     * 验证null 阶段明确失败且不遗留实体忙碌状态。
     */
    @Test
    void nullStageFailsAndDoesNotKeepEntityBusy() {
        var entity = new Entity(new EntityId("invalid implementation"));
        var bad = step("bad", Set.of(), Set.of(), Set.of(), context -> null);
        try (var control = ExecutionControl.create()) {
            assertStageFailure(NullPointerException.class, SystemPipeline.compile(List.of(bad)).execute(entity, control));
            SystemPipeline.compile(List.of()).execute(entity, control).toCompletableFuture().join();
        }
    }

    /**
     * 验证异步跳过步骤不能把过期输入传给后续步骤。
     */
    @Test
    void asynchronouslySkippedStepCannotPassStaleInputsToFollowingSystem() {
        var entity = new Entity(new EntityId("stale skip"), ComponentPatch.builder().put(COUNT, 1).build());
        var pending = new CompletableFuture<SystemResult>();
        var reached = new AtomicBoolean();
        var slow = step("slow", Set.of(COUNT), Set.of(), Set.of(), context -> pending);
        var next = step("next", Set.of(COUNT), Set.of(), Set.of("slow"), context -> {
            reached.set(true);
            return done(SystemResult.continueWith(ComponentPatch.empty()));
        });
        try (var control = ExecutionControl.create()) {
            var result = SystemPipeline.compile(List.of(next, slow)).execute(entity, control);
            entity.commit(0, ComponentPatch.builder().put(COUNT, 2).build());

            pending.complete(SystemResult.skip());

            assertStageFailure(ConcurrentModificationException.class, result);
            assertFalse(reached.get());
            assertEquals(2, entity.snapshot().require(COUNT));
        }
    }

    /**
     * 构造无组件写入的受控步骤，便于独立验证依赖排序。
     *
     * @param id 稳定标识
     * @param after 必须先完成的步骤标识集合
     * @return 满足本方法约束的 {@code IEntitySystem} 结果
     */
    private static IEntitySystem emptyStep(String id, Set<String> after) {
        return step(id, Set.of(), Set.of(), after, context -> done(SystemResult.continueWith(ComponentPatch.empty())));
    }

    /**
     * 构造声明明确的测试 System，将执行行为作为受控回调注入。
     *
     * @param id 稳定标识
     * @param required 步骤执行所必需的组件键
     * @param writes 步骤允许写入的组件键
     * @param after 必须先完成的步骤标识集合
     * @param execute 本步骤受控执行行为
     * @return 满足本方法约束的 {@code IEntitySystem} 结果
     */
    private static IEntitySystem step(String id, Set<ComponentKey<?>> required, Set<ComponentKey<?>> writes,
                                      Set<String> after, Function<SystemContext, CompletionStage<SystemResult>> execute) {
        return new TestSystem(new SystemDescriptor(id, required, writes, after), execute);
    }

    /**
     * 将已知步骤结果包装为立即完成阶段，不额外提交线程任务。
     *
     * @param result 已经形成的处理结果
     * @return 包含步骤决定与补丁的完成阶段，由管线校验并提交
     */
    private static CompletionStage<SystemResult> done(SystemResult result) {
        return CompletableFuture.completedFuture(result);
    }

    /**
     * 断言管线以指定异常类型结束，避免通过阶段包装掩盖错误。
     *
     * @param type 运行时类型
     * @param stage 需要观察或组合的完成阶段
     * @param <T> 本方法约定的泛型数据类型
     * @return 满足本方法约束的 {@code T} 结果
     */
    private static <T extends Throwable> T assertStageFailure(Class<T> type, CompletionStage<?> stage) {
        var failure = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join()).getCause();
        return assertInstanceOf(type, failure);
    }

    /**
     * 以显式声明和受控回调组成的测试 System。
     *
     * @param descriptor 用于本次测试的 {@code descriptor} 数据
     * @param action 需要运行的处理回调
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record TestSystem(SystemDescriptor descriptor,
                              Function<SystemContext, CompletionStage<SystemResult>> action) implements IEntitySystem {
        /**
         * 运行测试注入的单步骤行为，保留真实管线的控制与提交边界。
         *
         * @param context 用于本次测试的 {@code context} 数据
         * @return 包含步骤决定与补丁的完成阶段，由管线校验并提交
         */
        @Override
        public CompletionStage<SystemResult> execute(SystemContext context) {
            return action.apply(context);
        }
    }
}
