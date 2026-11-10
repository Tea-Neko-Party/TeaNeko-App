package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.concurrent.CompletionStage;
/**
 * 单次受限视觉任务，不借用用户权限、聊天记忆或命令工具。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * <br>2026/11/10：声明最多模型调用次数与备用请求的追加额度预约。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.1-l
 */
public interface IStickerAnalyzer {
    /**
     * @param source 固定来源
     * @param settings 当前配置
     * @return 是否已有可用视觉路由
     */
    boolean ready(Stickers.Source source, StickerSettings settings);
    /**
     * 一轮同时识别类型、用途及分类。
     * @param source 固定来源
     * @param prepared 已验证预览
     * @param settings 固定预算
     * @param control 借用控制
     * @return 结构化判定
     */
    CompletionStage<Stickers.Analysis> analyze(Stickers.Source source,IStickerAssetStore.Prepared prepared,StickerSettings settings,ExecutionControl control);

    /**
     * 2026/11/10：备用请求需要单独取得持久额度，旧分析器保持单次调用。
     * @param source 固定来源
     * @param prepared 受控预览
     * @param settings 固定配置
     * @param control 借用控制
     * @param additionalCall 追加模型调用前的持久额度预约
     * @return 实际业务结束阶段
     */
    default CompletionStage<Stickers.Analysis> analyze(Stickers.Source source, IStickerAssetStore.Prepared prepared,
            StickerSettings settings, ExecutionControl control,
            java.util.function.Supplier<CompletionStage<Boolean>> additionalCall) {
        return analyze(source, prepared, settings, control);
    }

    /**
     * @param source 固定来源
     * @param settings 当前配置
     * @return 2026/11/10：本作业最多实际请求次数，旧分析器为一次
     */
    default int maxModelCalls(Stickers.Source source, StickerSettings settings) {
        return 1;
    }
}
