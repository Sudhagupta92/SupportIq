package com.supportiq.repository;

import com.supportiq.model.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

    List<TicketMessage> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    List<TicketMessage> findByTicketTicketNumberOrderByCreatedAtAsc(String ticketNumber);
}
