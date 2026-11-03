package org.zexnocs.teanekoagent.file_config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekoagent.sticker.StickerSettings;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

import java.util.List;
import java.util.Map;

/**
 * 全实例素材库配置；不保存模型凭据，不随记忆空间清理。
 * <br>2026/11/03：后台事件仅向明确选择的所属插件开放。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
 */
@Getter @Setter @NoArgsConstructor
@FileConfig(value="stickers",path="agent",type=FileConfigType.YAML)
public class AgentStickerFileConfig implements IFileConfigData {
    private boolean enabled=true, collectEnabled=true, passiveEnabled=true;
    private String catalogId="main", storageRoot="data/agent/stickers", analyzer="llm", assets="local", admission="weighted", catalog="easydata", modelId="";
    private int analysisVersion=1, maxActive=200, maxPerCategory=32, maxPending=32;
    private long maxStorageBytes=512L*1024*1024;
    private long maxPendingBytes=64L*1024*1024;
    private int maxFileBytes=6*1024*1024, downloadsPerHour=120, scopeDownloadsPerHour=30, analysesPerHour=20, scopeAnalysesPerHour=5, timeoutSeconds=60, probationDays=3;
    private int maintenanceSeconds=60;
    private List<Double> scoreWeights=List.of(.4,.2,.2,.1,.1);
    private double minConfidence=.85, minQuality=.65, replacementMargin=.08;
    private List<String> allowedHosts=List.of("gchat.qpic.cn","multimedia.nt.qq.com","*.qpic.cn","*.gtimg.cn");
    private List<String> privateHosts=List.of();
    // 2026/11/03：未声明归属的宿主监听保留原行为，第三方须明确选择。
    private List<String> eventPlugins=List.of();
    private Map<String,String> categories=Map.ofEntries(Map.entry("happy","开心、愉快"),Map.entry("sad","伤心、失落"),Map.entry("angry","生气、不满"),
            Map.entry("confused","疑惑、没理解"),Map.entry("surprised","惊讶"),Map.entry("agree","赞同"),Map.entry("refuse","拒绝"),Map.entry("comfort","安慰"),
            Map.entry("thanks","感谢"),Map.entry("awkward","尴尬"),Map.entry("tease","调侃，留意反讽"),Map.entry("waiting","等待"));
    /** @return 运行时校验并固定的预算与路由选择 */
    public StickerSettings snapshot(){return new StickerSettings(enabled,collectEnabled,passiveEnabled,catalogId,storageRoot,analyzer,assets,admission,catalog,modelId,analysisVersion,
            maxActive,maxPerCategory,maxPending,maxStorageBytes,maxPendingBytes,maxFileBytes,downloadsPerHour,scopeDownloadsPerHour,analysesPerHour,scopeAnalysesPerHour,
            timeoutSeconds,maintenanceSeconds,minConfidence,minQuality,probationDays,replacementMargin,allowedHosts,privateHosts,categories,scoreWeights);}
}
