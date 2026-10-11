package com.bytebazaar.controller;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.User;
import com.bytebazaar.service.AuthService;
import com.bytebazaar.service.FileStorageService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final FileStorageService files;

    @PostMapping("/api/auth/register")
    public ResponseEntity<AuthRes> register(@Valid @RequestBody RegisterReq r) { return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(r)); }

    @PostMapping("/api/auth/login")
    public AuthRes login(@Valid @RequestBody LoginReq r) { return auth.login(r); }

    @PostMapping("/api/auth/forgot-password")
    public Map<String, String> forgot(@Valid @RequestBody ForgotReq r) {
        auth.forgotPassword(r.email());
        return Map.of("message", "If that email is registered, a password reset link has been sent.");
    }

    @PostMapping("/api/auth/reset-password")
    public Map<String, String> reset(@Valid @RequestBody ResetReq r) { auth.resetPassword(r); return Map.of("message", "Password updated. You can log in now."); }

    /** JWTs are stateless: logout = the client discards the token. Endpoint exists for API completeness. */
    @PostMapping("/api/auth/logout")
    public Map<String, String> logout() { return Map.of("message", "Logged out"); }

    @GetMapping("/api/auth/me")
    public UserDto me(@AuthenticationPrincipal User u) { return AuthService.toDto(u); }

    @PutMapping("/api/user/profile")
    public UserDto update(@AuthenticationPrincipal User u, @Valid @RequestBody ProfileReq r) { return auth.updateProfile(u, r); }

    @PutMapping("/api/user/password")
    public Map<String, String> password(@AuthenticationPrincipal User u, @Valid @RequestBody ChangePasswordReq r) {
        auth.changePassword(u, r); return Map.of("message", "Password changed");
    }

    @PostMapping("/api/user/profile-image")
    public UserDto image(@AuthenticationPrincipal User u, @RequestParam("file") MultipartFile f) { return auth.setProfileImage(u, files.store(f, "profiles")); }

    /** Generic authenticated image upload (product / banner images). Returns the stored URL. */
    @PostMapping("/api/uploads")
    public Map<String, String> upload(@AuthenticationPrincipal User u, @RequestParam("file") MultipartFile f) {
        return Map.of("url", files.store(f, "products"));
    }
}
