package org.zexnocs.teanekoapp.teauser;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 以 TeaNeko 用户查询已有客户端映射，不创建用户，也不执行发现扫描。
 * <br>2026/11/06：供跨客户端管理页面显示号码和最近观察昵称。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
@Service
public final class TeaUserDirectory {
    private final TeaNekoClientScanner clients;
    private final ITeaUserService users;
    private final ChatIdentityService identities;

    /**
     * @param clients 已发布的客户端注册表
     * @param users 原用户映射查询
     * @param identities 公开资料查询
     */
    public TeaUserDirectory(TeaNekoClientScanner clients, ITeaUserService users, ChatIdentityService identities) {
        this.clients = clients;
        this.users = users;
        this.identities = identities;
    }

    /**
     * @param subject 已存在的主体
     * @return 已知平台标识与昵称，无映射时为空
     */
    public List<Mapping> mappings(UUID subject) {
        var found = new ArrayList<Mapping>();
        for (var entry : clients.getHandlerMap().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            var client = entry.getValue().second();
            String platformId = users.getPlatformId(client, subject);
            if (platformId != null) {
                String nickname = identities.user(client.getClientId(), subject).map(ChatIdentityService.Identity::nickname).orElse("");
                found.add(new Mapping(client.getClientId(), platformId, nickname));
            }
        }
        return List.copyOf(found);
    }

    /**
     * 不包含权限或认证信息的用户映射。
     * @param client 客户端标识
     * @param platformId 平台用户号
     * @param nickname 最近观察昵称
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.8.4-l
     */
    public record Mapping(String client, String platformId, String nickname) {}
}
