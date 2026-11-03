package org.zexnocs.teanekoclient.web;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.client.IClientManagementAdapter;
import org.zexnocs.teanekoapp.web.client.TeaNekoClientManagement;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 网页平台按账号与聊天加入原配置、Agent 管理导航，不将 HTTP 服务存在当作浏览器在线。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
@TeaNekoClientManagement(WebTeaNekoClient.ID)
public final class WebManagementAdapter implements IClientManagementAdapter {
    private final WebChatStore store;

    /** @param store 原持久网页目录 */
    public WebManagementAdapter(WebChatStore store) {
        this.store = store;
    }

    /** @return 已装配 HTTP 能力，零连接不伪装为有在线浏览器 */
    @Override
    public View describe() {
        return new View(WebTeaNekoClient.ID, "网页聊天", "AVAILABLE", 0,
                "使用原登录会话访问；不统计浏览器在线数，断开网页不删除持久聊天。", Instant.now(), true);
    }

    /** @return 当前网页机器人唯一账号 */
    @Override
    public CompletionStage<List<ChatTargets.Account>> accounts() {
        return CompletableFuture.completedStage(List.of(new ChatTargets.Account(WebChat.SELF, "网页机器人", WebChat.SELF, Instant.now())));
    }

    /**
     * @param account 固定网页机器人引用
     * @param after 原页尾引用
     * @param kind 原聊天类型过滤
     * @return 原真实聊天目录
     */
    @Override
    public CompletionStage<ChatTargets.Page> targets(String account, String after, String kind) {
        requireAccount(account);
        if (!Set.of("", "PRIVATE", "GROUP").contains(kind) || after.length() > 64) {
            throw new IllegalArgumentException("网页聊天目录过滤无效。");
        }
        return store.rooms(() -> {}).thenApply(rooms -> {
            var found = rooms.stream().filter(room -> room.id().compareTo(after) > 0
                    && (kind.isEmpty() || kind.equals(room.kind()))).sorted(Comparator.comparing(WebChat.Room::id))
                    .limit(51).toList();
            var values = found.stream().limit(50).map(room -> new ChatTargets.Target(room.id(), room.kind(), room.name(),
                    room.kind().equals("GROUP") ? room.id() : room.members().getFirst().profile(), "PLATFORM", false, room.createdAt())).toList();
            return new ChatTargets.Page(values, found.size() > 50 ? values.getLast().ref() : "", false);
        });
    }

    /**
     * @param account 固定机器人账号
     * @param target 服务端已登记聊天
     * @return 原内部 scope；不接受浏览器自己拼接
     */
    @Override
    public CompletionStage<ChatTargets.Resolved> resolve(String account, String target) {
        requireAccount(account);
        return store.room(target, () -> {}).thenApply(room -> new ChatTargets.Resolved(WebTeaNekoClient.ID, account,
                room.id(), room.scope(), room.name(), room.kind(),
                room.kind().equals("GROUP") ? room.id() : room.members().getFirst().profile(), false));
    }

    /**
     * @param account 原账号引用
     * @throws SecurityException 当请求试图使用其他平台账号时
     */
    private void requireAccount(String account) {
        if (!WebChat.SELF.equals(account)) {
            throw new SecurityException("该账号不属于网页客户端。");
        }
    }
}
