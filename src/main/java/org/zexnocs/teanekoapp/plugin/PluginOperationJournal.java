package org.zexnocs.teanekoapp.plugin;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.utils.PrivateFiles;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 插件开关的私有持久预写账本，配置原子替换与资源应用分别保留真实回执。
 * <br>2026/11/07：只保存非秘密操作参数和版本，进程中断后通过原文件版本恢复确认。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
public final class PluginOperationJournal {
    private static final int MAX_BYTES = 2_097_152;
    private final ObjectMapper mapper;
    private final Path root;
    private @Nullable Map<UUID, Entry> entries;

    /**
     * 固定实例级私有目录，不接受浏览器指定文件路径。
     * @param mapper 原转换器
     */
    @Autowired
    public PluginOperationJournal(ObjectMapper mapper) {
        this(mapper, Path.of("data/plugins"));
    }

    /**
     * 为隔离测试提供明确的临时根目录。
     * @param mapper 原转换器
     * @param root 固定私有目录
     */
    public PluginOperationJournal(ObjectMapper mapper, Path root) {
        this.mapper = mapper;
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * 查询原操作，不因为重试请求生成第二次启停。
     * @param operationId 原操作编号
     * @return 已记录条目，不存在时为空
     */
    public synchronized @Nullable Entry find(UUID operationId) {
        return load().get(operationId);
    }

    /**
     * 列出尚未确认完成的预写，供启动协调后恢复。
     * @return 固定的未决操作列表
     */
    public synchronized List<Entry> pending() {
        return load().values().stream().filter(entry -> entry.receipt().status().equals("APPLYING")).toList();
    }

    /**
     * 原子替换有界账本，写失败时不提前发布内存结果。
     * @param entry 原编号的新阶段
     */
    public synchronized void put(Entry entry) {
        var next = new LinkedHashMap<>(load());
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        next.values().removeIf(value -> !value.receipt().status().equals("APPLYING") && value.at().isBefore(cutoff));
        UUID id = entry.request().operationId();
        if (!next.containsKey(id) && next.size() >= 256) {
            throw new IllegalStateException("插件操作回执窗口已满，请先处理未决操作或等待保留期结束。");
        }
        next.put(id, entry);
        byte[] content = mapper.writeValueAsBytes(new State(next));
        if (content.length > MAX_BYTES) {
            throw new IllegalStateException("插件操作回执超过大小上限。");
        }
        try {
            PrivateFiles.rejectLinks(root);
            Files.createDirectories(root);
            PrivateFiles.protect(root, true);
            Path target = root.resolve("operations.json");
            PrivateFiles.rejectLinks(target);
            Path temporary = Files.createTempFile(root, "plugin-operation-", ".tmp");
            try {
                PrivateFiles.protect(temporary, false);
                try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    var buffer = ByteBuffer.wrap(content);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    channel.force(true);
                }
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            entries = Map.copyOf(next);
        } catch (IOException failure) {
            throw new IllegalStateException("插件操作回执未能持久确认，请保留原编号查询。", failure);
        }
    }

    /**
     * 首次读取私有账本，损坏或超限时明确拒绝后续写入。
     * @return 当前完整账本
     */
    private Map<UUID, Entry> load() {
        if (entries != null) {
            return entries;
        }
        Path path = root.resolve("operations.json");
        try {
            PrivateFiles.rejectLinks(path);
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                entries = Map.of();
                return entries;
            }
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("插件回执不是常规文件。");
            }
            byte[] content;
            try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                content = input.readNBytes(MAX_BYTES + 1);
            }
            if (content.length > MAX_BYTES) {
                throw new IOException("插件回执超过大小上限。");
            }
            var state = mapper.readValue(content, State.class);
            if (state.entries() == null || state.entries().size() > 256) {
                throw new IOException("插件回执结构无效。");
            }
            entries = Map.copyOf(state.entries());
            return entries;
        } catch (IOException | tools.jackson.core.JacksonException failure) {
            throw new IllegalStateException("插件操作账本不可用，已拒绝继续写入。", failure);
        }
    }

    /**
     * 同一操作的预写和真实结果，主体只用于内部归属复核。
     * @param subject 已认证主体
     * @param request 固定原请求
     * @param expected 原配置文件版本
     * @param planned 预写的目标文件版本
     * @param at 操作时间
     * @param receipt 当前回执
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Entry(String subject, PluginManagement.Change request, String expected, String planned,
                        Instant at, PluginManagement.Receipt receipt) {
        /**
         * 更新结果而不替换原请求与原子保存证据。
         * @param receipt 新阶段回执
         * @return 同一操作的新状态
         */
        public Entry with(PluginManagement.Receipt receipt) {
            return new Entry(subject, request, expected, planned, at, receipt);
        }
    }

    /**
     * 内部序列化账本，不作为管理 API 输出。
     * @param entries 有界操作记录
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private record State(Map<UUID, Entry> entries) {
    }
}
