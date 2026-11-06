package org.zexnocs.teanekoagent.component.care;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.component.social.SocialState;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 关怀条件不依赖真实模型，重点验证跨聊天活跃、未知结果和提醒上限。
 * <br>2026/11/06：用户在群内继续聊天不能被判为久未联系。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
class CarePolicyTest {
    /** 沉默满足阈值才候选，低亲近度、冷却、已占用及次数上限均禁止。 */
    @Test void suppressesIneligibleOrAlreadyReservedCare() {
        var now = Instant.parse("2026-11-06T00:00:00Z");
        var old = now.minus(Duration.ofDays(4));
        var activity = new CareContextSource.Activity("message", old, old.plusSeconds(20));
        var config = new ObjectMapper().createObjectNode();
        assertTrue(CareContextSource.due(state(80, old, 0, "", Instant.EPOCH), activity, config, now));
        assertFalse(CareContextSource.due(state(30, old, 0, "", Instant.EPOCH), activity, config, now));
        assertFalse(CareContextSource.due(state(80, old, 2, "", Instant.EPOCH), activity, config, now));
        assertFalse(CareContextSource.due(state(80, old, 0, "uncertain", Instant.EPOCH), activity, config, now));
        assertFalse(CareContextSource.due(state(80, old, 0, "", now.plusSeconds(1)), activity, config, now));
        assertFalse(CareContextSource.due(state(80, now.minusSeconds(60), 0, "", Instant.EPOCH), activity, config, now));
    }
    /** 心情随时间回归平静，正负感受都不能越过零点继续变化。 */
    @Test void decaysMoodWithoutChangingAffinity() {
        var now = Instant.now();
        var value = new SocialState(80, -40, now, now, "evidence", now, "source", Instant.EPOCH, 0, "", now, 1);
        assertEquals(-20, value.currentMood(now.plus(Duration.ofHours(6)), 12));
        assertEquals(0, value.currentMood(now.plus(Duration.ofDays(2)), 12));
        assertEquals(80, value.affinity());
    }
    /**
     * @param affinity 亲近度
     * @param user 最新来信
     * @param count 关怀次数
     * @param pending 未决标识
     * @param next 冷却
     * @return 测试状态
     */
    private static SocialState state(int affinity, Instant user, int count, String pending, Instant next) {
        return new SocialState(affinity, 0, user, user, "", user, "message", Instant.EPOCH, count, pending, next, 1);
    }
}
