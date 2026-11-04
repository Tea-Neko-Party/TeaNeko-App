package org.zexnocs.teanekoapp.web.files;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekocore.file_config.FileConfigDocument;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * 文件配置管理应用边界：字段白名单、版本保存、持久恢复和模块确认分别处理。
 * 不管理任意路径，不把磁盘与运行状态假定为同一个事务。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：受限机器令牌用例复用相同的文件回执恢复规则。
 * <br>2026/11/04：专用与自动文件投影共享原文档事务和授权，不自动允许新发现的类型。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.1-l
 */
@Service
public final class FileSettingsService {
    private static final String ACTOR = StableDigest.sha256("web.local-admin");
    private final FileSettingsDirectory adapters;
    private final IFileConfigService files;
    private final PluginRegistry plugins;
    private final FileConfigOperations operations;
    private final Executor executor;
    private final Semaphore slots = new Semaphore(16);

    /**
     * @param adapters 专用与自动投影目录
     * @param files 原配置入口
     * @param plugins 原插件代次
     * @param operations 私有持久账本
     * @param executor 借用文件业务执行域
     */
    public FileSettingsService(FileSettingsDirectory adapters, IFileConfigService files, PluginRegistry plugins,
            FileConfigOperations operations, @Qualifier("virtualExecutor") Executor executor) {
        this.files = files;
        this.plugins = plugins;
        this.operations = operations;
        this.executor = executor;
        this.adapters = adapters;
    }

    /**
     * @param guard 当前会话
     * @return 本地明确允许且所属插件启用的区
     */
    public List<FileSettings.Section> list(Runnable guard) {
        guard.run();
        return adapters.entries().keySet().stream().sorted().filter(this::allowed).map(id -> {
            var value = adapters.require(id).bean();
            return new FileSettings.Section(id, value.name(), value.application());
        }).toList();
    }

    /**
     * @param id 原区标识
     * @param guard 当前会话
     * @return 未展开、脱敏的文件表单及实际运行代次
     */
    public CompletionStage<FileSettings.View> read(String id, Runnable guard) {
        return run(id, guard, (lease, document) -> {
            var snapshot = document.read();
            var tree = document.tree(snapshot);
            if (tree == null || !tree.isObject()) {
                throw new IllegalArgumentException("文件根节点必须是对象。");
            }
            var sizes = new LinkedHashMap<String, Integer>();
            for (var collection : lease.adapter().collections()) {
                var array = tree.at(collection.path());
                if (!array.isMissingNode() && (!array.isArray() || array.size() > collection.maximum())) {
                    throw new IllegalArgumentException("配置集合类型或数量无效。");
                }
                sizes.put(collection.path(), array.isArray() ? array.size() : 0);
            }
            return new FileSettings.View(id, lease.adapter().name(), snapshot.revision(), snapshot.exists(),
                    lease.adapter().application(), document.runtime(),
                    FileSettingsPolicy.fields(tree, lease.adapter(), snapshot.exists()), lease.adapter().collections(), sizes);
        });
    }

    /**
     * @param id 原配置区
     * @param change 原版本及候选字段
     * @param guard 当前会话
     * @return 仅表示预检通过，不保存或发布任何配置
     */
    public CompletionStage<Map<String, String>> validate(String id, FileSettings.Change change, Runnable guard) {
        return run(id, guard, (lease, document) -> {
            candidate(document, lease, change);
            return Map.of("status", "VALID", "message", lease.adapter().validationNote());
        });
    }

    /**
     * @param id 原区
     * @param change 固定版本和完整原请求
     * @param guard 当前会话
     * @return 真实磁盘回执；保存不发布运行缓存
     */
    public CompletionStage<FileSettings.Receipt> save(String id, FileSettings.Change change, Runnable guard) {
        return run(id, guard, (lease, document) -> operations.locked(journal -> {
            String fingerprint = journal.fingerprint(List.of("SAVE", id, change));
            var previous = journal.find(change.operationId());
            if (previous != null) {
                verify(previous, id, fingerprint);
                if (!previous.receipt().status().equals("PREPARED")
                        || !document.read().revision().equals(previous.expected())) {
                    return recover(journal, change.operationId(), previous, document);
                }
            }
            byte[] bytes = candidate(document, lease, change);
            Instant now = previous == null ? Instant.now() : previous.receipt().at();
            var paths = change.edits().stream().map(FileSettings.Edit::path).sorted().toList();
            try (var prepared = document.prepare(bytes)) {
                var receipt = new FileSettings.Receipt(change.operationId(), "PREPARED", prepared.revision(),
                        document.runtime(), "原请求已登记，磁盘替换结果尚未确认。", paths, now, false);
                var entry = new FileConfigOperations.Entry(ACTOR, id, fingerprint, change.expectedRevision(),
                        prepared.revision(), receipt);
                journal.put(change.operationId(), entry);
                prepared.commit(change.expectedRevision(), lease.guard());
                if (!document.read().revision().equals(prepared.revision())) {
                    return recover(journal, change.operationId(), entry, document);
                }
                receipt = new FileSettings.Receipt(change.operationId(), "SAVED", prepared.revision(), document.runtime(),
                        "文件已原子保存，运行缓存尚未应用。", paths, now, false);
                journal.put(change.operationId(), entry.with(receipt));
                return receipt;
            }
        }));
    }

    /**
     * @param id 原区
     * @param application 固定已保存版本的独立应用请求
     * @param guard 当前会话
     * @return 原运行发布与模块确认结果；失败可能已发布缓存，不能据此推定回滚
     */
    public CompletionStage<FileSettings.Receipt> apply(String id, FileSettings.Application application, Runnable guard) {
        return run(id, guard, (lease, document) -> operations.locked(journal -> {
            String fingerprint = journal.fingerprint(List.of("APPLY", id, application));
            var previous = journal.find(application.operationId());
            if (previous != null) {
                verify(previous, id, fingerprint);
                return recover(journal, application.operationId(), previous, document);
            }
            var snapshot = document.read();
            requireRevision(snapshot.revision(), application.expectedRevision());
            if (!snapshot.exists()) {
                throw new IllegalArgumentException("配置尚未保存，不能应用模板预览。");
            }
            lease.adapter().validateDocument(document.tree(snapshot));
            var candidate = document.parse(snapshot.content());
            validateCandidate(lease, candidate);
            Instant now = Instant.now();
            var receipt = new FileSettings.Receipt(application.operationId(), "APPLYING", snapshot.revision(), document.runtime(),
                    "文件应用已经开始，模块生效尚未确认。", List.of(), now, false);
            var entry = new FileConfigOperations.Entry(ACTOR, id, fingerprint, snapshot.revision(), snapshot.revision(), receipt);
            journal.put(application.operationId(), entry);
            if (lease.adapter().applicationFor(candidate).equals("RESTART")) {
                receipt = new FileSettings.Receipt(application.operationId(), "RESTART_REQUIRED", snapshot.revision(),
                        document.runtime(), "模块包含固定资源，需要重启；本次未发布运行缓存。", List.of(), now, false);
            } else {
                try {
                    var stamp = document.publish(snapshot.revision(), candidate, lease.guard());
                    String note = lease.adapter().verifyApplied(candidate);
                    lease.guard().run();
                    if (!document.runtime().equals(stamp) || !document.read().revision().equals(snapshot.revision())) {
                        throw new IllegalStateException("应用期间文件或运行快照已经变化。");
                    }
                    receipt = new FileSettings.Receipt(application.operationId(), "APPLIED", snapshot.revision(),
                            stamp, note, List.of(), now, false);
                } catch (RuntimeException failure) {
                    receipt = new FileSettings.Receipt(application.operationId(), "APPLY_FAILED", snapshot.revision(),
                            document.runtime(), "文件保留在磁盘；运行缓存可能已发布，但模块确认未完成。请检查当前状态后明确重新应用。",
                            List.of(), now, false);
                }
            }
            journal.put(application.operationId(), entry.with(receipt));
            return receipt;
        }));
    }

    /**
     * @param id 原配置区
     * @param operation 原编号
     * @param guard 当前会话
     * @return 原回执或根据真实文件版本恢复的明确结果
     */
    public CompletionStage<FileSettings.Receipt> operation(String id, UUID operation, Runnable guard) {
        return run(id, guard, (lease, document) -> operations.locked(journal -> {
            var entry = journal.find(operation);
            if (entry == null || !entry.actor().equals(ACTOR) || !entry.section().equals(id)) {
                throw new NoSuchElementException("文件配置操作回执不存在或已过期。");
            }
            return recover(journal, operation, entry, document);
        }));
    }

    /**
     * @param document 原文件能力
     * @param lease 当前模块与权限
     * @param change 原版本及修改
     * @return 未展开环境值的候选字节
     */
    private byte[] candidate(FileConfigDocument document, Lease lease, FileSettings.Change change) {
        var snapshot = document.read();
        requireRevision(snapshot.revision(), change.expectedRevision());
        if (change.edits().stream().mapToLong(edit -> edit.value() == null ? 0 : edit.value().toString().length()).sum() > 262144) {
            throw new IllegalArgumentException("字段修改总量超过二十六万字符上限。");
        }
        var tree = FileSettingsPolicy.edit(document.tree(snapshot), lease.adapter(), change.edits());
        lease.adapter().validateDocument(tree);
        byte[] bytes = document.encode(tree);
        if (bytes.length > FileConfigDocument.MAX_BYTES) {
            throw new IllegalArgumentException("配置候选超过大小上限。");
        }
        validateCandidate(lease, document.parse(bytes));
        return bytes;
    }

    /**
     * @param lease 当前模块
     * @param candidate 原解析结果
     */
    private void validateCandidate(Lease lease, org.zexnocs.teanekocore.file_config.api.IFileConfigData candidate) {
        try {
            lease.adapter().validate(candidate);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("模块配置校验失败，请检查字段说明和本地环境引用。");
        }
        lease.guard().run();
    }

    /**
     * @param actual 当前版本
     * @param expected 原预期
     */
    private static void requireRevision(String actual, String expected) {
        if (!actual.equals(expected)) {
            throw new ConcurrentModificationException("文件配置版本已经变化。");
        }
    }

    /**
     * @param entry 原账本
     * @param id 当前区
     * @param fingerprint 当前完整请求摘要
     */
    private static void verify(FileConfigOperations.Entry entry, String id, String fingerprint) {
        if (!entry.actor().equals(ACTOR) || !entry.section().equals(id) || !entry.fingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("文件操作编号已绑定其他动作、参数或主体。");
        }
    }

    /**
     * 2026/11/04：供同包受限文件用例复用，不扩大普通文件编辑白名单。
     * @param journal 原账本
     * @param operation 原编号
     * @param entry 原阶段
     * @param document 当前实际文档
     * @return 原结果或明确恢复状态
     */
    static FileSettings.Receipt recover(FileConfigOperations.Session journal, UUID operation,
            FileConfigOperations.Entry entry, FileConfigDocument document) {
        var result = entry.receipt();
        if (result.status().equals("PREPARED")) {
            String actual = document.read().revision();
            String status = actual.equals(entry.planned()) ? "SAVED" : actual.equals(entry.expected()) ? "NOT_SAVED" : "INDETERMINATE";
            result = new FileSettings.Receipt(operation, status, entry.planned(), document.runtime(),
                    status.equals("SAVED") ? "根据实际磁盘版本恢复确认保存；未宣称已经应用。"
                            : status.equals("NOT_SAVED") ? "原文件版本仍存在，可以使用完全相同请求重试。"
                            : "原文件与拟替换版本均不匹配，保存结果无法确认；禁止自动覆盖。",
                    result.fields(), result.at(), true);
            if (!status.equals("NOT_SAVED")) {
                journal.put(operation, entry.with(result));
            }
        } else if (result.status().equals("APPLYING")) {
            result = new FileSettings.Receipt(operation, "APPLY_FAILED", result.diskRevision(), document.runtime(),
                    "原应用确认发生中断；可能已发布缓存，不能据此判断回滚。请检查当前版本再明确应用。",
                    result.fields(), result.at(), true);
            journal.put(operation, entry.with(result));
        }
        return new FileSettings.Receipt(operation, result.status(), result.diskRevision(), result.runtime(), result.note(),
                result.fields(), result.at(), true);
    }

    /**
     * @param id 原区
     * @return 本地明确允许且插件启用
     */
    private boolean allowed(String id) {
        if (!files.requireCurrent(WebSecurityFileConfig.class).snapshot().fileSections().contains(id)) {
            return false;
        }
        var owner = AnnotatedElementUtils.findMergedAnnotation(adapters.require(id).type(), PluginOwned.class);
        return owner == null || plugins.isEnabled(owner.value());
    }

    /**
     * @param id 原区
     * @param session 原会话
     * @param action 锁内实际动作
     * @param <T> 结果类型
     * @return 在真实工作结束后完成的阶段
     */
    private <T> CompletionStage<T> run(String id, Runnable session, Work<T> action) {
        session.run();
        if (!allowed(id)) {
            throw new SecurityException("该文件配置未在本地开放或所属插件已关闭。");
        }
        var entry = adapters.require(id);
        var owner = AnnotatedElementUtils.findMergedAnnotation(entry.type(), PluginOwned.class);
        var token = owner == null ? null : plugins.capture(owner.value());
        Runnable checked = () -> {
            session.run();
            if (!allowed(id)) {
                throw new SecurityException("文件配置授权已经撤销。");
            }
            if (token != null) {
                plugins.check(token);
            }
        };
        if (!slots.tryAcquire()) {
            return CompletableFuture.failedStage(new IllegalStateException("文件管理操作繁忙，请稍后重试。"));
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                checked.run();
                T result = files.withDocument(entry.bean().type(), document -> action.apply(new Lease(entry.bean(), checked), document));
                checked.run();
                return result;
            }, executor).whenComplete((ignored, failure) -> slots.release()).minimalCompletionStage();
        } catch (RuntimeException failure) {
            slots.release();
            return CompletableFuture.failedStage(failure);
        }
    }

    /**
     * 仅在本次文件锁内使用的实现和授权。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param adapter 已声明模块
     * @param guard 当前能力
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record Lease(IFileSettingsAdapter adapter, Runnable guard) {}

    /**
     * 锁内短文件操作，不接收远程类名或路径。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param <T> 返回类型
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @FunctionalInterface
    private interface Work<T> {
        /**
         * @param lease 当前实现和能力
         * @param document 原文件能力
         * @return 实际结果
         */
        T apply(Lease lease, FileConfigDocument document);
    }
}
