package org.zexnocs.teanekoagent.component.expression;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.zexnocs.teanekoagent.component.expression.AgentReplyPlan.Mode;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.*;
import java.util.concurrent.CompletionStage;

/**
 * 精确客户端路由和插件代次；未知客户端不推测媒体能力。
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
public final class ExpressionClientRegistry{
    private final AnnotatedBeanRegistry<String,IExpressionClientAdapter> entries;
    private final @Nullable PluginRegistry plugins;
    /**
     * @param scanner 原扫描器
     * @param plugins 可选原插件目录
     */
    public ExpressionClientRegistry(IBeanScanner scanner,@Nullable PluginRegistry plugins){
        this.plugins=plugins;entries=new AnnotatedBeanRegistry<>(scanner,TeaNekoExpressionClient.class,IExpressionClientAdapter.class,a->{
            if(a.value().isBlank()||a.value().length()>64||a.version()!=1){throw new IllegalArgumentException("表达客户端声明无效。");}return a.value();
        });
    }
    /**
     * @param data 当前目标
     * @param settings 当前媒体配置
     * @return 明确声明的能力，没有适配器时为空
     */
    public Optional<View> view(ITeaNekoMessageData data,MediaReplySettings settings){
        var entry=entries.entries().get(data.getClient().getClientId());if(entry==null){return Optional.empty();}
        var owner=AnnotatedElementUtils.findMergedAnnotation(entry.type(),PluginOwned.class);
        if(plugins==null&&owner!=null){throw new IllegalStateException("表达客户端缺少所属插件目录。");}
        String plugin=owner==null?"teaneko.agent":owner.value();if(plugins!=null&&!plugins.isEnabled(plugin)){return Optional.empty();}
        var tokens=plugins==null?List.<PluginRegistry.Token>of():List.of(plugins.capture(plugin));
        var faces=Collections.unmodifiableMap(new TreeMap<>(entry.bean().faces(data,settings)));
        if(faces.size()>64||faces.entrySet().stream().anyMatch(e->!e.getKey().matches("[a-zA-Z0-9_.:-]{1,64}")||e.getValue().isBlank()||e.getValue().length()>80)){throw new IllegalArgumentException("客户端表情目录超过合同。");}
        return Optional.of(new View(data.getClient().getClientId(),entry.bean().images(data),faces,tokens));
    }
    /**
     * @param expected 原能力
     * @param data 原目标
     * @param settings 当前配置
     */
    public void verify(View expected,ITeaNekoMessageData data,MediaReplySettings settings){
        if(plugins!=null){expected.tokens().forEach(plugins::check);}
        if(view(data,settings).filter(expected::equals).isEmpty()){throw new IllegalStateException("客户端表达能力或表情目录已经变化。");}
    }
    /**
     * @param expected 原能力
     * @param data 原目标
     * @param bytes 原图编码
     * @param mode 方式
     * @param settings 当前设置
     * @return 真实确认
     */
    public CompletionStage<Void> image(View expected,ITeaNekoMessageData data,String bytes,Mode mode,MediaReplySettings settings){
        verify(expected,data,settings);if(!expected.images()){throw new IllegalStateException("当前客户端不支持图库图片。");}return entries.require(expected.clientId()).bean().image(data,bytes,mode);
    }
    /**
     * @param expected 原能力
     * @param data 原目标
     * @param id 目录 ID
     * @param mode 方式
     * @param settings 设置
     * @return 真实确认
     */
    public CompletionStage<Void> face(View expected,ITeaNekoMessageData data,String id,Mode mode,MediaReplySettings settings){
        verify(expected,data,settings);if(!expected.faces().containsKey(id)){throw new IllegalArgumentException("原生表情不在本轮目录。");}return entries.require(expected.clientId()).bean().face(data,id,mode);
    }
    /**
     * 可固定到计划的数据，没有容器对象。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param clientId 客户端
     * @param images 图片能力
     * @param faces 原生目录
     * @param tokens 原插件代次
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    public record View(String clientId,boolean images,Map<String,String> faces,List<PluginRegistry.Token> tokens){
        /**
         * 深层固定能力。
         * @param clientId 客户端
         * @param images 图片能力
         * @param faces 目录
         * @param tokens 代次
         */
        public View{faces=Collections.unmodifiableMap(new TreeMap<>(faces));tokens=List.copyOf(tokens);}
    }
}
