package org.zexnocs.teanekoapp.fake_client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.response.api.IGroupMemberResponseData;

import java.time.Instant;

/**
 * 供离线客户端查询使用的群成员响应数据。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FakeGroupMemberResponseData implements IGroupMemberResponseData {
    private @Nullable String nickname;
    private @Nullable String userId;
    private @Nullable String card;
    private @Nullable Instant joinInstant;
    private @Nullable Instant lastSentInstant;
    private @Nullable Integer level;
    private @Nullable String title;
    private @Nullable String role;
}
