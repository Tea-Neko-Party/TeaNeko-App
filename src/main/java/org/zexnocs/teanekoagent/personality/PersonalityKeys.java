package org.zexnocs.teanekoagent.personality;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 为人格内容和物理存储键生成无拼接歧义的摘要，不把摘要解释为权限。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class PersonalityKeys {
    /** 禁止实例化无状态定位工具。 */
    private PersonalityKeys() {}
    /**
     * 按长度和 Java 字符编码保留原始差异，包括不成对代理项。
     * @param parts 顺序固定的定位值
     * @return SHA-256 摘要
     * @throws IllegalStateException 当标准摘要算法不可用时
     */
    public static String digest(String... parts) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var part : parts) {
                int size = part.length(); for (int shift = 24; shift >= 0; shift -= 8) { digest.update((byte) (size >>> shift)); }
                for (int i = 0; i < size; i++) { char value = part.charAt(i); digest.update((byte) (value >>> 8)); digest.update((byte) value); }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("运行环境缺少标准人格摘要算法。"); }
    }
}
