package org.zexnocs.teanekoagent.llm.file_config;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMProviderAccess;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.route.ILLMRouteSource;
import org.zexnocs.teanekoagent.llm.framework.route.LLMRouteSource;

import java.util.function.Supplier;

/**
 * 系统模型配置来源，复用原文件发布锁及版本，不读取聊天数据库。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@LLMRouteSource(namespace = "file")
public final class FileLLMRouteSource implements ILLMRouteSource {
    private final ILLMFileConfigService files;
    private final LLMFileConfigurationEpoch epoch;

    /**
     * @param files 原模型文件端口
     * @param epoch 原发布代次边界
     */
    public FileLLMRouteSource(ILLMFileConfigService files, LLMFileConfigurationEpoch epoch) {
        this.files = files;
        this.epoch = epoch;
    }

    /**
     * @param route 原系统路由
     * @return 当前文件配置项
     */
    private LLMModelFileConfigParameter entry(String route) {
        return files.getConfig().findModelConfig(LLMModelId.of(route))
                .orElseThrow(() -> new LLMConfigurationException("所选系统模型路由不存在。"));
    }

    /**
     * @param route 系统路由
     * @return 实际协议
     */
    @Override
    public LLMModelId adapter(String route) {
        return entry(route).adapterId();
    }

    /**
     * @param route 系统路由
     * @param base 协议初值
     * @return 原文件合并参数
     */
    @Override
    public LLMModelOptions defaults(String route, ILLMModelOptions base) {
        return files.getDefaultOptions(LLMModelId.of(route), base);
    }

    /**
     * @param route 系统路由
     * @return 当前同代次连接
     */
    @Override
    public LLMProviderAccess credentials(String route) {
        var entry = entry(route);
        return new LLMProviderAccess(entry.getApiKey(), entry.getBaseUrl(), entry.getApiPath());
    }

    /**
     * @param route 系统路由
     * @param action 同步构造
     * @param <T> 结果类型
     * @return 同一文件版本内的构造结果
     */
    @Override
    public <T> T fixed(String route, Supplier<T> action) {
        return epoch.fixed(action);
    }

    /** @param options 原解析参数 */
    @Override
    public void verify(ILLMModelOptions options) {
        epoch.verify(options);
    }

    /**
     * @param options 当前参数
     * @return 标记原文件版本的参数
     */
    @Override
    public LLMModelOptions stamp(LLMModelOptions options) {
        return epoch.stamp(options);
    }
}
