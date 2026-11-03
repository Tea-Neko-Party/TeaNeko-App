package org.zexnocs.teanekoagent.sticker;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.concurrent.CompletionStage;
/**
 * 单次受限视觉任务，不借用用户权限、聊天记忆或命令工具。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
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
}
