package org.zexnocs.teanekoagent.adapter.history;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 独立历史表，按可信分区及递增序号分页，过期索引用于有界清理。
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Entity @Getter @Setter @NoArgsConstructor
@Table(name = "agent_conversation_history", uniqueConstraints = @UniqueConstraint(columnNames = {"partition_key", "message_key"}),
        indexes = {@Index(name = "idx_agent_history_page", columnList = "partition_key,id"), @Index(name = "idx_agent_history_expiry", columnList = "expires_at,id")})
public class HistoryDataObject {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "partition_key", length = 64, nullable = false) private String partition;
    @Column(name = "message_key", length = 64, nullable = false) private String messageKey;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "payload", nullable = false, columnDefinition = "longtext") private String payload;
}
