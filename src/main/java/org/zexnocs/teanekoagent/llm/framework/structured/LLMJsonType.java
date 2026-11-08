package org.zexnocs.teanekoagent.llm.framework.structured;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明可生成的 JSON 根类型，由初始化扫描器预编译，字段使用 Description 描述。
 * <br>2026/11/06：固定结构和输出容量，不在请求路径发现类型。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LLMJsonType {
    /** @return 解码前的字符上限，不改变模型自身输出 token 额度 */
    int maxCharacters() default 65536;
}
