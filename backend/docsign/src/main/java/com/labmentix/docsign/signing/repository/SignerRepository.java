package com.labmentix.docsign.signing.repository;

import com.labmentix.docsign.signing.entity.Signer;
import com.labmentix.docsign.signing.entity.SignerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SignerRepository extends JpaRepository<Signer, UUID> {

    Optional<Signer> findBySigningToken(String signingToken);

    List<Signer> findByDocumentIdOrderBySigningOrderAsc(UUID documentId);

    List<Signer> findByDocumentIdAndStatus(UUID documentId, SignerStatus status);

    long countByDocumentIdAndStatus(UUID documentId, SignerStatus status);

    boolean existsByDocumentIdAndEmail(UUID documentId, String email);

    /**
     * Returns true when every signer on the document has SIGNED.
     * Used to determine if the document can transition to COMPLETED.
     */
    @Query("""
           SELECT COUNT(s) = 0
           FROM Signer s
           WHERE s.document.id = :docId
             AND s.status <> 'SIGNED'
           """)
    boolean areAllSignersDone(@Param("docId") UUID documentId);

    /**
     * Returns the next signer(s) who should be notified in a sequential workflow.
     * For parallel workflows this returns all PENDING signers.
     */
    @Query("""
           SELECT s FROM Signer s
           WHERE s.document.id = :docId
             AND s.status = 'PENDING'
           ORDER BY s.signingOrder ASC
           """)
    List<Signer> findPendingSigners(@Param("docId") UUID documentId);
}