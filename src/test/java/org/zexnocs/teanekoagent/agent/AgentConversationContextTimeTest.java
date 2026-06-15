package org.zexnocs.teanekoagent.agent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

/**
 * Agent 对话上下文时间线测试。
 * <br>验证消息发生时间、记录时间以及上下文压缩后的时间信息保留行为。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
class AgentConversationContextTimeTest {
    /**
     * 验证新增消息时会同时记录外部发生时间和系统写入时间。
     */
    @Test
    void recordsMessageOccurrenceAndRecordingTimes() {
        var context = new AgentConversationContext("conversation", "agent", "scope", "user");
        var occurredAt = Instant.parse("2026-06-11T12:22:00Z");

        var message = context.addUserMessage("hello", occurredAt);
        var timeline = context.snapshotMessageTimeline();

        Assertions.assertEquals(1, timeline.size());
        Assertions.assertSame(message, timeline.getFirst().message());
        Assertions.assertEquals(occurredAt, timeline.getFirst().occurredAt());
        Assertions.assertNotNull(timeline.getFirst().recordedAt());
    }

    /**
     * 验证替换或压缩消息列表时，仍保留被保留消息原有的时间线记录。
     */
    @Test
    void preservesTimelineWhenMessagesAreCompressed() {
        var context = new AgentConversationContext("conversation", "agent", "scope", "user");
        var first = context.addUserMessage("first", Instant.parse("2026-06-11T10:00:00Z"));
        context.addAssistantMessage("second");

        context.replaceMessages(List.of(first));

        Assertions.assertEquals(1, context.snapshotMessageTimeline().size());
        Assertions.assertEquals(
                Instant.parse("2026-06-11T10:00:00Z"),
                context.snapshotMessageTimeline().getFirst().occurredAt()
        );
    }
}
