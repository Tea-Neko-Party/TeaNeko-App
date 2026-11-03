package org.zexnocs.teanekoagent.sticker;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 一次作业固定使用的图库预算；模型路由仍引用原 model.yml。
 * @param enabled 总开关
 * @param collectEnabled 主动及被动采集总开关
 * @param passiveEnabled 被动采集开关
 * @param catalogId 部署实例持久标识
 * @param storageRoot 宿主文件根目录
 * @param analyzer 分析器标识
 * @param assets 文件后端标识
 * @param admission 准入策略标识
 * @param catalog 目录后端标识
 * @param modelId 可选模型适配器标识
 * @param analysisVersion 分析缓存版本
 * @param maxActive 全库有效数量
 * @param maxPerCategory 单类别有效数量
 * @param maxPending 未完成作业上限
 * @param maxStorageBytes 含暂存和待删文件的总预算
 * @param maxPendingBytes 待处理预约预算
 * @param maxFileBytes 单文件上限
 * @param downloadsPerHour 实例下载次数
 * @param scopeDownloadsPerHour 单来源下载次数
 * @param analysesPerHour 实例模型次数
 * @param scopeAnalysesPerHour 单来源模型次数
 * @param timeoutSeconds 单作业时限
 * @param maintenanceSeconds 文件巡检间隔
 * @param minConfidence 类型置信下限
 * @param minQuality 质量下限
 * @param probationDays 新素材保护天数
 * @param replacementMargin 替换分差
 * @param allowedHosts 可信媒体域名，可使用子域通配符
 * @param privateHosts 明确允许私有地址的可信主机
 * @param categories 稳定类别及中文说明
 * @param scoreWeights 质量、人数、频率、近期及稀缺性的权重
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
public record StickerSettings(boolean enabled, boolean collectEnabled, boolean passiveEnabled, String catalogId, String storageRoot,
        String analyzer, String assets, String admission, String catalog, String modelId, int analysisVersion,
        int maxActive, int maxPerCategory, int maxPending, long maxStorageBytes, long maxPendingBytes, int maxFileBytes,
        int downloadsPerHour, int scopeDownloadsPerHour, int analysesPerHour, int scopeAnalysesPerHour,
        int timeoutSeconds, int maintenanceSeconds, double minConfidence, double minQuality, int probationDays, double replacementMargin,
        List<String> allowedHosts, List<String> privateHosts, Map<String,String> categories,List<Double> scoreWeights) {
    /**
     * 校验有界配置，拒绝不可兑现的容量与类别。
     * @param enabled 总开关
     * @param collectEnabled 采集开关
     * @param passiveEnabled 被动开关
     * @param catalogId 实例标识
     * @param storageRoot 目录
     * @param analyzer 分析器
     * @param assets 后端
     * @param admission 策略
     * @param catalog 目录后端
     * @param modelId 路由
     * @param analysisVersion 分析版本
     * @param maxActive 有效上限
     * @param maxPerCategory 类别上限
     * @param maxPending 队列上限
     * @param maxStorageBytes 磁盘预算
     * @param maxPendingBytes 暂存预约
     * @param maxFileBytes 文件预算
     * @param downloadsPerHour 下载额度
     * @param scopeDownloadsPerHour 来源下载额度
     * @param analysesPerHour 分析额度
     * @param scopeAnalysesPerHour 来源分析额度
     * @param timeoutSeconds 超时
     * @param maintenanceSeconds 巡检间隔
     * @param minConfidence 置信阈值
     * @param minQuality 质量阈值
     * @param probationDays 保护期
     * @param replacementMargin 分差
     * @param allowedHosts 可信主机
     * @param privateHosts 私有媒体主机
     * @param categories 类别
     * @param scoreWeights 排序权重
     * @throws IllegalArgumentException 当配置越界时
     */
    public StickerSettings {
        if (!catalogId.matches("[a-z0-9][a-z0-9_-]{0,47}") || storageRoot.isBlank()
                || analysisVersion < 1 || maxActive < 1 || maxActive > 1000 || maxPerCategory < 1 || maxPerCategory > 64
                || maxPending < 1 || maxPending > 128 || maxFileBytes < 1024 || maxFileBytes > 8*1024*1024
                || maxStorageBytes < maxFileBytes || maxStorageBytes > 10L*1024*1024*1024 || maxPendingBytes<maxFileBytes || maxPendingBytes>maxStorageBytes
                || downloadsPerHour < 1 || downloadsPerHour > 10000 || scopeDownloadsPerHour < 1
                || analysesPerHour < 1 || analysesPerHour > 1000 || scopeAnalysesPerHour < 1
                || timeoutSeconds < 10 || timeoutSeconds > 120 || maintenanceSeconds<1 || maintenanceSeconds>3600 || probationDays < 0 || probationDays > 30
                || !unit(minConfidence) || !unit(minQuality) || !unit(replacementMargin)) {
            throw new IllegalArgumentException("表情包配置超出允许范围。");
        }
        allowedHosts=List.copyOf(allowedHosts); privateHosts=List.copyOf(privateHosts); categories=Map.copyOf(categories);
        scoreWeights=List.copyOf(scoreWeights);
        if(scoreWeights.size()!=5||scoreWeights.stream().anyMatch(v->!unit(v))||Math.abs(scoreWeights.stream().mapToDouble(Double::doubleValue).sum()-1)>.000001){throw new IllegalArgumentException("素材排序须提供五项非负权重且总和为一。");}
        if (categories.isEmpty() || categories.size()>32 || categories.entrySet().stream().anyMatch(e->!e.getKey().matches("[a-z][a-z0-9_-]{0,31}") || e.getValue().isBlank() || e.getValue().length()>80)) {
            throw new IllegalArgumentException("表情包类别必须为有界的稳定标识与说明。");
        }
    }
    /**
     * @param value 分数
     * @return 是否为有限归一化分数
     */
    public static boolean unit(double value){return Double.isFinite(value) && value>=0 && value<=1;}
    /** @return 本次后台任务时限 */
    public Duration timeout(){return Duration.ofSeconds(timeoutSeconds);}
}
