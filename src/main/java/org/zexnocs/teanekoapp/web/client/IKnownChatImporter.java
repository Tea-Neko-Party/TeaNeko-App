package org.zexnocs.teanekoapp.web.client;

import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import java.util.concurrent.CompletionStage;

/**
 * 各业务登记配置向聊天目录贡献真实已知定位，不通过浏览器拼接内部键。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public interface IKnownChatImporter {
    /**
     * @param client 实际平台注册实例
     * @param guard 当前管理授权
     * @return 完成一批有界发现
     */
    CompletionStage<Void> discover(ITeaNekoClient client, Runnable guard);
}
