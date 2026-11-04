package org.zexnocs.teanekoapp.teauser;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataObject;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 记录平台已观察的聊天与成员公开资料，显示名称不参与身份映射或权限判定。
 * 客户端和真实 scope 隔离群名片；空名称不覆盖已知名称，迟到消息不回退新资料。
 *
 * 修改记录：<br>
 * 2026/11/04：提供所有客户端共享的持久资料入口，供目录和业务服务读取。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.7-l
 * @version 4.7.7-l
 */
@Service
public final class ChatIdentityService {
    private final IEasyDataService data;
    private final ObjectMapper mapper;

    /**
     * @param data 已封装的 EasyData DTO 入口
     * @param mapper 项目转换器
     */
    public ChatIdentityService(IEasyDataService data, ObjectMapper mapper) {
        this.data = data;
        this.mapper = mapper;
    }

    /**
     * @param client 已验证平台标识
     * @return 应用资料域，不使用 Agent 专用数据库
     */
    private IEasyDataDto dto(String client) {
        return data.getEasyDataFor(GeneralEasyDataObject.class, "teaneko.identity", client);
    }

    /**
     * @param client 平台
     * @param scope 原 scope
     * @return 已知聊天名称，尚未观察时为空
     */
    public String name(String client, String scope) {
        return Optional.ofNullable(dto(client).get("scope:" + StableDigest.sha256(scope), Identity.class))
                .map(Identity::scopeName).orElse("");
    }

    /**
     * @param client 平台
     * @param scope 群或私聊定位
     * @param user 原 TeaNeko 用户 UUID
     * @return 当前聊天内最后观察的用户资料，不作为授权证明
     */
    public Optional<Identity> member(String client, String scope, UUID user) {
        return Optional.ofNullable(dto(client).get("member:" + StableDigest.sha256(scope, user.toString()), Identity.class));
    }

    /**
     * 在同一 DTO 事务中保留聊天名和具体成员，只有真实入站可以调用。
     * @param message 原可信入站消息，不读取消息正文
     * @return 资料实际提交完成信号
     */
    public CompletionStage<Void> observe(ITeaNekoMessageData message) {
        var user = message.getUserData();
        boolean group = message.getMessageType() == TeaNekoMessageType.GROUP;
        String name = group ? message.getScopeName() : user.getNickname();
        var value = new Identity(message.getClient().getClientId(), message.getScopeId(), bounded(name),
                user.getUuid(), user.getUserIdInPlatform(), bounded(user.getNickname()),
                bounded(message.getSenderGroupNickname()), group ? "GROUP" : "PRIVATE", message.getTime());
        var dto = dto(value.client());
        var task = dto.getTaskConfig("更新聊天与成员资料");
        task.addTransactionTask(() -> {
            var transaction = dto.atomic();
            for (String key : java.util.List.of("scope:" + StableDigest.sha256(value.scope()),
                    "member:" + StableDigest.sha256(value.scope(), value.user().toString()))) {
                var row = transaction.find(key, EasyDataLock.WRITE);
                var old = row == null ? null : mapper.readValue(row.getValue(), Identity.class);
                if (old != null && old.observedAt().isAfter(value.observedAt())) {
                    continue;
                }
                var previousMember = old != null && old.user().equals(value.user()) ? old : null;
                var effective = new Identity(value.client(), value.scope(),
                        nameOrPrevious(value.scopeName(), old == null ? "" : old.scopeName()), value.user(),
                        value.platformId(), nameOrPrevious(value.nickname(), previousMember == null ? "" : previousMember.nickname()),
                        nameOrPrevious(value.card(), previousMember == null ? "" : previousMember.card()), value.kind(), value.observedAt());
                var body = mapper.writeValueAsString(effective);
                if (row == null) {
                    transaction.save(transaction.create(key, body));
                } else {
                    row.setValue(body);
                    transaction.save(row);
                }
            }
        });
        return task.pushAsync();
    }

    /**
     * @param value 新资料
     * @param previous 已知资料
     * @return 空值不会清除已知名称
     */
    private static String nameOrPrevious(String value, String previous) {
        return value.isBlank() || value.equals("user") ? previous : value;
    }

    /**
     * @param value 平台显示文本
     * @return 有界资料，不含消息正文
     */
    private static String bounded(String value) {
        return value.length() > 128 ? value.substring(0, 128) : value;
    }

    /**
     * 一次实际入站观察的公开资料，角色与认证令牌不存入该视图。
     *
     * @param client 平台
     * @param scope 原聊天定位
     * @param scopeName 聊天名称
     * @param user TeaNeko 用户 UUID
     * @param platformId 平台用户号
     * @param nickname 昵称
     * @param card 当前群名片
     * @param kind 群或私聊
     * @param observedAt 原消息时间
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.7-l
     * @version 4.7.7-l
     */
    public record Identity(String client, String scope, String scopeName, UUID user, String platformId,
                           String nickname, String card, String kind, Instant observedAt) {}
}
