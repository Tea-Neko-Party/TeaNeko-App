package org.zexnocs.teanekoplugin.agent.cognition.personality;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 为人格内容和物理存储键生成无拼接歧义的摘要，不把摘要解释为权限。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
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
