package org.zexnocs.teanekoclient.web;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明网页房间准备扩展的唯一标识，通过原 Bean scanner 发现。
 *
 * 修改记录：<br>
 * 2026/11/04：扩展标识改用独立 id，避免 Spring 将业务值作为 Bean 名。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.7.5-l
 */
@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface WebRoomPreparation {
    /**
     * 2026/11/04：此属性仅用于扩展注册，不参与容器组件命名。
     * @return 模块内稳定且唯一的准备扩展标识
     */
    String id();
}
