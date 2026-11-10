package org.zexnocs.teanekoapp.web;

import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 由上层模块声明管理员 HTTP 入口，宿主统一应用会话与防伪校验。
 * <br>2026/11/10：声明只在安全链初始化时读取；模块仍负责资源授权和插件实时资格。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
public interface IWebAdminRoutes {
    /**
     * 返回预先构建的路径及方法匹配器，不在请求路径中扫描扩展。
     * @return 本模块明确开放给管理员的 HTTP 入口
     */
    RequestMatcher requests();
}
