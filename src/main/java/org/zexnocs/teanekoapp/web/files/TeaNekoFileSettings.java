package org.zexnocs.teanekoapp.web.files;

import java.lang.annotation.*;

/**
 * 声明一个可供受限网页管理的配置区，原配置类注册本身不构成远程写权限。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TeaNekoFileSettings {
    /** @return 稳定管理区标识 */
    String value();
    /** @return 管理合同版本 */
    int version() default 1;
}
