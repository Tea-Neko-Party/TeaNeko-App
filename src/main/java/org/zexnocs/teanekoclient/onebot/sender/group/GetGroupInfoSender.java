package org.zexnocs.teanekoclient.onebot.sender.group;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.response.params.GroupListResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.params.group.GetGroupInfoParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * 有时限的单群信息读取，不通过全群列表实现当前群查询。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.6.0-l
 */
@Component
public class GetGroupInfoSender extends AbstractOnebotSender<GetGroupInfoParamsData, GroupListResponseData> {
    /**
     * 借用既有 OneBot 发送与序列化资源。
     * @param senders 原发送服务
     * @param client 原生 OneBot 连接
     * @param mapper 协议转换器
     */
    public GetGroupInfoSender(ISenderService senders, OnebotClient client, @Qualifier("onebotObjectMapper") ObjectMapper mapper) {
        super(senders, client, mapper);
    }

    /**
     * 查询单个群，响应 ID 必须与原请求相同；失败不伪装成空群。
     * @param groupId 宿主固定的正整数群号
     * @return 实际资料完成阶段，失败时异常结束
     * @throws IllegalArgumentException 当群号不是正整数时
     */
    public CompletionStage<GroupListResponseData> get(String groupId) {
        long id = Long.parseLong(groupId);
        if (id <= 0) { throw new IllegalArgumentException("群号必须为正整数。"); }
        return sendWithFuture(new GetGroupInfoParamsData(id, false), Duration.ZERO, 0, Duration.ZERO)
                .asStage().toCompletableFuture().orTimeout(8, TimeUnit.SECONDS).thenApply(result -> {
            if (!result.isSuccess() || result.getResult() == null || result.getResult().size() != 1) {
                throw new IllegalStateException("OneBot 群资料读取未成功。");
            }
            var value = result.getResult().getFirst();
            if (value.getGroupId() != id) { throw new IllegalStateException("OneBot 群资料来源不匹配。"); }
            return value;
        });
    }
}
