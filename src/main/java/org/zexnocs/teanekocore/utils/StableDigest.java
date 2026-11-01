package org.zexnocs.teanekocore.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 按有序字符串及长度计算稳定摘要，保留分隔和不成对代理项差异，不表达授权。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class StableDigest {
    /** 禁止实例化无状态摘要工具。 */
    private StableDigest() {}
    /**
     * 按长度和 UTF-16 代码单元计算 SHA-256，不进行隐式文本规范化。
     * @param parts 顺序固定且非空的字符串
     * @return 十六进制摘要
     * @throws IllegalStateException 当环境缺少标准算法时
     * @throws NullPointerException 当数组或其中任一字符串为空时
     */
    public static String sha256(String... parts) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var part : parts) {
                for (int shift = 24; shift >= 0; shift -= 8) { digest.update((byte) (part.length() >>> shift)); }
                for (int i = 0; i < part.length(); i++) { char value = part.charAt(i); digest.update((byte) (value >>> 8)); digest.update((byte) value); }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("环境缺少标准摘要算法。"); }
    }
}
