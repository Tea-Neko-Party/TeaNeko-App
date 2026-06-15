package org.zexnocs.teanekoplugin.general;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoplugin.general.info.messageboard.MessageBoardInfoData;
import org.zexnocs.teanekoplugin.general.info.personal.PersonalInfoData;
import org.zexnocs.teanekoplugin.general.signin.data.SignInChunkData;
import org.zexnocs.teanekoplugin.general.signin.data.SignInData;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 时间字段持久化兼容性测试。
 *
 * @author zExNocs
 * @date 2026/06/12
 * @since 4.4.0
 */
class TimePersistenceCompatibilityTest {
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .findAndAddModules()
            .build();

    @Test
    void shouldReadLegacyMillisecondFields() {
        var expected = Instant.ofEpochMilli(1_750_000_000_123L);

        var signIn = mapper.readValue(
                "{\"total_days\":1,\"last_time_ms\":1750000000123}", SignInData.class);
        var chunk = mapper.readValue(
                "{\"last_time_ms\":1750000000123,\"continuous\":1}", SignInChunkData.class);
        var personalInfo = mapper.readValue(
                "{\"personal_info\":\"test\",\"time\":1750000000123}", PersonalInfoData.class);
        var message = mapper.readValue(
                "{\"message\":\"test\",\"sender_id\":\"%s\",\"time\":1750000000123}"
                        .formatted(UUID.randomUUID()), MessageBoardInfoData.class);

        assertEquals(expected, signIn.getLastTime());
        assertEquals(expected, chunk.getLastTime());
        assertEquals(expected, personalInfo.getUpdatedAt());
        assertEquals(expected, message.getCreatedAt());
    }

    @Test
    void shouldWriteInstantFieldsWithoutLegacyMillis() {
        var instant = Instant.parse("2026-06-12T12:34:56Z");
        var json = mapper.writeValueAsString(SignInData.builder()
                .lastTime(instant)
                .build());

        assertTrue(json.contains("\"last_time\":\"2026-06-12T12:34:56Z\""));
        assertFalse(json.contains("last_time_ms"));
    }
}
