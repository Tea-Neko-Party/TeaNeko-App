package org.zexnocs.teanekocore.database.easydata.general;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.springframework.stereotype.Repository;
import org.zexnocs.teanekocore.database.easydata.IBaseEasyDataRepository;

/**
 * 通用 EasyData 仓库接口。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Repository("generalEasyDataRepository")
public interface IGeneralEasyDataRepository extends IBaseEasyDataRepository<GeneralEasyDataObject> {
    /**
     * 创建一个数据对象。
     * @param namespace 命名空间。
     * @param target 对象。
     * @param key 键。
     * @param value 值。
     * @return 数据对象。
     */
    @Override
    default GeneralEasyDataObject create(String namespace, String target, String key, String value) {
        return new GeneralEasyDataObject(namespace, target, key, value);
    }
}
