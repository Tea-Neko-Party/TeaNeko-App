package org.zexnocs.teanekoplugin.onebot.common.title;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.zexnocs.teanekoplugin.support.PluginTaskScope;

import org.zexnocs.teanekocore.plugin.IPluginLifecycle;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoclient.onebot.sender.group.SetGroupSpecialTitleSender;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.TimerDisplay;
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
 * <br>2026/11/04：声明定时任务分类及网页控制边界。
 * <br>2026/11/04：补全定时任务的详细中文介绍，原调度和控制意图保持不变。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 * <br>2026/11/07：异步副作用前复核固定插件代次，停用后拒绝迟到业务。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.1
 * @version 4.9.0-l
 */
@Service("setSpecialTitleService")
// 2026/11/04：展示声明不改变原任务调度和生命周期。
// 2026/11/04：补充任务用途、执行边界与网页控制说明。
@TimerDisplay(category = "client.maintenance", categoryName = "客户端维护", name = "每日头衔限制清理",
        description = "按每日零点的 cron 规则清空特殊头衔功能的当日限制缓存，恢复下一周期的操作机会。"
        + "清理只影响本地限制记录，不批量修改群成员的现有头衔，也不代替设置头衔时的平台权限校验。"
        + "这是只读维护任务，实际触发时区以当前调度规则为准。", controllable = false)
@PluginOwned("onebot.common")
public class SetSpecialTitleService implements IPluginLifecycle {
    // 2026/11/07：异步业务固定入口代次，停用后不启动后续副作用。
    private final PluginAccess plugins;
    // 2026/11/07：每次启用单独登记定时器和在途工作，旧代次不可恢复。
    private @Nullable PluginTaskScope lifecycle;

    public static final String DEBUG_NAMESPACE = "头衔修改";

    private final SetGroupSpecialTitleSender setGroupSpecialTitleSender;
    private final ITimerService timerService;

    private final Map<String, Boolean> closedMap = new ConcurrentHashMap<>();

    /**
     * 注入头衔平台端口与定时服务，限制状态按原规则清理。
     *
     * @param setGroupSpecialTitleSender 由装配方提供的 {@code SetGroupSpecialTitleSender} 协作依赖
     * @param timerService 由装配方提供的 {@code ITimerService} 协作依赖
     * @param plugins 统一插件代次访问入口
     */
    @Autowired
    public SetSpecialTitleService(SetGroupSpecialTitleSender setGroupSpecialTitleSender,
                                  ITimerService timerService, PluginAccess plugins) {
        this.plugins = plugins;
        this.setGroupSpecialTitleSender = setGroupSpecialTitleSender;
        this.timerService = timerService;
    }

    /**
     * 注册每日头衔限制清理任务，不让未来定时运行阻塞当前命令完成。
     * <br>2026/11/07：由插件启用触发，每次启用使用独立定时器代次。
     * @return 定时器已完成登记的阶段
     */
    @Override
    public synchronized CompletionStage<Void> start() {
        if (lifecycle != null) {
            return CompletableFuture.completedStage(null);
        }
        var scope = new PluginTaskScope();
        lifecycle = scope;

        scope.own(timerService.registerByCron("清理限制性特殊头衔close列表",
                "set-special-title-close-cleaner",
                () -> scope.execute(() -> {
                    closedMap.clear();
                    return EmptyTaskResult.INSTANCE;
                }),
                "0 0 0 * * ?", // 每天凌晨0点执行
                EmptyTaskResult.getResultType()));
        return CompletableFuture.completedStage(null);
    }

    /** @return 2026/11/07：所属业务插件的稳定标识。 */
    @Override
    public String pluginId() {
        return "onebot.common";
    }

    /** @return 2026/11/07：插件内部唯一的后台任务标识。 */
    @Override
    public String id() {
        return "title-cleanup";
    }

    /**
     * 2026/11/07：关闭本代定时入口并等待正在执行的回调，不销毁借用资源。
     * @return 本代后台工作已结束的阶段
     */
    @Override
    public synchronized CompletionStage<Void> stop() {
        var scope = lifecycle;
        lifecycle = null;
        return scope == null ? CompletableFuture.completedStage(null) : scope.stop();
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
        // 2026/11/07：固定本次业务代次，异步恢复时必须复核。
        var pluginToken = plugins.captureOwner("onebot.common");
        if(title.length() > 6) {
            return CompletableFuture.completedStage("头衔长度不能超过6个字符喵！");
        }
        var debugDto = DebugEasyData
                .of(DEBUG_NAMESPACE)
                .get(groupId + "_" + userId);
        // 记录设置特殊头衔的时间和内容
        return setGroupSpecialTitleSender.setGroupSpecialTitleAsync(groupId, userId, title)
                .thenCompose(ignored -> {
                    plugins.check(pluginToken);
                    return debugDto.getTaskConfig("设置特殊头衔")
                            .set(ChinaDateUtil.Instance.getNowDateString(), title)
                            .addTransactionTask(() -> plugins.check(pluginToken))
                            .pushAsync();
                }).thenApply(ignored -> null);
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
