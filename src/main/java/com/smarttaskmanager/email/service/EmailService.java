package com.smarttaskmanager.email.service;

/**
 * Service for delivering system emails.
 */
public interface EmailService {

    /**
     * Sends an email verification link to a newly registered or requesting user.
     *
     * @param recipientEmail destination email address
     * @param recipientName recipient full name
     * @param rawToken unhashed single-use verification token
     */
    void sendVerificationEmail(String recipientEmail, String recipientName, String rawToken);

    /**
     * Sends a password reset link to a user requesting a password reset.
     *
     * @param recipientEmail destination email address
     * @param recipientName recipient full name
     * @param rawToken unhashed single-use password reset token
     */
    void sendPasswordResetEmail(String recipientEmail, String recipientName, String rawToken);

    /**
     * Sends a task reminder email to a user.
     *
     * @param recipientEmail destination email address
     * @param recipientName recipient full name
     * @param taskTitle title of the task being reminded about
     * @param taskDescription optional description of the task
     * @param reminderTime formatted reminder time string
     */
    void sendReminderEmail(String recipientEmail, String recipientName, String taskTitle, String taskDescription, String reminderTime);
}
