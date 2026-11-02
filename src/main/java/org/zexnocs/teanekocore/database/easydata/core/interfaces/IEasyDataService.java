package org.zexnocs.teanekocore.database.easydata.core.interfaces;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekocore.database.easydata.BaseEasyData;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.api.IEasyData;

/**
 * 对数据传输对象进行储存和获取的服务接口
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IEasyDataService {
    /**
     * 根据 EasyData 获取数据对象。
     * 优先从缓存中获取。
     * @param easyData 已声明实体及域的入口
     * @param target 固定目标
     * @return EasyData 数据对象
     */
    @NonNull
    IEasyDataDto getEasyData(IEasyData easyData, String target);

    /**
     * 配置等通用框架按本地声明选择专用实体，复用同一 DTO 缓存和任务设施。
     * @param entityType 可信声明的实体类型，不从用户参数反射取得
     * @param namespace 域
     * @param target 目标
     * @return 同一实体及域的普通 DTO
     */
    default IEasyDataDto getEasyDataFor(Class<? extends BaseEasyDataObject> entityType, String namespace, String target) {
        return getEasyData(new BaseEasyData(this, namespace) {
            /** @return 配置声明的专用实体 */
            @Override public Class<? extends BaseEasyDataObject> getEntityClass() { return entityType; }
        }, target);
    }
}
