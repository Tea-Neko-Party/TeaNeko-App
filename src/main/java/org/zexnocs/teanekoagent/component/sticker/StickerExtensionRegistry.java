package org.zexnocs.teanekoagent.component.sticker;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.ArrayList;
import java.util.List;

/**
 * 分别发现四类素材端口，固定本次实际选择及插件代次。
 * <br>2026/11/03：图片发送只选择目录和文件后端，不依赖视觉分析器启用。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.0-l
 */
public final class StickerExtensionRegistry {
    private final AnnotatedBeanRegistry<String,IStickerAnalyzer> analyzers;
    private final AnnotatedBeanRegistry<String,IStickerAssetStore> assets;
    private final AnnotatedBeanRegistry<String,IStickerAdmissionPolicy> policies;
    private final AnnotatedBeanRegistry<String,IStickerCatalog> catalogs;
    private final @Nullable PluginRegistry plugins;
    /**
     * @param scanner 原扫描器
     * @param plugins 原插件目录
     */
    public StickerExtensionRegistry(IBeanScanner scanner,@Nullable PluginRegistry plugins){
        this.plugins=plugins;
        analyzers=new AnnotatedBeanRegistry<>(scanner,TeaNekoStickerAnalyzer.class,IStickerAnalyzer.class,a->key(a.value(),a.version()));
        assets=new AnnotatedBeanRegistry<>(scanner,TeaNekoStickerAssets.class,IStickerAssetStore.class,a->key(a.value(),a.version()));
        policies=new AnnotatedBeanRegistry<>(scanner,TeaNekoStickerAdmission.class,IStickerAdmissionPolicy.class,a->key(a.value(),a.version()));
        catalogs=new AnnotatedBeanRegistry<>(scanner,TeaNekoStickerCatalog.class,IStickerCatalog.class,a->key(a.value(),a.version()));
    }
    /**
     * @param id 稳定标识
     * @param version 契约版本
     * @return 精确注册键
     */
    private static String key(String id,int version){if(!id.matches("[a-z][a-z0-9_-]{0,47}")||version!=1){throw new IllegalArgumentException("素材扩展标识或契约版本无效。");}return id;}
    /**
     * @param settings 本次配置
     * @return 固定端口和代次
     */
    public Selected select(StickerSettings settings){
        var a=analyzers.require(settings.analyzer());var b=assets.require(settings.assets());var c=policies.require(settings.admission());var d=catalogs.require(settings.catalog());
        var tokens=new ArrayList<PluginRegistry.Token>();for(var type:List.of(a.type(),b.type(),c.type(),d.type())){
            var owned=AnnotatedElementUtils.findMergedAnnotation(type,PluginOwned.class);
            if(plugins==null&&owned!=null){throw new IllegalStateException("素材扩展声明插件归属但缺少插件目录。");}
            if(plugins!=null){tokens.add(plugins.capture(owned==null?"teaneko.agent":owned.value()));}
        }
        return new Selected(a.bean(),b.bean(),c.bean(),d.bean(),List.copyOf(tokens));
    }
    /** @param selected 原端口快照 */
    public void verify(Selected selected){if(plugins!=null){selected.tokens().forEach(plugins::check);}}
    /**
     * 2026/11/03：只固定读取已有素材必需的端口。
     * @param settings 当前选择
     * @return 原目录、文件与插件代次
     */
    public Storage storage(StickerSettings settings){
        var a=assets.require(settings.assets());var c=catalogs.require(settings.catalog());var tokens=new ArrayList<PluginRegistry.Token>();
        for(var type:List.of(a.type(),c.type())){var owner=AnnotatedElementUtils.findMergedAnnotation(type,PluginOwned.class);
            if(plugins==null&&owner!=null){throw new IllegalStateException("素材存储缺少所属插件目录。");}
            if(plugins!=null){tokens.add(plugins.capture(owner==null?"teaneko.agent":owner.value()));}
        }return new Storage(a.bean(),c.bean(),List.copyOf(tokens));
    }
    /**
     * 2026/11/03：资源使用前复核原存储插件。
     * @param storage 原端口
     */
    public void verifyStorage(Storage storage){if(plugins!=null){storage.tokens().forEach(plugins::check);}}
    /**
     * 发送任务临时持有的存储端口，不写入计划或组件。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param assets 文件后端
     * @param catalog 目录后端
     * @param tokens 插件代次
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    @JsonIgnoreType public record Storage(IStickerAssetStore assets,IStickerCatalog catalog,List<PluginRegistry.Token> tokens){}
    /**
     * 仅宿主任务持有的容器端口，不写入数据库或组件。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param analyzer 视觉服务
     * @param assets 文件服务
     * @param policy 准入策略
     * @param catalog 原子目录
     * @param tokens 插件代次
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    @JsonIgnoreType public record Selected(IStickerAnalyzer analyzer,IStickerAssetStore assets,IStickerAdmissionPolicy policy,IStickerCatalog catalog,List<PluginRegistry.Token> tokens){}
}
