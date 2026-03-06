package org.zexnocs.teanekoclient.onebot.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.command.TeaNekoCommandConverter;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekocore.event.core.EventScanner;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

/**
 * 允许在 event 之间共享数据的类。
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Component
public class OnebotEventShareComponent {
    /// 日志
    public final ILogger logger;

    /// 事件扫描器
    public final EventScanner eventScanner;

    /// object mapper
    public final ObjectMapper objectMapper;

    /// onebot tea neko 客户端
    public final OnebotTeaNekoClient onebotTeaNekoClient;

    /// tea user service
    public final ITeaUserService iTeaUserService;

    /// 事件服务
    public final IEventService iEventService;

    /// tea neko converter
    public final TeaNekoCommandConverter teaNekoCommandConverter;

    @Autowired
    public OnebotEventShareComponent(ILogger logger,
                                     EventScanner eventScanner,
                                     @Qualifier("onebotObjectMapper") ObjectMapper customObjectMapper,
                                     OnebotTeaNekoClient onebotTeaNekoClient,
                                     ITeaUserService iTeaUserService,
                                     IEventService iEventService,
                                     TeaNekoCommandConverter teaNekoCommandConverter) {
        this.logger = logger;
        this.eventScanner = eventScanner;
        this.objectMapper = customObjectMapper;
        this.onebotTeaNekoClient = onebotTeaNekoClient;
        this.iTeaUserService = iTeaUserService;
        this.iEventService = iEventService;
        this.teaNekoCommandConverter = teaNekoCommandConverter;
    }
}
