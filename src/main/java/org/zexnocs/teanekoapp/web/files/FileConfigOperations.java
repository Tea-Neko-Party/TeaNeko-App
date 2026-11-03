package org.zexnocs.teanekoapp.web.files;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.utils.PrivateFiles;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 文件替换的私有持久预写账本，只保存带密钥的请求摘要、版本和回执，不保存候选秘密。
 * 预写与配置文件不是跨文件事务，中断由应用层结合真实文件版本恢复确认。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
public final class FileConfigOperations {
    private final ObjectMapper mapper;
    private final Path root;

    /** @param mapper 原转换器 */
    @Autowired
    public FileConfigOperations(ObjectMapper mapper) {
        this(mapper, Path.of("data/web/file-config-operations"));
    }

    /**
     * @param mapper 原转换器
     * @param root 宿主固定私有目录，测试可用临时目录
     */
    public FileConfigOperations(ObjectMapper mapper, Path root) {
        this.mapper = mapper;
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * 调用顺序固定为配置类型锁再进入此锁，回调不得反向申请其他配置类型锁。
     * @param action 短文件工作回调
     * @param <T> 返回类型
     * @return 实际持久结果
     */
    synchronized <T> T locked(Function<Session, T> action) {
        try {
            PrivateFiles.rejectLinks(root);
            Files.createDirectories(root);
            PrivateFiles.protect(root, true);
            var keyPath = root.resolve("fingerprint-key");
            var statePath = root.resolve("operations.json");
            PrivateFiles.rejectLinks(keyPath);
            PrivateFiles.rejectLinks(statePath);
            if (!Files.exists(keyPath)) {
                if (Files.exists(statePath)) {
                    throw new IOException("回执摘要密钥丢失。");
                }
                byte[] key = new byte[32];
                new SecureRandom().nextBytes(key);
                atomic(keyPath, key);
            }
            PrivateFiles.protect(keyPath, false);
            byte[] key = read(keyPath, 32);
            if (key.length != 32) {
                throw new IOException("回执摘要密钥无效。");
            }
            var entries = new LinkedHashMap<UUID, Entry>();
            if (Files.exists(statePath)) {
                PrivateFiles.protect(statePath, false);
                try {
                    entries.putAll(mapper.readValue(read(statePath, 2_097_152), State.class).entries());
                    if (entries.size() > 256) {
                        throw new IOException("回执数量超过上限。");
                    }
                } catch (tools.jackson.core.JacksonException | NullPointerException failure) {
                    throw new IOException("私有回执结构损坏。");
                }
            }
            Instant cutoff = Instant.now().minus(Duration.ofHours(24));
            entries.values().removeIf(entry -> entry.receipt().at().isBefore(cutoff));
            return action.apply(new Session(key, entries));
        } catch (IOException failure) {
            throw new IllegalStateException("文件配置回执不可用，已拒绝继续写入。");
        }
    }

    /**
     * @param path 本目录内固定文件
     * @param maximum 最大字节数
     * @return 有界原字节
     * @throws IOException 当不是常规文件或超过上限时
     */
    private byte[] read(Path path, int maximum) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("私有回执不是常规文件。");
        }
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] value = input.readNBytes(maximum + 1);
            if (value.length > maximum) {
                throw new IOException("私有回执超过大小上限。");
            }
            return value;
        }
    }

    /**
     * @param target 私有目录内固定目标
     * @param bytes 有界内容
     * @throws IOException 当原子持久替换失败时
     */
    private void atomic(Path target, byte[] bytes) throws IOException {
        PrivateFiles.rejectLinks(target);
        var temporary = Files.createTempFile(root, "operation-", ".tmp");
        try {
            PrivateFiles.protect(temporary, false);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * 一次私有账本锁内借用的能力，不导出候选内容或密钥。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    final class Session {
        private final byte[] key;
        private final Map<UUID, Entry> entries;

        /**
         * @param key 私有摘要密钥
         * @param entries 当前有界账本
         */
        Session(byte[] key, Map<UUID, Entry> entries) {
            this.key = key;
            this.entries = entries;
        }

        /**
         * @param value 原动作和完整请求，仅用于内存摘要
         * @return 无法离线枚举秘密值的带密钥摘要
         */
        String fingerprint(Object value) {
            try {
                var mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(key, "HmacSHA256"));
                return HexFormat.of().formatHex(mac.doFinal(mapper.writeValueAsBytes(value)));
            } catch (java.security.GeneralSecurityException failure) {
                throw new IllegalStateException("无法构造配置请求摘要。");
            }
        }

        /**
         * @param operation 原编号
         * @return 当前保留期内记录，缺失为空
         */
        @org.jspecify.annotations.Nullable Entry find(UUID operation) {
            return entries.get(operation);
        }

        /**
         * @param operation 原操作
         * @param entry 同一编号的新阶段
         */
        void put(UUID operation, Entry entry) {
            if (!entries.containsKey(operation) && entries.size() >= 256) {
                throw new IllegalStateException("文件配置回执窗口已满，请等待旧记录过期。");
            }
            entries.put(operation, entry);
            byte[] bytes = mapper.writeValueAsBytes(new State(entries));
            if (bytes.length > 2_097_152) {
                throw new IllegalStateException("文件配置回执窗口超过大小上限。");
            }
            try {
                atomic(root.resolve("operations.json"), bytes);
            } catch (IOException failure) {
                throw new IllegalStateException("配置操作结果未能持久确认，请查询原编号。");
            }
        }
    }

    /**
     * 只存摘要及状态，不存请求新值、旧值和环境展开值。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param actor 操作者摘要
     * @param section 固定配置区
     * @param fingerprint 完整原请求的私有摘要
     * @param expected 原磁盘版本
     * @param planned 拟替换版本
     * @param receipt 原阶段回执
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    record Entry(String actor, String section, String fingerprint, String expected, String planned,
                 FileSettings.Receipt receipt) {
        /**
         * @param receipt 新阶段回执
         * @return 同一请求的新阶段，不改变原摘要
         */
        Entry with(FileSettings.Receipt receipt) {
            return new Entry(actor, section, fingerprint, expected, planned, receipt);
        }
    }

    /**
     * 固定有界账本，不作为 HTTP 响应。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param entries 原操作记录
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record State(Map<UUID, Entry> entries) {}
}
