package org.zexnocs.teanekocore.database.easydata.api;

import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;

/**
 * 是获取某一个 EasyData 数据库和命名空间下数据对象的接口。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
public interface IEasyData {
    /**
     * 获取该数据对象的实体类。
     * 用于给服务来获取相应的数据库类。
     * @return 实体类。
     */
    Class<? extends BaseEasyDataObject> getEntityClass();

    /**
     * 获取该数据对象的命名空间。
     * @return 命名空间。
     */
    String getNamespace();

    /**
     * 根据 target 获取数据对象。
     */
    IEasyDataDto get(String target);

    /**
     * 根据 IEasyDataKey 获取数据对象。
     */
    default IEasyDataDto get(IEasyDataKey key) {
        return get(key.getKey());
    }
}
