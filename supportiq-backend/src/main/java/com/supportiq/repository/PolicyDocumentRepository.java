package com.supportiq.repository;

import com.supportiq.model.PolicyDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PolicyDocumentRepository extends JpaRepository<PolicyDocument, Long> {

    /**
     * Retrieve all documents ordered by creation timestamp descending (newest first).
     */
    List<PolicyDocument> findAllByOrderByCreatedAtDesc();
}
