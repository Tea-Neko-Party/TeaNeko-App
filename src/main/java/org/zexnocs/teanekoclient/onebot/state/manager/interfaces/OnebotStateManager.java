package org.zexnocs.teanekoclient.onebot.state.manager.interfaces;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoclient.onebot.state.OnebotState;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * onebot state 注解，规定需要处理哪些状态。
 * <br>注意，一个状态只能有一个管理器，但是一个管理器可以处理多个状态。
 * <br>需要实现 {@link IOnebotStateManager} 接口
 *
 * @author zExNocs
 * @date 2026/03/15
 * @since 4.3.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface OnebotStateManager {
    /**
     * 可以处理的状态集
     *
     * @return {@link OnebotState[] }
     */
    OnebotState[] value();
}
