package com.labmentix.docsign.document.repository;

import com.labmentix.docsign.document.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(UUID documentId, Integer versionNumber);

    @Query("SELECT MAX(v.versionNumber) FROM DocumentVersion v WHERE v.document.id = :docId")
    Optional<Integer> findMaxVersionNumber(@Param("docId") UUID documentId);
}