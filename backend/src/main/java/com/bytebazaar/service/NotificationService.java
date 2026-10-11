package com.bytebazaar.service;

import com.bytebazaar.entity.*;
import com.bytebazaar.repository.NotificationRepository;
import com.bytebazaar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repo;
    private final UserRepository users;
    private final MailService mail;

    public void notify(User u, NotificationType t, String msg) {
        Notification n = new Notification();
        n.setUser(u); n.setType(t); n.setMessage(msg);
        repo.save(n);
    }

    public void notifyAndEmail(User u, NotificationType t, String subject, String msg) {
        notify(u, t, msg);
        mail.send(u.getEmail(), subject, "Hi " + u.getFullName() + ",\n\n" + msg);
    }

    public void notifyAdmins(NotificationType t, String msg) {
        for (User a : users.findByRoleOrderByCreatedAtDesc(Role.ADMIN)) notify(a, t, msg);
    }
}
