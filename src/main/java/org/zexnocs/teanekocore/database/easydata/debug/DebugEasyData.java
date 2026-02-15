package org.zexnocs.teanekocore.database.easydata.debug;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekocore.database.easydata.BaseEasyData;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.core.EasyData;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;

/**
 * 用于展示给人看的 EasyData 类。
 * 一般给开发者看一些日志或者调试信息。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@EasyData
public class DebugEasyData extends BaseEasyData {
    // 用于获取数据库服务。
    private static IEasyDataService easyDataService;

    /**
     * 用于注入数据库服务。
     * @param easyDataService 数据库服务。
     */
    @Autowired
    public DebugEasyData(IEasyDataService easyDataService) {
        super(easyDataService, null);
        DebugEasyData.easyDataService = easyDataService;
    }

    /**
     * 用于创建一个数据对象。
     * @param namespace 数据对象的命名空间。
     */
    private DebugEasyData(String namespace) {
        super(easyDataService, namespace);
    }

    @Override
    public Class<? extends BaseEasyDataObject> getEntityClass() {
        return DebugEasyDataObject.class;
    }


    // ------- 静态方法 -------
    /**
     * 用于获取一个数据对象。
     * @param namespace 数据对象的命名空间。
     * @return 数据对象。
     */
    public static DebugEasyData of(String namespace) {
        return new DebugEasyData(namespace);
    }
}
