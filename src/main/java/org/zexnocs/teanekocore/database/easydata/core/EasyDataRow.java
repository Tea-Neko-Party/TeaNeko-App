package org.zexnocs.teanekocore.database.easydata.core;

import lombok.Getter;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;

import java.util.Objects;

/**
 * 单事务内使用的版本 DTO，不公开数据库实体或仓库，不可放入业务组件或长期缓存。
 * 修改正文只更新本地值，须由所属事务 DTO 显式保存后才进入数据库。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class EasyDataRow {
    private final Object owner;
    private final BaseEasyDataObject entity;
    /**
     * 当前 DTO 正文
     */
    @Getter
    private String value;

    /**
     * 仅允许基础设施包装受版本管理的实体。
     * @param owner 原仓库身份，仅在基础层验证
     * @param entity 原始实体
     */
    EasyDataRow(Object owner, BaseEasyDataObject entity) {
        this.owner = owner; this.entity = entity; value = entity.getValue();
    }
    /** @return 物理域 */
    public String getNamespace() { return entity.getNamespace(); }
    /** @return 固定目标 */
    public String getTarget() { return entity.getTarget(); }
    /** @return 稳定业务键 */
    public String getKey() { return entity.getKey(); }
    /** @return 已保存行的递增序号，尚未保存时为零 */
    public long getId() { return entity.getId(); }
    /** @return 数据库仲裁的原行版本 */
    public long getVersion() { return entity.getVersion(); }

    /**
     * 暂存替换正文，不触发隐式写入。
     * @param value 完整正文
     */
    public void setValue(String value) { this.value = Objects.requireNonNull(value, "EasyData 正文不能为空。"); }
    /**
     * 仅供基础层确认归属，业务层无法通过 DTO 取得实体。
     * @param repository 原仓库
     * @param namespace 预期域
     * @param target 预期目标
     * @return 内部原实体
     * @throws IllegalArgumentException 当 DTO 被提交到其他数据边界时
     */
    BaseEasyDataObject entity(Object repository, String namespace, String target) {
        if (owner != repository || !getNamespace().equals(namespace) || !getTarget().equals(target)) {
            throw new IllegalArgumentException("EasyData 行不能跨仓库、域或目标提交。");
        }
        return entity;
    }
}
