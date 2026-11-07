package org.zexnocs.teanekoagent.component.sticker;

import org.zexnocs.teanekocore.logger.DiagnosticException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/**
 * 目录适配器拥有的本地描述文件；不可变内容地址保证数据库回滚不会覆盖旧描述。
 * 便携旁车只从已提交引用发布，复制原图及旁车即可迁移，不需要复制数据库。
 * <br>2026/11/07：新增有界读取、完整性验证和按键缓存，目录发现仅由维护入口调用。
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.8.7-l
 * @version 4.8.7-l
 */
final class StickerMetadataFiles {
    private static final int MAX_BYTES = 16 * 1024;
    private static final int CACHE_SIZE = 4096;
    private final ObjectMapper mapper;
    private final LinkedHashMap<Path, Stickers.Analysis> cache = new LinkedHashMap<>(32, .75f, true);

    /** @param mapper 所属目录的序列化器 */
    StickerMetadataFiles(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 先保存不可变描述，再允许调用方把引用写入数据库。未提交文件不会被导入器发现。
     * @param settings 固定目录
     * @param entry 待保存业务条目
     */
    void persist(StickerSettings settings, Stickers.Entry entry) {
        var analysis = entry.getAnalysis();
        if (analysis == null) {
            entry.setAnalysisRef("");
            return;
        }
        byte[] bytes = mapper.writeValueAsBytes(analysis);
        String reference = digest(bytes);
        Path path = descriptionPath(settings, reference);
        synchronized (cache) {
            if (analysis.equals(cache.get(path)) && Files.isRegularFile(path, NOFOLLOW_LINKS)) {
                entry.setAnalysisRef(reference);
                return;
            }
        }
        try {
            prepareDirectory(settings);
            if (Files.exists(path, NOFOLLOW_LINKS)) {
                if (!digest(read(path)).equals(reference)) {
                    throw new IllegalStateException("本地表情包描述摘要不匹配：" + reference);
                }
            } else {
                write(path, bytes);
            }
            remember(path, analysis);
            entry.setAnalysisRef(reference);
        } catch (IOException failure) {
            throw new DiagnosticException("无法安全保存表情包描述：" + reference, failure);
        }
    }

    /**
     * 旧数据中的内联描述优先保留，首次提交时再迁出；新记录按不可变引用读取。
     * @param settings 固定目录
     * @param entry 反序列化后的条目
     */
    void hydrate(StickerSettings settings, Stickers.Entry entry) {
        if (entry.getAnalysis() == null && !entry.getAnalysisRef().isEmpty()) {
            entry.setAnalysis(analysis(settings, entry.getAnalysisRef()));
        }
    }

    /**
     * @param settings 固定目录
     * @param reference 受校验摘要
     * @return 已验证的本地描述；缺失或损坏明确报告
     */
    private Stickers.Analysis analysis(StickerSettings settings, String reference) {
        Path path = descriptionPath(settings, reference);
        synchronized (cache) {
            var found = cache.get(path);
            if (found != null) {
                return found;
            }
        }
        try {
            prepareDirectory(settings);
            byte[] bytes = read(path);
            if (!digest(bytes).equals(reference)) {
                throw new IllegalStateException("本地表情包描述摘要不匹配：" + reference);
            }
            var value = mapper.readValue(bytes, Stickers.Analysis.class);
            remember(path, value);
            return value;
        } catch (IOException | tools.jackson.core.JacksonException failure) {
            throw new DiagnosticException("表情包描述缺失或损坏，请恢复素材目录备份。描述=" + reference, failure);
        }
    }

    /**
     * 在根行锁内发布上次已提交的意图。重复发布幂等，失败保留意图供下次恢复。
     * @param settings 固定目录
     * @param hash 真实素材摘要
     * @param reference 已提交引用或撤销标记
     */
    void publish(StickerSettings settings, String hash, StickerPortableReference reference) {
        hash(hash);
        var path = StickerStoragePaths.root(settings).resolve("assets").resolve(hash + ".sticker.json");
        try {
            if (reference.blob() == null) {
                if (Files.exists(path, NOFOLLOW_LINKS)) {
                    read(path);
                    Files.delete(path);
                }
                return;
            }
            if (!hash.equals(reference.blob().hash())) {
                throw new IllegalStateException("便携素材引用与摘要不一致。");
            }
            var portable = new Portable(1, reference.blob(), analysis(settings, reference.analysisRef()),
                    reference.analysisVersion(), reference.enabled());
            write(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(portable));
        } catch (IOException failure) {
            throw new DiagnosticException("表情包便携描述发布失败，稍后重试。素材=" + hash, failure);
        }
    }

    /**
     * 仅低频维护调用；只保留游标之后至多 32 项名称，不加载目录内的图片或描述正文。
     * @param settings 固定目录
     * @param after 上页摘要游标
     * @return 本页摘要，字典序排列
     */
    List<String> scan(StickerSettings settings, String after) {
        var names = new TreeSet<String>();
        try (var paths = Files.newDirectoryStream(StickerStoragePaths.root(settings).resolve("assets"), "*.sticker.json")) {
            for (var path : paths) {
                String name = path.getFileName().toString();
                if (!name.matches("[a-f0-9]{64}\\.sticker\\.json")) {
                    continue;
                }
                String hash = name.substring(0, 64);
                if (hash.compareTo(after) > 0) {
                    names.add(hash);
                    if (names.size() > 32) {
                        names.pollLast();
                    }
                }
            }
            return List.copyOf(names);
        } catch (IOException failure) {
            throw new DiagnosticException("无法扫描表情包便携描述。", failure);
        }
    }

    /**
     * @param settings 固定目录
     * @param hash 本页的受控摘要
     * @return 已验证的便携声明；原图须再经文件后端解码验证
     */
    Portable portable(StickerSettings settings, String hash) {
        hash(hash);
        try {
            var result = mapper.readValue(read(StickerStoragePaths.root(settings).resolve("assets")
                    .resolve(hash + ".sticker.json")), Portable.class);
            if (!result.blob().hash().equals(hash)) {
                throw new IllegalArgumentException("便携文件名与原图摘要不一致。");
            }
            return result;
        } catch (IOException | tools.jackson.core.JacksonException failure) {
            throw new DiagnosticException("表情包便携描述无效。素材=" + hash, failure);
        }
    }

    /**
     * @param settings 固定目录
     * @param reference 不可变描述摘要
     * @return 精确内容地址键；真正 I/O 前由 prepareDirectory 验证父路径
     */
    private Path descriptionPath(StickerSettings settings, String reference) {
        hash(reference);
        return Path.of(settings.storageRoot()).toAbsolutePath().normalize().resolve(settings.catalogId())
                .resolve("descriptions").resolve(reference + ".json");
    }

    /** @param settings 真正文件 I/O 使用的受控目录，缓存命中不重复发现目录 */
    private void prepareDirectory(StickerSettings settings) {
        StickerStoragePaths.identity(settings);
        var directory = StickerStoragePaths.root(settings).resolve("descriptions");
        try {
            StickerStoragePaths.directory(directory);
        } catch (IOException failure) {
            throw new DiagnosticException("表情包描述目录不可用。", failure);
        }
    }

    /**
     * @param path 精确受控文件
     * @return 至多 16 KiB 的独立内容
     * @throws IOException 当文件缺失、不是普通文件或超限时
     */
    private static byte[] read(Path path) throws IOException {
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES) {
            throw new IOException("描述文件不存在、类型无效或超过 16 KiB。");
        }
        try (var input = Files.newInputStream(path, NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new IOException("描述文件读取期间超过 16 KiB。");
            }
            return bytes;
        }
    }

    /**
     * 强制写入暂存后原子更名，半份 JSON 永不成为有效文件。
     * @param target 精确受控文件
     * @param bytes 有界内容
     * @throws IOException 当文件超限、类型无效或无法原子发布时
     */
    private static void write(Path target, byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) {
            throw new IOException("描述文件超过 16 KiB。");
        }
        if (Files.exists(target, NOFOLLOW_LINKS) && !Files.isRegularFile(target, NOFOLLOW_LINKS)) {
            throw new IOException("描述目标不是普通文件。");
        }
        var temporary = Files.createTempFile(target.getParent(), ".description-", ".part");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(bytes);
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
     * @param path 内容地址
     * @param analysis 不可变分析
     */
    private void remember(Path path, Stickers.Analysis analysis) {
        synchronized (cache) {
            cache.put(path, analysis);
            while (cache.size() > CACHE_SIZE) {
                cache.pollFirstEntry();
            }
        }
    }

    /** @param hash 只允许标准 SHA-256，禁止路径输入 */
    private static void hash(String hash) {
        if (!hash.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("表情包描述摘要无效。");
        }
    }

    /**
     * @param bytes 原始序列化字节
     * @return 标准 SHA-256
     */
    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("环境缺少标准摘要算法。", failure);
        }
    }

    /**
     * 只包含复制迁移所需的素材和描述，不导出用户、租约、消息或来源 URL。
     * @param version 文件格式版本
     * @param blob 原图信息
     * @param analysis 类型、类别和用途描述
     * @param analysisVersion 分析协议版本
     * @param enabled 原素材是否启用
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.8.7-l
     * @version 4.8.7-l
     */
    record Portable(int version, Stickers.Blob blob, Stickers.Analysis analysis, int analysisVersion, boolean enabled) {
        /**
         * @param version 文件版本
         * @param blob 原图信息
         * @param analysis 描述
         * @param analysisVersion 分析版本
         * @param enabled 启用状态
         * @throws IllegalArgumentException 当文件版本或素材边界不合法时
         */
        Portable {
            java.util.Objects.requireNonNull(analysis);
            hash(blob.hash());
            if (version != 1 || analysisVersion < 1 || !Set.of("png", "jpg", "gif", "webp").contains(blob.format())
                    || blob.bytes() < 1 || blob.bytes() > 8 * 1024 * 1024 || blob.width() < 1 || blob.height() < 1
                    || blob.frames() < 1) {
                throw new IllegalArgumentException("便携表情包版本或素材信息无效。");
            }
        }
    }
}
