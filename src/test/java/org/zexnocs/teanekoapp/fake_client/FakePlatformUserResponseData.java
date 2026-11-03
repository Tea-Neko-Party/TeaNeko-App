package org.zexnocs.teanekoapp.fake_client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekoapp.response.api.IPlatformUserResponseData;

/**
 * 供离线用户查询使用的模拟平台资料。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FakePlatformUserResponseData implements IPlatformUserResponseData {
    private String platformUserId;
    private int level;
    private String nickname;
}
