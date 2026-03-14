package com.example.project.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    // Use configured mail username as From address
    @Value("${spring.mail.username}")
    private String mailFrom;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // Added bcc support. Pass null or empty array if no BCC.
    public void sendHtmlEmail(String[] to, String[] cc, String[] bcc, String subject, String htmlContent)
            throws MessagingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        // Set From (use configured username as default from)
        if (mailFrom != null && !mailFrom.isBlank()) {
            helper.setFrom(mailFrom);
        }

        // Set TO recipients
        helper.setTo(to);

        // Set CC recipients
        if (cc != null && cc.length > 0) {
            helper.setCc(cc);
        }

        // Set BCC recipients
        if (bcc != null && bcc.length > 0) {
            helper.setBcc(bcc);
        }

        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }
}
