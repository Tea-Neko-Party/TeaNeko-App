package org.zexnocs.teanekocore.plugin.management;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

/**
 * 明确声明实例级管理资源，宿主须单独授权，不能借一个聊天目标推定全局权限。
 * 仍通过原 ManagementGateway 执行，不提供匿名发现或默认授权。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param <Q> 明确请求类型
 * @param <R> 安全响应类型
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public interface IGlobalManagementResource<Q, R> extends IManagementResource<Q, R> {}
