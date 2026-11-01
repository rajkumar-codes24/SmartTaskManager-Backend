package com.smarttaskmanager.email.service;

import com.smarttaskmanager.common.config.ApplicationProperties;
import com.smarttaskmanager.email.template.EmailTemplates;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

/**
 * Concrete implementation of {@link EmailService} using Spring's {@link JavaMailSender}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final ApplicationProperties properties;

    @Override
    public void sendVerificationEmail(String recipientEmail, String recipientName, String rawToken) {
        // Use frontend base URL so the email link opens the React verify-email page,
        // not the backend API endpoint directly.
        String baseUrl = properties.frontendBaseUrl();
        String verificationUrl;
        if (baseUrl != null && !baseUrl.isBlank()) {
            verificationUrl = String.format("%s/verify-email?token=%s", baseUrl, rawToken);
        } else {
            // Fallback to backend API if frontend URL is not configured
            verificationUrl = String.format("%s/api/v1/auth/verify-email?token=%s",
                    properties.publicBaseUrl(), rawToken);
        }

        String htmlContent = EmailTemplates.buildVerificationEmailHtml(recipientName, verificationUrl);
        String textContent = EmailTemplates.buildVerificationEmailText(recipientName, verificationUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String fromEmail = properties.mail().from();
            String fromName = properties.mail().fromName();

            helper.setFrom(fromEmail, fromName);
            helper.setTo(recipientEmail);
            helper.setSubject("Verify Your Smart Task Manager Account");
            helper.setText(textContent, htmlContent);

            mailSender.send(message);
            log.info("Verification email successfully dispatched to {}", recipientEmail);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            log.error("Failed to send verification email to {}: {}", recipientEmail, e.getMessage());
            // Intentionally not rethrowing: email delivery failure should not break user registration state
        }
    }

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String recipientName, String rawToken) {
        // Use frontend base URL so the email link opens the React reset-password page,
        // not the backend API endpoint directly.
        String baseUrl = properties.frontendBaseUrl();
        String resetUrl;
        if (baseUrl != null && !baseUrl.isBlank()) {
            resetUrl = String.format("%s/reset-password?token=%s", baseUrl, rawToken);
        } else {
            // Fallback to backend API if frontend URL is not configured
            resetUrl = String.format("%s/api/v1/auth/reset-password?token=%s",
                    properties.publicBaseUrl(), rawToken);
        }

        String htmlContent = EmailTemplates.buildPasswordResetEmailHtml(recipientName, resetUrl);
        String textContent = EmailTemplates.buildPasswordResetEmailText(recipientName, resetUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String fromEmail = properties.mail().from();
            String fromName = properties.mail().fromName();

            helper.setFrom(fromEmail, fromName);
            helper.setTo(recipientEmail);
            helper.setSubject("Reset Your Smart Task Manager Password");
            helper.setText(textContent, htmlContent);

            mailSender.send(message);
            log.info("Password reset email successfully dispatched to {}", recipientEmail);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            log.error("Failed to send password reset email to {}: {}", recipientEmail, e.getMessage());
            // Intentionally not rethrowing: email delivery failure should not break password reset request flow
        }
    }

    @Override
    public void sendReminderEmail(String recipientEmail, String recipientName, String taskTitle, String taskDescription, String reminderTime) {
        String htmlContent = EmailTemplates.buildReminderEmailHtml(recipientName, taskTitle, taskDescription, reminderTime);
        String textContent = EmailTemplates.buildReminderEmailText(recipientName, taskTitle, taskDescription, reminderTime);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String fromEmail = properties.mail().from();
            String fromName = properties.mail().fromName();

            helper.setFrom(fromEmail, fromName);
            helper.setTo(recipientEmail);
            helper.setSubject("Task Reminder: " + taskTitle);
            helper.setText(textContent, htmlContent);

            mailSender.send(message);
            log.info("Reminder email successfully dispatched to {} for task: {}", recipientEmail, taskTitle);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            log.error("Failed to send reminder email to {} for task {}: {}", recipientEmail, taskTitle, e.getMessage());
            throw new RuntimeException("Failed to send reminder email: " + e.getMessage(), e);
        }
    }
}
