package org.zexnocs.teanekoagent.component.sticker;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
/**
 * 专用 EasyData 中按部署实例锁定有界目录，独立于记忆清空与人格切换。
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
@Component @TeaNekoStickerCatalog("easydata")
public final class EasyDataStickerCatalog implements IStickerCatalog {
    private final TeaNekoAgentEasyData data;
    private final ObjectMapper mapper;
    /**
     * @param data 专用入口
     * @param mapper 原转换器
     */
    public EasyDataStickerCatalog(TeaNekoAgentEasyData data,ObjectMapper mapper){this.data=data.inNamespace("agent.sticker.v1");this.mapper=mapper;}
    /**
     * 根记录初次创建由原唯一键与重试仲裁，不使用普通缓存读取。
     * @param settings 固定配置
     * @param action 短事务
     * @param <T> 结果类型
     * @return 实际提交结果
     */
    @Override public <T> CompletionStage<T> change(StickerSettings settings,Function<Stickers.State,T> action){
        return data.transaction("更新实例表情包目录",()->{
            var dto=data.get("catalog:"+settings.catalogId()).atomic(); var row=dto.find("state",EasyDataLock.WRITE);
            var state=row==null?new Stickers.State():mapper.readValue(row.getValue(),Stickers.State.class);
            var result=java.util.Objects.requireNonNull(action.apply(state)); var json=mapper.writeValueAsString(state);
            if(json.length()>8*1024*1024){throw new IllegalStateException("表情包目录超过持久容量上限。");}
            if(row==null){dto.save(dto.create("state",json));}else if(!row.getValue().equals(json)){row.setValue(json);dto.save(row);} return result;
        });
    }
}
