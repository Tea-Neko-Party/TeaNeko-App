package org.zexnocs.teanekoapp.web;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地 Web 认证根配置，凭据禁止普通导出，未启用时保留原机器接入方式。
 * <br>2026/11/03：聊天配置区须在本地显式允许，扫描到第三方实现不会自动开放编辑。
 * <br>2026/11/03：管理资源增加独立白名单，变更同步撤销旧会话代次。
 * <br>2026/11/03：文件编辑区使用独立本地白名单，并参与认证快照换代。
 * <br>2026/11/03：显式开放实例运行、日志与聊天计划管理资源。
 * <br>2026/11/03：指令台前缀须在本机显式允许，变更撤销旧会话能力。
 * <br>2026/11/03：网页聊天容量作为独立非秘密文件区，认证根配置保持本地管理。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：机器令牌允许短值及空白放行，管理员凭据继续独立校验。
 * <br>2026/11/04：统一空白机器令牌快照，并提供仅替换机器策略的比较入口。
 * <br>2026/11/04：配置分组须由本地显式授权，并参与会话代次及受限应用比较。
 * <br>2026/11/04：聊天配置改为管理员直接访问，旧配置区与分组字段只保留读取兼容。
 *
 * <br>2026/11/06：声明独立空间管理与观测文件白名单，保留显式禁用。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.4-l
 */
@Getter
@Setter
@FileConfig(value = "security", path = "web", type = FileConfigType.YAML, sensitive = true)
public class WebSecurityFileConfig implements IFileConfigData {
    private boolean enabled;
    @NonNull
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String adminToken = "";
    @NonNull
    private String publicOrigin = "";
    @NonNull
    private List<String> trustedProxies = List.of();
    @NonNull
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Map<String, String> machineTokens = Map.of();
    // 2026/11/04：保留旧字段读取兼容；管理员聊天配置已不再使用该白名单。
    @NonNull
    private List<String> settingsSections = List.of("agent");
    // 2026/11/04：保留旧分组字段读取兼容，不再限制管理员可见的聊天配置。
    @NonNull
    private List<String> settingsGroups = List.of();
    // 2026/11/03：认证根配置不进入文件编辑白名单，文件类型仍由本地适配器固定。
    @NonNull
    private List<String> fileSections = List.of("agent.main", "agent.models", "agent.personality", "agent.expression",
            "agent.stickers", "agent.media", "agent.observability", "onebot.main", "web.chat");
    // 2026/11/03：管理资源同样需要本地白名单，不能因为注册插件就自动向公网开放。
    @NonNull
    private List<String> managementResources = List.of("teaneko.agent:activity", "teaneko.agent:memory",
            "teaneko.agent:profiles", "teaneko.agent:personality", "teaneko.agent:layers",
            "teaneko.agent:schedules", "teaneko.agent:library", "teaneko.agent:memory-space",
            "teaneko.agent:personality-space", "teaneko.agent:profile-space", "teaneko.agent:layers-space", "teaneko.core:runtime", "teaneko.core:logs");
    private int idleMinutes = 30;
    @NonNull
    // 2026/11/06：缺省仅开放显式管理来源的内置命令；明确空列表仍关闭全部指令。
    private List<String> consolePrefixes = List.of("/console");
    private int absoluteHours = 8;

    /**
     * 固定配置及凭据，不把坏配置当作未配置令牌。
     * 2026/11/03：管理资源、文件区与指令台白名单均参与快照校验和认证代次。
     * @return 经校验的运行快照
     * @throws IllegalArgumentException 当已启用配置不安全或边界无效时
     */
    public Snapshot snapshot() {
        if (idleMinutes < 5 || idleMinutes > 120 || absoluteHours < 1 || absoluteHours > 24
                || trustedProxies.size() > 16 || machineTokens.size() > 16 || settingsSections.size() > 16
                // 2026/11/04：配置键保留已有管理器的大小写；授权仍按精确标识匹配。
                || settingsSections.stream().anyMatch(value -> !value.matches("[a-zA-Z][a-zA-Z0-9_.-]{0,63}"))
                || settingsSections.stream().distinct().count() != settingsSections.size()
                || settingsGroups.size() > 16
                || settingsGroups.stream().anyMatch(value -> !value.matches("[a-zA-Z][a-zA-Z0-9_.-]{0,63}"))
                || settingsGroups.stream().distinct().count() != settingsGroups.size()
                || fileSections.size() > 32 || fileSections.stream().anyMatch(value ->
                !value.matches("[a-z][a-z0-9_.-]{0,63}"))
                || fileSections.stream().distinct().count() != fileSections.size()
                || managementResources.size() > 32 || managementResources.stream().anyMatch(value ->
                !value.matches("[a-z][a-z0-9_.-]{0,63}:[a-z][a-z0-9.-]{0,63}"))
                || managementResources.stream().distinct().count() != managementResources.size()
                || consolePrefixes.size() > 32 || consolePrefixes.stream().anyMatch(value ->
                        !value.matches("/[a-zA-Z][a-zA-Z0-9_-]{0,63}"))
                || consolePrefixes.stream().distinct().count() != consolePrefixes.size()) {
            throw new IllegalArgumentException("Web 会话期限或接入配置数量无效。");
        }
        if (enabled) {
            URI origin = URI.create(publicOrigin);
            if (!"https".equals(origin.getScheme()) || origin.getHost() == null
                    || origin.getRawUserInfo() != null || origin.getRawQuery() != null
                    || origin.getRawFragment() != null || !origin.getRawPath().isEmpty()) {
                throw new IllegalArgumentException("Web 外部来源必须是没有路径的完整 HTTPS 来源。");
            }
            if (!adminToken.isEmpty()) {
                validateSecret(adminToken);
            }
            machineTokens.forEach((id, secret) -> {
                if (!id.matches("[a-z][a-z0-9_-]{0,63}")) {
                    throw new IllegalArgumentException("机器客户端标识无效。");
                }
                // 2026/11/04：空白机器令牌显式放行；非空值允许 1～256 字符，不包含内部空白。
                if (secret.length() > 256 || !secret.isBlank() && secret.chars().anyMatch(Character::isWhitespace)) {
                    throw new IllegalArgumentException("机器令牌最多二百五十六字符，非空令牌不能包含空白。");
                }
                if (!secret.isBlank() && secret.equals(adminToken)) {
                    throw new IllegalArgumentException("机器凭据必须与管理员令牌分开配置。");
                }
            });
        }
        // 2026/11/04：空白策略归一化，握手证明和后续收发使用完全相同的值。
        var normalizedTokens = new LinkedHashMap<String, String>();
        machineTokens.forEach((id, secret) -> normalizedTokens.put(id, secret.isBlank() ? "" : secret));
        return new Snapshot(enabled, adminToken, publicOrigin, List.copyOf(trustedProxies),
                Map.copyOf(normalizedTokens), List.copyOf(settingsSections), List.copyOf(fileSections), List.copyOf(managementResources),
                List.copyOf(consolePrefixes), idleMinutes, absoluteHours, List.copyOf(settingsGroups));
    }

    /**
     * @param secret 本地配置的随机秘密
     * @throws IllegalArgumentException 当长度或格式不足时
     */
    private static void validateSecret(String secret) {
        if (secret.length() < 32 || secret.length() > 256 || secret.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Web 凭据应为三十二至二百五十六字符的随机值，不能包含空白。");
        }
    }

    /**
     * 只在宿主认证边界保留的配置，不供普通序列化和诊断。
     * <br>2026/11/03：固定本地管理资源白名单。
     * <br>2026/11/03：固定本地文件区白名单，参与会话撤销判定。
     * <br>2026/11/03：固定指令台前缀，修改后撤销旧会话。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：比较机器策略以外的认证根设置，阻止网页顺带应用本机其他改动。
     * <br>2026/11/04：配置分组参与认证代次与受限应用比较，不默认开放新分组。
     *
     * 2026/11/04：旧聊天配置白名单只保留兼容值，不参与配置访问判断。
     * @param enabled 是否启用控制面
     * @param adminToken 根令牌，空值触发本次启动生成
     * @param publicOrigin 唯一外部 HTTPS 来源
     * @param trustedProxies 显式可信代理地址
     * @param machineTokens 独立机器接入凭据
     * @param settingsSections 旧聊天配置区兼容值
     * @param fileSections 本地明确允许的文件区
     * @param managementResources 本地明确允许的管理资源
     * @param consolePrefixes 本地明确允许的指令台前缀
     * @param idleMinutes 空闲期限
     * @param absoluteHours 绝对期限
     * @param settingsGroups 旧配置分组兼容值
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.3-l
     */
    @JsonIgnoreType
    public record Snapshot(boolean enabled, @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String adminToken,
                           String publicOrigin, List<String> trustedProxies,
                           @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) Map<String, String> machineTokens,
                           List<String> settingsSections, List<String> fileSections, List<String> managementResources,
                           List<String> consolePrefixes,
                           int idleMinutes, int absoluteHours, List<String> settingsGroups) {
        /**
         * 2026/11/04：只替换机器策略，其余认证根字段保持原值。
         * @param tokens 已校验且归一化的机器令牌
         * @return 用于确认受限应用范围的快照
         */
        public Snapshot withMachineTokens(Map<String, String> tokens) {
            return new Snapshot(enabled, adminToken, publicOrigin, trustedProxies, Map.copyOf(tokens),
                    settingsSections, fileSections, managementResources, consolePrefixes, idleMinutes, absoluteHours, settingsGroups);
        }

        /** @return 不含配置和令牌的说明 */
        @Override
        @NonNull
        public String toString() {
            return "Web 安全配置快照（敏感值已隐藏）";
        }
    }
}
