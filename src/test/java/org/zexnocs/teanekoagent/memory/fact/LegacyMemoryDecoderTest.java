package org.zexnocs.teanekoagent.memory.fact;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.adapter.memory.LegacyMemoryDecoder;
import org.zexnocs.teanekoagent.memory.fact.legacy.LegacyMemoryLocation;
import org.zexnocs.teanekoagent.memory.fact.legacy.LegacyMemorySnapshot;
import org.zexnocs.teanekoagent_old.memory.*;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用真实旧 Bean 及缺失字段资料验证可重复转换、时间兼容和原始证据保留。
 * 旧类型只作为兼容测试样本，新生产代码不导入旧包。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LegacyMemoryDecoderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LegacyMemoryDecoder decoder = new LegacyMemoryDecoder(mapper);
    private final LegacyMemoryLocation location = new LegacyMemoryLocation("旧群", "旧角色", "平台用户");
    private final Instant fallback = Instant.parse("2026-10-29T01:00:00Z");

    /**
     * 以同一明确来源和估算时刻转换，避免测试依赖系统时钟。
     * @param json 原数组 JSON
     * @return 稳定预览
     */
    private LegacyMemorySnapshot decode(String json) { return decoder.decode(location, 7, json, fallback); }

    /** 验证真实旧序列化格式、小时精度、锁定、过期及原来源均保留。 */
    @Test void actualLegacyBeanKeepsEvidenceAndTimes() {
        var old = new AgentMemoryRecord(); old.setId("旧 ID"); old.setType(MemoryRecordType.PREFERENCE);
        old.setContent("喜欢清茶"); old.setSource("原消息:42"); old.setSubjectId("123456"); old.setScopeId("旧群"); old.setAgentId("旧角色");
        old.setTags(List.of("茶", "偏好")); old.setConfidence(.75); old.setImportance(MemoryImportance.HIGH); old.setLocked(true);
        old.setCreatedAt(fallback.minusSeconds(3600)); old.setUpdatedAt(fallback.minusSeconds(1800)); old.setExpiresAt(fallback.minusSeconds(1));
        var time = MemoryTimeRange.exact(fallback.minusSeconds(7200)); time.setPrecision(MemoryTimePrecision.HOUR); time.setOriginalExpression("两小时前"); old.setEventTime(time);
        var json = mapper.writeValueAsString(List.of(old)); var snapshot = assertDoesNotThrow(() -> decode(json), () -> "真实旧 Bean 的序列化样本：" + json); var entry = snapshot.entries().getFirst();
        assertEquals(7, snapshot.version()); assertEquals(LegacyMemoryDecoder.digest(json), snapshot.digest()); assertTrue(snapshot.skipped().isEmpty());
        assertEquals("旧 ID", entry.id()); assertEquals("123456", entry.subjectKey()); assertEquals(MemoryFactValue.Kind.PREFERENCE, entry.value().kind());
        assertEquals(MemoryEventTime.Precision.HOUR, entry.value().eventTime().precision()); assertEquals("两小时前", entry.value().eventTime().expression());
        assertEquals(old.getCreatedAt(), entry.createdAt()); assertEquals(old.getUpdatedAt(), entry.updatedAt()); assertEquals(old.getExpiresAt(), entry.value().expiresAt());
        assertEquals(.75, entry.value().confidence()); assertEquals(MemoryFactValue.Importance.HIGH, entry.value().importance()); assertTrue(entry.locked());
        var raw = mapper.readTree(entry.provenance().originalJson()); assertEquals("原消息:42", raw.get("source").stringValue());
        assertEquals("123456", raw.get("subjectId").stringValue()); assertEquals(mapper.readTree(json).get(0), raw);
        assertTrue(entry.provenance().adjustments().isEmpty()); assertEquals(LegacyMemoryDecoder.digest(entry.provenance().originalJson()), entry.provenance().digest());
    }

    /** 验证缺失 ID、主体及时间有可重复依据，不触发旧 Bean 的随机或当前时刻默认值。 */
    @Test void missingFieldsUseFixedDocumentedDefaults() {
        var json = "[{\"content\":\"明确事实\"}]"; var first = decode(json); var entry = first.entries().getFirst();
        assertEquals(first, decode(json)); assertEquals("row-index:0", entry.id()); assertEquals("平台用户", entry.subjectKey());
        assertEquals(fallback, entry.createdAt()); assertEquals(fallback, entry.updatedAt()); assertFalse(entry.locked());
        assertEquals(1, entry.value().confidence()); assertEquals(MemoryFactValue.Importance.NORMAL, entry.value().importance());
        assertEquals(MemoryFactValue.Kind.FACT, entry.value().kind()); assertEquals(MemoryEventTime.unknown(), entry.value().eventTime());
        assertEquals(5, entry.provenance().adjustments().size());
        assertNotEquals(first, decoder.decode(location, 7, json, fallback.plusSeconds(1)));
        assertThrows(UnsupportedOperationException.class, () -> first.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> entry.provenance().adjustments().clear());
    }

    /** 验证旧全部时间精度可读，不把小时和年份降级成未知。 */
    @Test void allKnownLegacyPrecisionsSurvive() {
        for (var precision : MemoryTimePrecision.values()) {
            if (precision == MemoryTimePrecision.UNKNOWN) { continue; }
            var snapshot = decode("[{\"id\":\"时间\",\"content\":\"事件\",\"eventTime\":{\"start\":\"2026-10-28T10:00:00Z\",\"precision\":\"" + precision + "\"}}]");
            assertEquals(precision.name(), snapshot.entries().getFirst().value().eventTime().precision().name());
        }
    }

    /** 验证只有终点的旧查询使用记录时刻形成区间，且倒置调整可以追溯。 */
    @Test void endOnlyPreservesOldRangeRatherThanBecomingPoint() {
        var json = "[{\"content\":\"事件\",\"createdAt\":\"2026-10-29T01:00:00Z\",\"eventTime\":{\"end\":\"2026-10-28T01:00:00Z\",\"originalExpression\":\"昨天以前\"}}]";
        var entry = decode(json).entries().getFirst(); var time = entry.value().eventTime();
        assertEquals(fallback.minusSeconds(86400), time.start()); assertEquals(fallback, time.end());
        assertEquals(MemoryEventTime.Precision.APPROXIMATE, time.precision()); assertEquals("昨天以前", time.expression());
        assertTrue(time.overlaps(fallback.minusSeconds(3600), fallback.minusSeconds(3599), fallback, true));
        assertTrue(entry.provenance().adjustments().contains("仅有事件终点按旧规则补入记录时间"));
        assertTrue(entry.provenance().adjustments().contains("倒置事件区间按旧规则交换端点"));
        assertTrue(entry.provenance().originalJson().contains("昨天以前"));
    }

    /** 验证倒置有端点范围和无端点伪精度按明确兼容规则处理，原文没有消失。 */
    @Test void inconsistentEventPrecisionIsMarkedWithoutRewritingEvidence() {
        var inverted = decode("[{\"content\":\"事件\",\"eventTime\":{\"start\":\"2026-10-29T01:00:00Z\",\"end\":\"2026-10-28T01:00:00Z\",\"precision\":\"DAY\"}}]").entries().getFirst();
        assertEquals(fallback.minusSeconds(86400), inverted.value().eventTime().start()); assertEquals(MemoryEventTime.Precision.DAY, inverted.value().eventTime().precision());
        var absent = decode("[{\"content\":\"事件\",\"eventTime\":{\"precision\":\"YEAR\",\"originalExpression\":\"某年\"}}]").entries().getFirst();
        assertEquals(MemoryEventTime.Precision.UNKNOWN, absent.value().eventTime().precision());
        assertEquals("某年", absent.value().eventTime().expression()); assertTrue(absent.provenance().adjustments().contains("无事件端点改用未知精度"));
    }

    /** 验证人格、关系和摘要保持独立，未转换类型在预览中明确报告。 */
    @Test void knownNonFactTypesAreReportedAndRemainInSource() {
        var snapshot = decode("[{\"id\":\"关系\",\"type\":\"RELATIONSHIP\"},{\"id\":\"人格\",\"type\":\"PERSONALITY_DELTA\"},{\"id\":\"摘要\",\"type\":\"EPISODE_SUMMARY\"}]");
        assertTrue(snapshot.entries().isEmpty()); assertEquals(3, snapshot.skipped().size());
        assertEquals("人格", snapshot.skipped().get(1).id());
    }

    /** 验证损坏、未来格式、重复键、尾随数据和未知类型整体失败且异常不泄露资料。 */
    @Test void unsupportedJsonFailsWithoutOriginalBodyOrCause() {
        for (var json : List.of("{}", "null", "[] []", "[", "[7]", "[{\"content\":\"秘密\",\"content\":\"覆盖\"}]",
                "[{\"content\":\"秘密\",\"futureField\":1}]", "[{\"content\":\"秘密\",\"type\":\"FUTURE\"}]",
                "[{\"content\":\"秘密\",\"confidence\":\"1\"}]", "[{\"content\":\"秘密\",\"locked\":1}]",
                "[{\"content\":\"秘密\",\"tags\":[1]}]", "[{\"content\":\"秘密\",\"source\":{}}]",
                "[{\"content\":\"秘密\",\"eventTime\":{\"precision\":\"FUTURE\"}}]",
                "[{\"content\":\"秘密\",\"eventTime\":{\"known\":true}}]")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> decode(json)); assertNull(failure.getCause());
            assertFalse(failure.getMessage().contains("秘密"));
        }
    }

    /** 验证重复标识、正文与物理来源冲突及记录时间倒置不能被默认值掩盖。 */
    @Test void identityAndRecordedTimeConflictsRejectWholeArray() {
        for (var json : List.of("[{\"id\":\"重复\",\"content\":\"甲\"},{\"id\":\"重复\",\"content\":\"乙\"}]",
                "[{\"id\":\"row-index:1\",\"content\":\"甲\"},{\"content\":\"乙\"}]",
                "[{\"content\":\"甲\",\"scopeId\":\"其他群\"}]", "[{\"content\":\"甲\",\"agentId\":\"其他角色\"}]",
                "[{\"content\":\"甲\",\"createdAt\":\"2026-10-29T01:00:00Z\",\"updatedAt\":\"2026-10-28T01:00:00Z\"}]")) {
            assertThrows(IllegalArgumentException.class, () -> decode(json));
        }
    }

    /** 验证原文 UTF-8 大小、数组条数和新内容限额均明确拒绝，不截断成成功。 */
    @Test void oversizedSourcesAndValuesAreNotSilentlyTruncated() {
        assertThrows(IllegalArgumentException.class, () -> decode("[{\"content\":\"" + "文".repeat(350000) + "\"}]"));
        assertThrows(IllegalArgumentException.class, () -> decode("[" + String.join(",", Collections.nCopies(1001, "{\"content\":\"事实\"}")) + "]"));
        assertThrows(IllegalArgumentException.class, () -> decode("[{\"content\":\"" + "文".repeat(4097) + "\"}]"));
        assertThrows(IllegalArgumentException.class, () -> decode("[{\"content\":\"事实\",\"confidence\":1.1}]"));
    }

    /** 验证缺失字段兼容不修改共享 JSON 配置，实际旧键不被隐式正规化。 */
    @Test void parserConfigurationAndPhysicalKeysStayExplicit() {
        new LegacyMemoryDecoder(mapper); assertEquals(2, mapper.readTree("{\"键\":1,\"键\":2}").get("键").intValue());
        assertThrows(IllegalArgumentException.class, () -> new LegacyMemoryLocation(" 旧群", "角色", "用户"));
        assertThrows(IllegalArgumentException.class, () -> new LegacyMemoryLocation("群", "角色", ""));
        assertThrows(IllegalArgumentException.class, () -> new LegacyMemoryLocation("群".repeat(250), "角色", "用户"));
        assertTrue(decode("[]").entries().isEmpty());
    }
}
