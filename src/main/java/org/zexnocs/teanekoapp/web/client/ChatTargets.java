package org.zexnocs.teanekoapp.web.client;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import java.time.Instant;
import java.util.List;

/**
 * 平台聊天目录的安全投影，不把内部配置键当作用户界面或授权凭据。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public final class ChatTargets {
    /** 禁止实例化数据命名空间。 */
    private ChatTargets() {}

    /**
     * 观察到的真实账号或明确标记的未归属历史目录。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param ref 不透明账号引用
     * @param name 中文名称
     * @param platformId 真实账号号，历史目录为空
     * @param observedAt 最近发现时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Account(String ref, String name, String platformId, Instant observedAt) {}

    /**
     * 当前页面中的群或私聊，不公开内部 scope。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param ref 不透明聊天引用
     * @param kind GROUP 或 PRIVATE
     * @param name 已知名称或真实号码回退
     * @param platformId 实际群号或用户号
     * @param source MESSAGE、PLATFORM 或 CONFIG
     * @param shared 是否可能与其他账号或客户端共享配置
     * @param observedAt 元数据观测时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Target(String ref, String kind, String name, String platformId,
                         String source, boolean shared, Instant observedAt) {}

    /**
     * 固定账号内的有界页面。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param items 当前页
     * @param next 下页游标
     * @param importMore 是否还有已登记配置可继续发现
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Page(List<Target> items, String next, boolean importMore) {}

    /**
     * 宿主解析后的实际目标，仅在管理应用层使用。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param clientId 原平台
     * @param accountRef 原账号引用
     * @param ref 原目标引用
     * @param scope 原已验证配置键
     * @param name 人可理解名称
     * @param kind 群或私聊
     * @param platformId 实际平台号码，用于提交时复核身份映射
     * @param shared 共享影响
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @JsonIgnoreType
    public record Resolved(String clientId, String accountRef, String ref, String scope,
                           String name, String kind, String platformId, boolean shared) {}

    /**
     * 来自可信原生消息、平台查询或配置映射的观察，不接收浏览器提交。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param clientId 实际平台
     * @param self 真实机器人账号，未知历史归属为空
     * @param kind 群或私聊
     * @param platformId 原平台号码
     * @param scope 原实际配置键
     * @param name 已知名称，缺失时为空
     * @param source 元数据来源
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @JsonIgnoreType
    public record Observation(String clientId, String self, String kind, String platformId,
                              String scope, String name, String source) {}
}
