package org.zexnocs.teanekoagent.sticker;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * 以质量、独立人类、衰减频率和类别稀缺程度排序，机器人展示不计热度。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Component @TeaNekoStickerAdmission("weighted")
public final class WeightedStickerAdmission implements IStickerAdmissionPolicy {
    /**
     * @param analysis 判定
     * @param settings 阈值
     * @return 用途明确且达到质量门槛
     */
    @Override public boolean accepts(Stickers.Analysis analysis,StickerSettings settings){return analysis.worthKeeping()&&!analysis.usageDescription().isBlank()&&analysis.quality()>=settings.minQuality();}
    /**
     * @param entry 条目
     * @param categoryCount 同类数量
     * @param now 时刻
     * @param settings 本地权重
     * @return 确定性分数
     */
    @Override public double score(Stickers.Entry entry,long categoryCount,Instant now,StickerSettings settings){
        var analysis=entry.getAnalysis(); if(analysis==null){return 0;}
        long today=now.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();
        double frequency=entry.getHumanDays().entrySet().stream().mapToDouble(e->e.getValue()*Math.exp(-Math.max(0,today-Long.parseLong(e.getKey().substring(0,e.getKey().indexOf(':'))))/7.0)).sum();
        long authors=entry.getHumanDays().keySet().stream().map(v->v.substring(v.indexOf(':')+1)).distinct().count();
        double recent=entry.getLastHuman()==null?0:Math.exp(-Math.max(0,Duration.between(entry.getLastHuman(),now).toDays())/7.0);
        var weights=settings.scoreWeights();return weights.get(0)*analysis.quality()+weights.get(1)*Math.min(1,authors/5.0)+weights.get(2)*Math.min(1,frequency/20)+weights.get(3)*recent+weights.get(4)/(1+categoryCount);
    }
}
