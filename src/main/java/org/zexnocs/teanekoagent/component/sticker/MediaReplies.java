package org.zexnocs.teanekoagent.component.sticker;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.expression.ReplyPlans;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import java.time.Instant;
import java.util.*;
/**
 * 素材查询与最终表达之间的不可变数据，不存储宿主对象。
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
public final class MediaReplies {
    /** 禁止实例化数据命名空间。 */ private MediaReplies(){}
    /**
     * 类别摘要，不携带素材明细。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param id 稳定类别
     * @param description 中文用途
     * @param count 当前区域可用数量
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    public record Category(String id,String description,int count){}
    /**
     * 模型得到的有界素材投影，没有来源聊天或文件路径。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param id 素材或原生表情 ID
     * @param revision 条目版本
     * @param hash 实际内容或目录摘要
     * @param description 通用表达用途
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    public record Item(String id,long revision,String hash,String description){}
    /**
     * 宿主固定的资源绑定数据，证明由服务另行签名。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param requestId 原请求
     * @param target 原身份及目标摘要
     * @param settings 本地设置摘要
     * @param storage 持久素材卷身份
     * @param client 客户端目录摘要
     * @param tokens 存储插件代次
     * @param expiresAt 当轮资源到期
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    public record Binding(UUID requestId,String target,String settings,String storage,String client,List<PluginRegistry.Token> tokens,Instant expiresAt){
        /**
         * 固定嵌套集合。
         * @param requestId 原请求
         * @param target 原身份及目标摘要
         * @param settings 本地设置摘要
         * @param storage 持久素材卷身份
         * @param client 客户端目录摘要
         * @param tokens 存储插件代次
         * @param expiresAt 当轮资源到期
         */
        public Binding{tokens=List.copyOf(tokens);}
    }
    /**
     * 本轮完整资源资料，签名防止其他来源仿造目录。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param binding 原上下文绑定
     * @param categories 可用类别
     * @param faces 当前原生目录
     * @param images 当前是否允许图库图片
     * @param proof 宿主完整数据签名
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    public record Resources(Binding binding,List<Category> categories,Map<String,String> faces,boolean images,String proof){
        /**
         * 固定嵌套集合。
         * @param binding 原上下文绑定
         * @param categories 可用类别
         * @param faces 当前原生目录
         * @param images 当前是否允许图库图片
         * @param proof 宿主完整数据签名
         */
        public Resources{categories=List.copyOf(categories);faces=Collections.unmodifiableMap(new TreeMap<>(faces));}
    }
    /**
     * 一次完整类别回执，不以截断内容冒充完整目录。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param requestId 原请求
     * @param resourceProof 原资料签名
     * @param category 本次类别
     * @param catalogRevision 查询时目录版本
     * @param items 该类别全部可用条目
     * @param complete 是否完整展开
     * @param reason 未展开原因
     * @param proof 完整回执签名
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    public record Page(UUID requestId,String resourceProof,String category,long catalogRevision,List<Listing> items,boolean complete,String reason,String proof){
        /**
         * 固定嵌套集合。
         * @param requestId 原请求
         * @param resourceProof 原资料签名
         * @param category 本次类别
         * @param catalogRevision 查询时目录版本
         * @param items 该类别全部可用条目
         * @param complete 是否完整展开
         * @param reason 未展开原因
         * @param proof 完整回执签名
         */
        public Page{items=List.copyOf(items);}
    }
    /**
     * 工具中的紧凑条目；文件指纹由编译宿主从当前版本另行取得。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param id 素材 ID
     * @param revision 条目版本
     * @param description 通用用途
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    public record Listing(String id,long revision,String description){}
    /**
     * 编译后的单项许可，只含数据，不持有后端或执行控制。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param binding 原绑定
     * @param item 唯一冻结条目
     * @param sticker 是否为图库图片
     * @param proof 原宿主签名
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    public record Permit(Binding binding,Item item,boolean sticker,String proof){}
    /**
     * 独立表情包内容，尚未解析时没有许可。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param id 模型选择的素材 ID
     * @param permit 编译后固定的当轮证明
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    public record Sticker(String id,@Nullable Permit permit) implements ReplyPlans.Content{}
    /**
     * 客户端原生表情内容，不进入表情包图库。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param id 客户端不透明 ID
     * @param permit 编译后固定的目录证明
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    public record Face(String id,@Nullable Permit permit) implements ReplyPlans.Content{}
}

