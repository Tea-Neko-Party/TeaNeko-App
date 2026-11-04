package org.zexnocs.teanekoagent.component.llm.framework.model.interfaces;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.llm.framework.model.LLMProviderAccess;

/**
 * 在供应商请求边界解析凭据；结果不能放入 Prompt、Options 或组件。
 * <br>2026/11/03：文件实现可同时核对原已解析参数的配置代次。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：增加配置路由归属检查，保持独立认证实现的同名语义。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@FunctionalInterface
public interface ICredentialResolver {
    /**
     * 2026/11/04：独立实现默认只允许同名路由，文件实现验证显式声明的协议映射。
     * @param route 唯一配置路由
     * @param adapter 实际协议适配器
     * @return 配置路由是否属于该协议
     */
    default boolean supportsRoute(String route, String adapter) {
        return route.equals(adapter);
    }
    /**
     * 2026/11/03：独立旧实现保留单供应商行为，文件实现增加原参数换代检查。
     * @param provider 原供应商
     * @param options 原已解析参数
     * @return 同一可信请求构造边界的凭据
     */
    default LLMProviderAccess require(String provider, ILLMModelOptions options) {
        return require(provider);
    }
    /**
     * 按供应商标识解析调用边界所需凭据；实现须避免将原始配置值放入异常或可导出数据。
     *
     * @param provider 供应商注册标识，不由模型名称推断
     * @return 仅供实际认证边界读取的可信凭据与连接数据
     * @throws org.zexnocs.teanekoagent.component.llm.framework.model.LLMConfigurationException 当模型路由、凭据或本地配置不满足调用边界时
     */
    LLMProviderAccess require(String provider);
}
