package org.zexnocs.teanekocore.utils;

import java.util.regex.Pattern;

/**
 * 字符串工具类，提供常用的字符串处理方法，例如 URL 验证。
 *
 * @author zExNocs
 * @date 2026/02/12
 */
public enum StringUtils {
    Instance;

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://)?" +                                    // 可选的 http:// 或 https://
                    "(([\\w-]+\\.)+[\\w-]+)" +                         // 域名，例如 www.example.com
                    "(:\\d{1,5})?" +                                   // 可选端口号，例如 :8080
                    "(/[\\w\\-./?%&=]*)?$",                            // 可选路径和查询参数
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 验证给定的字符串是否是一个有效的 URL。URL 可以包含可选的协议（http:// 或 https://）、域名、端口号和路径。
     *
     * @param url 要验证的字符串
     * @return boolean
     */
    public boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return URL_PATTERN.matcher(url).matches();
    }
}
