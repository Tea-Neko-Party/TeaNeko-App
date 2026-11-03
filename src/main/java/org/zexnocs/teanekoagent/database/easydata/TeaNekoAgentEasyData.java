package org.zexnocs.teanekoagent.database.easydata;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekocore.database.easydata.BaseEasyData;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.core.EasyData;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Agent 唯一数据入口，沿用 of(namespace).get(target) 的 DTO 用法，并组合跨目标原子事务。
 * 注入入口可创建隔离视图，避免测试或宿主组合依赖全局静态状态；构造时无数据库访问。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
@EasyData
public final class TeaNekoAgentEasyData extends BaseEasyData {
    private static volatile TeaNekoAgentEasyData root;
    private final IEasyDataService service;
    /**
     * 装配专用入口，不解析配置或预先加载 DTO。
     * @param service Core EasyData 服务
     */
    @Autowired public TeaNekoAgentEasyData(IEasyDataService service) {
        this(service, "agent"); root = this;
    }
    /**
     * 创建捕获原服务的独立域视图，不覆盖静态根入口。
     * @param service 原服务
     * @param namespace 业务域
     */
    private TeaNekoAgentEasyData(IEasyDataService service, String namespace) { super(service, namespace); this.service = Objects.requireNonNull(service); }
    /**
     * 沿用项目现有静态工厂约定。
     * @param namespace 业务域
     * @return 当前宿主的专用域视图
     * @throws IllegalStateException 当 EasyData 尚未装配时
     */
    public static TeaNekoAgentEasyData of(String namespace) {
        var current = root;
        if (current == null) { throw new IllegalStateException("Agent EasyData 尚未装配。"); }
        return current.inNamespace(namespace);
    }
    /**
     * 使用注入入口的固定服务创建域视图，业务 Store 优先采用此方式支持隔离装配。
     * @param namespace 业务域
     * @return 不执行 I/O 的域视图
     */
    public TeaNekoAgentEasyData inNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) { throw new IllegalArgumentException("Agent 数据域不能为空。"); }
        return new TeaNekoAgentEasyData(service, namespace);
    }
    /** @return 唯一 Agent 数据实体类型 */
    @Override public Class<? extends BaseEasyDataObject> getEntityClass() { return TeaNekoAgentEasyDataObject.class; }
    /**
     * 使用原 EasyData/DatabaseTask 执行跨目标事务，重试重新读取 DTO，提交后才公开结果。
     * @param name 固定中文任务名
     * @param action 事务内业务，不调用普通缓存读取
     * @param <T> 非空结果类型
     * @return 实际提交后完成的只读阶段
     */
    public <T> CompletionStage<T> transaction(String name, Supplier<T> action) {
        var result = new AtomicReference<T>(); var task = get("transaction").getTaskConfig(name);
        task.addTransactionTask(() -> { result.set(null); result.set(Objects.requireNonNull(action.get(), "Agent 数据事务未产生结果。")); });
        return task.pushAsync().thenApply(ignored -> Objects.requireNonNull(result.get(), "Agent 数据事务未产生结果。"))
                .toCompletableFuture().minimalCompletionStage();
    }
}
