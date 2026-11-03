package org.zexnocs.teanekoapp.web.client;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 有界聊天元数据目录端口，不提供直接数据库或任意 scope 写入能力。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public interface IChatTargetStore {
    /**
     * @param client 原平台
     * @param self 来自真实连接或生命周期的机器人号
     * @return 实际记录账号
     */
    CompletionStage<Void> rememberAccount(String client, String self);

    /**
     * @param client 实际平台
     * @return 已知账号，含显式未归属历史目录
     */
    CompletionStage<List<ChatTargets.Account>> accounts(String client);

    /**
     * @param client 原平台
     * @param account 原账号引用
     * @param after 原页尾引用
     * @param kind 空、GROUP 或 PRIVATE
     * @return 至多五十项的稳定页
     */
    CompletionStage<ChatTargets.Page> page(String client, String account, String after, String kind);

    /**
     * @param client 原平台
     * @param account 原账号
     * @param target 原聊天
     * @return 与三个定位完全一致的真实映射
     */
    CompletionStage<ChatTargets.Resolved> resolve(String client, String account, String target);

    /**
     * @param values 可信且有界的观察
     * @param guard 提交前授权或来源检查
     * @return 实际完成的元数据写入
     */
    CompletionStage<Void> remember(List<ChatTargets.Observation> values, Runnable guard);

    /**
     * @param client 原平台
     * @return 已登记配置的发现游标
     */
    CompletionStage<String> importCursor(String client);

    /**
     * @param client 原平台
     * @param cursor 下一游标，结束时为特殊完成标识
     * @return 实际保存
     */
    CompletionStage<Void> imported(String client, String cursor);
}
