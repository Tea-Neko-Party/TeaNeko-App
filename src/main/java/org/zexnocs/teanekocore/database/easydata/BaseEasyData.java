package org.zexnocs.teanekocore.database.easydata;

import org.zexnocs.teanekocore.database.easydata.api.IEasyData;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;

/**
 * 抽象的 EasyData 类，用于继承
 *
 * @author zExNocs
 * @date 2026/02/15
 */
public abstract class BaseEasyData implements IEasyData {
    /// EasyData 服务
    private final IEasyDataService service;

    /// 命名空间
    private final String namespace;

    protected BaseEasyData(IEasyDataService service, String namespace) {
        this.service = service;
        this.namespace = namespace;
    }

    /**
     * 获取该数据对象的命名空间。
     * @return 命名空间。
     */
    @Override
    public String getNamespace() {
        return namespace;
    }

    /**
     * 根据 target 获取数据对象。
     */
    @Override
    public IEasyDataDto get(String target) {
        return service.getEasyData(this, target);
    }
}
