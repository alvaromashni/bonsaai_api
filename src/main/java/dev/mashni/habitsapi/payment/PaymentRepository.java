package dev.mashni.habitsapi.payment;

import dev.mashni.habitsapi.payment.model.Payment;
import dev.mashni.habitsapi.payment.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByCorrelationId(String correlationId);

    List<Payment> findByUserIdAndStatus(UUID userId, PaymentStatus status);

    List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
