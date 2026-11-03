package org.zexnocs.teanekoagent.sticker;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentScope;

import java.time.Instant;
import java.util.*;

/**
 * 素材与作业的有界持久数据；来源仅用于审计，不投影为跨群对话资料。
 * <br>2026/11/03：记录独立的真实机器人展示、区域回避轮次和展示幂等摘要。
 * <br>2026/11/03：管理状态、删除墓碑及重新分析按原版本仲裁。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
public final class Stickers {
    /** 不实例化数据命名空间。 */
    private Stickers(){}
    /**
     * 实际视觉类型，画风不等于聊天用途。
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    public enum Kind { STICKER, ILLUSTRATION, PHOTO, SCREENSHOT, OTHER, UNKNOWN }
    /**
     * 单次模型判定；宿主仍独立校验类型、类别和阈值。
     * @param kind 视觉类型
     * @param confidence 类型置信度
     * @param worthKeeping 收藏建议
     * @param visualDescription 客观画面
     * @param usageDescription 聊天用途
     * @param primaryCategory 主类别
     * @param secondaryTags 检索标签
     * @param quality 清晰度与用途的综合分数
     * @param reasons 简短判断依据
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    public record Analysis(Kind kind,double confidence,boolean worthKeeping,String visualDescription,String usageDescription,
                           String primaryCategory,List<String> secondaryTags,double quality,String reasons){
        /**
         * 校验所有模型字段，额外字段由严格解析器拒绝。
         * @param kind 类型
         * @param confidence 置信度
         * @param worthKeeping 建议
         * @param visualDescription 描述
         * @param usageDescription 用途
         * @param primaryCategory 类别
         * @param secondaryTags 标签
         * @param quality 质量
         * @param reasons 依据
         * @throws IllegalArgumentException 当输出不完整或越界时
         */
        public Analysis {
            Objects.requireNonNull(kind); secondaryTags=List.copyOf(secondaryTags);
            if (!StickerSettings.unit(confidence)||!StickerSettings.unit(quality)||visualDescription.isBlank()||visualDescription.length()>160
                    ||usageDescription.length()>80||primaryCategory.length()>32||reasons.length()>160||secondaryTags.size()>4
                    ||secondaryTags.stream().anyMatch(v->v.isBlank()||v.length()>24)) {throw new IllegalArgumentException("表情包分析结果不完整或超出边界。");}
        }
    }
    /**
     * 入站固定身份与短期引用；不接受工具直接指定 URL。
     * @param scope 真实作用域
     * @param author 真实作者 UUID
     * @param sender 平台作者
     * @param self 固定机器人账号
     * @param message 消息 ID
     * @param image 图片序号
     * @param occurredAt 真实入站时间
     * @param manual 是否主动收藏
     * @param requester 主动请求者 UUID
     * @param url 可信消息端口取得的短期引用
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    public record Source(AgentScope scope,UUID author,String sender,String self,String message,int image,Instant occurredAt,
                         boolean manual,UUID requester,String url){ }
    /**
     * 内容地址元数据；不持久保存模型预览或任意宿主路径。
     * @param hash 原文件 SHA-256
     * @param format 真实解码格式
     * @param bytes 原文件大小
     * @param width 画布宽
     * @param height 画布高
     * @param frames 帧数
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    public record Blob(String hash,String format,long bytes,int width,int height,int frames){ }
    /**
     * 后台作业状态；分析不确定时不因普通重复图片自动再次计费。
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    public enum JobStatus { QUEUED, RUNNING, SAVED, DUPLICATE, REJECTED, FAILED }
    /**
     * 素材可选状态与文件回收状态；只有 ACTIVE 可以交给后续选择端口。
     * <br>2026/11/03：人工停用和删除不与可自动回收缓存混用。
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    public enum EntryStatus { CACHED, ACTIVE, DISABLED, DELETED, RETIRED, UNAVAILABLE, DELETING }
    /**
     * 单作业持久状态；清理文件前保留容量预约与来源，终态清理后移除私有引用。
     * <br>2026/11/03：重新分析绑定现有文件和固定条目版本，不重新下载。
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    @Data @NoArgsConstructor public static class Job {
        private UUID id=UUID.randomUUID(), lease;
        private Source source;
        private Instant created, leaseUntil, nextAttempt;
        private JobStatus status=JobStatus.QUEUED;
        private @Nullable Blob blob;
        private long reserved;
        private String reason="", stickerId="";
        private boolean cleanup;
        // 2026/11/03：人工动作可使迟到分析失效，建议不构成收藏授权。
        private boolean refresh, activateAfterRefresh;
        private long expectedEntryRevision;
        private String hint="";
    }
    /**
     * 素材条目和有限人类使用窗口；机器人展示另计，不用于抬高人类热度。
     * <br>2026/11/03：持久保存真实确认次数，不加入人类评分。
     * <br>2026/11/03：管理删除在物理回收完成前禁止恢复。
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    @Data @NoArgsConstructor public static class Entry {
        private String id, hash;
        private EntryStatus state=EntryStatus.CACHED;
        private int analysisVersion;
        private long revision=1;
        private @Nullable Analysis analysis;
        private @Nullable Blob blob;
        private Instant created, activatedAt, lastHuman, cachedUntil;
        private boolean pinned;
        private Map<String,Integer> humanDays=new LinkedHashMap<>();
        private Map<UUID,Instant> readers=new LinkedHashMap<>();
        // 2026/11/03：仅实际平台确认后更新。
        private long robotShows;
        private Instant lastRobot;
        // 2026/11/03：墓碑维持 DELETED，额外标记防止回收期间重新启用。
        private boolean deleting;
    }
    /**
     * 有界小时计数，不是完整聊天历史。
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    @Data @NoArgsConstructor public static class Quota {private long hour; private int downloads,analyses;}
    /**
     * 一行目录事务快照；有界集合避免全表扫描及跨行激活竞态。
     * <br>2026/11/03：按真实区域保存有限轮次与机器人展示回执摘要。
     * <br>2026/11/03：管理回执与条目变更原子提交。
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    @Data @NoArgsConstructor public static class State {
        private long revision;
        private int inspection;
        private String storageIdentity="";
        private UUID worker;
        private Instant workerUntil=Instant.EPOCH;
        private Map<String,Entry> entries=new LinkedHashMap<>();
        private Map<UUID,Job> jobs=new LinkedHashMap<>();
        private Map<String,Instant> receipts=new LinkedHashMap<>();
        private Map<String,Quota> quotas=new LinkedHashMap<>();
        // 2026/11/03：与内容共享分开，回避记录不成为跨群聊天资料。
        private Map<String,ScopeUsage> scopeUses=new LinkedHashMap<>();
        private Map<String,Instant> shown=new LinkedHashMap<>();
        // 2026/11/03：有限期操作记录，不保存权限对象或原始管理正文。
        private Map<UUID,StickerManagement.Operation> operations=new LinkedHashMap<>();
    }
    /**
     * 当前表达区域的有限轮次与最近展示，失败轮次也属于已准入的对话尝试。
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    @Data @NoArgsConstructor public static class ScopeUsage {
        private long turn;
        private Instant updated=Instant.EPOCH;
        private Map<UUID,Long> requests=new LinkedHashMap<>();
        private Map<String,Long> used=new LinkedHashMap<>();
    }
    /**
     * 工具可见的脱敏作业回执。
     * @param jobId 作业标识
     * @param status 实际持久状态
     * @param reason 固定原因码
     * @param stickerId 已激活素材标识
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.0.0
     * @version 4.6.1-l
     */
    public record Receipt(UUID jobId,JobStatus status,String reason,String stickerId){ }
}
