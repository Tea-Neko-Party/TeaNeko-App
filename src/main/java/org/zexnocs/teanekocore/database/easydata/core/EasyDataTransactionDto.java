package org.zexnocs.teanekocore.database.easydata.core;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataRepository;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataTransactionDto;

import java.util.List;
import java.util.Objects;

/**
 * EasyData 基础层的事务 DTO 实现，集中封装仓库、实体、锁和提交后缓存协调。
 * <br>2026/11/03：提供不扩展写入权限的固定域维护分页。
 * @param <T> 由数据入口固定的实体类型，不由业务参数选择
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.6.1-l
 */
public final class EasyDataTransactionDto<T extends BaseEasyDataObject> implements IEasyDataTransactionDto {
    private final BaseEasyDataRepository<T> repository;
    private final String namespace;
    private final String target;
    private final Runnable invalidate;

    /**
     * 仅由 EasyData DTO 构造事务视图，不访问仓库。
     * @param repository 基础层仓库
     * @param namespace 域
     * @param target 目标
     * @param invalidate 提交后缓存失效动作
     */
    EasyDataTransactionDto(BaseEasyDataRepository<T> repository, String namespace, String target, Runnable invalidate) {
        this.repository = Objects.requireNonNull(repository); this.namespace = namespace; this.target = target; this.invalidate = invalidate;
    }
    /**
     * 使用相同物理边界执行快照或当前读，返回不公开实体的 DTO。
     * @param key 精确键
     * @param lock 锁方式
     * @return 原行或空
     */
    @Override public @Nullable EasyDataRow find(String key, EasyDataLock lock) {
        requireTransaction();
        var entity = switch (Objects.requireNonNull(lock)) {
            case NONE -> repository.findByNamespaceAndTargetAndKey(namespace, target, key);
            case READ -> repository.lockDataForRead(namespace, target, key);
            case WRITE -> repository.lockDataForWrite(namespace, target, key);
        };
        return entity == null ? null : new EasyDataRow(repository, entity);
    }
    /**
     * 创建原边界内的候选，不隐式 upsert 覆盖已存在记录。
     * @param key 固定键
     * @param value 正文
     * @return 未保存 DTO
     */
    @Override public EasyDataRow create(String key, String value) {
        requireTransaction(); return new EasyDataRow(repository, repository.create(namespace, target, key, value));
    }
    /**
     * 原版本保存，提交成功前不让普通缓存看到候选值。
     * @param row 当前边界的 DTO
     */
    @Override public void save(EasyDataRow row) {
        requireTransaction(); var entity = entity(row); entity.setValue(row.getValue()); repository.saveAndFlush(entity); changed();
    }
    /**
     * 版本删除与其他目标的写入由同一外层事务仲裁。
     * @param row 当前版本行
     */
    @Override public void delete(EasyDataRow row) {
        requireTransaction(); repository.delete(entity(row)); repository.flush(); changed();
    }
    /**
     * 有界扫描固定目标，不让业务服务自行拼接 JPQL 或接触 Pageable。
     * @param after 下界
     * @param through 可选上界
     * @param limit 页面大小
     * @param lock 锁方式
     * @return 不可变行页
     */
    @Override public List<EasyDataRow> scan(String after, @Nullable String through, int limit, EasyDataLock lock) {
        requireTransaction(); validateLimit(limit); var page = PageRequest.of(0, limit);
        var entities = switch (Objects.requireNonNull(lock)) {
            case NONE -> repository.scanData(namespace, target, after, through, page);
            case READ -> repository.scanDataForRead(namespace, target, after, through, page);
            case WRITE -> repository.scanDataForWrite(namespace, target, after, through, page);
        };
        return entities.stream().map(entity -> new EasyDataRow(repository, entity)).toList();
    }
    /**
     * 保留稳定行序号，限制原始扫描工作量。
     * @param before 上一序号
     * @param limit 页面大小
     * @return 序号倒序页面
     */
    @Override public List<EasyDataRow> recent(long before, int limit) {
        requireTransaction(); validateLimit(limit);
        if (before < 1) { throw new IllegalArgumentException("EasyData 逆序游标必须为正。"); }
        return repository.findByNamespaceAndTargetAndIdLessThanOrderByIdDesc(namespace, target, before, PageRequest.of(0, limit))
                .stream().map(entity -> new EasyDataRow(repository, entity)).toList();
    }
    /**
     * 2026/11/03：保持域隔离和分页上限，不使用缓存或在扫描阶段改变行锁顺序。
     * @param before 排除序号
     * @param limit 页面大小
     * @return 固定域的倒序只读 DTO 页
     * @throws IllegalArgumentException 当序号或页大小无效时
     * @throws IllegalStateException 当缺少活动数据库事务时
     */
    @Override public List<EasyDataRow> recentNamespace(long before, int limit) {
        requireTransaction(); validateLimit(limit);
        if (before < 1) { throw new IllegalArgumentException("EasyData 域维护游标必须为正。"); }
        return repository.findByNamespaceAndIdLessThanOrderByIdDesc(namespace, before, PageRequest.of(0, limit))
                .stream().map(entity -> new EasyDataRow(repository, entity)).toList();
    }
    /**
     * 根据不可伪造的仓库归属还原泛型实体，业务层不能取得该引用。
     * @param row 同仓库创建的 DTO
     * @return 基础层内部实体
     */
    @SuppressWarnings("unchecked")
    private T entity(EasyDataRow row) { return (T) row.entity(repository, namespace, target); }
    /**
     * 缓存只在事务实际提交后失效，回滚不发布写入。
     */
    private void changed() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** 提交后清除缓存，下一次普通读取重新加载已提交数据。 */
            @Override public void afterCommit() { invalidate.run(); }
        });
    }
    /**
     * 拒绝逃离外层事务的 DTO 调用，防止一组业务写入变成多次自动提交。
     * @throws IllegalStateException 当外层事务或同步设施不存在时
     */
    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("EasyData 事务 DTO 必须在数据库任务事务内使用。");
        }
    }
    /**
     * 校验公共扫描硬上限，禁止调用者绕过分页读取全表。
     * @param limit 页面大小
     * @throws IllegalArgumentException 当页面大小越界时
     */
    private void validateLimit(int limit) {
        if (limit < 1 || limit > 1000) { throw new IllegalArgumentException("EasyData 页面大小必须为一至一千。"); }
    }
}
