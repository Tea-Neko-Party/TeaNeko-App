package org.zexnocs.teanekoapp.message.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 {@link ITeaNekoContent} 实现类的注解，用于交给 {@link org.zexnocs.teanekoapp.message.ContentScanner} 扫描。
 * <p>用于 JSON 序列化时方便找到对应的类进行反序列化。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TeaNekoContent {
    /**
     * 消息内容类型，建议的命名规范为：
     * <pre> "平台前缀-消息类型" </pre>
     * <p>例如默认的文本消息内容类型为 "TeaNeko-text"。
     * <p>重写的消息内容可以为 "MyApp-text" 等。
     *
     * @return 消息内容类型
     */
    String value();
}
