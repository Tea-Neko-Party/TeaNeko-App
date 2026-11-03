package org.zexnocs.teanekocore.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.*;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 为宿主拥有的敏感文件设置所有者权限，不支持可靠权限控制时拒绝继续。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public final class PrivateFiles {
    /** 禁止实例化文件权限工具。 */
    private PrivateFiles() {}

    /**
     * @param path 已存在的本次资源
     * @param directory 是否为目录
     * @throws IOException 当权限无法可靠设置或路径为符号链接时
     */
    public static void protect(Path path, boolean directory) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("私有资源不能是符号链接。");
        }
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            Files.setPosixFilePermissions(path, directory
                    ? Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
                    : Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            return;
        }
        var acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl == null) {
            throw new IOException("文件系统不支持私有资源权限。");
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
            throw new IOException("私有资源仍允许其他主体访问。");
        }
    }

    /**
     * @param path 固定宿主路径
     * @throws IOException 当任一已有父路径为符号链接时
     */
    public static void rejectLinks(Path path) throws IOException {
        for (Path part = path.toAbsolutePath().normalize(); part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part)) {
                throw new IOException("受限路径不能经过符号链接。");
            }
        }
    }
}
