package com.labmentix.docsign.signing.repository;

import com.labmentix.docsign.signing.entity.Signature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SignatureRepository extends JpaRepository<Signature, UUID> {

    List<Signature> findByDocumentIdOrderBySignedAtAsc(UUID documentId);

    List<Signature> findBySignerId(UUID signerId);

    /**
     * Fetches all signatures for a document with their signer eagerly loaded.
     * Used during PDF sealing to gather all placements in one query.
     */
    @Query("""
           SELECT sig FROM Signature sig
           JOIN FETCH sig.signer s
           WHERE sig.document.id = :docId
           ORDER BY sig.signedAt ASC
           """)
    List<Signature> findByDocumentIdWithSigner(@Param("docId") UUID documentId);
}