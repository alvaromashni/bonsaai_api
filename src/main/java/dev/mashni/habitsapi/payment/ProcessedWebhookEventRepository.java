package dev.mashni.habitsapi.payment;

import dev.mashni.habitsapi.payment.model.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, UUID> {
    boolean existsByEventId(String eventId);
}
