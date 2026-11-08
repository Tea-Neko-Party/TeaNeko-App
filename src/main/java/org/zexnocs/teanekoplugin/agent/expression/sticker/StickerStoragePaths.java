package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekocore.logger.DiagnosticException;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/**
 * 素材与描述共用的受控路径及卷身份，不发现扩展，也不访问数据库。
 * <br>2026/11/07：抽取原本地素材路径规则，描述文件沿用同一身份与符号链接约束。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.8.7-l
 * @version 4.9.0-l
 */
final class StickerStoragePaths {
    /** 工具类不允许实例化。 */
    private StickerStoragePaths() {}

    /**
     * @param settings 固定目录配置
     * @return 无符号链接的实例根及已创建的素材子目录
     */
    static Path root(StickerSettings settings) {
        var root = Path.of(settings.storageRoot()).toAbsolutePath().normalize().resolve(settings.catalogId());
        try {
            Path current = root.getRoot();
            for (var part : root) {
                current = current.resolve(part);
                if (Files.isSymbolicLink(current)) {
                    throw new IllegalStateException("素材目录不允许符号链接。");
                }
            }
            directory(root.resolve("staging"));
            directory(root.resolve("assets"));
            return root;
        } catch (IOException failure) {
            throw new DiagnosticException("素材目录无法创建。", failure);
        }
    }

    /**
     * @param directory 受控子目录
     * @throws IOException 当路径不是普通目录或不能创建时
     */
    static void directory(Path directory) throws IOException {
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("素材子目录不允许符号链接。");
        }
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) {
            throw new IOException("素材子目录类型无效。");
        }
    }

    /**
     * @param settings 固定目录
     * @return 存储卷持久身份；非空无身份卷须由操作者核实
     */
    static String identity(StickerSettings settings) {
        var root = root(settings);
        var marker = root.resolve(".teaneko-sticker-store");
        try {
            if (!Files.exists(marker, NOFOLLOW_LINKS)) {
                try (var paths = Files.list(root)) {
                    for (var path : paths.toList()) {
                        if (path.equals(marker)) {
                            continue;
                        }
                        if (!Set.of("staging", "assets").contains(path.getFileName().toString())) {
                            throw new IllegalStateException("素材目录没有身份文件且包含未知内容，须人工核实。");
                        }
                        try (var children = Files.list(path)) {
                            if (children.findAny().isPresent() && !Files.exists(marker, NOFOLLOW_LINKS)) {
                                throw new IllegalStateException("非空素材卷缺少身份文件，不能自动接管。迁移时复制完整实例目录，或先启动目标图库再复制 assets 文件。");
                            }
                        }
                    }
                }
                try {
                    Files.writeString(marker, UUID.randomUUID().toString(), StandardOpenOption.CREATE_NEW);
                } catch (FileAlreadyExistsException ignored) {
                    // 其他副本先建成，继续验证同一个身份文件。
                }
            }
            if (!Files.isRegularFile(marker, NOFOLLOW_LINKS) || Files.size(marker) > 64) {
                throw new IllegalStateException("素材卷身份文件类型或长度无效。");
            }
            String id = Files.readString(marker);
            if (!id.matches("[a-f0-9-]{36}")) {
                throw new IllegalStateException("素材卷身份文件无效。");
            }
            return id;
        } catch (IOException failure) {
            throw new DiagnosticException("无法读取素材卷身份。", failure);
        }
    }
}
