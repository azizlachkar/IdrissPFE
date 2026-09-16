package com.cmrt.pfe.services;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Outbound email. Delivery is opt-in ({@code app.mail.enabled}) so the platform runs
 * end-to-end without SMTP credentials - in-app notifications are the primary channel
 * and email only mirrors them.
 */
@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;
    private final String frontendUrl;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.enabled}") boolean enabled,
                        @Value("${spring.mail.username}") String from,
                        @Value("${app.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    @Async("notificationExecutor")
    public void sendVerificationEmail(String to, String prenom, String nom, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        String html = layout("Bienvenue chez CMRT",
                "Bonjour " + safe(prenom) + " " + safe(nom) + ",",
                "Votre compte sur la plateforme Engineering CMRT a bien ete cree. "
                        + "Activez-le en cliquant sur le bouton ci-dessous.",
                "Activer mon compte", link);
        send(to, "CMRT Engineering - Activez votre compte", html);
    }

    @Async("notificationExecutor")
    public void sendNotificationEmail(String to, String title, String message, String link) {
        String url = link != null ? frontendUrl + link : frontendUrl;
        String html = layout(title, title, message, "Ouvrir la plateforme", url);
        send(to, "CMRT Engineering - " + title, html);
    }

    @Async("notificationExecutor")
    public void sendPasswordResetEmail(String to, String prenom, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        String html = layout("Reinitialisation du mot de passe",
                "Bonjour " + safe(prenom) + ",",
                "Une reinitialisation de mot de passe a ete demandee pour votre compte. "
                        + "Ce lien expire dans 30 minutes. Ignorez ce message si vous n'etes pas a l'origine de la demande.",
                "Choisir un nouveau mot de passe", link);
        send(to, "CMRT Engineering - Reinitialisation du mot de passe", html);
    }

    private void send(String to, String subject, String html) {
        if (!enabled) {
            log.info("[email desactive] destinataire={} sujet={}", to, subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email envoye a {}", to);
        } catch (Exception e) {
            // Never fail the business operation because SMTP is unavailable.
            log.warn("Echec de l'envoi de l'email a {} : {}", to, e.getMessage());
        }
    }

    private String layout(String heading, String greeting, String body, String ctaLabel, String ctaUrl) {
        return """
                <div style="font-family:Segoe UI,Arial,sans-serif;background:#f4f7fb;padding:32px">
                  <div style="max-width:560px;margin:auto;background:#fff;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0">
                    <div style="background:#1a365d;color:#fff;padding:24px 28px">
                      <div style="font-size:20px;font-weight:700;letter-spacing:1px">CMRT</div>
                      <div style="font-size:13px;opacity:.8">Plateforme Engineering</div>
                    </div>
                    <div style="padding:28px">
                      <h2 style="margin:0 0 12px;color:#1a365d;font-size:18px">%s</h2>
                      <p style="color:#2d3748;margin:0 0 8px">%s</p>
                      <p style="color:#4a5568;line-height:1.6;margin:0 0 24px">%s</p>
                      <a href="%s" style="display:inline-block;background:#00b5d8;color:#fff;text-decoration:none;padding:12px 22px;border-radius:8px;font-weight:600">%s</a>
                      <p style="color:#a0aec0;font-size:12px;margin-top:24px">Si le bouton ne fonctionne pas, copiez ce lien :<br>%s</p>
                    </div>
                    <div style="background:#f7fafc;padding:16px 28px;color:#718096;font-size:12px">
                      Controle, Mesure et Regulation Tunisie - message automatique
                    </div>
                  </div>
                </div>
                """.formatted(heading, greeting, body, ctaUrl, ctaLabel, ctaUrl);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
