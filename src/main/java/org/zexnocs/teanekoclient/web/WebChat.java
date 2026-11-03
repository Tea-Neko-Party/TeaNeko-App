package org.zexnocs.teanekoclient.web;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 网页平台的持久消息与管理请求，不接收浏览器自报的 TeaNeko UUID 或执行权限。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public final class WebChat {
    public static final String ACTOR = "web.local-admin";
    public static final String SELF = "web-bot";
    public static final String DEFAULT_PROFILE = "web-local-admin";

    /** 禁止实例化协议命名空间。 */
    private WebChat() {}

    /** @return 与网页工具箱一致的固定机器人档案，不冒充任何人类平台身份 */
    public static Profile bot() {
        return new Profile(SELF, UUID.nameUUIDFromBytes("teaneko:web:bot".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "TeaNeko");
    }

    /**
     * 网页平台的独立档案。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 服务端平台账号
     * @param uuid 原 TeaUser 映射
     * @param name 显示昵称
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Profile(String id, UUID uuid, String name) {}

    /**
     * 群成员权限只由专用管理操作变更。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param profile 已存在档案
     * @param card 群名片
     * @param role MEMBER、ADMIN 或 OWNER，不允许 DEBUG
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Member(String profile, String card, String role) {
        /**
         * @param profile 服务端平台号
         * @param card 群名片
         * @param role 受限平台角色
         * @throws IllegalArgumentException 当标识、显示名或角色无效时
         */
        public Member {
            if (profile == null || profile.isBlank() || profile.length() > 64 || card == null || card.isBlank()
                    || card.length() > 80 || role == null || !Set.of("MEMBER", "ADMIN", "OWNER").contains(role)) {
                throw new IllegalArgumentException("网页成员身份、名片或角色无效。");
            }
        }
    }

    /**
     * 持久网页聊天；内部 scope 只交给服务端管理适配器。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 服务端聊天标识
     * @param kind GROUP 或 PRIVATE
     * @param name 显示名
     * @param scope 原作用域映射
     * @param revision 成员配置版本
     * @param members 已授权模拟成员
     * @param createdAt 创建时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Room(String id, String kind, String name, String scope, long revision,
                       List<Member> members, Instant createdAt) {
        /**
         * @param id 固定标识
         * @param kind 聊天种类
         * @param name 显示名
         * @param scope 内部作用域
         * @param revision 成员版本
         * @param members 原成员快照
         * @param createdAt 创建时刻
         */
        public Room {
            members = List.copyOf(members);
        }
    }

    /**
     * 明确支持的消息片段，不存任意平台 JSON。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param kind TEXT、AT、REPLY 或 IMAGE
     * @param value 正文、平台成员号、原消息号或本聊天媒体号
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Part(String kind, String value) {}

    /**
     * 一条已提交消息，不将持久化等同于网页已读。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 真实消息编号
     * @param sequence 聊天内递增游标
     * @param sender 服务端发送者快照
     * @param card 当时群昵称
     * @param role 当时平台角色
     * @param bot 是否机器人消息
     * @param origin 触发来源消息号，入站时为空
     * @param parts 不可变片段
     * @param at 实际保存时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Message(String id, long sequence, Profile sender, String card, String role, boolean bot,
                          String origin, List<Part> parts, Instant at) {
        /**
         * @param id 消息号
         * @param sequence 原游标
         * @param sender 原档案
         * @param card 原名片
         * @param role 原角色
         * @param bot 机器人标记
         * @param origin 原入站消息号
         * @param parts 片段快照
         * @param at 保存时刻
         */
        public Message {
            parts = List.copyOf(parts);
        }
    }

    /**
     * 页面重连只补读当前窗口，不重新触发消息。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param items 当前有界页
     * @param next 已返回的末尾游标
     * @param more 是否还有下一页
     * @param reset 原游标是否已超出保留窗口
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Page(List<Message> items, long next, boolean more, boolean reset) {}

    /**
     * 原消息操作的真实受理事实。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 原编号
     * @param messageId 已保存消息号
     * @param status PERSISTED、DISPATCHED、REJECTED 或 INDETERMINATE
     * @param note 中文事实说明
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Receipt(UUID operationId, String messageId, String status, String note) {}

    /**
     * 发言只选择已登记成员，不接受角色、UUID、scope 或任意图片 URL。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 原编号
     * @param profile 当前聊天内的档案
     * @param text 原文
     * @param mention 是否提及当前网页机器人
     * @param reply 原聊天消息号，空值表示不引用
     * @param media 原聊天上传媒体号，空值表示无图
     * @param observedAt 页面服务端观察时刻
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Send(UUID operationId, String profile, String text, boolean mention, String reply,
                       String media, Instant observedAt) {
        /**
         * @param operationId 原编号
         * @param profile 档案引用
         * @param text 用户原文
         * @param mention 提及信号
         * @param reply 引用号
         * @param media 媒体号
         * @param observedAt 原观察时刻
         * @throws IllegalArgumentException 当必需参数或消息边界无效时
         */
        public Send {
            if (operationId == null || observedAt == null || profile == null || profile.length() > 64
                    || text == null || text.length() > 16384 || reply == null || reply.length() > 128
                    || media == null || media.length() > 64 || text.isBlank() && media.isEmpty()) {
                throw new IllegalArgumentException("网页消息参数无效，需有正文或已上传图片。");
            }
        }
    }

    /**
     * 专用档案管理请求，TeaUser 映射由服务器创建。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 幂等编号
     * @param name 昵称
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record CreateProfile(UUID operationId, String name) {
        /**
         * @param operationId 原编号
         * @param name 原昵称
         * @throws IllegalArgumentException 当必需参数或名称边界无效时
         */
        public CreateProfile {
            if (operationId == null || name == null || name.isBlank() || name.length() > 80) {
                throw new IllegalArgumentException("网页档案创建参数无效。");
            }
        }
    }

    /**
     * 私聊或模拟群创建请求。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 幂等编号
     * @param kind 聊天类型
     * @param name 显示名
     * @param profiles 已登记档案，默认成员角色均为 MEMBER
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record CreateRoom(UUID operationId, String kind, String name, List<String> profiles) {
        /**
         * @param operationId 原编号
         * @param kind 原类型
         * @param name 原名称
         * @param profiles 原成员引用
         * @throws IllegalArgumentException 当必需参数或成员边界无效时
         */
        public CreateRoom {
            if (operationId == null || kind == null || !Set.of("GROUP", "PRIVATE").contains(kind)
                    || name == null || name.isBlank() || name.length() > 80 || profiles == null || profiles.isEmpty()
                    || profiles.size() > 32 || profiles.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("网页聊天创建参数无效。");
            }
            profiles = List.copyOf(profiles);
        }
    }

    /**
     * 成员修改与发消息分离，并通过原版本仲裁。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 幂等编号
     * @param revision 已读聊天版本
     * @param member 增改成员；REMOVE 时仍只使用其 profile
     * @param action UPSERT 或 REMOVE
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record ChangeMember(UUID operationId, long revision, Member member, String action) {
        /**
         * @param operationId 原编号
         * @param revision 原版本
         * @param member 原成员设置
         * @param action 原动作
         * @throws IllegalArgumentException 当动作、成员或角色不允许时
         */
        public ChangeMember {
            if (operationId == null || revision < 0 || member == null || member.profile() == null
                    || action == null || !Set.of("UPSERT", "REMOVE").contains(action)
                    || action.equals("UPSERT") && (member.card() == null || member.card().isBlank()
                    || member.card().length() > 80 || member.role() == null
                    || !Set.of("MEMBER", "ADMIN", "OWNER").contains(member.role()))) {
                throw new IllegalArgumentException("网页成员管理参数无效，不允许 DEBUG 或浏览器自报执行身份。");
            }
        }
    }

    /**
     * 安全启动视图；scope 不作为浏览器路径或身份输入。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param profiles 服务端档案
     * @param rooms 人可理解聊天视图
     * @param self 当前机器人平台号
     * @param observedAt 服务端观察时刻
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Home(List<Profile> profiles, List<RoomView> rooms, String self, Instant observedAt) {}

    /**
     * 不含内部 scope 的聊天视图。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 原聊天引用
     * @param kind 类型
     * @param name 名称
     * @param revision 成员版本
     * @param members 当前成员
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record RoomView(String id, String kind, String name, long revision, List<Member> members) {}
}
