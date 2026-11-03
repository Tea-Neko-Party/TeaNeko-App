package org.zexnocs.teanekoagent.sticker;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentScope;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 管理 SPI 的明确请求与脱敏视图，不向客户端开放任意对象或文件路径。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
public final class StickerManagement {
    /** 禁止实例化数据命名空间。 */ private StickerManagement(){}
    /**
     * 有界查询的明确类型。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public enum QueryKind{CATALOG, JOBS}
    /**
     * 版本管理动作，删除与显式重新允许采集分开。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public enum Action{PIN, UNPIN, DISABLE, ENABLE, DELETE, RESTORE, PATCH, REFRESH}
    /**
     * 目录或作业查询。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param kind 查询类型
     * @param after 原末项 ID，首屏为空
     * @param limit 一至一百项
     * @param category 空或主类别 ID
     * @param state 可选条目状态
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record Query(QueryKind kind,String after,int limit,String category,Stickers.@Nullable EntryStatus state){
        /** 校验请求或固定集合。
         * @param kind 查询类型
         * @param after 原末项 ID，首屏为空
         * @param limit 一至一百项
         * @param category 空或主类别 ID
         * @param state 可选条目状态
         */
        public Query{Objects.requireNonNull(kind,"查询类型不能为空。");if(after==null||after.length()>80||limit<1||limit>100||category==null||category.length()>32){throw new IllegalArgumentException("管理查询游标或页大小无效。");}}
    }
    /**
     * 人工修正描述及类别，不提升图片类型或模型置信度。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param category 受控主类别
     * @param visualDescription 客观画面
     * @param usageDescription 完整用途
     * @param tags 至多四项标签
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record Details(String category,String visualDescription,String usageDescription,List<String> tags){
        /** 校验请求或固定集合。
         * @param category 受控主类别
         * @param visualDescription 客观画面
         * @param usageDescription 完整用途
         * @param tags 至多四项标签
         */
        public Details{tags=List.copyOf(tags);if(category==null||!category.matches("[a-z][a-z0-9_-]{0,31}")||visualDescription==null||visualDescription.isBlank()||visualDescription.length()>160||usageDescription==null||usageDescription.isBlank()||usageDescription.length()>80||tags.size()>4||tags.stream().anyMatch(v->v.isBlank()||v.length()>24)){throw new IllegalArgumentException("修正类别、描述或标签无效。");}}
    }
    /**
     * 管理写入携带明确版本和持久幂等标识。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 幂等标识
     * @param id 素材 ID
     * @param expectedRevision 预期条目版本
     * @param action 明确动作
     * @param details 仅 PATCH 使用
     * @param analysisScope 仅 REFRESH 使用的已登记分析区域
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record Change(UUID operationId,String id,long expectedRevision,Action action,@Nullable Details details,@Nullable AgentScope analysisScope){
        /** 校验请求或固定集合。
         * @param operationId 幂等标识
         * @param id 素材 ID
         * @param expectedRevision 预期条目版本
         * @param action 明确动作
         * @param details 仅 PATCH 使用
         * @param analysisScope 仅 REFRESH 使用的已登记分析区域
         */
        public Change{Objects.requireNonNull(operationId,"操作 ID 不能为空。");Objects.requireNonNull(action,"管理动作不能为空。");if(id==null||!id.matches("[a-zA-Z0-9_-]{1,80}")||expectedRevision<1){throw new IllegalArgumentException("素材 ID 或预期版本无效。");}if(action==Action.PATCH){Objects.requireNonNull(details,"修正动作须提供完整描述。");}else if(details!=null){throw new IllegalArgumentException("当前动作不接受描述字段。");}if(action==Action.REFRESH){Objects.requireNonNull(analysisScope,"重新分析须指定已登记区域。");}else if(analysisScope!=null){throw new IllegalArgumentException("当前动作不接受分析区域。");}}
    }
    /**
     * 按固定版本请求受控预览。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 素材 ID
     * @param expectedRevision 预期版本
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record Preview(String id,long expectedRevision){
        /** 校验请求或固定集合。
         * @param id 素材 ID
         * @param expectedRevision 预期版本
         */
        public Preview{if(id==null||!id.matches("[a-zA-Z0-9_-]{1,80}")||expectedRevision<1){throw new IllegalArgumentException("预览 ID 或预期版本无效。");}}
    }
    /**
     * 查询本人原操作的真实提交回执。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 原幂等标识
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record OperationQuery(UUID operationId){
        /** 校验请求或固定集合。
         * @param operationId 原幂等标识
         */
        public OperationQuery{Objects.requireNonNull(operationId,"操作 ID 不能为空。");}
    }
    /**
     * 不包含路径、作者或原始链接的管理条目。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 稳定 ID
     * @param revision 条目版本
     * @param state 当前状态
     * @param pinned 是否固定
     * @param category 主类别
     * @param visualDescription 画面描述
     * @param usageDescription 用途
     * @param quality 模型综合分数
     * @param bytes 文件字节数
     * @param width 宽
     * @param height 高
     * @param frames 帧数
     * @param humanObservations 有界人类观察样本
     * @param robotShows 真实确认展示
     * @param lastHuman 最近人类观察
     * @param lastRobot 最近机器人确认
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record ItemView(String id,long revision,Stickers.EntryStatus state,boolean pinned,String category,String visualDescription,String usageDescription,double quality,long bytes,int width,int height,int frames,long humanObservations,long robotShows,@Nullable Instant lastHuman,@Nullable Instant lastRobot){}
    /**
     * 作业诊断不导出图片引用和来源原话。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 作业 ID
     * @param status 实际状态
     * @param reason 固定原因
     * @param stickerId 关联素材
     * @param scope 固定任务区域
     * @param refresh 是否重新分析
     * @param created 创建时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record JobView(UUID id,Stickers.JobStatus status,String reason,String stickerId,AgentScope scope,boolean refresh,Instant created){}
    /**
     * 实例级容量快照，含预约和待删字节。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param active 有效项
     * @param disabled 人工停用项
     * @param pending 在途任务
     * @param maxActive 有效上限
     * @param accountedBytes 保守占用
     * @param maxBytes 磁盘预算
     * @param categories 各主类别有效数
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record Capacity(int active,int disabled,int pending,int maxActive,long accountedBytes,long maxBytes,Map<String,Long> categories){
        /** 校验请求或固定集合。
         * @param active 有效项
         * @param disabled 人工停用项
         * @param pending 在途任务
         * @param maxActive 有效上限
         * @param accountedBytes 保守占用
         * @param maxBytes 磁盘预算
         * @param categories 各主类别有效数
         */
        public Capacity{categories=Map.copyOf(categories);}
    }
    /**
     * 管理分页及容量共用同一目录快照。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param catalogRevision 目录版本
     * @param items 条目页
     * @param jobs 作业页
     * @param next 后继游标，结束时为空
     * @param capacity 实例统计
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record Page(long catalogRevision,List<ItemView> items,List<JobView> jobs,String next,Capacity capacity){
        /** 校验请求或固定集合。
         * @param catalogRevision 目录版本
         * @param items 条目页
         * @param jobs 作业页
         * @param next 后继游标，结束时为空
         * @param capacity 实例统计
         */
        public Page{items=List.copyOf(items);jobs=List.copyOf(jobs);}
    }
    /**
     * 单幅有界 PNG 预览，不是原文件下载地址。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 素材 ID
     * @param revision 原版本
     * @param image 首幅合成预览 data URI
     * @param note 动画采样说明
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record PreviewView(String id,long revision,String image,String note){}
    /**
     * 提交后的管理回执；后台重新分析与本次请求提交分开。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 幂等标识
     * @param id 素材
     * @param revision 本操作形成的条目版本
     * @param state 提交状态
     * @param catalogRevision 提交目录版本
     * @param jobId 可选后台作业
     * @param replayed 是否返回既有回执
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record ChangeResult(UUID operationId,String id,long revision,Stickers.EntryStatus state,long catalogRevision,@Nullable UUID jobId,boolean replayed){}
    /**
     * 有界持久幂等记录，主体只存摘要。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param subject 管理主体摘要
     * @param digest 全部请求摘要
     * @param at 提交时间
     * @param result 原真实回执
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record Operation(String subject,String digest,Instant at,ChangeResult result){}
}

