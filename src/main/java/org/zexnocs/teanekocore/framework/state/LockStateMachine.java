package org.zexnocs.teanekocore.framework.state;

import lombok.NonNull;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 线性安全的状态机，允许多个线程同时访问和修改。
 *
 * @author zExNocs
 * @date 2026/02/12
 */
public class LockStateMachine<T extends IState> implements IStateMachine<T> {
    /// 任务状态锁
    protected final ReentrantLock lock = new ReentrantLock();

    /// 当前状态
    private T currentState;

    /// 构造函数，接受初始状态
    public LockStateMachine(@NonNull T initialState) {
        this.currentState = initialState;
        initialState.onEnter();
    }

    /**
     * 获取当前状态。
     *
     * @return 当前状态对象
     */
    @Override
    public T getCurrentState() {
        lock.lock();
        try {
            return currentState;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 是否处于某个状态。
     *
     * @param stateClass 状态类对象
     */
    @Override
    public boolean isInState(Class<? extends T> stateClass) {
        lock.lock();
        try {
            return stateClass.isInstance(currentState);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 切换到新的状态。
     * 切换 → 退出 → 进入。
     * @param newState 新的状态对象
     */
    public void switchState(T newState) {
        T old;
        lock.lock();

        // 切换状态
        try {
            old = currentState;
            currentState = newState;
        } finally {
            lock.unlock();
        }

        // 退出旧状态
        if(old != null) {
            old.onExit();
        }

        // 进入新状态
        if(newState != null) {
            newState.onEnter();
        }
    }

    /**
     * 在某个预期状态下切换到另一个状态。
     * 如果当前不是在预期的状态，则返回 false
     *
     * @param expectedState 预期的当前状态类
     * @param newState      新的状态
     * @return 是否切换成功
     */
    @Override
    public boolean switchStateUnderExpected(Class<? extends T> expectedState, T newState) {
        T old;

        lock.lock();
        try {
            if(!expectedState.isInstance(currentState)) {
                return false;
            }
            old = currentState;
            currentState = newState;
        } finally {
            lock.unlock();
        }

        // 退出旧状态
        old.onExit();

        // 进入新状态
        if(newState != null) {
            newState.onEnter();
        }
        return true;
    }
}
