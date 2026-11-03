package org.zexnocs.teanekoapp.web.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 可信管理上下文的内部目标编码，浏览器不能以此编码代替目标目录解析。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public final class ManagementTargets {
    /** 禁止实例化无状态编码工具。 */
    private ManagementTargets() {}

    /**
     * @param client 已验证平台
     * @param scope 已验证内部目标
     * @return 无分隔符歧义的内部引用
     */
    public static String encode(String client, String scope) {
        return client + ":" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(scope.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param value 宿主上下文的目标引用
     * @return 固定目标，不代表授权
     * @throws IllegalArgumentException 当编码或身份格式无效时
     */
    public static Target decode(String value) {
        int separator = value.indexOf(':');
        if (separator < 1 || value.length() > 1024) {
            throw new IllegalArgumentException("管理目标引用无效。");
        }
        String client = value.substring(0, separator);
        String scope = new String(Base64.getUrlDecoder().decode(value.substring(separator + 1)), StandardCharsets.UTF_8);
        if (!client.matches("[a-z][a-z0-9_-]{0,63}") || scope.isBlank() || scope.length() > 256
                || scope.chars().anyMatch(Character::isISOControl) || !encode(client, scope).equals(value)) {
            throw new IllegalArgumentException("管理目标引用无效。");
        }
        return new Target(client, scope);
    }

    /**
     * 已从可信上下文解析的平台与目标。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param client 平台标识
     * @param scope 内部目标
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Target(String client, String scope) {}
}
