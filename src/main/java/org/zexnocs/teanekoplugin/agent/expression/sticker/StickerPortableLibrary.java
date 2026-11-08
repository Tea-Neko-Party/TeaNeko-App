package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 低频导入原图及便携描述；文件解码在事务外完成，最终入库重查删除、占用与容量。
 * <br>2026/11/07：复制描述不会覆盖本库管理决定，也不会触发模型分析。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.8.7-l
 * @version 4.9.0-l
 */
final class StickerPortableLibrary {
    private final IStickerCatalog catalog;
    private final StickerMetadataFiles metadata;

    /**
     * @param catalog 原目录事务端口
     * @param metadata 所属目录的文件帮助对象
     */
    StickerPortableLibrary(IStickerCatalog catalog, StickerMetadataFiles metadata) {
        this.catalog = catalog;
        this.metadata = metadata;
    }

    /**
     * 在宿主虚拟线程执行，所有等待均等待真实事务结果，不猜测取消后的提交状态。
     * @param settings 固定配置
     * @param assets 实际文件后端
     * @param policy 当前准入策略
     * @param control 当前期限
     * @param verify 当前配置与扩展代次
     * @return 实际导入数和至多 32 条失败原因
     */
    IStickerCatalog.Maintenance maintain(StickerSettings settings, IStickerAssetStore assets,
                                         IStickerAdmissionPolicy policy, ExecutionControl control, Runnable verify) {
        String cursor = catalog.change(settings, state -> {
            verify.run();
            control.checkActive();
            return state.getImportCursor();
        }).toCompletableFuture().join();
        var names = metadata.scan(settings, cursor);
        var candidates = catalog.transact(settings, transaction -> {
            verify.run();
            control.checkActive();
            return names.stream().filter(hash -> !transaction.state().getEntries().containsKey(hash)
                    && transaction.deletedByHash(hash).isEmpty()).toList();
        }).toCompletableFuture().join();
        var validated = new ArrayList<StickerMetadataFiles.Portable>();
        var warnings = new ArrayList<String>();
        for (var hash : candidates) {
            verify.run();
            control.checkActive();
            try {
                var item = metadata.portable(settings, hash);
                var prepared = assets.preview(item.blob(), settings, control);
                if (!prepared.blob().equals(item.blob())) {
                    throw new IllegalArgumentException("原图的格式、尺寸或摘要与便携描述不一致。");
                }
                validated.add(item);
            } catch (RuntimeException failure) {
                control.checkActive();
                warnings.add("素材=" + hash + "；便携描述或原图校验未通过。" + FailureDiagnostic.from(failure));
            }
        }
        return catalog.transact(settings, transaction -> {
            verify.run();
            control.checkActive();
            var state = transaction.state();
            var now = Instant.now();
            // 后台租约可在文件校验期间被其他副本取得；不越过占用导入或推进游标。
            if (state.getWorkerUntil().isAfter(now) || !state.getImportCursor().equals(cursor)) {
                return new IStickerCatalog.Maintenance(0, List.copyOf(warnings));
            }
            var notices = new ArrayList<>(warnings);
            int imported = 0;
            for (var item : validated) {
                String hash = item.blob().hash();
                if (state.getEntries().containsKey(hash) || transaction.deletedByHash(hash).isPresent()) {
                    continue;
                }
                long bytes = state.getEntries().values().stream().map(Stickers.Entry::getBlob)
                        .filter(Objects::nonNull).mapToLong(Stickers.Blob::bytes).sum()
                        + state.getJobs().values().stream().mapToLong(Stickers.Job::getReserved).sum();
                if (StickerLibrary.cacheSize(state) >= 2048 || bytes + item.blob().bytes() > settings.maxStorageBytes()) {
                    notices.add("素材=" + hash + "；图库条目或磁盘预算不足，扩容后下次巡检重试导入。");
                    continue;
                }
                var active = state.getEntries().values().stream()
                        .filter(entry -> entry.getState() == Stickers.EntryStatus.ACTIVE).toList();
                long categoryCount = active.stream().filter(entry -> entry.getAnalysis() != null
                        && entry.getAnalysis().primaryCategory().equals(item.analysis().primaryCategory())).count();
                boolean enabled = item.enabled() && active.size() < settings.maxActive()
                        && categoryCount < settings.maxPerCategory() && item.analysis().kind() == Stickers.Kind.STICKER
                        && settings.categories().containsKey(item.analysis().primaryCategory())
                        && item.analysis().confidence() >= settings.minConfidence()
                        && item.analysis().quality() >= settings.minQuality() && policy.accepts(item.analysis(), settings);
                var entry = new Stickers.Entry();
                entry.setHash(hash);
                entry.setId("stk_" + StableDigest.sha256(settings.catalogId(), hash).substring(0, 32));
                entry.setBlob(item.blob());
                entry.setAnalysis(item.analysis());
                entry.setAnalysisVersion(item.analysisVersion());
                entry.setState(enabled ? Stickers.EntryStatus.ACTIVE : Stickers.EntryStatus.DISABLED);
                entry.setCreated(now);
                entry.setActivatedAt(now);
                entry.setCachedUntil(now.plus(Duration.ofDays(30)));
                state.getEntries().put(hash, entry);
                state.setRevision(state.getRevision() + 1);
                imported++;
            }
            state.setImportCursor(names.size() == 32 ? names.getLast() : "");
            return new IStickerCatalog.Maintenance(imported, List.copyOf(notices));
        }).toCompletableFuture().join();
    }
}
