package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * 实例目录原子事务端口；回调只操作有界状态，不执行文件或网络 I/O。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：扩展同根事务中的独立删除目录，兼容原内嵌存储宿主。
 * <br>2026/11/07：增加可选的低频便携目录维护，重文件校验由宿主虚拟线程执行。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
public interface IStickerCatalog {
    /**
     * 在实例唯一根行锁下读改写，实际提交后才返回结果。
     * @param settings 固定实例及容量
     * @param action 短事务领域操作
     * @param <T> 非空结果
     * @return 真实提交后的值
     */
    <T> CompletionStage<T> change(StickerSettings settings, Function<Stickers.State,T> action);

    /**
     * 2026/11/05：有界状态和独立删除决定须在同一根锁下提交。
     * @param settings 固定配置
     * @param action 不可逃逸的事务操作
     * @param <T> 非空结果
     * @return 真实提交后的值
     */
    default <T> CompletionStage<T> transact(StickerSettings settings, Function<IStickerCatalogTransaction, T> action) {
        return change(settings, state -> action.apply(() -> state));
    }

    /**
     * 只由低频维护调用，可等待真实数据库提交；没有本地交换能力的目录无需实现。
     * @param settings 固定配置
     * @param assets 实际素材验证端口
     * @param policy 当前准入策略
     * @param control 由宿主管理的期限与取消
     * @param verify 当前配置及扩展代次复核，不替代数据库版本检查
     * @return 本批导入统计及安全诊断
     */
    default Maintenance maintain(StickerSettings settings, IStickerAssetStore assets, IStickerAdmissionPolicy policy,
                                 ExecutionControl control, Runnable verify) {
        return new Maintenance(0, List.of());
    }

    /**
     * 一批有界导入结果，坏文件不阻止其他文件或永久卡住扫描游标。
     * @param imported 真正提交的新条目数
     * @param warnings 素材摘要与安全失败原因
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.8.7-l
     * @version 4.9.0-l
     */
    record Maintenance(int imported, List<String> warnings) {}
}
