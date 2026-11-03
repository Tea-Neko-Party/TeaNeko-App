package org.zexnocs.teanekoagent.personality;

import org.zexnocs.teanekoagent.agent.AgentScope;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 独立管理事务端口，复用人格存储；原生权限与配置由可信入口持续复核。
 * <br>2026/11/03：新增同主体管理回执查询。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.7.0-l
 */
public interface IPersonalityManagementStore {
    /**
     * 2026/11/03：查询同主体原回执，不猜测提交结果。
     * @param operationId 原操作
     * @param actor 原主体及目标
     * @param guard 当前授权
     * @return 持久化原结果
     * @throws UnsupportedOperationException 当自定义存储未支持查询时
     */
    default CompletionStage<PersonalityManagementResult> operation(java.util.UUID operationId,
            org.zexnocs.teanekoagent.agent.AgentActorContext actor, Runnable guard) {
        throw new UnsupportedOperationException("当前人格存储未实现管理回执查询。");
    }
    /**
     * 管理查询的原子初始化或基础切换，同事务复核授权。
     * @param scope 原作用域
     * @param base 当前可信基础人格
     * @param now 固定时刻
     * @param guard 当前授权及取消复核
     * @return 当前完整选择
     */
    CompletionStage<IPersonalityStore.Selection> inspect(AgentScope scope, BasePersonality base, Instant now, Runnable guard);
    /**
     * 查询指定 key 最近至多六十四份备份引用，不加载其他域或整个表。
     * @param key 可信管理定位
     * @return 从新到旧的备份 key
     */
    CompletionStage<List<String>> backups(String key);
    /**
     * 在同一事务保存管理结果、备份及操作回执；授权失败使整批回滚。
     * @param command 固定来源及预期版本
     * @param base 本地可信基础人格
     * @param now 原操作时刻
     * @param guard 当前权限、配置及取消复核
     * @return 原提交回执，重放不重复执行
     */
    CompletionStage<PersonalityManagementResult> apply(PersonalityManagementCommand command, BasePersonality base, Instant now, Runnable guard);
}
