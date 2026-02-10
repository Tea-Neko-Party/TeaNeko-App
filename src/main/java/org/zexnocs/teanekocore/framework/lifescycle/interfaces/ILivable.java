package org.zexnocs.teanekocore.framework.lifescycle.interfaces;

/**
 * 生命周期接口。
 * 判断该对象是否存活。
 * 这种死亡是不可逆的。
 * 如果不存活则会被自动摧毁。
 *
 * @author zExNocs
 * @date 2026/02/10
 */
public interface ILivable {
    /**
     * 检测是否存活。
     * @return 如果存活则返回 true
     */
    boolean isAlive();
}