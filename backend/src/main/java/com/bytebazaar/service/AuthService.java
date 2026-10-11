package com.bytebazaar.service;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import com.bytebazaar.exception.*;
import com.bytebazaar.repository.UserRepository;
import com.bytebazaar.security.JwtService;
import com.bytebazaar.util.AppConstants;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final MailService mail;
    private final NotificationService notif;
    @Value("${bytebazaar.base-url}") private String baseUrl;

    public static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(), u.getStatus(),
                u.getAddress(), u.getCity(), u.getState(), u.getPin(), u.getProfileImage(), u.getStoreName(), u.getCreatedAt());
    }

    public AuthRes register(RegisterReq r) {
        if (!r.password().equals(r.confirmPassword())) throw new InvalidRequestException("Passwords do not match");
        if (users.existsByEmailIgnoreCase(r.email())) throw new ConflictException("An account with this email already exists");
        if (users.existsByPhone(r.phone())) throw new ConflictException("An account with this phone number already exists");
        User u = new User();
        u.setFullName(r.fullName().trim()); u.setEmail(r.email().trim().toLowerCase()); u.setPhone(r.phone());
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRole("SELLER".equalsIgnoreCase(r.role()) ? Role.SELLER : Role.USER);   // ADMIN can never self-register
        u.setAddress(r.address()); u.setCity(r.city()); u.setState(r.state()); u.setPin(r.pin());
        if (u.getRole() == Role.SELLER) u.setStoreName(r.storeName() == null || r.storeName().isBlank() ? r.fullName() + " Store" : r.storeName());
        users.save(u);
        notif.notify(u, NotificationType.ACCOUNT, "Welcome to ByteBazaar! Your account has been created.");
        mail.send(u.getEmail(), "Welcome to ByteBazaar", "Hi " + u.getFullName() + ",\n\nWelcome to ByteBazaar - Shop More. Live Better. Your account is ready.");
        return new AuthRes(jwt.generate(u), toDto(u));
    }

    public AuthRes login(LoginReq r) {
        String id = r.identifier().trim();
        User u = users.findByEmailIgnoreCaseOrPhone(id, id).orElseThrow(() -> new UnauthorizedException("Invalid email/phone or password"));
        if (!encoder.matches(r.password(), u.getPasswordHash())) throw new UnauthorizedException("Invalid email/phone or password");
        if (u.getStatus() == AccountStatus.BLOCKED) throw new BlockedAccountException(AppConstants.MSG_BLOCKED);
        return new AuthRes(jwt.generate(u), toDto(u));
    }

    public void forgotPassword(String email) {
        users.findByEmailIgnoreCase(email.trim()).ifPresent(u -> {
            u.setResetToken(UUID.randomUUID().toString());
            u.setResetExpiry(LocalDateTime.now().plusMinutes(30));
            String link = baseUrl + "/reset-password.html?token=" + u.getResetToken();
            mail.send(u.getEmail(), "Reset your password",
                    "Hi " + u.getFullName() + ",\n\nUse this link to reset your password (valid for 30 minutes):\n" + link
                            + "\n\nIf you did not request this, you can ignore this email.");
        });
        // Always succeed silently so the endpoint cannot be used to discover which emails are registered.
    }

    public void resetPassword(ResetReq r) {
        if (!r.newPassword().equals(r.confirmPassword())) throw new InvalidRequestException("Passwords do not match");
        User u = users.findByResetToken(r.token()).orElseThrow(() -> new InvalidRequestException("This reset link is invalid or has already been used"));
        if (u.getResetExpiry() == null || u.getResetExpiry().isBefore(LocalDateTime.now()))
            throw new InvalidRequestException("This reset link has expired. Please request a new one");
        u.setPasswordHash(encoder.encode(r.newPassword()));
        u.setResetToken(null); u.setResetExpiry(null);
        notif.notify(u, NotificationType.ACCOUNT, "Your password was changed successfully.");
    }

    public UserDto updateProfile(User current, ProfileReq r) {
        User u = users.findById(current.getId()).orElseThrow(() -> new UserNotFoundException("User not found"));
        String email = r.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(u.getEmail()) && users.existsByEmailIgnoreCase(email)) throw new ConflictException("Email is already in use");
        if (!r.phone().equals(u.getPhone()) && users.existsByPhone(r.phone())) throw new ConflictException("Phone number is already in use");
        u.setFullName(r.fullName().trim()); u.setEmail(email); u.setPhone(r.phone());
        u.setAddress(r.address()); u.setCity(r.city()); u.setState(r.state()); u.setPin(r.pin());
        if (u.getRole() == Role.SELLER && r.storeName() != null && !r.storeName().isBlank()) u.setStoreName(r.storeName());
        return toDto(u);
    }

    public void changePassword(User current, ChangePasswordReq r) {
        User u = users.findById(current.getId()).orElseThrow(() -> new UserNotFoundException("User not found"));
        if (!encoder.matches(r.currentPassword(), u.getPasswordHash())) throw new InvalidRequestException("Current password is incorrect");
        u.setPasswordHash(encoder.encode(r.newPassword()));
        notif.notify(u, NotificationType.ACCOUNT, "Your password was changed successfully.");
    }

    public UserDto setProfileImage(User current, String url) {
        User u = users.findById(current.getId()).orElseThrow(() -> new UserNotFoundException("User not found"));
        u.setProfileImage(url);
        return toDto(u);
    }
}
