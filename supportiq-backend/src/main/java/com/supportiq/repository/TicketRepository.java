package com.supportiq.repository;

import com.supportiq.model.Ticket;
import com.supportiq.model.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    boolean existsByTicketNumber(String ticketNumber);

    Page<Ticket> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Ticket> findByTicketNumberAndUserId(String ticketNumber, Long userId);

    List<Ticket> findByUserIdAndRelatedOrderNumberAndStatusInOrderByCreatedAtDesc(
            Long userId,
            String relatedOrderNumber,
            Collection<TicketStatus> statuses
    );

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

    @Query("SELECT t.ticketNumber FROM Ticket t WHERE t.ticketNumber LIKE 'TKT-%' ORDER BY t.id DESC")
    List<String> findLatestTicketNumbers(Pageable pageable);
}
