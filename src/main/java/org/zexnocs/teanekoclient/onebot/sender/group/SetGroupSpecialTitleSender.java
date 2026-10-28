package org.zexnocs.teanekoclient.onebot.sender.group;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.data.send.params.group.SetGroupSpecialTitleRequestSendParamsData;
import org.zexnocs.teanekoclient.onebot.sender.AbstractOnebotSender;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 设置群头衔发送器
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.12
 * @version 4.5.0-l
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
     * @param groupId      群号
     * @param userId       成员QQ号
     * @param specialTitle 头衔，长度限制为6个汉字
     * @param duration     头衔有效期，单位为秒
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    public void setGroupSpecialTitle(long groupId, long userId, String specialTitle, long duration) {
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
        send(paramsData, Duration.ZERO, 3, Duration.ofMillis(200));
    }

    /**
     * 设置群头衔
     *
     * @param groupId      群号
     * @param userId       成员QQ号
     * @param specialTitle 头衔，长度限制为6个汉字
     */
    public void setGroupSpecialTitle(long groupId, long userId, String specialTitle) {
        setGroupSpecialTitle(groupId, userId, specialTitle, 0);
    }

    /**
     * 提交群头衔变更并等待平台回执，实际限制仍由原业务和发送链执行。
     *
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     * @param specialTitle 需要提交的群头衔文本
     * @param duration 需要换算或推进的时长
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    public CompletionStage<Void> setGroupSpecialTitleAsync(long groupId, long userId, String specialTitle, long duration) {
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
        return sendAsync(paramsData, Duration.ZERO, 3, Duration.ofMillis(200));
    }
    /**
     * 提交群头衔变更并等待平台回执，实际限制仍由原业务和发送链执行。
     *
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     * @param title 需要提交的群头衔文本
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> setGroupSpecialTitleAsync(long groupId, long userId, String title) {
        return setGroupSpecialTitleAsync(groupId, userId, title, 0);
    }
}
