package org.zexnocs.teanekocore.database.easydata.cleanable;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekocore.database.easydata.BaseEasyData;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.core.EasyData;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;

/**
 * 可清理的 EasyData 类。
 * 该类的数据库只做临时存储，后续可随意清理。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@EasyData
public class CleanableEasyData extends BaseEasyData {
    /// 用于获取数据库服务。
    private static IEasyDataService easyDataService;

    @Autowired
    protected CleanableEasyData(IEasyDataService service) {
        super(service, null);
        CleanableEasyData.easyDataService = service;
    }

    /**
     * 用于创建一个数据对象。
     * @param namespace 数据对象的命名空间。
     */
    private CleanableEasyData(String namespace) {
        super(easyDataService, namespace);
    }

    /**
     * 获取该数据对象的实体类。
     * 用于给服务来获取相应的数据库类。
     * @return 实体类。
     */
    @Override
    public Class<? extends BaseEasyDataObject> getEntityClass() {
        return CleanableEasyDataObject.class;
    }

    // ------- 静态方法 -------
    /**
     * 用于获取一个数据对象。
     * @param namespace 数据对象的命名空间。
     * @return 数据对象。
     */
    public static CleanableEasyData of(String namespace) {
        return new CleanableEasyData(namespace);
    }
}
