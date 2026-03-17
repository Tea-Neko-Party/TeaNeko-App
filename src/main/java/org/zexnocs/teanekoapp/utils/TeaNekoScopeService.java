package org.zexnocs.teanekoapp.utils;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.framework.pair.Pair;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 用于序列化和反序列化 scope 的服务
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 */
@Service
public class TeaNekoScopeService {
    /// group 的 scope ID 的正则表达式
    private final Pattern GROUP_PATTERN = Pattern.compile("(.+)-group@(.+)");
    private final TeaNekoClientScanner teaNekoClientScanner;

    public TeaNekoScopeService(TeaNekoClientScanner teaNekoClientScanner) {
        this.teaNekoClientScanner = teaNekoClientScanner;
    }

    /**
     * 根据用户 UUID 获取作用域 ID。
     *
     * @param uuid 用户 UUID
     * @return {@link String } 作用域 ID
     */
    public String getPrivateScopeId(UUID uuid) {
        return "private@" + uuid;
    }

    /**
     * 根据 client 和 groupId 获取作用域 ID。
     *
     * @param client  客户端
     * @param groupId 群 ID
     * @return 作用域 ID
     */
    public String getGroupScopeId(ITeaNekoClient client, String groupId) {
        return client.getClientId() + "-group@" + groupId;
    }

    /**
     * 根据 group scope id 获取到 client 和 groupID
     *
     * @param groupScopeId scope ID
     * @return (client, groupID)
     * @throws IllegalArgumentException 解析失败
     */
    public Pair<ITeaNekoClient, String> fromGroupScopeId(String groupScopeId) throws IllegalArgumentException {
        var matcher = GROUP_PATTERN.matcher(groupScopeId);
        if(matcher.matches()) {
            String clientId = matcher.group(1);
            String groupId = matcher.group(2);
            var clientPair = teaNekoClientScanner.getPair(clientId);
            if(clientPair == null) {
                throw new IllegalArgumentException("无法找到对应的客户端，clientId: " + clientId);
            }
            return IndependentPair.of(clientPair.second(), groupId);
        }
        throw new IllegalArgumentException("无法解析 group scope ID: " + groupScopeId);
    }
}
