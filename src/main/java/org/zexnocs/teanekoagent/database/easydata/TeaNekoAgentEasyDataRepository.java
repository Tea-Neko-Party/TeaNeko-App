package org.zexnocs.teanekoagent.database.easydata;

import org.springframework.stereotype.Repository;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataRepository;

/**
 * Agent 专用表的基础设施仓库，仅由 Core EasyData 服务发现和访问，业务 Store 不注入此类型。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Repository
public interface TeaNekoAgentEasyDataRepository extends BaseEasyDataRepository<TeaNekoAgentEasyDataObject> {
    /**
     * 创建本表候选实体，锁和版本处理复用 Core 事务 DTO。
     * @param namespace 域
     * @param target 目标
     * @param key 键
     * @param value 正文
     * @return 未保存实体
     */
    @Override default TeaNekoAgentEasyDataObject create(String namespace, String target, String key, String value) {
        return new TeaNekoAgentEasyDataObject(namespace, target, key, value);
    }
}
