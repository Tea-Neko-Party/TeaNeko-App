package org.zexnocs.teanekoagent.component.sticker;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataTransactionDto;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * 专用 EasyData 中按部署实例锁定有界目录，独立于记忆清空与人格切换。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：已回收删除条目逐行存储，按指纹与公开标识索引，旧目录迁移与原操作共同提交。
 * <br>2026/11/06：分流终态作业与短期管理回执，待执行和待清理状态继续持久保存。
 * <br>2026/11/07：描述迁入不可变本地文件，事务仅保存引用；便携发布意图随目录共同提交。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.7-l
 */
@Component
@TeaNekoStickerCatalog("easydata")
public final class EasyDataStickerCatalog implements IStickerCatalog {
    private static final int MAX_CHARACTERS = 8 * 1024 * 1024;
    private final TeaNekoAgentEasyData data;
    private final ObjectMapper mapper;
    private final StickerMetadataFiles metadata;

    /**
     * @param data 专用入口
     * @param mapper 原转换器
     */
    public EasyDataStickerCatalog(TeaNekoAgentEasyData data, ObjectMapper mapper) {
        this.data = data.inNamespace("agent.sticker.v1");
        this.mapper = mapper;
        // 2026/11/07：文件帮助对象由目录独占，不是另一个 Spring 服务或扫描器。
        this.metadata = new StickerMetadataFiles(mapper);
    }

    /**
     * 2026/11/05：原状态回调复用同一迁移及删除仲裁，不写回无限墓碑主行。
     * @param settings 固定配置
     * @param action 原短事务
     * @param <T> 非空结果
     * @return 实际提交结果
     */
    @Override public <T> CompletionStage<T> change(StickerSettings settings, Function<Stickers.State, T> action) {
        return transact(settings, transaction -> action.apply(transaction.state()));
    }

    /**
     * 迁移、独立删除记录和主目录共用根锁；失败时全部回滚，不发布半迁移状态。
     * @param settings 固定配置
     * @param action 短事务，不得借走事务访问器
     * @param <T> 非空结果
     * @return 实际提交结果
     * @throws IllegalStateException 当主目录超限或索引不一致时
     */
    @Override public <T> CompletionStage<T> transact(StickerSettings settings, Function<StickerCatalogTransaction, T> action) {
        return data.transaction("更新实例表情包目录", () -> {
            var dto = data.get("catalog:" + settings.catalogId()).atomic();
            var row = dto.find("state", EasyDataLock.WRITE);
            var state = row == null ? new Stickers.State() : mapper.readValue(row.getValue(), Stickers.State.class);
            // 2026/11/07：只发布上一次已提交的意图；本次回滚也不会导出未提交的描述。
            if (!state.getMetadataIdentity().isEmpty() || !state.getPendingPortable().isEmpty()) {
                bindMetadata(settings, state);
            }
            state.getPendingPortable().forEach((hash, reference) -> metadata.publish(settings, hash, reference));
            state.getPendingPortable().clear();
            var before = new HashMap<String, StickerPortableReference>();
            state.getEntries().forEach((hash, entry) -> {
                before.put(hash, StickerPortableReference.from(entry));
                metadata.hydrate(settings, entry);
            });
            // 2026/11/06：根锁同时保护两表，已结束作业和短期管理回执仅留在可清理表。
            var observations = data.cleanable().get("catalog:" + settings.catalogId()).atomic();
            var observedRow = observations.find("observations", EasyDataLock.WRITE);
            if (observedRow != null) {
                var observed = mapper.readValue(observedRow.getValue(), Observations.class);
                observed.operations().forEach((id, operation) -> {
                    var previous = state.getOperations().putIfAbsent(id, operation);
                    if (previous != null && !previous.equals(operation)) {
                        throw new IllegalStateException("图库管理回执在迁移时发生冲突。");
                    }
                });
                observed.completedJobs().forEach((id, job) -> {
                    if (job.requiresRecovery() || state.getJobs().containsKey(id)) {
                        throw new IllegalStateException("图库短期记录不能包含待恢复或重复的作业。");
                    }
                    state.getJobs().put(id, job);
                });
            }
            var transaction = new Transaction(settings, state);
            transaction.archive();
            var result = Objects.requireNonNull(action.apply(transaction));
            // 2026/11/07：先落不可变描述，后写引用；旁车只排队，失败事务不会发布新版本。
            state.getEntries().forEach((hash, entry) -> {
                if (entry.getAnalysis() != null && state.getMetadataIdentity().isEmpty()) {
                    bindMetadata(settings, state);
                }
                metadata.persist(settings, entry);
                var next = StickerPortableReference.from(entry);
                var prior = before.remove(hash);
                if (!next.equals(prior) && (next.blob() != null || prior != null && prior.blob() != null)) {
                    state.getPendingPortable().put(hash, next);
                }
            });
            before.forEach((hash, prior) -> {
                if (prior.blob() != null) {
                    state.getPendingPortable().put(hash, new StickerPortableReference(null, "", 0, false));
                }
            });
            transaction.archive();
            var completed = new LinkedHashMap<UUID, Stickers.Job>();
            state.getJobs().forEach((id, job) -> {
                if (!job.requiresRecovery()) {
                    completed.put(id, job);
                }
            });
            var observedJson = mapper.writeValueAsString(new Observations(state.getOperations(), completed));
            if (state.getOperations().isEmpty() && completed.isEmpty()) {
                if (observedRow != null) {
                    observations.delete(observedRow);
                }
            } else if (observedRow == null) {
                observations.save(observations.create("observations", observedJson));
            } else if (!observedRow.getValue().equals(observedJson)) {
                observedRow.setValue(observedJson);
                observations.save(observedRow);
            }
            var json = durableJson(state);
            if (json.length() > MAX_CHARACTERS) {
                throw new IllegalStateException("表情包活动目录超过持久容量上限；删除记录已使用独立行存储。");
            }
            if (row == null) {
                dto.save(dto.create("state", json));
            } else if (!row.getValue().equals(json)) {
                row.setValue(json);
                dto.save(row);
            }
            return result;
        });
    }

    /**
     * 序列化持久部分，业务回调仍看到统一目录；旧主行中的短期内容随本次提交迁出。
     * @param state 原业务视图
     * @return 仅含长期状态和恢复数据的 JSON
     */
    private String durableJson(Stickers.State state) {
        var json = (tools.jackson.databind.node.ObjectNode) mapper.valueToTree(state);
        // 2026/11/07：不改变回调视图，但数据库不再保存任何条目的描述正文。
        if (json.get("entries") instanceof tools.jackson.databind.node.ObjectNode entries) {
            entries.properties().forEach(entry -> ((tools.jackson.databind.node.ObjectNode) entry.getValue()).remove("analysis"));
        }
        json.remove("operations");
        if (json.get("jobs") instanceof tools.jackson.databind.node.ObjectNode jobs) {
            state.getJobs().forEach((id, job) -> {
                if (!job.requiresRecovery()) {
                    jobs.remove(id.toString());
                }
            });
        }
        return mapper.writeValueAsString(json);
    }

    /**
     * @param settings 当前目录
     * @param state 本事务状态
     * @throws IllegalStateException 当只迁移数据库却没有共享原描述卷时
     */
    private void bindMetadata(StickerSettings settings, Stickers.State state) {
        String identity = StickerStoragePaths.identity(settings);
        if (!state.getMetadataIdentity().isEmpty() && !state.getMetadataIdentity().equals(identity)) {
            throw new IllegalStateException("表情包描述卷身份不一致；恢复数据库时须一并恢复完整素材目录。");
        }
        state.setMetadataIdentity(identity);
    }

    /**
     * @param settings 固定目录
     * @param state 根锁内的状态
     * @param entry 待归档条目
     * @return 不含描述正文的删除记录
     */
    private String entryJson(StickerSettings settings, Stickers.State state, Stickers.Entry entry) {
        if (entry.getAnalysis() != null && state.getMetadataIdentity().isEmpty()) {
            bindMetadata(settings, state);
        }
        metadata.persist(settings, entry);
        var json = (tools.jackson.databind.node.ObjectNode) mapper.valueToTree(entry);
        json.remove("analysis");
        return mapper.writeValueAsString(json);
    }

    /**
     * 旧删除记录每批迁移至多 32 行，图片校验及目录扫描留在事务外。
     * @param settings 固定配置
     * @param assets 文件后端
     * @param policy 准入策略
     * @param control 宿主控制
     * @param verify 实时配置及插件复核
     * @return 实际导入统计
     */
    @Override
    public Maintenance maintain(StickerSettings settings, IStickerAssetStore assets, IStickerAdmissionPolicy policy,
                                ExecutionControl control, Runnable verify) {
        change(settings, state -> {
            verify.run();
            control.checkActive();
            var deleted = data.get("deleted:" + settings.catalogId()).atomic();
            var rows = deleted.scan(state.getDeletedMetadataCursor(), null, 32, EasyDataLock.WRITE);
            for (var row : rows) {
                var entry = mapper.readValue(row.getValue(), Stickers.Entry.class);
                metadata.hydrate(settings, entry);
                String json = entryJson(settings, state, entry);
                if (!json.equals(row.getValue())) {
                    row.setValue(json);
                    deleted.save(row);
                }
            }
            state.setDeletedMetadataCursor(rows.size() == 32 ? rows.getLast().getKey() : "");
            return true;
        }).toCompletableFuture().join();
        return new StickerPortableLibrary(this, metadata).maintain(settings, assets, policy, control, verify);
    }

    /**
     * 一天内供用户复查的观察，不承担执行权；清理后版本检查仍可阻止旧管理请求重放。
     * <br>2026/11/06：资源尚未释放的作业不得进入此类型。
     * @param operations 管理回执
     * @param completedJobs 已结束且已完成清理的作业
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.5-l
     * @version 4.8.5-l
     */
    private record Observations(Map<UUID, StickerManagement.Operation> operations,
                                Map<UUID, Stickers.Job> completedJobs) {}

    /**
     * 根锁下的删除索引访问，不另开事务；条目分行保留本地描述引用和恢复版本。
     * <br>2026/11/06：目录容量按持久部分计量，不包含可清理观察。
     * <br>2026/11/07：删除记录按键恢复本地描述，旧内联正文在原事务中迁出。
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.3-l
     * @version 4.8.7-l
     */
    private final class Transaction implements StickerCatalogTransaction {
        private final Stickers.State state;
        private final IEasyDataTransactionDto deleted;
        private final IEasyDataTransactionDto ids;
        private final StickerSettings settings;

        /**
         * @param settings 已锁根目录的实例
         * @param state 根行状态
         */
        private Transaction(StickerSettings settings, Stickers.State state) {
            this.state = state;
            this.settings = settings;
            deleted = data.get("deleted:" + settings.catalogId()).atomic();
            ids = data.get("deleted-ids:" + settings.catalogId()).atomic();
        }

        /** @return 本事务根状态 */
        @Override public Stickers.State state() { return state; }

        /**
         * @param hash 指纹
         * @return 当前权威拒绝决定
         */
        @Override public Optional<Stickers.Entry> deletedByHash(String hash) {
            var row = deleted.find(hash, EasyDataLock.READ);
            if (row == null) {
                return Optional.empty();
            }
            // 2026/11/07：旧墓碑按键访问时迁出描述，仍由同一个目录根锁仲裁。
            var entry = mapper.readValue(row.getValue(), Stickers.Entry.class);
            metadata.hydrate(settings, entry);
            String json = entryJson(settings, state, entry);
            if (!row.getValue().equals(json)) {
                row.setValue(json);
                deleted.save(row);
            }
            return Optional.of(entry);
        }

        /**
         * @param id 公开标识
         * @return 经权威回查的原条目
         * @throws IllegalStateException 当持久索引缺失或错配时
         */
        @Override public Optional<Stickers.Entry> deletedById(String id) {
            var row = ids.find(id, EasyDataLock.READ);
            if (row == null) {
                return Optional.empty();
            }
            var entry = deletedByHash(row.getValue()).orElseThrow(() -> new IllegalStateException("表情包删除索引缺少权威记录。"));
            if (!entry.getId().equals(id)) {
                throw new IllegalStateException("表情包删除索引标识不一致。");
            }
            return Optional.of(entry);
        }

        /**
         * 原版本显式恢复后才移除拒绝记录，外层同时保存有界状态和回执。
         * @param entry 已核对的原条目
         * @throws IllegalStateException 当删除记录版本或计数不一致时
         */
        @Override public void restoreDeleted(Stickers.Entry entry) {
            var row = deleted.find(entry.getHash(), EasyDataLock.WRITE);
            if (row == null) {
                return;
            }
            var original = mapper.readValue(row.getValue(), Stickers.Entry.class);
            if (!original.getId().equals(entry.getId()) || original.getRevision() != entry.getRevision()
                    || state.getDeletedDecisions() < 1) {
                throw new IllegalStateException("表情包删除决定已变化，请重新读取。");
            }
            var index = ids.find(entry.getId(), EasyDataLock.WRITE);
            if (index == null || !index.getValue().equals(entry.getHash())) {
                throw new IllegalStateException("表情包删除索引缺失或错配。");
            }
            deleted.delete(row);
            ids.delete(index);
            state.setDeletedDecisions(state.getDeletedDecisions() - 1);
        }

        /** @return 独立删除行的持久计数，不加载完整历史 */
        @Override public long deletedCount() { return state.getDeletedDecisions(); }

        /** 2026/11/06：容量只计算持久主目录，不计已分流的短期观察。 @return 持久 JSON 字符数 */
        @Override public long catalogCharacters() { return durableJson(state).length(); }

        /** @return 原主目录字符上限，独立删除行不计入此项 */
        @Override public long catalogCharacterLimit() { return MAX_CHARACTERS; }

        /**
         * 有界活动目录与至多 128 个删除索引合并，稀疏筛选也返回扫描游标。
         * @param query 固定过滤
         * @return 不跳过另一目录条目的有界页面
         */
        @Override public Page page(StickerManagement.Query query) {
            if (query.state() != null && query.state() != Stickers.EntryStatus.DELETED) {
                return StickerCatalogTransaction.super.page(query);
            }
            var indices = ids.scan(query.after(), null, 128, EasyDataLock.READ);
            boolean more = indices.size() == 128;
            String scannedThrough = more ? indices.getLast().getKey() : "";
            var values = new ArrayList<Stickers.Entry>();
            for (var index : indices) {
                var entry = deletedById(index.getKey()).orElseThrow();
                if (StickerCatalogTransaction.matches(entry, query)) {
                    values.add(entry);
                }
            }
            state.getEntries().values().stream().filter(entry -> !more || entry.getId().compareTo(scannedThrough) <= 0)
                    .filter(entry -> StickerCatalogTransaction.matches(entry, query)).forEach(values::add);
            values.sort(Comparator.comparing(Stickers.Entry::getId));
            var page = values.stream().limit(query.limit()).toList();
            String next = values.size() > query.limit() ? page.getLast().getId() : scannedThrough;
            return new Page(page, next);
        }

        /**
         * 开始迁移旧数据，结束归档新删除；原描述与公开版本保留，重复迁移不增加计数。
         * @throws IllegalStateException 当同指纹记录的身份或版本冲突时
         */
        private void archive() {
            var iterator = state.getEntries().values().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (!StickerCatalogTransaction.archivable(entry)) {
                    continue;
                }
                var row = deleted.find(entry.getHash(), EasyDataLock.WRITE);
                if (row == null) {
                    deleted.save(deleted.create(entry.getHash(), entryJson(settings, state, entry)));
                    state.setDeletedDecisions(Math.addExact(state.getDeletedDecisions(), 1));
                } else {
                    var prior = mapper.readValue(row.getValue(), Stickers.Entry.class);
                    if (!prior.getId().equals(entry.getId()) || prior.getRevision() > entry.getRevision()) {
                        throw new IllegalStateException("表情包删除记录身份或版本冲突，未覆盖原决定。");
                    }
                    var json = entryJson(settings, state, entry);
                    if (!row.getValue().equals(json)) {
                        row.setValue(json);
                        deleted.save(row);
                    }
                }
                var index = ids.find(entry.getId(), EasyDataLock.WRITE);
                if (index == null) {
                    ids.save(ids.create(entry.getId(), entry.getHash()));
                } else if (!index.getValue().equals(entry.getHash())) {
                    throw new IllegalStateException("表情包删除标识发生冲突。");
                }
                iterator.remove();
            }
        }
    }
}
