package org.zexnocs.teanekoagent.schedule;

import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

/**
 * 任务独立域的事务存储；创建原规格同时充当持久化幂等回执。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class EasyDataScheduleStore {
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
