package org.zexnocs.teanekocore.database.easydata.general;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekocore.database.easydata.BaseEasyData;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.core.EasyData;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;

/**
 * 通用 EasyData 类。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@EasyData
public class GeneralEasyData extends BaseEasyData {
    // 用于获取数据库服务。
    private static IEasyDataService easyDataService;

    /**
     * 用于注入数据库服务。
     * @param easyDataService 数据库服务。
     */
    @Autowired
    public GeneralEasyData(IEasyDataService easyDataService) {
        super(easyDataService, null);
        GeneralEasyData.easyDataService = easyDataService;
    }

    /**
     * 用于创建一个数据对象。
     * @param namespace 数据对象的命名空间。
     */
    private GeneralEasyData(String namespace) {
        super(easyDataService, namespace);
    }

    /**
     * 获取该数据对象的实体类。
     * 用于给服务来获取相应的数据库类。
     * @return 实体类。
     */
    @Override
    public Class<? extends BaseEasyDataObject> getEntityClass() {
        return GeneralEasyDataObject.class;
    }

    // ------- 静态方法 -------
    /**
     * 用于获取一个数据对象。
     * @param namespace 数据对象的命名空间。
     * @return 数据对象。
     */
    public static GeneralEasyData of(String namespace) {
        return new GeneralEasyData(namespace);
    }
}
