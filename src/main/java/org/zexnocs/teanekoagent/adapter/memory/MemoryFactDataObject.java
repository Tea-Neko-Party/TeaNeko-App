package org.zexnocs.teanekoagent.adapter.memory;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;

/**
 * 沿用 llm_related 表承载独立事实行，不依赖旧 Java 包或覆盖旧 memory/profile 数据。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@NoArgsConstructor
@Entity
@Table(name = "llm_related", uniqueConstraints = @UniqueConstraint(columnNames = {"namespace", "target", "o_key"}))
public class MemoryFactDataObject extends BaseEasyDataObject {
    /**
     * 由存储适配器构造有明确物理定位的行，ID 与 Version 由 JPA 管理。
     * @param namespace 物理域
     * @param target 空间及代次目标
     * @param key 稳定记录或操作键
     * @param value 显式 schema JSON
     */
    protected MemoryFactDataObject(String namespace, String target, String key, String value) { super(namespace, target, key, value); }
}
