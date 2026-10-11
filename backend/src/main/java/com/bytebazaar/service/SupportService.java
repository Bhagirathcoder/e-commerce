package com.bytebazaar.service;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import com.bytebazaar.exception.NotFoundException;
import com.bytebazaar.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manual "Chat with Admin". conversation id == customer's user id. */
@Service
@RequiredArgsConstructor
@Transactional
public class SupportService {
    private final SupportMessageRepository messages;
    private final UserRepository users;
    private final ContactMessageRepository contacts;
    private final NotificationService notif;

    private ChatMsgDto dto(SupportMessage m) { return new ChatMsgDto(m.getId(), m.getSender(), m.getMessage(), m.isSeen(), m.getCreatedAt()); }

    public ChatMsgDto userSend(User u, String text) {
        SupportMessage m = new SupportMessage();
        m.setUser(u); m.setSender(SupportSender.USER); m.setMessage(text);
        messages.save(m);
        notif.notifyAdmins(NotificationType.SUPPORT, "New support message from " + u.getFullName() + ".");
        return dto(m);
    }

    public List<ChatMsgDto> userThread(User u) {
        List<SupportMessage> list = messages.findByUserOrderByCreatedAtAscIdAsc(u);
        list.stream().filter(m -> m.getSender() == SupportSender.ADMIN && !m.isSeen()).forEach(m -> m.setSeen(true));
        return list.stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> conversations() {
        List<ConversationDto> out = new ArrayList<>();
        for (Long uid : messages.conversationUserIds()) {
            User cu = users.findById(uid).orElse(null);
            if (cu == null) continue;
            SupportMessage last = messages.findFirstByUserOrderByIdDesc(cu).orElse(null);
            out.add(new ConversationDto(uid, cu.getFullName(), cu.getEmail(), last == null ? "" : last.getMessage(),
                    last == null ? null : last.getCreatedAt(), messages.countByUserAndSenderAndSeenFalse(cu, SupportSender.USER)));
        }
        return out;
    }

    public List<ChatMsgDto> adminThread(Long userId) {
        User cu = users.findById(userId).orElseThrow(() -> new NotFoundException("Customer not found"));
        List<SupportMessage> list = messages.findByUserOrderByCreatedAtAscIdAsc(cu);
        list.stream().filter(m -> m.getSender() == SupportSender.USER && !m.isSeen()).forEach(m -> m.setSeen(true));
        return list.stream().map(this::dto).toList();
    }

    public ChatMsgDto adminReply(User admin, Long userId, String text) {
        User cu = users.findById(userId).orElseThrow(() -> new NotFoundException("Customer not found"));
        SupportMessage m = new SupportMessage();
        m.setUser(cu); m.setAdmin(admin); m.setSender(SupportSender.ADMIN); m.setMessage(text);
        messages.save(m);
        notif.notify(cu, NotificationType.SUPPORT, "ByteBazaar support replied to your message.");
        return dto(m);
    }

    public void contact(User userOrNull, ContactReq r) {
        ContactMessage c = new ContactMessage();
        c.setName(r.name()); c.setEmail(r.email()); c.setPhone(r.phone()); c.setSubject(r.subject()); c.setMessage(r.message());
        c.setUser(userOrNull);
        contacts.save(c);
        notif.notifyAdmins(NotificationType.SUPPORT, "New contact message: " + r.subject());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> contactMessages() {
        return contacts.findAllByOrderByCreatedAtDesc().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId()); m.put("name", c.getName()); m.put("email", c.getEmail()); m.put("phone", c.getPhone());
            m.put("subject", c.getSubject()); m.put("message", c.getMessage()); m.put("status", c.getStatus()); m.put("createdAt", c.getCreatedAt());
            return m;
        }).toList();
    }

    public void closeContact(Long id) {
        contacts.findById(id).orElseThrow(() -> new NotFoundException("Message not found")).setStatus("CLOSED");
    }
}
