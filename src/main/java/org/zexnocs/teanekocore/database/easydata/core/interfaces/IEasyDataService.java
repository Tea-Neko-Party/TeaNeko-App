package org.zexnocs.teanekocore.database.easydata.core.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.database.easydata.api.IEasyData;

/**
 * 对数据传输对象进行储存和获取的服务接口
 *
 * @author zExNocs
 * @date 2026/02/15
 */
public interface IEasyDataService {
    /**
     * 根据 EasyData 获取数据对象。
     * 优先从缓存中获取。
     * @return EasyData 数据对象
     */
    @NonNull
    IEasyDataDto getEasyData(IEasyData easyData, String target);
}
