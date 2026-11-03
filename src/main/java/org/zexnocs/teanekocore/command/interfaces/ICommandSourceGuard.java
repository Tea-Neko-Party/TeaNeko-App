package org.zexnocs.teanekocore.command.interfaces;

/**
 * 可撤销原生来源的可选守卫，供命令分发与排队启动再次核对当前能力。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public interface ICommandSourceGuard {
    /**
     * 复核当前原生来源能力，不将曾经受理当作永久执行许可。
     * @throws SecurityException 当原来源会话、期限或宿主能力已经失效时
     */
    void checkCommandSource();
}
