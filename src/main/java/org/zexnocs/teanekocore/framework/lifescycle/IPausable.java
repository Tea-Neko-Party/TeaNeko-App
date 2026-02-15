package org.zexnocs.teanekocore.framework.lifescycle;

/**
 * 可暂停的对象。
 * 如果对象已经暂停，则不会被更新，但也不会被摧毁。
 * 可以恢复对象。
 *
 * @author zExNocs
 * @date 2026/02/10
 */
public interface IPausable {
    /**
     * 暂停对象。
     */
    void pause();

    /**
     * 恢复对象。
     */
    void resume();

    /**
     * 是否暂停。
     * @return 如果暂停则返回 true
     */
    boolean isPaused();
}
