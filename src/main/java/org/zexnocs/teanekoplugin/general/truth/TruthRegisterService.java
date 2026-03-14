package org.zexnocs.teanekoplugin.general.truth;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekoplugin.general.dice.event.DiceAfterEvent;
import org.zexnocs.teanekoplugin.general.dice.event.DiceSetEvent;

/**
 * 真心话大冒险注册服务，监听骰子事件，注册参与者，并禁止在游戏期间使用骰子设置。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@EventListener
public class TruthRegisterService {
    private final TruthService truthService;

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
        truthService.join(attempt.getMessageData(),
                data.getDiceResultList().getFirst(),
                System.currentTimeMillis());
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
        data.getMessageReceiveData()
                .getMessageSender()
                .sendReplyMessage("真心话游戏期间禁止设置骰子！");
    }
}
