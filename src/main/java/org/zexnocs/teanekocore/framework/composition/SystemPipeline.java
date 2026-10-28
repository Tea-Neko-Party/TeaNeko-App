package org.zexnocs.teanekocore.framework.composition;

import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 预编译、按请求运行的异步组件管线；不创建线程池，不等待 Stage。
 * 每个步骤按 revision 提交自己的 patch，失败不回滚已完成步骤或外部副作用。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class SystemPipeline {
    private final List<Step> steps;

    /**
     * 固定已经编译的步骤顺序，不在每次请求中重新扫描或排序。
     *
     * @param steps 已编译的步骤顺序
     */
    private SystemPipeline(List<Step> steps) {
        this.steps = List.copyOf(steps);
    }

    /**
     * 预编译 System 依赖和稳定顺序，拒绝重名、缺依赖、依赖环、组件类型冲突及无顺序的多个写入者。
     *
     * @param systems 需要装配的 System 集合
     * @return 已验证依赖并固定执行顺序的管线
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public static SystemPipeline compile(Collection<? extends IEntitySystem> systems) {
        Objects.requireNonNull(systems, "systems");
        var nodes = new LinkedHashMap<String, Step>();
        var keyTypes = new HashMap<String, ComponentKey<?>>();
        for (var system : systems) {
            Objects.requireNonNull(system, "system");
            var descriptor = Objects.requireNonNull(system.descriptor(), "descriptor");
            if (nodes.putIfAbsent(descriptor.id(), new Step(system, descriptor)) != null) {
                throw new IllegalArgumentException("Duplicate system: " + descriptor.id());
            }
            descriptor.required().forEach(key -> registerType(keyTypes, key));
            descriptor.writes().forEach(key -> registerType(keyTypes, key));
        }

        var indegree = new HashMap<String, Integer>();
        var successors = new HashMap<String, List<String>>();
        for (var step : nodes.values()) {
            var descriptor = step.descriptor();
            indegree.put(descriptor.id(), descriptor.after().size());
            for (var dependency : descriptor.after()) {
                if (!nodes.containsKey(dependency)) {
                    throw new IllegalArgumentException("Missing system dependency: " + descriptor.id() + " -> " + dependency);
                }
                successors.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(descriptor.id());
            }
        }

        var ready = new PriorityQueue<String>();
        indegree.forEach((id, count) -> {
            if (count == 0) {
                ready.add(id);
            }
        });
        var ordered = new ArrayList<Step>();
        while (!ready.isEmpty()) {
            var id = ready.remove();
            ordered.add(nodes.get(id));
            for (var next : successors.getOrDefault(id, List.of())) {
                if (indegree.compute(next, (ignored, count) -> count - 1) == 0) {
                    ready.add(next);
                }
            }
        }
        if (ordered.size() != nodes.size()) {
            throw new IllegalArgumentException("System dependency cycle: " + nodes.keySet());
        }
        validateWriters(nodes, ordered);
        return new SystemPipeline(ordered);
    }

    /**
     * 提供编译后的执行顺序，供装配核对和诊断使用。
     *
     * @return 已编译且保持稳定顺序的步骤标识列表
     */
    public List<String> systemIds() {
        return steps.stream().map(step -> step.descriptor().id()).toList();
    }

    /**
     * 同实体的并发运行快速失败，由上层会话队列决定排队策略。
     * 返回只读的完成阶段；取消执行请使用传入的 ExecutionControl。
     *
     * @param entity 持有本轮执行租约的实体
     * @param control 借用的取消、截止时间和预算控制
     * @return 承载本次异步处理实际结果的可组合完成阶段
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public CompletionStage<PipelineResult> execute(Entity entity, ExecutionControl control) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(control, "control");
        if (!entity.acquireExecution()) {
            return CompletableFuture.<PipelineResult>failedFuture(
                    new IllegalStateException("Entity is already executing: " + entity.snapshot().id().value()))
                    .minimalCompletionStage();
        }
        CompletableFuture<RunState> chain = CompletableFuture.completedFuture(new RunState(entity.snapshot()));
        for (var step : steps) {
            chain = chain.thenCompose(state -> state.stopped
                    ? CompletableFuture.completedFuture(state)
                    : runStep(step, entity, state, control));
        }
        return chain.thenApply(state -> control.whileActive(() -> {
                    entity.commit(state.snapshot.revision(), ComponentPatch.empty());
                    return new PipelineResult(state.snapshot, state.stopped, state.executed, state.skipped);
                }))
                .whenComplete((ignored, failure) -> entity.releaseExecution())
                .minimalCompletionStage();
    }

    /**
     * 串行执行单个步骤并校验快照版本、必需组件与写入声明；停止后不再提交迟到补丁。
     *
     * @param step 当前需要执行的步骤
     * @param entity 持有本轮执行租约的实体
     * @param state 当前管线执行状态
     * @param control 借用的取消、截止时间和预算控制
     * @return 承载本次异步处理实际结果的可组合完成阶段
     * @throws IllegalStateException 当执行状态、装配契约或返回的完成信号不符合要求时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    private CompletionStage<RunState> runStep(Step step, Entity entity, RunState state, ExecutionControl control) {
        try {
            control.whileActive(() -> entity.commit(state.snapshot.revision(), ComponentPatch.empty()));
            var descriptor = step.descriptor();
            var missing = descriptor.required().stream().filter(key -> !state.snapshot.contains(key)).toList();
            if (!missing.isEmpty()) {
                if (!descriptor.optional()) {
                    throw new IllegalStateException("System " + descriptor.id() + " requires components: " + missing);
                }
                state.skipped.add(descriptor.id());
                return CompletableFuture.completedFuture(state);
            }
            var pending = Objects.requireNonNull(
                    step.system().execute(new SystemContext(state.snapshot, control)),
                    "System " + descriptor.id() + " returned a null CompletionStage");
            return control.guard(pending).thenApply(result -> control.whileActive(() -> {
                Objects.requireNonNull(result, "System " + descriptor.id() + " returned a null result");
                if (!descriptor.writes().containsAll(result.patch().changedKeys())) {
                    throw new IllegalStateException("System wrote undeclared components: " + descriptor.id());
                }
                if (result.outcome() == SystemResult.Outcome.SKIP) {
                    entity.commit(state.snapshot.revision(), ComponentPatch.empty());
                    state.skipped.add(descriptor.id());
                } else {
                    state.snapshot = entity.commit(state.snapshot.revision(), result.patch());
                    state.executed.add(descriptor.id());
                    state.stopped = result.outcome() == SystemResult.Outcome.STOP;
                }
                return state;
            }));
        } catch (Exception failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /**
     * 在装配时固定每个组件键的类型，拒绝跨步骤的同名异型声明。
     *
     * @param keyTypes 已声明的稳定组件键与类型映射
     * @param key 定位目标数据的键
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    private static void registerType(Map<String, ComponentKey<?>> keyTypes, ComponentKey<?> key) {
        var previous = keyTypes.putIfAbsent(key.id(), key);
        if (previous != null && !previous.equals(key)) {
            throw new IllegalArgumentException("Conflicting component type: " + key.id());
        }
    }

    /**
     * 检查同一组件的多个写入者具有明确依赖顺序，避免以扫描顺序决定覆盖。
     *
     * @param nodes 按稳定标识索引的步骤表
     * @param steps 已编译的步骤顺序
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    private static void validateWriters(Map<String, Step> nodes, List<Step> steps) {
        var writers = new HashMap<ComponentKey<?>, List<String>>();
        for (var step : steps) {
            for (var key : step.descriptor().writes()) {
                var previous = writers.computeIfAbsent(key, ignored -> new ArrayList<>());
                for (var other : previous) {
                    if (!dependsOn(nodes, step.descriptor().id(), other)) {
                        throw new IllegalArgumentException(
                                "Writers need an explicit dependency for " + key.id() + ": " + other + ", " + step.descriptor().id());
                    }
                }
                previous.add(step.descriptor().id());
            }
        }
    }

    /**
     * 遍历前置依赖判断步骤间的顺序约束，并用已访问集合避免重复展开。
     *
     * @param nodes 按稳定标识索引的步骤表
     * @param system 需要检查依赖的步骤标识
     * @param expected 约定的预期状态或版本
     * @return 目标步骤存在直接或间接前置依赖时为 true
     */
    private static boolean dependsOn(Map<String, Step> nodes, String system, String expected) {
        var pending = new ArrayDeque<>(nodes.get(system).descriptor().after());
        var visited = new HashSet<String>();
        while (!pending.isEmpty()) {
            var id = pending.remove();
            if (id.equals(expected)) {
                return true;
            }
            if (visited.add(id)) {
                pending.addAll(nodes.get(id).descriptor().after());
            }
        }
        return false;
    }

    /**
     * 编译时固定的 System 与其声明，避免运行期间重新解释依赖。
     *
     * @param system 需要检查依赖的步骤标识
     * @param descriptor 用于本次处理的 {@code descriptor} 数据
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Step(IEntitySystem system, SystemDescriptor descriptor) {}

    /**
     * 单次管线独有的执行进度，只沿串行步骤链更新。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class RunState {
        private EntitySnapshot snapshot;
        private final List<String> executed = new ArrayList<>();
        private final List<String> skipped = new ArrayList<>();
        private boolean stopped;

        /**
         * 为单次串行管线保存起始快照和进度，不与其他实体或请求共享可变状态。
         *
         * @param snapshot 本步骤读取的不可变实体快照
         */
        private RunState(EntitySnapshot snapshot) {
            this.snapshot = snapshot;
        }
    }
}
