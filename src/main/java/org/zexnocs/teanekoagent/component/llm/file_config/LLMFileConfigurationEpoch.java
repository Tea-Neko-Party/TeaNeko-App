package org.zexnocs.teanekoagent.component.llm.file_config;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 将已解析模型参数与同一文件发布代次关联，旧参数不能静默拼接新凭据。
 * 只在同步参数和 HTTP 请求构造期间借用文件锁，不持锁等待网络完成。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.0-l
 */
@Component
public final class LLMFileConfigurationEpoch {
    private static final String KEY = "teaneko.configuration-version";
    private final IFileConfigService files;
    private final UUID instance = UUID.randomUUID();

    /** @param files 原文件版本入口 */
    public LLMFileConfigurationEpoch(IFileConfigService files) {
        this.files = files;
    }

    /**
     * @param action 仅同步构造参数或请求，不等待异步网络
     * @param <T> 返回类型
     * @return 同一文件锁内的结果
     */
    public <T> T fixed(Supplier<T> action) {
        return files.withDocument(LLMModelFileConfig.class, document -> action.get());
    }

    /**
     * @param options 已解析且不含凭据的参数
     * @return 标记当前文件代次的同一参数
     */
    public LLMModelOptions stamp(LLMModelOptions options) {
        var metadata = new LinkedHashMap<>(options.findMetadata().orElse(Map.of()));
        metadata.put(KEY, current());
        options.setMetadata(metadata);
        return options;
    }

    /**
     * @param options 原已解析参数
     * @throws LLMConfigurationException 当参数来自已失效文件代次时
     */
    public void verify(ILLMModelOptions options) {
        Object original = options.findMetadata().orElse(Map.of()).get(KEY);
        if (original != null && !original.equals(current())) {
            throw new LLMConfigurationException("模型配置已换代，请重新解析参数后再发起请求。");
        }
    }

    /**
     * @return 当前有效缓存代次，不携带凭据或文件正文
     */
    private String current() {
        return files.withDocument(LLMModelFileConfig.class, document -> {
            var stamp = document.runtime();
            if (!stamp.current()) {
                throw new LLMConfigurationException("当前模型文件未成功加载。");
            }
            return instance + ":" + stamp.generation() + ":" + stamp.revision();
        });
    }
}
