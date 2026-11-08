package org.zexnocs.teanekocore.framework.function;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.zexnocs.teanekocore.utils.MethodCallableUtils;

import java.io.Serializable;
import java.util.concurrent.Callable;

/**
 * 支持从方法中提取注解信息的接口。
 * 使用 {@link MethodCallableUtils} 提取出方法注解
 *
 * @see org.zexnocs.teanekocore.actuator.task.api.SetTaskStage
 * @author zExNocs
 * @date 2026/02/18
 */
@FunctionalInterface
public interface IMethodCallable<T> extends Callable<T>, Serializable {
}
