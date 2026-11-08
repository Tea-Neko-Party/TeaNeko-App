package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.jspecify.annotations.Nullable;

/**
 * 待发布的便携描述引用，不在数据库中重复存储描述正文。
 * 仅从已提交引用生成旁车文件；空文件元数据表示移除旁车，不删除原素材。
 * <br>2026/11/07：把文件发布意图与目录变更共同提交，下一次目录访问恢复发布。
 * @param blob 原素材元数据，空表示撤销便携条目
 * @param analysisRef 不可变本地描述的摘要
 * @param analysisVersion 描述协议版本
 * @param enabled 是否可以作为已启用素材迁移
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.8.7-l
 * @version 4.9.0-l
 */
public record StickerPortableReference(Stickers.@Nullable Blob blob, String analysisRef, int analysisVersion,
                                       boolean enabled) {
    /**
     * @param entry 当前条目，描述引用已安全保存
     * @return 可复制项或撤销标记；缓存、待删除和丢失素材不能导入
     */
    static StickerPortableReference from(Stickers.Entry entry) {
        boolean portable = entry.getBlob() != null && !entry.getAnalysisRef().isEmpty()
                && (entry.getState() == Stickers.EntryStatus.ACTIVE || entry.getState() == Stickers.EntryStatus.DISABLED);
        return new StickerPortableReference(portable ? entry.getBlob() : null,
                portable ? entry.getAnalysisRef() : "", portable ? entry.getAnalysisVersion() : 0,
                portable && entry.getState() == Stickers.EntryStatus.ACTIVE);
    }
}
