package org.zexnocs.teanekocore.framework.state;

/**
 * 状态接口，表示当前对象处在的状态。
 *
 * @author zExNocs
 * @date 2026/02/12
 */
public interface IState {
    /**
     * 进入状态时的处理逻辑。
     */
    default void onEnter() {}

    /**
     * 离开状态时的处理逻辑。
     */
    default void onExit() {}

    /**
     * 使用当前时间更新状态。
     * delta 时间请在状态内部进行计算。
     * @param currentTimeInMillis 当前时间，单位为毫秒
     */
    default void update(long currentTimeInMillis) {}
}
