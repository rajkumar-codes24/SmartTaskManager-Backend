package com.smarttaskmanager.email.service;

import com.smarttaskmanager.common.config.ApplicationProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTests {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private ApplicationProperties properties;

    @Mock
    private ApplicationProperties.Mail mailConfig;

    @InjectMocks
    private EmailServiceImpl emailService;

    @Test
    @DisplayName("EmailService successfully formats and delivers verification email")
    void sendVerificationEmail_success() {
        when(properties.frontendBaseUrl()).thenReturn("http://localhost:3000");
        when(properties.mail()).thenReturn(mailConfig);
        when(mailConfig.from()).thenReturn("noreply@smarttaskmanager.com");
        when(mailConfig.fromName()).thenReturn("Smart Task Manager");

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        assertDoesNotThrow(() ->
                emailService.sendVerificationEmail("alice@example.com", "Alice Smith", "sampleToken123")
        );

        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("EmailService handles SMTP exceptions safely without breaking calling execution")
    void sendVerificationEmail_smtpFailure_handledGracefully() {
        when(properties.frontendBaseUrl()).thenReturn("http://localhost:3000");
        when(properties.mail()).thenReturn(mailConfig);
        when(mailConfig.from()).thenReturn("noreply@smarttaskmanager.com");
        when(mailConfig.fromName()).thenReturn("Smart Task Manager");

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        doThrow(new MailSendException("SMTP connection timed out"))
                .when(mailSender).send(any(MimeMessage.class));

        // Must complete without rethrowing or crashing
        assertDoesNotThrow(() ->
                emailService.sendVerificationEmail("alice@example.com", "Alice Smith", "sampleToken123")
        );
    }
}
