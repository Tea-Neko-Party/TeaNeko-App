package org.zexnocs.teanekoagent.database;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekocore.database.easydata.BaseEasyData;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.core.EasyData;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;

/**
 * LLM 相关持久化数据的 EasyData 入口类。
 * <br>该类用于承载 agent 记忆、prompt 缓存、运行状态等 LLM 相关数据，具体业务含义由 namespace 区分。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
@EasyData
public class LLMRelatedEasyData extends BaseEasyData {
    /**
     * EasyData 服务实例。
     * <br>用于静态 {@link #of(String)} 方法创建指定 namespace 的数据入口。
     */
    private static IEasyDataService easyDataService;

    /**
     * 创建默认 LLM 相关 EasyData 入口。
     *
     * @param easyDataService EasyData 服务。
     */
    @Autowired
    public LLMRelatedEasyData(IEasyDataService easyDataService) {
        super(easyDataService, null);
        LLMRelatedEasyData.easyDataService = easyDataService;
    }

    /**
     * 创建指定命名空间的 LLM 相关 EasyData 入口。
     *
     * @param namespace EasyData 命名空间。
     */
    private LLMRelatedEasyData(String namespace) {
        super(easyDataService, namespace);
    }

    /**
     * 获取当前 EasyData 对应的数据库实体类型。
     *
     * @return LLM 相关 EasyData 实体类型。
     */
    @Override
    public Class<? extends BaseEasyDataObject> getEntityClass() {
        return LLMRelatedEasyDataObject.class;
    }

    /**
     * 获取指定命名空间的 LLM 相关 EasyData 入口。
     *
     * @param namespace EasyData 命名空间，例如 {@code memory}。
     * @return LLM 相关 EasyData 入口。
     */
    public static LLMRelatedEasyData of(String namespace) {
        return new LLMRelatedEasyData(namespace);
    }
}
