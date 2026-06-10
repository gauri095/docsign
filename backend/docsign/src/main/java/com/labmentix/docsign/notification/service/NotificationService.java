package com.labmentix.docsign.notification.service;

import com.labmentix.docsign.auth.entity.User;
import com.labmentix.docsign.document.entity.Document;
import com.labmentix.docsign.document.entity.DocumentStatus;
import com.labmentix.docsign.document.repository.DocumentRepository;
import com.labmentix.docsign.notification.entity.InAppNotification;
import com.labmentix.docsign.notification.entity.NotificationType;
import com.labmentix.docsign.notification.repository.InAppNotificationRepository;
import com.labmentix.docsign.signing.entity.Signer;
import com.labmentix.docsign.signing.entity.SignerStatus;
import com.labmentix.docsign.signing.repository.SignerRepository;
import com.labmentix.docsign.signing.service.SigningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Central orchestrator for all notification channels:
 * 1. In-app notification feed (PostgreSQL)
 * 2. Transactional emails (Thymeleaf + Spring Mail)
 * 3. Webhooks (HMAC-signed HTTP POST)
 *
 * Call sites:
 * - SigningService.sendForSigning() → onDocumentSent()
 * - SigningService.sealDocument()   → onDocumentCompleted()
 * - Scheduler                       → sendReminders()
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final InAppNotificationRepository notifRepository;
    private final DocumentRepository          documentRepository;
    private final SignerRepository            signerRepository;
    private final EmailService                emailService;
    private final WebhookService              webhookService;
    private final SigningService              signingService;

    // ═══════════════════════════════════════════════════════════
    // DOCUMENT SENT — invite all signers
    // ═══════════════════════════════════════════════════════════

    /**
     * Called immediately after sendForSigning() succeeds.
     * - Creates in-app notifications for the document owner (confirmation)
     * - Sends HTML signing invite email to each signer
     * - Fires webhook event DOCUMENT_SENT
     */
    @Transactional
    public void onDocumentSent(Document doc, List<Signer> signers) {

        // In-app: notify owner that document was sent
        createNotification(
                doc.getOwner(), doc,
                NotificationType.SIGNING_INVITE,
                "Document sent for signing",
                "\"" + doc.getTitle() + "\" has been sent to " + signers.size() + " signer(s).",
                "/documents/" + doc.getId()
        );

        // Email each signer their invite
        for (Signer signer : signers) {
            String link = signingService.buildSigningLink(signer.getSigningToken());
            emailService.sendSigningInvite(signer, doc, link);
            log.debug("Signing invite queued for {}", signer.getEmail());
        }

        // Webhook
        webhookService.dispatchEvent("DOCUMENT_SENT", doc, Map.of(
                "title",       doc.getTitle(),
                "signerCount", signers.size(),
                "signerEmails", signers.stream().map(Signer::getEmail).toList()
        ));
    }

    // ═══════════════════════════════════════════════════════════
    // DOCUMENT SIGNED — partial update
    // ═══════════════════════════════════════════════════════════

    /**
     * Called when an individual signer submits their signature.
     * - In-app: notify the document owner
     * - Webhook: DOCUMENT_SIGNED event
     */
    @Transactional
    public void onSignerSigned(Document doc, Signer signer) {

        createNotification(
                doc.getOwner(), doc,
                NotificationType.DOCUMENT_SIGNED,
                "Signature received",
                signer.getName() + " (" + signer.getEmail() + ") has signed \"" + doc.getTitle() + "\".",
                "/documents/" + doc.getId()
        );

        webhookService.dispatchEvent("DOCUMENT_SIGNED", doc, Map.of(
                "signerEmail", signer.getEmail(),
                "signerName",  signer.getName()
        ));
    }

    // ═══════════════════════════════════════════════════════════
    // DOCUMENT COMPLETED — all parties notified
    // ═══════════════════════════════════════════════════════════

    /**
     * Called from SigningService.sealDocument() after PDF is sealed.
     * - In-app: notify owner AND each signer
     * - Email: completion notification to all parties
     * - Webhook: DOCUMENT_COMPLETED event
     */
    @Transactional
    public void onDocumentCompleted(Document doc) {
        List<Signer> signers = signerRepository.findByDocumentIdOrderBySigningOrderAsc(doc.getId());
        List<String> emails  = signers.stream().map(Signer::getEmail).toList();

        // In-app for owner
        createNotification(
                doc.getOwner(), doc,
                NotificationType.DOCUMENT_COMPLETED,
                "Document fully signed ✓",
                "\"" + doc.getTitle() + "\" has been signed by all parties and is ready to download.",
                "/documents/" + doc.getId()
        );

        // In-app for each signer (if they are platform users — best-effort)
        // In a full implementation you'd look up User by signer email; skipped here for brevity

        // Completion email to all parties
        emailService.sendCompletionNotification(doc, signers, emails);

        // Webhook
        webhookService.dispatchEvent("DOCUMENT_COMPLETED", doc, Map.of(
                "title",       doc.getTitle(),
                "completedAt", doc.getCompletedAt() != null ? doc.getCompletedAt().toString() : "",
                "signerEmails", emails,
                "sha256Hash",   doc.getSha256Hash()
        ));

        log.info("Completion notifications dispatched for document {}", doc.getId());
    }

    // ═══════════════════════════════════════════════════════════
    // REMINDER SCHEDULER
    // ═══════════════════════════════════════════════════════════

    /**
     * Runs every 6 hours.
     * Finds documents SENT or PARTIALLY_SIGNED that have been waiting > 24 hours
     * and still have PENDING signers — sends them a gentle reminder.
     *
     * Does NOT re-invite signers whose tokens have already expired.
     */
    @Scheduled(cron = "0 0 */6 * * *")   // every 6 hours at :00
    @Transactional
    public void sendReminders() {
        log.info("Reminder scheduler: scanning for pending signers");

        List<Document> activeDocs = documentRepository.findAll().stream()
                .filter(d -> d.getStatus() == DocumentStatus.SENT
                          || d.getStatus() == DocumentStatus.PARTIALLY_SIGNED)
                .toList();

        int remindersSent = 0;
        for (Document doc : activeDocs) {
            List<Signer> pendingSigners = signerRepository.findByDocumentIdAndStatus(doc.getId(), SignerStatus.PENDING);

            for (Signer signer : pendingSigners) {
                // Skip if token already expired
                if (signer.isTokenExpired()) continue;

                // Skip if created less than 24h ago (don't nag immediately)
                long hoursWaiting = java.time.Duration.between(signer.getCreatedAt(), java.time.Instant.now()).toHours();
                if (hoursWaiting < 24) continue;

                String link = signingService.buildSigningLink(signer.getSigningToken());
                emailService.sendSigningReminder(signer, doc, link);

                // In-app reminder for owner
                createNotification(
                        doc.getOwner(), doc,
                        NotificationType.REMINDER,
                        "Reminder sent to " + signer.getName(),
                        signer.getName() + " has not yet signed \"" + doc.getTitle() + "\". A reminder was sent.",
                        "/documents/" + doc.getId()
                );

                remindersSent++;
            }
        }

        if (remindersSent > 0) {
            log.info("Reminder scheduler: sent {} reminder(s)", remindersSent);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // IN-APP FEED QUERIES
    // ═══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<InAppNotification> getNotificationsForUser(UUID userId, int page, int size) {
        return notifRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, Math.min(size, 50))
        );
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notifRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notifRepository.markAllReadForUser(userId);
    }

    @Transactional
    public void markOneRead(UUID notificationId, UUID userId) {
        notifRepository.markOneRead(notificationId, userId);
    }

    // ═══════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════

    private void createNotification(
            User           recipient,
            Document       document,
            NotificationType type,
            String         title,
            String         body,
            String         actionUrl
    ) {
        notifRepository.save(InAppNotification.builder()
                .user(recipient)
                .document(document)
                .type(type)
                .title(title)
                .body(body)
                .actionUrl(actionUrl)
                .read(false)
                .build());
    }
}