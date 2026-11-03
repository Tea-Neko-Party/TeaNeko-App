package org.zexnocs.teanekoagent.sticker;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
/**
 * 采集决策与提交后观察的宿主端口，不允许监听器代替最终事务校验。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
 */
public interface IStickerLifecycle {
    /**
     * @param source 固定真实候选
     * @param settings 本批配置
     * @return 有界建议或不可撤销拒绝
     */
    CompletionStage<Decision> before(Stickers.Source source,StickerSettings settings);
    /** @param notice 实际提交后的脱敏通知 */
    void committed(Notice notice);
    /**
     * 监听器只能提出提示或拒绝，不能提供实际收藏成功回执。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * @param allowed 是否允许继续
     * @param hint 低信任识别提示
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.2-l
     */
    record Decision(boolean allowed,String hint){}
    /**
     * 只反映已经提交的变化，允许丢失，不作为持久审计或公开订阅授权。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * @param catalogId 实例库
     * @param kind QUEUED、COLLECTION_COMPLETED 或 MANAGEMENT
     * @param operationId 作业或管理操作 ID
     * @param entryId 关联素材，没有时为空
     * @param catalogRevision 目录版本
     * @param at 提交观察时间
     * @param status 固定状态或动作名称
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.2-l
     */
    record Notice(String catalogId,String kind,UUID operationId,String entryId,long catalogRevision,Instant at,String status){}
}
