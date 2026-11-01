package com.smarttaskmanager.email.template;

/**
 * Reusable HTML and plain-text email templates.
 */
public final class EmailTemplates {

    private EmailTemplates() {}

    public static String buildVerificationEmailHtml(String recipientName, String verificationUrl) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Verify Your Email</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f6f9fc; margin: 0; padding: 20px; }
                        .container { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 8px; padding: 32px; box-shadow: 0 2px 4px rgba(0,0,0,0.08); }
                        .header { font-size: 24px; font-weight: 700; color: #1a1f36; margin-bottom: 16px; }
                        .content { font-size: 16px; line-height: 1.6; color: #4f566b; margin-bottom: 24px; }
                        .button { display: inline-block; background-color: #5469d4; color: #ffffff !important; text-decoration: none; padding: 12px 24px; border-radius: 6px; font-weight: 600; font-size: 16px; }
                        .footer { margin-top: 32px; font-size: 13px; color: #8792a2; border-top: 1px solid #e3e8ee; padding-top: 16px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">Welcome to Smart Task Manager</div>
                        <div class="content">
                            <p>Hi %s,</p>
                            <p>Thank you for registering. Please confirm your email address to activate your account and start organizing your tasks efficiently.</p>
                            <p style="text-align: center; margin: 32px 0;">
                                <a href="%s" class="button" target="_blank">Verify Email Address</a>
                            </p>
                            <p>Or copy and paste this URL into your browser:</p>
                            <p style="word-break: break-all; color: #5469d4;">%s</p>
                            <p>This verification link will expire in 24 hours. If you did not create an account, you can safely ignore this email.</p>
                        </div>
                        <div class="footer">
                            &copy; Smart Task Manager. All rights reserved.
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(recipientName, verificationUrl, verificationUrl);
    }

    public static String buildVerificationEmailText(String recipientName, String verificationUrl) {
        return """
                Hi %s,
                
                Welcome to Smart Task Manager!
                
                Please verify your email address to activate your account by visiting the following link:
                %s
                
                This link will expire in 24 hours. If you did not create an account, please ignore this email.
                
                Regards,
                Smart Task Manager Team
                """.formatted(recipientName, verificationUrl);
    }

    public static String buildPasswordResetEmailHtml(String recipientName, String resetUrl) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Reset Your Password</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f6f9fc; margin: 0; padding: 20px; }
                        .container { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 8px; padding: 32px; box-shadow: 0 2px 4px rgba(0,0,0,0.08); }
                        .header { font-size: 24px; font-weight: 700; color: #1a1f36; margin-bottom: 16px; }
                        .content { font-size: 16px; line-height: 1.6; color: #4f566b; margin-bottom: 24px; }
                        .button { display: inline-block; background-color: #5469d4; color: #ffffff !important; text-decoration: none; padding: 12px 24px; border-radius: 6px; font-weight: 600; font-size: 16px; }
                        .footer { margin-top: 32px; font-size: 13px; color: #8792a2; border-top: 1px solid #e3e8ee; padding-top: 16px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">Reset Your Password</div>
                        <div class="content">
                            <p>Hi %s,</p>
                            <p>We received a request to reset your password for your Smart Task Manager account. Click the button below to set a new password.</p>
                            <p style="text-align: center; margin: 32px 0;">
                                <a href="%s" class="button" target="_blank">Reset Password</a>
                            </p>
                            <p>Or copy and paste this URL into your browser:</p>
                            <p style="word-break: break-all; color: #5469d4;">%s</p>
                            <p>This reset link will expire in 1 hour. If you did not request a password reset, you can safely ignore this email and your password will remain unchanged.</p>
                        </div>
                        <div class="footer">
                            &copy; Smart Task Manager. All rights reserved.
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(recipientName, resetUrl, resetUrl);
    }

    public static String buildPasswordResetEmailText(String recipientName, String resetUrl) {
        return """
                Hi %s,
                
                We received a request to reset your password for your Smart Task Manager account.
                
                Please reset your password by visiting the following link:
                %s
                
                This link will expire in 1 hour. If you did not request a password reset, please ignore this email and your password will remain unchanged.
                
                Regards,
                Smart Task Manager Team
                """.formatted(recipientName, resetUrl);
    }

    public static String buildReminderEmailHtml(String recipientName, String taskTitle, String taskDescription, String reminderTime) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Task Reminder</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f6f9fc; margin: 0; padding: 20px; }
                        .container { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 8px; padding: 32px; box-shadow: 0 2px 4px rgba(0,0,0,0.08); }
                        .header { font-size: 24px; font-weight: 700; color: #1a1f36; margin-bottom: 16px; }
                        .content { font-size: 16px; line-height: 1.6; color: #4f566b; margin-bottom: 24px; }
                        .task-title { font-size: 18px; font-weight: 600; color: #1a1f36; background: #f0f4ff; padding: 12px 16px; border-radius: 6px; margin: 16px 0; }
                        .task-desc { font-size: 14px; color: #6b7280; margin: 8px 0; }
                        .reminder-time { font-size: 14px; color: #5469d4; font-weight: 500; margin: 12px 0; }
                        .footer { margin-top: 32px; font-size: 13px; color: #8792a2; border-top: 1px solid #e3e8ee; padding-top: 16px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">Task Reminder</div>
                        <div class="content">
                            <p>Hi %s,</p>
                            <p>This is a reminder for your upcoming task:</p>
                            <div class="task-title">%s</div>
                            %s
                            <div class="reminder-time">Scheduled reminder time: %s</div>
                            <p>Please log in to your Smart Task Manager account to view and manage this task.</p>
                        </div>
                        <div class="footer">
                            &copy; Smart Task Manager. All rights reserved.<br>
                            This is an automated reminder. Please do not reply to this email.
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(recipientName, taskTitle,
                        taskDescription != null && !taskDescription.isEmpty()
                                ? "<div class=\"task-desc\">" + escapeHtml(taskDescription) + "</div>"
                                : "",
                        reminderTime);
    }

    public static String buildReminderEmailText(String recipientName, String taskTitle, String taskDescription, String reminderTime) {
        return """
                Hi %s,

                This is a reminder for your upcoming task:

                Task: %s
                %s
                Scheduled reminder time: %s

                Please log in to your Smart Task Manager account to view and manage this task.

                Regards,
                Smart Task Manager Team
                This is an automated reminder. Please do not reply to this email.
                """.formatted(recipientName, taskTitle,
                        taskDescription != null && !taskDescription.isEmpty()
                                ? "Description: " + taskDescription
                                : "",
                        reminderTime);
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
