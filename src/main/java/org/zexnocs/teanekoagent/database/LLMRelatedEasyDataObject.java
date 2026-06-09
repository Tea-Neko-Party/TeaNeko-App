package org.zexnocs.teanekoagent.database;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;

/**
 * LLM 相关 EasyData 数据库实体。
 * <br>该实体使用统一的 {@code namespace + target + o_key + o_value} 结构保存 LLM 相关键值数据。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
@NoArgsConstructor
@Entity
@Table(name = "llm_related", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"namespace", "target", "o_key"})
})
public class LLMRelatedEasyDataObject extends BaseEasyDataObject {
    /**
     * 创建 LLM 相关 EasyData 数据库实体。
     *
     * @param namespace 数据命名空间。
     * @param target    数据目标。
     * @param key       数据键。
     * @param value     数据值。
     */
    protected LLMRelatedEasyDataObject(String namespace, String target, String key, String value) {
        super(namespace, target, key, value);
    }
}
