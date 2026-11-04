package org.zexnocs.teanekoapp.web.files;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketHandler;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekocore.file_config.FileConfigDocument;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

/**
 * 只编辑认证文件中的机器令牌；复用原文件类型锁、原子替换及持久回执。
 * 应用前确认其他认证根字段与当前运行值一致，不顺带应用本机尚未确认的设置。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
@Service
public final class MachineTokenService {
    private static final String SECTION = "web.machine";
    private static final String ACTOR = StableDigest.sha256("web.local-admin");
    private final IFileConfigService files;
    private final ClientManagementDirectory clients;
    private final TeaNekoClientScanner scanner;
    private final FileConfigOperations operations;
    private final Executor executor;
    private final Semaphore slots = new Semaphore(16);

    /**
     * @param files 原文件配置入口
     * @param clients 已注册的平台及插件代次
     * @param scanner 实际机器 WebSocket 目录
     * @param operations 私有持久回执账本
     * @param executor 原虚拟线程执行域
     */
    public MachineTokenService(IFileConfigService files, ClientManagementDirectory clients,
            TeaNekoClientScanner scanner, FileConfigOperations operations,
            @Qualifier("virtualExecutor") Executor executor) {
        this.files = files;
        this.clients = clients;
        this.scanner = scanner;
        this.operations = operations;
        this.executor = executor;
    }

    /**
     * @param guard 当前管理员会话
     * @return 不含秘密的真实入口目录及磁盘、运行状态
     */
    public CompletionStage<MachineTokens.View> read(Runnable guard) {
        return run(guard, document -> {
            var snapshot = document.read();
            var disk = parse(document, snapshot).snapshot();
            var active = files.requireCurrent(WebSecurityFileConfig.class).snapshot();
            var values = clients.list().stream().filter(value -> machine(value.clientId()))
                    .map(value -> new MachineTokens.Client(value.clientId(), value.name(),
                            policy(disk.machineTokens(), value.clientId()), policy(active.machineTokens(), value.clientId())))
                    .toList();
            guard.run();
            return new MachineTokens.View(snapshot.revision(), document.runtime(), values);
        });
    }

    /**
     * @param client 已注册机器入口
     * @param guard 当前会话
     * @return 指定客户端的实际磁盘令牌，不包含其他字段
     */
    public CompletionStage<MachineTokens.Secret> reveal(String client, Runnable guard) {
        var checked = clientGuard(client, guard);
        return run(checked, document -> {
            var snapshot = document.read();
            String value = parse(document, snapshot).snapshot().machineTokens().get(client);
            if (value == null) {
                throw new NoSuchElementException("该客户端尚未配置机器令牌。");
            }
            checked.run();
            return new MachineTokens.Secret(snapshot.revision(), value);
        });
    }

    /**
     * @param client 已注册机器入口
     * @param change 固定原版本及原操作
     * @param guard 提交前复核会话
     * @return 只表示磁盘保存的真实回执
     */
    public CompletionStage<FileSettings.Receipt> save(String client, MachineTokens.Change change, Runnable guard) {
        var checked = clientGuard(client, guard);
        return run(checked, document -> operations.locked(journal -> {
            String section = SECTION + "." + client;
            // 2026/11/04：显式纳入秘密计算带密钥摘要，不能因 WRITE_ONLY 序列化而遗漏新值。
            String fingerprint = journal.fingerprint(List.of("SAVE", section, change.expectedRevision(),
                    change.action(), change.value() == null ? "" : change.value()));
            var previous = journal.find(change.operationId());
            if (previous != null) {
                verify(previous, section, fingerprint);
                if (!previous.receipt().status().equals("PREPARED")
                        || !document.read().revision().equals(previous.expected())) {
                    return FileSettingsService.recover(journal, change.operationId(), previous, document);
                }
            }
            var snapshot = document.read();
            requireRevision(snapshot.revision(), change.expectedRevision());
            var tree = document.tree(snapshot);
            if (!(tree instanceof ObjectNode root)) {
                throw new IllegalArgumentException("安全配置根节点必须是对象。");
            }
            var tokens = root.get("machine-tokens");
            if (tokens == null) {
                tokens = root.putObject("machine-tokens");
            }
            if (!(tokens instanceof ObjectNode object)) {
                throw new IllegalArgumentException("机器令牌配置必须是对象。");
            }
            switch (change.action()) {
                case "SET" -> object.put(client, change.value());
                case "OPEN" -> object.put(client, "");
                case "DENY" -> object.remove(client);
                default -> throw new IllegalArgumentException("机器令牌动作无效。");
            }
            byte[] bytes = document.encode(root);
            ((WebSecurityFileConfig) document.parse(bytes)).snapshot();
            Instant at = previous == null ? Instant.now() : previous.receipt().at();
            try (var prepared = document.prepare(bytes)) {
                var receipt = new FileSettings.Receipt(change.operationId(), "PREPARED", prepared.revision(),
                        document.runtime(), "原机器令牌请求已登记，保存结果尚待确认。", List.of(client), at, false);
                var entry = new FileConfigOperations.Entry(ACTOR, section, fingerprint, change.expectedRevision(),
                        prepared.revision(), receipt);
                journal.put(change.operationId(), entry);
                prepared.commit(change.expectedRevision(), checked);
                receipt = new FileSettings.Receipt(change.operationId(), "SAVED", prepared.revision(),
                        document.runtime(), "机器令牌已保存，尚未应用；客户端继续使用原运行策略。", List.of(client), at, false);
                journal.put(change.operationId(), entry.with(receipt));
                return receipt;
            }
        }));
    }

    /**
     * 只发布机器策略，发布后原管理员会话失效，但成功结果仍交还本次请求。
     * @param application 固定磁盘版本及应用编号
     * @param guard 发布前的真实会话
     * @return 应用回执，不能把会话换代误报为保存或应用失败
     */
    public CompletionStage<FileSettings.Receipt> apply(FileSettings.Application application, Runnable guard) {
        return run(guard, document -> operations.locked(journal -> {
            String fingerprint = journal.fingerprint(List.of("APPLY", SECTION, application));
            var previous = journal.find(application.operationId());
            if (previous != null) {
                verify(previous, SECTION, fingerprint);
                return FileSettingsService.recover(journal, application.operationId(), previous, document);
            }
            var snapshot = document.read();
            requireRevision(snapshot.revision(), application.expectedRevision());
            var candidate = parse(document, snapshot);
            var requested = candidate.snapshot();
            var current = files.requireCurrent(WebSecurityFileConfig.class).snapshot();
            if (!snapshot.exists() || !current.equals(requested.withMachineTokens(current.machineTokens()))) {
                throw new IllegalStateException("认证根设置还有其他未应用变更，请先在本机确认。");
            }
            Instant at = Instant.now();
            var receipt = new FileSettings.Receipt(application.operationId(), "APPLYING", snapshot.revision(),
                    document.runtime(), "机器策略正在应用，结果尚待确认。", List.of(), at, false);
            var entry = new FileConfigOperations.Entry(ACTOR, SECTION, fingerprint, snapshot.revision(), snapshot.revision(), receipt);
            journal.put(application.operationId(), entry);
            var stamp = document.publish(snapshot.revision(), candidate, guard);
            // 2026/11/04：授权已在实际发布前复核，不能再用刚被本次变更撤销的会话判定失败。
            receipt = new FileSettings.Receipt(application.operationId(), "APPLIED", snapshot.revision(), stamp,
                    "机器策略已应用。请重新登录；原机器连接在后续收发时复核，新连接使用新策略。", List.of(), at, false);
            journal.put(application.operationId(), entry.with(receipt));
            return receipt;
        }));
    }

    /**
     * @param operation 原编号
     * @param guard 当前有效会话，包括重新登录后的管理员
     * @return 原机器令牌操作的恢复回执，不导出候选秘密
     */
    public CompletionStage<FileSettings.Receipt> operation(UUID operation, Runnable guard) {
        return run(guard, document -> operations.locked(journal -> {
            var entry = journal.find(operation);
            if (entry == null || !entry.actor().equals(ACTOR)
                    || !(entry.section().equals(SECTION) || entry.section().startsWith(SECTION + "."))) {
                throw new NoSuchElementException("原机器令牌操作不存在或已经过期。");
            }
            return FileSettingsService.recover(journal, operation, entry, document);
        }));
    }

    /**
     * @param client 目录标识
     * @return 是否是本机真实注册的机器 WebSocket
     */
    private boolean machine(String client) {
        var pair = scanner.getPair(client);
        return pair != null && pair.second().getClient() instanceof WebSocketHandler;
    }

    /**
     * @param client 已注册标识
     * @param guard 原会话
     * @return 同时复核会话、插件及真实机器入口的提交守卫
     */
    private Runnable clientGuard(String client, Runnable guard) {
        if (!client.matches("[a-z][a-z0-9_-]{0,63}") || !machine(client)) {
            throw new NoSuchElementException("机器客户端入口不存在。");
        }
        var checked = clients.guard(client, guard);
        return () -> {
            checked.run();
            if (!machine(client)) {
                throw new IllegalStateException("机器客户端入口已变更。");
            }
        };
    }

    /**
     * @param document 原锁内文档
     * @param snapshot 固定原始字节
     * @return 已完成语义校验的安全配置
     */
    private static WebSecurityFileConfig parse(FileConfigDocument document, FileConfigDocument.Snapshot snapshot) {
        var value = (WebSecurityFileConfig) document.parse(snapshot.content());
        value.snapshot();
        return value;
    }

    /**
     * @param tokens 已归一化的令牌映射
     * @param client 固定客户端
     * @return 与真实握手一致的策略标识
     */
    private static String policy(Map<String, String> tokens, String client) {
        String token = tokens.get(client);
        return token == null ? "DENIED" : token.isEmpty() ? "OPEN" : "PROTECTED";
    }

    /**
     * @param actual 当前版本
     * @param expected 原请求版本
     * @throws ConcurrentModificationException 当文件已由其他操作者修改时
     */
    private static void requireRevision(String actual, String expected) {
        if (!actual.equals(expected)) {
            throw new ConcurrentModificationException("安全配置已变更，请重新读取。");
        }
    }

    /**
     * @param entry 原持久记录
     * @param section 固定机器区
     * @param fingerprint 完整请求的带密钥摘要
     * @throws IllegalArgumentException 当编号被用于其他参数或主体时
     */
    private static void verify(FileConfigOperations.Entry entry, String section, String fingerprint) {
        if (!entry.actor().equals(ACTOR) || !entry.section().equals(section) || !entry.fingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("机器令牌操作编号已绑定其他请求。");
        }
    }

    /**
     * @param guard 实际提交守卫
     * @param action 只在原安全配置类型锁内使用的短工作
     * @param <T> 安全返回类型
     * @return 借用原执行器的有界工作，不在发布后使用已失效会话复核
     */
    private <T> CompletionStage<T> run(Runnable guard, Function<FileConfigDocument, T> action) {
        guard.run();
        if (!slots.tryAcquire()) {
            return CompletableFuture.failedStage(new IllegalStateException("机器令牌管理请求过多。"));
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return files.withDocument(WebSecurityFileConfig.class, document -> {
                        guard.run();
                        return action.apply(document);
                    });
                } finally {
                    slots.release();
                }
            }, executor);
        } catch (RuntimeException failure) {
            slots.release();
            throw failure;
        }
    }
}
