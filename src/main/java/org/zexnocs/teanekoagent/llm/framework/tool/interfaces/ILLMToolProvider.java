package org.zexnocs.teanekoagent.llm.framework.tool.interfaces;

import org.zexnocs.teanekoagent.llm.framework.tool.api.LLMToolMapping;

/**
 * 大语言模型工具提供者标记接口。
 * <br>实现该接口的 Spring Bean 中，带有
 * {@link LLMToolMapping} 的方法会被自动注册为工具。
 *
 * @author zExNocs
 * @date 2026/06/05
 * @since 4.4.0
 */
public interface ILLMToolProvider {
    /**
     * 获取该提供者下工具的默认所属包。
     * <br>方法级注解未声明包时会使用该值；默认空字符串表示不指定包。
     *
     * @return 工具默认所属包
     */
    default String getLLMToolPackage() {
        return "";
    }
}
