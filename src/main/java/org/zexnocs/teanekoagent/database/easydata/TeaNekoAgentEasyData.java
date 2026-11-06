package org.zexnocs.teanekoagent.database.easydata;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekocore.database.easydata.BaseEasyData;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.cleanable.CleanableEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.core.EasyData;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Agent 数据入口，显式区分持久状态和可清理观察，组合跨表、跨目标原子事务。
 * 注入入口可创建隔离视图，避免测试或宿主组合依赖全局静态状态；构造时无数据库访问。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/06：增加同域可清理视图，跨表写入继续由原 EasyData 事务仲裁。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.8.5-l
 */
@EasyData
public final class TeaNekoAgentEasyData extends BaseEasyData {
    private static volatile TeaNekoAgentEasyData root;
    private final IEasyDataService service;
    private final Class<? extends BaseEasyDataObject> entityClass;
    /**
     * 装配专用入口，不解析配置或预先加载 DTO。
     * @param service Core EasyData 服务
     */
    @Autowired public TeaNekoAgentEasyData(IEasyDataService service) {
        this(service, "agent", TeaNekoAgentEasyDataObject.class); root = this;
    }
    /**
     * 创建捕获原服务的独立域视图，不覆盖静态根入口。
     * @param service 原服务
     * @param namespace 业务域
     * @param entityClass 明确的存储层级
     */
    private TeaNekoAgentEasyData(IEasyDataService service, String namespace,
                                Class<? extends BaseEasyDataObject> entityClass) {
        super(service, namespace);
        this.service = Objects.requireNonNull(service);
        this.entityClass = entityClass;
    }
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
        return new TeaNekoAgentEasyData(service, namespace, entityClass);
    }

    /**
     * 2026/11/06：创建同域的可清理视图；防重、租约、记忆和业务水位不得写入此视图。
     * 清理后的数据只能表现为观察缺失，不能因此取得新的业务执行权。
     * @return 捕获同一服务、参与同一事务的可清理数据入口
     */
    public TeaNekoAgentEasyData cleanable() {
        return new TeaNekoAgentEasyData(service, getNamespace(), CleanableEasyDataObject.class);
    }

    /** @return 当前明确选择的实体类型 */
    @Override public Class<? extends BaseEasyDataObject> getEntityClass() { return entityClass; }
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
