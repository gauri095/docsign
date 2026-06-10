package com.labmentix.docsign.notification.service;

import com.labmentix.docsign.document.entity.Document;
import com.labmentix.docsign.signing.entity.Signer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Renders Thymeleaf HTML email templates and dispatches them via Spring Mail.
 *
 * All public methods are {@code @Async} — email delivery never blocks callers.
 * Failures are logged but not re-thrown; the webhook system handles guaranteed delivery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy 'at' HH:mm 'UTC'")
                             .withZone(ZoneOffset.UTC);

    private final JavaMailSender  mailSender;
    private final TemplateEngine  templateEngine;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.signing.base-url}")
    private String baseUrl;

    // ═══════════════════════════════════════════════════════════
    // SIGNING INVITE
    // ═══════════════════════════════════════════════════════════

    /**
     * Sends a signing invitation to a single signer.
     * Called by NotificationService after sendForSigning() completes.
     */
    @Async("notificationExecutor")
    public void sendSigningInvite(Signer signer, Document doc, String signingLink) {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("signerName",    signer.getName());
        ctx.setVariable("senderName",    doc.getOwner().getName());
        ctx.setVariable("senderEmail",   doc.getOwner().getEmail());
        ctx.setVariable("documentTitle", doc.getTitle());
        ctx.setVariable("signingLink",   signingLink);
        ctx.setVariable("signingOrder",  signer.getSigningOrder());
        ctx.setVariable("expiresAt",     DISPLAY_FMT.format(signer.getTokenExpiresAt()));
        ctx.setVariable("appBaseUrl",    baseUrl);

        String html = templateEngine.process("email/signing-invite", ctx);
        send(signer.getEmail(), signer.getName(),
             "You have a document to sign: " + doc.getTitle(),
             html);
    }

    // ═══════════════════════════════════════════════════════════
    // COMPLETION NOTIFICATION
    // ═══════════════════════════════════════════════════════════

    /**
     * Notifies every party (owner + all signers) when a document is completed.
     * Each recipient gets a personalised copy with their name.
     */
    @Async("notificationExecutor")
    public void sendCompletionNotification(
            Document       doc,
            List<Signer>   signers,
            List<String>   signerEmails
    ) {
        String downloadUrl = baseUrl + "/documents/" + doc.getId();
        String auditUrl    = baseUrl + "/documents/" + doc.getId() + "/audit";

        // Build a context shared across all recipients
        Context baseCtx = new Context(Locale.ENGLISH);
        baseCtx.setVariable("documentTitle", doc.getTitle());
        baseCtx.setVariable("completedAt",   DISPLAY_FMT.format(doc.getCompletedAt()));
        baseCtx.setVariable("signerCount",   signers.size());
        baseCtx.setVariable("signers",       signerEmails);
        baseCtx.setVariable("sha256Hash",    doc.getSha256Hash());
        baseCtx.setVariable("downloadUrl",   downloadUrl);
        baseCtx.setVariable("auditUrl",      auditUrl);
        baseCtx.setVariable("appBaseUrl",    baseUrl);

        // Notify owner
        baseCtx.setVariable("recipientName", doc.getOwner().getName());
        String ownerHtml = templateEngine.process("email/document-completed", baseCtx);
        send(doc.getOwner().getEmail(), doc.getOwner().getName(),
             "✅ Document signed: " + doc.getTitle(),
             ownerHtml);

        // Notify each signer
        for (Signer signer : signers) {
            baseCtx.setVariable("recipientName", signer.getName());
            String signerHtml = templateEngine.process("email/document-completed", baseCtx);
            send(signer.getEmail(), signer.getName(),
                 "✅ Document signed: " + doc.getTitle(),
                 signerHtml);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // REMINDER
    // ═══════════════════════════════════════════════════════════

    /**
     * Sends a signing reminder to a specific pending signer.
     * Called by the scheduled reminder job in NotificationService.
     */
    @Async("notificationExecutor")
    public void sendSigningReminder(Signer signer, Document doc, String signingLink) {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("signerName",    signer.getName());
        ctx.setVariable("senderName",    doc.getOwner().getName());
        ctx.setVariable("senderEmail",   doc.getOwner().getEmail());
        ctx.setVariable("documentTitle", doc.getTitle());
        ctx.setVariable("signingLink",   signingLink);
        ctx.setVariable("expiresAt",     DISPLAY_FMT.format(signer.getTokenExpiresAt()));
        ctx.setVariable("appBaseUrl",    baseUrl);

        String html = templateEngine.process("email/signing-reminder", ctx);
        send(signer.getEmail(), signer.getName(),
             "⏱ Reminder: " + doc.getTitle() + " is waiting for your signature",
             html);
    }

    // ═══════════════════════════════════════════════════════════
    // CORE SEND
    // ═══════════════════════════════════════════════════════════

    /**
     * Sends a single HTML email. Failures are logged — never rethrown.
     */
    private void send(String toEmail, String toName, String subject, String htmlBody) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom(fromAddress, fromName);
            helper.setTo(toEmail);            // MimeMessageHelper accepts plain address
            helper.setSubject(subject);
            helper.setText(htmlBody, true);   // true = HTML

            mailSender.send(msg);
            log.info("Email sent: [{}] → {}", subject, toEmail);

        } catch (MessagingException e) {
            log.error("Email failed to {}: {} — {}", toEmail, subject, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected email error to {}: {}", toEmail, e.getMessage(), e);
        }
    }
}