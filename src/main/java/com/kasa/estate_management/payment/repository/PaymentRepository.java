package com.kasa.estate_management.payment.repository;

import com.kasa.estate_management.payment.model.Payment;
import com.kasa.estate_management.payment.model.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByCheckoutRequestId(String checkoutRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.checkoutRequestId = :checkoutRequestId")
    Optional<Payment> findByCheckoutRequestIdForUpdate(@Param("checkoutRequestId") String checkoutRequestId);

    boolean existsByMpesaReceiptNumber(String mpesaReceiptNumber);

    List<Payment> findByInvoiceId(UUID invoiceId);

    List<Payment> findByInvoiceIdAndStatus(UUID invoiceId, PaymentStatus status);
}