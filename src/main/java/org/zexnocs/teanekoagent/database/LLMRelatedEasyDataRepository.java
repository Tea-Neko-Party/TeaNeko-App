package org.zexnocs.teanekoagent.database;

import org.springframework.stereotype.Repository;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataRepository;

/**
 * LLM 相关 EasyData 仓库接口。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
@Repository("llmRelatedEasyDataRepository")
public interface LLMRelatedEasyDataRepository extends BaseEasyDataRepository<LLMRelatedEasyDataObject> {
    /**
     * 创建 LLM 相关 EasyData 实体。
     *
     * @param namespace 数据命名空间。
     * @param target    数据目标。
     * @param key       数据键。
     * @param value     数据值。
     * @return LLM 相关 EasyData 实体。
     */
    @Override
    default LLMRelatedEasyDataObject create(String namespace, String target, String key, String value) {
        return new LLMRelatedEasyDataObject(namespace, target, key, value);
    }
}
