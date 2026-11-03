package org.zexnocs.teanekoagent.database.easydata;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;

/**
 * Agent 专用键值表，统一元数据、事实、人格、历史及执行账本，按业务域隔离。
 * <br>2026/11/03：增加固定域维护分页索引，避免跨目标维护按整个表排序。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.6.1-l
 */
@Entity @NoArgsConstructor
@Table(name = "tea_neko_agent_data", uniqueConstraints = @UniqueConstraint(columnNames = {"namespace", "target", "o_key"}),
        // 2026/11/03：普通近期查询与维护扫描分别保留对应索引。
        indexes = {@Index(name = "idx_agent_easy_recent", columnList = "namespace,target,id"),
                @Index(name = "idx_agent_easy_maintenance", columnList = "namespace,id")})
public class TeaNekoAgentEasyDataObject extends BaseEasyDataObject {
    /**
     * 仅供基础设施构造尚未保存的行。
     * @param namespace 业务域
     * @param target 目标
     * @param key 稳定键
     * @param value 正文
     */
    protected TeaNekoAgentEasyDataObject(String namespace, String target, String key, String value) { super(namespace, target, key, value); }
}
