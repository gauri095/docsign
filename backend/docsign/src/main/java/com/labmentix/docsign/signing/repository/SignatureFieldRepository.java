package com.labmentix.docsign.signing.repository;

import com.labmentix.docsign.signing.entity.SignatureField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SignatureFieldRepository extends JpaRepository<SignatureField, UUID> {

    List<SignatureField> findByDocumentIdOrderByPageNumberAscCreatedAtAsc(UUID documentId);

    List<SignatureField> findByDocumentIdAndAssignedEmail(UUID documentId, String assignedEmail);

    long countByDocumentId(UUID documentId);

    @Modifying
    @Query("DELETE FROM SignatureField sf WHERE sf.document.id = :documentId")
    void deleteAllByDocumentId(@Param("documentId") UUID documentId);
}