package org.zexnocs.teanekocore.command.interfaces;

import java.lang.reflect.Method;

/**
 * 由管理宿主持有的实时能力，启动方法及实际副作用时复核，不参与序列化或原生身份推导。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public interface IManagedCommandSource {
    /**
     * @param method 原目录实际选择的方法
     * @throws SecurityException 当当前会话、连接、声明或调用边界已经失效时
     */
    void checkCommand(Method method);
}
