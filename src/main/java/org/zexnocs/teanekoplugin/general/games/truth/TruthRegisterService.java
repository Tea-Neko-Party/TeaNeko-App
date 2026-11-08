package org.zexnocs.teanekoplugin.general.games.truth;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekoplugin.general.games.dice.event.DiceAfterEvent;
import org.zexnocs.teanekoplugin.general.games.dice.event.DiceSetEvent;

import java.time.Instant;

/**
 * 真心话大冒险注册服务，监听骰子事件，注册参与者，并禁止在游戏期间使用骰子设置。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.9.0-l
 */
@EventListener
@PluginOwned("general.games")
public class TruthRegisterService {
    private final TruthService truthService;

    /**
     * 关联真心话服务，供原事件入口完成参与者登记。
     *
     * @param truthService 由装配方提供的 {@code TruthService} 协作依赖
     */
    @Autowired
    public TruthRegisterService(TruthService truthService) {
        this.truthService = truthService;
    }

    /**
     * 监听骰子事件，注册真心话大冒险参与者
     *
     * @param event 骰子事件
     */
    @EventHandler
    public void onTruthRegister(DiceAfterEvent event) {
        var data = event.getData();
        var attempt = data.getAttemptDiceData();
        // 判断是否是群聊的骰子事件
        if (attempt == null || data.getDiceResultList().isEmpty()) {
            return;
        }

        // 判断该群聊是否在进行真心话大冒险
        if (truthService.isNotRunning(attempt.getDiceData().getScopeId())) {
            return;
        }

        // 设置不使用默认通知
        data.setUseDefaultNotification(false);
        // 注册数据
        event.includeCompletion(truthService.join(attempt.getMessageData(),
                data.getDiceResultList().getFirst(),
                Instant.now()));
    }

    /**
     * 监听骰子设置事件，禁止在真心话大冒险中使用骰子设置。
     *
     * @param event 骰子设置事件
     */
    @EventHandler
    public void onTruthDiceSetting(DiceSetEvent event) {
        var data = event.getData();
        // 判断该群聊是否在进行真心话大冒险
        if (truthService.isNotRunning(data.getMessageReceiveData().getScopeId())) {
            return;
        }
        // 取消该事件
        event.setCancelled(true);
        event.includeCompletion(data.getMessageReceiveData()
                .getMessageSender().sendReplyMessageAsync("真心话游戏期间禁止设置骰子！"));
    }
}
