package org.zexnocs.teanekoapp.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 在本机私有目录原子替换临时根令牌，不供普通文件下载。
 * <br>2026/11/04：启动日志的显式令牌提示由认证宿主管理。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
final class WebTokenFile {
    /** 禁止实例化本机文件工具。 */
    private WebTokenFile() {}

    /**
     * @param directory 固定运行目录
     * @param token 本次启动的秘密
     * @throws IllegalStateException 当无法安全保存时
     */
    static void write(Path directory, String token) {
        Path root = directory.toAbsolutePath().normalize();
        Path temporary = null;
        try {
            for (Path part = root; part != null; part = part.getParent()) {
                if (Files.isSymbolicLink(part)) {
                    throw new IOException("运行目录不能经过符号链接。");
                }
            }
            Files.createDirectories(root);
            protect(root, true);
            Path target = root.resolve("runtime-admin-token");
            if (Files.isSymbolicLink(target)) {
                throw new IOException("令牌文件不能是符号链接。");
            }
            temporary = Files.createTempFile(root, "login-", ".tmp");
            protect(temporary, false);
            Files.writeString(temporary, token, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            protect(target, false);
        } catch (IOException | UnsupportedOperationException failure) {
            throw new IllegalStateException("无法创建受限 Web 令牌文件，管理入口保持关闭。");
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // 私有目录中的已知暂存仍不可由网页访问，不扩大删除范围。
                }
            }
        }
    }

    /**
     * 只允许资源所有者访问；不支持安全权限控制时拒绝继续。
     * @param path 本次拥有的文件或目录
     * @param directory 是否需要目录进入权限
     * @throws IOException 当权限不能可靠设置时
     */
    private static void protect(Path path, boolean directory) throws IOException {
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            Files.setPosixFilePermissions(path, directory
                    ? Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE)
                    : Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            return;
        }
        var acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl == null) {
            throw new IOException("文件系统不支持受限令牌权限。");
        }
        var owner = acl.getOwner();
        var builder = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) {
            builder.setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT);
        }
        acl.setAcl(List.of(builder.build()));
        if (acl.getAcl().stream().anyMatch(entry -> entry.type() == AclEntryType.ALLOW
                && !entry.principal().equals(owner))) {
            throw new IOException("令牌目录仍存在其他主体访问权限。");
        }
    }
}
