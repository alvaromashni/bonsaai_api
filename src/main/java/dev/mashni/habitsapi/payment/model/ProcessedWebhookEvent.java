package dev.mashni.habitsapi.payment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_webhook_events")
public class ProcessedWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "event_id", unique = true, nullable = false)
    private String eventId;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        processedAt = LocalDateTime.now();
    }

    public ProcessedWebhookEvent() {}

    public ProcessedWebhookEvent(String eventId, String correlationId, String eventType) {
        this.eventId = eventId;
        this.correlationId = correlationId;
        this.eventType = eventType;
    }

    public UUID getId() { return id; }
    public String getEventId() { return eventId; }
    public String getCorrelationId() { return correlationId; }
    public String getEventType() { return eventType; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
