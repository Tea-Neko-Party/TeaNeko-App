package org.zexnocs.teanekocore.file_config;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.IFileDocumentParser;
import org.zexnocs.teanekocore.utils.PrivateFiles;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 单次文件锁内借用的原文档能力，原子替换与缓存发布分开，不在浏览器暴露路径或凭据。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：提供原解析器的类型元数据，不暴露文件或凭据。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.6-l
 */
@JsonIgnoreType
public final class FileConfigDocument {
    public static final int MAX_BYTES = 1_048_576;
    private final Path root;
    private final Path path;
    private final Class<? extends IFileConfigData> type;
    private final IFileDocumentParser parser;
    private final byte[] fallback;
    private final BiFunction<IFileConfigData, String, Stamp> publication;
    private final Supplier<Stamp> runtime;

    /**
     * @param root 固定配置根目录
     * @param path 已注册的完整文件路径
     * @param type 固定类型
     * @param parser 原格式及环境解析器
     * @param fallback 原模板或默认预览
     * @param publication 原缓存发布端口
     * @param runtime 原缓存代次读取
     */
    FileConfigDocument(Path root, Path path, Class<? extends IFileConfigData> type, IFileDocumentParser parser,
            byte[] fallback, BiFunction<IFileConfigData, String, Stamp> publication, Supplier<Stamp> runtime) {
        this.root = root.toAbsolutePath().normalize();
        this.path = path.toAbsolutePath().normalize();
        this.type = type;
        this.parser = parser;
        this.fallback = fallback.clone();
        this.publication = publication;
        this.runtime = runtime;
    }

    /**
     * @return 有界原文件或尚未写入的模板快照
     */
    public Snapshot read() {
        try {
            checkPath();
            var value = snapshot(path);
            return value.exists() ? value : new Snapshot("absent", fallback, false);
        } catch (IOException failure) {
            throw new IllegalStateException("无法读取受限配置文档。");
        }
    }

    /**
     * @param snapshot 固定原始字节
     * @return 未展开的文档，调用方只能经明确字段投影导出
     */
    public JsonNode tree(Snapshot snapshot) {
        return parser.readDocument(snapshot.content());
    }

    /**
     * 2026/11/04：仅提供原类型声明的字段说明，不返回文件正文或环境展开值。
     * @return 原解析器支持的元数据，不支持时为空
     */
    public org.zexnocs.teanekocore.framework.config.@org.jspecify.annotations.Nullable ConfigSchema schema() {
        return parser.schema(type);
    }

    /**
     * @param tree 未展开文档
     * @return 原格式字节
     */
    public byte[] encode(JsonNode tree) {
        return parser.writeDocument(tree);
    }

    /**
     * @param content 候选原字节
     * @return 经原环境解析和类型绑定的候选，不发布运行缓存
     */
    public IFileConfigData parse(byte[] content) {
        return parser.fromFileToData(new ByteArrayInputStream(content), type);
    }

    /**
     * @return 当前进程最近加载状态，不把磁盘保存当作已应用
     */
    public Stamp runtime() {
        return runtime.get();
    }

    /**
     * @param expected 原磁盘版本
     * @param candidate 已校验的同一候选
     * @param guard 当前权限
     * @return 本次缓存发布的精确代次
     */
    public Stamp publish(String expected, IFileConfigData candidate, Runnable guard) {
        if (!read().revision().equals(expected)) {
            throw new ConcurrentModificationException("磁盘配置版本已经变化。");
        }
        guard.run();
        return publication.apply(type.cast(candidate), expected);
    }

    /**
     * @param content 已校验候选
     * @return 私有暂存，调用者负责在整次保存结束后关闭
     */
    public Prepared prepare(byte[] content) {
        if (content.length == 0 || content.length > MAX_BYTES) {
            throw new IllegalArgumentException("配置文件大小超出一兆字节上限。");
        }
        Path temporary = null;
        try {
            checkPath();
            Files.createDirectories(path.getParent());
            checkPath();
            temporary = Files.createTempFile(path.getParent(), ".teaneko-config-", ".tmp");
            PrivateFiles.protect(temporary, false);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var buffer = java.nio.ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            return new Prepared(temporary, snapshot(temporary).revision());
        } catch (IOException failure) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // 已知暂存仍具有私有权限，不扩大删除范围。
                }
            }
            throw new IllegalStateException("无法创建配置原子替换暂存。");
        }
    }

    /**
     * @throws IOException 当路径越界、链接或不是常规文件时
     */
    private void checkPath() throws IOException {
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IOException("配置路径超出声明根目录。");
        }
        PrivateFiles.rejectLinks(path);
        if (Files.exists(root) && Files.exists(path.getParent())
                && !path.getParent().toRealPath().startsWith(root.toRealPath())) {
            throw new IOException("配置目录实际位置越界。");
        }
    }

    /**
     * @param path 固定宿主文件
     * @return 同一身份、修改时间和正文的快照
     * @throws IOException 当文件不稳定、非常规或超出上限时
     */
    static Snapshot snapshot(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return new Snapshot("absent", new byte[0], false);
        }
        var before = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || before.size() > MAX_BYTES) {
            throw new IOException("配置必须是有界常规文件。");
        }
        byte[] bytes;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        var after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (bytes.length > MAX_BYTES || !Objects.equals(before.fileKey(), after.fileKey())
                || !before.lastModifiedTime().equals(after.lastModifiedTime()) || before.size() != after.size()) {
            throw new IOException("配置在读取期间发生变化。");
        }
        String revision = StableDigest.sha256(String.valueOf(after.fileKey()), after.lastModifiedTime().toString(),
                Base64.getEncoder().encodeToString(bytes));
        return new Snapshot("f_" + revision, bytes, true);
    }

    /**
     * 在当前文件锁内完成的私有暂存，不允许跨锁保存能力对象。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public final class Prepared implements AutoCloseable {
        private final Path temporary;
        private final String revision;

        /**
         * @param temporary 本次拥有的唯一暂存
         * @param revision 暂存身份与正文版本
         */
        private Prepared(Path temporary, String revision) {
            this.temporary = temporary;
            this.revision = revision;
        }

        /** @return 原子移动后应保持的文件身份版本 */
        public String revision() {
            return revision;
        }

        /**
         * @param expected 原目标版本
         * @param guard 替换前的实际权限
         */
        public void commit(String expected, Runnable guard) {
            if (!read().revision().equals(expected)) {
                throw new ConcurrentModificationException("配置版本冲突，未覆盖磁盘。");
            }
            guard.run();
            try {
                checkPath();
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                throw new IllegalStateException("配置原子替换未能确认，请查询原操作。");
            }
        }

        /** 只清理本次拥有的单个暂存，不删除实际配置。 */
        @Override
        public void close() {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // 私有暂存可由操作者按已知位置处理，不执行目录清理。
            }
        }
    }

    /**
     * 不可通过普通序列化导出的原文快照。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param revision 文件身份和内容版本
     * @param content 原始字节
     * @param exists 是否真实存在
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @JsonIgnoreType
    public record Snapshot(String revision, byte[] content, boolean exists) {
        /**
         * @param revision 文件版本
         * @param content 原文
         * @param exists 是否已存在
         */
        public Snapshot {
            content = content.clone();
        }
        /** @return 防止调用方修改已校验字节的独立副本 */
        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    /**
     * 当前进程运行快照状态，不携带原文或配置对象。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param revision 最近成功加载的文件版本
     * @param generation 本进程发布代次
     * @param current 最近加载是否成功
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Stamp(String revision, long generation, boolean current) {}
}
