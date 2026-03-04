package org.zexnocs.teanekoclient.onebot.sender.request;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.send.params.request.SetGroupSpecialTitleRequestSendParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

/**
 * 设置群头衔发送器
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
@SuppressWarnings("rawtypes")
@Component("setGroupSpecialTitleSender")
public class SetGroupSpecialTitleSender extends AbstractOnebotSender<SetGroupSpecialTitleRequestSendParamsData, Map> {

    /**
     * 构造函数，初始化发送器。
     *
     * @param senderService 发送服务实例，用于将发送数据推送给客户端，并处理响应数据
     * @param client        要推送数据的客户端
     * @param mapper        mapper，建议使用 {@code @Qualifier("onebotObjectMapper") } 的 mapper
     */
    @Autowired
    public SetGroupSpecialTitleSender(ISenderService senderService,
                                      OnebotClient client,
                                      @Qualifier("onebotObjectMapper") ObjectMapper mapper) {
        super(senderService, client, mapper);
    }

    /**
     * 设置群头衔
     *
     * @param token        发送器发送环境的标识符
     * @param groupId      群号
     * @param userId       成员QQ号
     * @param specialTitle 头衔，长度限制为6个汉字
     * @param duration     头衔有效期，单位为秒
     */
    public void setGroupSpecialTitle(String token, long groupId, long userId, String specialTitle, long duration) {
        if (specialTitle == null) {
            specialTitle = "";
        }
        if(specialTitle.length() > 6) {
            throw new IllegalArgumentException("头衔长度不能超过6个汉字");
        }

        SetGroupSpecialTitleRequestSendParamsData paramsData = SetGroupSpecialTitleRequestSendParamsData
                .builder()
                .groupId(groupId)
                .userId(userId)
                .specialTitle(specialTitle)
                .duration(duration)
                .build();
        send(token, paramsData, Duration.ZERO, 3, Duration.ofMillis(200));
    }

    /**
     * 设置群头衔
     *
     * @param token        发送器发送环境的标识符
     * @param groupId      群号
     * @param userId       成员QQ号
     * @param specialTitle 头衔，长度限制为6个汉字
     */
    public void setGroupSpecialTitle(String token, long groupId, long userId, String specialTitle) {
        setGroupSpecialTitle(token, groupId, userId, specialTitle, 0);
    }
}
