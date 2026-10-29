package org.zexnocs.teanekoagent.personality;

import org.zexnocs.teanekoagent.agent.AgentScope;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 人格引用、补正和备份的事务端口；仅供可信业务调用，聊天管理权限由独立入口校验。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IPersonalityStore {
    /**
     * 原子初始化或切换基础人格：先备份原补正，再建立当前 scope 的新空引用。
     * @param scope 当前作用域
     * @param base 本轮可信基础人格
     * @param now 切换时刻
     * @return 提交后的绑定和补正
     */
    CompletionStage<Selection> resolve(AgentScope scope, BasePersonality base, Instant now);
    /**
     * 只读当前绑定，不初始化或切换。
     * @param scope 原作用域
     * @return 当前绑定
     */
    CompletionStage<Optional<PersonalityBinding>> binding(AgentScope scope);
    /**
     * 供可信管理读取指定补正或备份，不对模型开放任意 key。
     * @param key 已授权逻辑定位
     * @return 独立不可变快照
     */
    CompletionStage<Optional<PersonalityState>> state(String key);
    /**
     * 在固定绑定及集合版本保护下接受一条来源候选，重复标识不再次写入。
     * @param expected 原绑定
     * @param correction 已校验候选
     * @return 提交后是否新增；相同请求相同内容返回 false
     */
    CompletionStage<Boolean> append(PersonalityBinding expected, PersonalityCorrection correction);
    /**
     * 可信管理入口绑定已有或新空补正 key，保留原空间；不能由模型或普通 cfg 直接调用。
     * @param expected 原绑定及预期版本
     * @param key 目标 key
     * @return 已提交绑定
     */
    CompletionStage<PersonalityBinding> bind(PersonalityBinding expected, String key);

    /**
     * 一次事务读取的绑定和补正，不暴露 JPA 对象。
     * @param binding 当前引用
     * @param state 对应补正
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    record Selection(PersonalityBinding binding, PersonalityState state) {}
}
