package com.supportiq.repository;

import com.supportiq.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "user"})
    Optional<Order> findByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"items", "user"})
    Optional<Order> findByOrderNumberAndUserId(String orderNumber, Long userId);

    @EntityGraph(attributePaths = {"items"})
    List<Order> findByUserIdOrderByPlacedAtDesc(Long userId, Pageable pageable);

    boolean existsByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"items", "user"})
    Page<Order> findAllByOrderByPlacedAtDesc(Pageable pageable);
}
