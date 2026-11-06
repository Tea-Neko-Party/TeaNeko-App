package org.zexnocs.teanekocore.database.easydata.cleanable;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;

/**
 * 可清理的 EasyData 对象。
 * <br>2026/11/06：增加命名空间维护分页索引，避免清理查询反复扫描整表。
 *
 * @author zExNocs
 * @date 2026/02/16
 * @since 4.0.8
 * @version 4.8.5-l
 */
@NoArgsConstructor
@Entity
@Table(name = "cleanable_data", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"namespace", "target", "o_key"})
}, indexes = @Index(name = "idx_cleanable_easy_maintenance", columnList = "namespace,id"))
public class CleanableEasyDataObject extends BaseEasyDataObject {
    /**
     * 创建命名空间内的临时记录，生命周期由对应业务维护器管理。
     * @param namespace 业务域
     * @param target 目标分区
     * @param key 唯一键
     * @param value 序列化内容
     */
    protected CleanableEasyDataObject(String namespace, String target, String key, String value) {
        super(namespace, target, key, value);
    }
}
