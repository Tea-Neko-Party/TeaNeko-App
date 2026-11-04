package org.zexnocs.teanekoagent.component.llm.framework.route;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 模型配置来源的命名空间声明，使用原 scanner 发现，命名空间冲突立即拒绝。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface LLMRouteSource {
    /** @return 路由冒号前的来源标识；file 也负责没有前缀的原系统路由。 */
    String namespace();
}
