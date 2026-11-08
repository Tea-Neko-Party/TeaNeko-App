package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.jspecify.annotations.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 根事务内的有界状态与独立删除目录访问，不得逃逸回调或执行网络、文件操作。
 * 默认方法保留独立宿主的内嵌目录语义，生产存储覆盖为按键和分页访问。
 * <br>2026/11/05：删除决定独立扩容，仍参加同一根锁和管理版本仲裁。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
@FunctionalInterface
public interface IStickerCatalogTransaction {
    /** @return 当前事务拥有的有界状态 */
    Stickers.State state();

    /**
     * @param hash 已验证的文件摘要
     * @return 已删除且无文件的决定
     */
    default Optional<Stickers.Entry> deletedByHash(String hash) {
        return Optional.ofNullable(state().getEntries().get(hash)).filter(IStickerCatalogTransaction::archivable);
    }

    /**
     * @param id 公开标识
     * @return 已回收的原删除决定及版本
     */
    default Optional<Stickers.Entry> deletedById(String id) {
        return state().getEntries().values().stream().filter(IStickerCatalogTransaction::archivable)
                .filter(entry -> entry.getId().equals(id)).findFirst();
    }

    /**
     * 显式恢复时移除独立拒绝决定，调用方同时更新版本与有界状态。
     * @param entry 已复核版本的原删除项
     */
    default void restoreDeleted(Stickers.Entry entry) {
        // 2026/11/05：旧宿主使用同一 Entry，没有第二份需要移除的索引。
    }

    /** @return 已回收文件的持久删除决定数 */
    default long deletedCount() {
        return state().getEntries().values().stream().filter(IStickerCatalogTransaction::archivable).count();
    }

    /** @return 当前主目录 JSON 字符数，替换存储未提供时为 -1 */
    default long catalogCharacters() { return -1; }

    /** @return 主目录字符上限，替换存储未提供时为 -1 */
    default long catalogCharacterLimit() { return -1; }

    /**
     * 返回有界目录页，不加载完整删除历史。
     * @param query 固定筛选
     * @return 页面与真实继续游标
     */
    default Page page(StickerManagement.Query query) {
        var values = state().getEntries().values().stream().filter(entry -> matches(entry, query))
                .sorted(Comparator.comparing(Stickers.Entry::getId)).limit(query.limit() + 1L).toList();
        var items = values.stream().limit(query.limit()).toList();
        return new Page(items, values.size() > query.limit() ? items.getLast().getId() : "");
    }

    /**
     * @param entry 权威条目
     * @param query 请求
     * @return 是否符合共同筛选合同
     */
    static boolean matches(Stickers.Entry entry, StickerManagement.Query query) {
        return entry.getId().compareTo(query.after()) > 0 && (query.state() == null || entry.getState() == query.state())
                && (query.category().isEmpty() || entry.getAnalysis() != null
                && entry.getAnalysis().primaryCategory().equals(query.category()));
    }

    /**
     * @param entry 原条目
     * @return 已完成字节回收且没有读占用时为真
     */
    static boolean archivable(Stickers.@Nullable Entry entry) {
        return entry != null && entry.getState() == Stickers.EntryStatus.DELETED && entry.getBlob() == null
                && !entry.isDeleting() && entry.getReaders().isEmpty();
    }

    /**
     * 一次有界合并页，空页可以仍有继续游标。
     * @param items 权威条目
     * @param next 后继游标，空表示扫描结束
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.3-l
     * @version 4.9.0-l
     */
    record Page(List<Stickers.Entry> items, String next) { }
}
