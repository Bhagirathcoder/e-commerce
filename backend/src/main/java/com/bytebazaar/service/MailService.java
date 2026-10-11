package com.bytebazaar.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Sends email through Spring Mail when SMTP is configured; otherwise logs the message (dev mode). */
@Service
@Slf4j
public class MailService {
    private final ObjectProvider<JavaMailSender> sender;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> sender, @Value("${bytebazaar.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    public void send(String to, String subject, String body) {
        JavaMailSender s = sender.getIfAvailable();
        if (s == null) {
            log.info("[MAIL not configured] To: {} | Subject: {} | Body: {}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from); m.setTo(to); m.setSubject("ByteBazaar - " + subject);
            m.setText(body + "\n\n-- ByteBazaar | Shop More. Live Better.");
            s.send(m);
        } catch (Exception e) {
            log.warn("Could not send email to {}: {}", to, e.getMessage());
        }
    }
}
