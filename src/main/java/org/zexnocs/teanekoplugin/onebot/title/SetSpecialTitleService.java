package org.zexnocs.teanekoplugin.onebot.title;

import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoclient.onebot.sender.group.SetGroupSpecialTitleSender;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.database.easydata.debug.DebugEasyData;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设置特殊头衔服务。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.1
 * @version 4.5.0-l
 */
@Service("setSpecialTitleService")
public class SetSpecialTitleService {
    public static final String DEBUG_NAMESPACE = "头衔修改";

    private final SetGroupSpecialTitleSender setGroupSpecialTitleSender;
    private final ITimerService timerService;

    private final Map<String, Boolean> closedMap = new ConcurrentHashMap<>();

    /**
     * 注入头衔平台端口与定时服务，限制状态按原规则清理。
     *
     * @param setGroupSpecialTitleSender 由装配方提供的 {@code SetGroupSpecialTitleSender} 协作依赖
     * @param timerService 由装配方提供的 {@code ITimerService} 协作依赖
     */
    @Autowired
    public SetSpecialTitleService(SetGroupSpecialTitleSender setGroupSpecialTitleSender,
                                  ITimerService timerService) {
        this.setGroupSpecialTitleSender = setGroupSpecialTitleSender;
        this.timerService = timerService;
    }

    /**
     * 注册每日头衔限制清理任务，不让未来定时运行阻塞当前命令完成。
     */
    @PostConstruct
    public void init() {
        timerService.registerByCron("清理限制性特殊头衔close列表",
                "set-special-title-close-cleaner",
                () -> {
                    closedMap.clear();
                    return EmptyTaskResult.INSTANCE;
                },
                "0 0 0 * * ?", // 每天凌晨0点执行
                EmptyTaskResult.getResultType());
    }

    /**
     * 直接设置特殊头衔。
     *
     * @param groupId 群号
     * @param userId  用户号
     * @param title   特殊头衔
     * @return 承载本次异步处理实际结果的可组合完成阶段
     */
    public CompletionStage<@Nullable String> setGroupSpecialTitle(long groupId, long userId, String title) {
        if(title.length() > 6) {
            return CompletableFuture.completedStage("头衔长度不能超过6个字符喵！");
        }
        var debugDto = DebugEasyData
                .of(DEBUG_NAMESPACE)
                .get(groupId + "_" + userId);
        // 记录设置特殊头衔的时间和内容
        return setGroupSpecialTitleSender.setGroupSpecialTitleAsync(groupId, userId, title)
                .thenCompose(ignored -> debugDto.getTaskConfig("设置特殊头衔")
                .set(ChinaDateUtil.Instance.getNowDateString(), title)
                .pushAsync()).thenApply(ignored -> null);
    }

    /**
     * 从限制设置特殊头衔的列表中移除。
     *
     * @param groupId 群号
     * @param userId  用户号
     * @return 返回要回复的消息内容。
     */
    public String removeLimit(Long groupId, Long userId) {
        var key = groupId + "_" + userId;
        if (closedMap.getOrDefault(key, false)) {
            closedMap.remove(key);
            return "可以重新设头衔了喵~";
        }
        return "今天还没有设置过头衔喵~";
    }

    /**
     * 限制设置特殊头衔。
     * 每日只能设置一次。
     *
     * @param groupId 群号
     * @param userId  用户号
     * @param title   特殊头衔
     * @return 承载本次异步处理实际结果的可组合完成阶段
     */
    public CompletionStage<@Nullable String> setGroupSpecialTitleWithLimit(long groupId, long userId, String title) {
        // 检测是否有 “猫、喵、🐱"
        if (!title.contains("猫") && !title.contains("喵") && !title.contains("🐱")) {
            title += "猫"; // 如果没有包含，则添加 "猫"
        }
        if(title.length() > 6) {
            return CompletableFuture.completedStage("头衔长度不能超过6个字符喵！");
        }
        var key = groupId + "_" + userId;
        if (closedMap.putIfAbsent(key, true) != null) {
            return CompletableFuture.completedStage("主人今天已经设置过头衔了喵~ 明天再来吧！");
        }
        return setGroupSpecialTitle(groupId, userId, title).whenComplete((ignored, failure) -> {
            if (failure != null) closedMap.remove(key);
        });
    }
}
