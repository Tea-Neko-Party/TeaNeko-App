package org.zexnocs.teanekoagent.schedule;

import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

/**
 * 任务独立域的事务存储；创建原规格同时充当持久化幂等回执。
 * <br>2026/11/03：管理取消的版本检查、当前授权与持久回执在原事务提交。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.7.0-l
 */
public final class EasyDataScheduleStore {
    /**
     * 2026/11/03：显式管理来源仅取消现有计划，不转换成原生创建者或执行来源。
     * @param scope 宿主已解析的真实聊天
     * @param actor 原管理主体
     * @param query 原取消或回执查询
     * @param guard 实际事务内当前授权
     * @return 与计划状态同事务保存的原回执
     */
    public CompletionStage<ScheduleManagement.Receipt> manage(org.zexnocs.teanekoagent.agent.AgentScope scope,
            String actor, ScheduleManagement.Query query, Runnable guard) {
        return data.transaction("管理取消 Agent 计划", () -> {
            guard.run();
            String subject = org.zexnocs.teanekocore.utils.StableDigest.sha256(actor);
            String target = scope.clientId() + ":" + scope.scopeId();
            var dto = data.get("management").atomic();
            var row = dto.find("operations", EasyDataLock.WRITE);
            var journal = row == null ? new java.util.LinkedHashMap<UUID, ScheduleManagement.Operation>()
                    : new java.util.LinkedHashMap<>(mapper.readValue(row.getValue(), ScheduleManagement.Journal.class).operations());
            var now = java.time.Instant.now();
            journal.values().removeIf(operation -> operation.receipt().at().plusSeconds(86400).isBefore(now));
            var previous = journal.get(query.operationId());
            if (previous != null) {
                var original = previous.request();
                if (!previous.actor().equals(subject) || !previous.scope().equals(target)
                        || !Objects.equals(original.id(), query.id()) || original.revision() != query.revision()) {
                    throw new IllegalArgumentException("计划操作编号已绑定其他主体、目标或参数。");
                }
                guard.run();
                return previous.receipt();
            }
            if (query.action().equals("OPERATION")) {
                throw new java.util.NoSuchElementException("计划操作回执不存在或已过期。");
            }
            if (!query.action().equals("CANCEL") || journal.size() >= 256) {
                throw new IllegalStateException("计划动作无效或回执窗口已满。");
            }
            var tasks = data.get("tasks").atomic();
            var taskRow = tasks.find(java.util.Objects.requireNonNull(query.id()).toString(), EasyDataLock.WRITE);
            if (taskRow == null) {
                throw new java.util.NoSuchElementException("当前聊天没有该计划。");
            }
            var task = mapper.readValue(taskRow.getValue(), AgentScheduledTask.class);
            if (!task.spec().creator().scope().equals(scope)) {
                throw new SecurityException("不能管理其他聊天的计划。");
            }
            if (task.revision() != query.revision()) {
                throw new java.util.ConcurrentModificationException("计划版本已经变化。");
            }
            boolean changed = java.util.Set.of(AgentScheduledTask.Status.ACTIVE, AgentScheduledTask.Status.RUNNING,
                    AgentScheduledTask.Status.PAUSED).contains(task.status());
            if (changed) {
                task = new AgentScheduledTask(task.id(), task.spec(), task.nextFire(), AgentScheduledTask.Status.CANCELLED,
                        Math.addExact(task.revision(), 1), "管理入口已取消未来计划；已开始的外部动作不能保证撤销");
                taskRow.setValue(mapper.writeValueAsString(task));
                guard.run();
                tasks.save(taskRow);
            }
            var receipt = new ScheduleManagement.Receipt(query.operationId(), changed ? "APPLIED" : "UNCHANGED",
                    task.id().toString(), -1, task.revision(), "未来计划状态已确认；取消不表示在途外部动作已撤销。", now);
            journal.put(query.operationId(), new ScheduleManagement.Operation(subject, target, query, receipt));
            if (row == null) {
                row = dto.create("operations", mapper.writeValueAsString(new ScheduleManagement.Journal(journal)));
            } else {
                row.setValue(mapper.writeValueAsString(new ScheduleManagement.Journal(journal)));
            }
            guard.run();
            dto.save(row);
            return receipt;
        });
    }
    private final TeaNekoAgentEasyData data;
    private final ObjectMapper mapper;

    /**
     * 借用专用 EasyData 和原数据库事务任务。
     * @param data 专用入口
     * @param mapper 存储转换器
     */
    public EasyDataScheduleStore(TeaNekoAgentEasyData data, ObjectMapper mapper) {
        this.data = data.inNamespace("agent.schedule.v1"); this.mapper = mapper;
    }

    /**
     * 同一 ID 重放只允许完全相同的原始规格，不复活已取消任务。
     * @param task 新任务
     * @return 提交后的已有或新建快照
     */
    public CompletionStage<AgentScheduledTask> create(AgentScheduledTask task) {
        return data.transaction("创建 Agent 计划任务", () -> {
            var dto = data.get("tasks").atomic(); var row = dto.find(task.id().toString(), EasyDataLock.WRITE);
            if (row != null) {
                var old = mapper.readValue(row.getValue(), AgentScheduledTask.class);
                if (!old.spec().equals(task.spec())) { throw new IllegalArgumentException("任务请求标识已用于不同规格。"); }
                return old;
            }
            dto.save(dto.create(task.id().toString(), mapper.writeValueAsString(task))); return task;
        });
    }

    /**
     * 在行锁内执行状态比较与写入，事务重试重新读取原状态。
     * @param id 任务标识
     * @param update 纯状态转换，不进行外部副作用
     * @return 提交后的快照
     */
    public CompletionStage<AgentScheduledTask> update(UUID id, UnaryOperator<AgentScheduledTask> update) {
        return data.transaction("更新 Agent 计划任务", () -> {
            var dto = data.get("tasks").atomic(); var row = dto.find(id.toString(), EasyDataLock.WRITE);
            if (row == null) { throw new IllegalArgumentException("计划任务不存在。"); }
            var old = mapper.readValue(row.getValue(), AgentScheduledTask.class); var next = update.apply(old);
            if (!next.id().equals(old.id()) || !next.spec().equals(old.spec())) { throw new IllegalStateException("不能改变任务身份或原规格。"); }
            if (!next.equals(old)) { row.setValue(mapper.writeValueAsString(next)); dto.save(row); }
            return next;
        });
    }

    /**
     * 同一发生只能由实际完成状态交换的调用方执行，不能把别人已认领的快照当作自己的回执。
     * @param expected 原活动快照
     * @param next 认领或暂停状态
     * @return 当前事务是否真正取得交换权
     */
    public CompletionStage<Boolean> compareAndSet(AgentScheduledTask expected, AgentScheduledTask next) {
        return data.transaction("仲裁 Agent 任务执行权", () -> {
            if (!expected.id().equals(next.id()) || !expected.spec().equals(next.spec())
                    || next.revision() != Math.addExact(expected.revision(), 1)) {
                throw new IllegalArgumentException("任务状态交换不合法。");
            }
            var dto = data.get("tasks").atomic(); var row = dto.find(expected.id().toString(), EasyDataLock.WRITE);
            if (row == null || !mapper.readValue(row.getValue(), AgentScheduledTask.class).equals(expected)) { return false; }
            row.setValue(mapper.writeValueAsString(next)); dto.save(row); return true;
        });
    }

    /**
     * 在事务内读取权威状态，不使用进程缓存判断执行权。
     * @param id 任务标识
     * @return 当前快照或空结果
     */
    public CompletionStage<Optional<AgentScheduledTask>> find(UUID id) {
        return data.transaction("查询 Agent 计划任务", () -> {
            var row = data.get("tasks").atomic().find(id.toString(), EasyDataLock.READ);
            return row == null ? Optional.empty() : Optional.of(mapper.readValue(row.getValue(), AgentScheduledTask.class));
        });
    }

    /**
     * 按稳定 ID 游标有界读取，调用方负责按可信 scope 过滤后展示。
     * @param after 排他游标，首页为空字符串
     * @return 最多一百项，不代表完整任务集合
     */
    public CompletionStage<List<AgentScheduledTask>> page(String after) {
        return data.transaction("分页读取 Agent 计划任务", () -> data.get("tasks").atomic()
                .scan(after, null, 100, EasyDataLock.NONE).stream()
                .map(row -> mapper.readValue(row.getValue(), AgentScheduledTask.class)).toList());
    }
}
