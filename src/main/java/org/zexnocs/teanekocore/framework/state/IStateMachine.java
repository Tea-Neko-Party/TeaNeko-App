package org.zexnocs.teanekocore.framework.state;

/**
 * 状态机接口，表示一个对象的状态转换逻辑。
 *
 * @param <T> 状态机的状态类型，必须是 IState 的子类
 * @author zExNocs
 * @date 2026/02/12
 */
public interface IStateMachine<T extends IState> {
    /**
     * 获取当前状态。
     * @return 当前状态对象
     */
    T getCurrentState();

    /**
     * 是否处于某个状态。
     * @param stateClass 状态类对象
     */
    boolean isInState(Class<? extends T> stateClass);

    /**
     * 切换到新的状态。
     * 切换 → 退出 → 进入。
     * @param newState 新的状态对象
     */
    void switchState(T newState);

    /**
     * 在某个预期状态下切换到另一个状态。
     * 如果当前不是在预期的状态，则返回 false
     *
     * @param expectedState 预期的当前状态类
     * @param newState      新的状态
     * @return 是否切换成功
     */
    boolean switchStateUnderExpected(Class<? extends T> expectedState, T newState);

    /**
     * 使用当前时间更新状态机。
     * delta 时间请在状态机内部进行计算。
     * @param currentTimeInMillis 当前时间，单位为毫秒
     */
    default void update(long currentTimeInMillis) {}
}
