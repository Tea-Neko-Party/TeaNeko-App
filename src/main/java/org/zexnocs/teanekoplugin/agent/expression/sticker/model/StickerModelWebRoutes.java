package org.zexnocs.teanekoplugin.agent.expression.sticker.model;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.web.IWebAdminRoutes;

/**
 * 将两处识图检测的 POST 入口接入宿主管理员安全链。
 * <br>2026/11/10：精确声明路径及方法，插件停用与配置权限仍由检测控制器复核。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 * @see StickerModelController
 */
@Component
public final class StickerModelWebRoutes implements IWebAdminRoutes {
    private final RequestMatcher requests = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST,
                    "/api/v1/plugins/sticker-models/probe/global"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST,
                    "/api/v1/plugins/sticker-models/probe/component"));

    /** @return 初始化时构建的两个精确 POST 匹配器。 */
    @Override
    public RequestMatcher requests() {
        return requests;
    }
}
