package org.zexnocs.teanekocore.database.easydata.cleanable;

import org.springframework.stereotype.Repository;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataRepository;

/**
 * 可清理的 EasyData 仓库接口。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Repository
public interface CleanableEasyDataRepository extends BaseEasyDataRepository<CleanableEasyDataObject> {
    /**
     * 创建一个数据对象。
     * @param namespace 命名空间。
     * @param target 对象。
     * @param key 键。
     * @param value 值。
     * @return 数据对象。
     */
    @Override
    default CleanableEasyDataObject create(String namespace, String target, String key, String value) {
        return new CleanableEasyDataObject(namespace, target, key, value);
    }
}
