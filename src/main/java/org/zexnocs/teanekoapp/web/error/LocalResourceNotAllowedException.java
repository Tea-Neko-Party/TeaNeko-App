package org.zexnocs.teanekoapp.web.error;

import java.io.Serial;

/**
 * 已登录管理员请求被本地可选白名单拒绝，区别于会话失效。
 * <br>2026/11/07：为控制台提供明确且不含秘密的授权诊断。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class LocalResourceNotAllowedException extends SecurityException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建固定说明，不把请求内容或秘密带入响应。 */
    public LocalResourceNotAllowedException() {
        super("本地白名单限制了此管理资源，请调整 web/security.yml 的授权设置。");
    }
}
