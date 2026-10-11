package com.bytebazaar.dto;

import com.bytebazaar.entity.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** All request/response DTOs. Entities are never returned directly from controllers. */
public final class Dtos {
    private Dtos() {}

    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\w\\s]).{8,}$";
    public static final String PASSWORD_MSG = "Password must be 8+ characters with upper, lower case, a digit and a special character";

    // ---------- auth / user ----------
    public record RegisterReq(
            @NotBlank(message = "Full name is required") @Size(max = 100) String fullName,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email,
            @NotBlank(message = "Phone is required") @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number") String phone,
            @NotBlank(message = "Password is required") @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password,
            @NotBlank(message = "Confirm your password") String confirmPassword,
            String address, String city, String state,
            @Pattern(regexp = "^$|^\\d{6}$", message = "PIN code must be 6 digits") String pin,
            String role, String storeName) {}
    public record LoginReq(@NotBlank(message = "Email or phone is required") String identifier,
                           @NotBlank(message = "Password is required") String password) {}
    public record ForgotReq(@NotBlank @Email String email) {}
    public record ResetReq(@NotBlank String token,
                           @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String newPassword,
                           @NotBlank String confirmPassword) {}
    public record ChangePasswordReq(@NotBlank String currentPassword,
                                    @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String newPassword) {}
    public record ProfileReq(@NotBlank @Size(max = 100) String fullName,
                             @NotBlank @Email String email,
                             @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number") String phone,
                             String address, String city, String state,
                             @Pattern(regexp = "^$|^\\d{6}$", message = "PIN code must be 6 digits") String pin,
                             String storeName) {}
    public record UserDto(Long id, String fullName, String email, String phone, Role role, AccountStatus status,
                          String address, String city, String state, String pin, String profileImage,
                          String storeName, LocalDateTime createdAt) {}
    public record AuthRes(String token, UserDto user) {}

    // ---------- catalog ----------
    public record CategoryDto(Long id, String name, Long parentId, List<CategoryDto> children) {}
    public record ProductDto(Long id, String name, String brand, String category, Long categoryId, String parentCategory,
                             String description, String specifications, String sku, String tags,
                             BigDecimal price, int discountPercent, BigDecimal finalPrice, int stock, boolean inStock,
                             List<String> images, int warrantyMonths, boolean returnable,
                             Long sellerId, String sellerName, VerificationStatus verification, boolean active,
                             double rating, int reviewCount, int soldCount, LocalDateTime createdAt, boolean onSale) {}
    public record ProductReq(
            @NotBlank(message = "Product name is required") @Size(max = 200) String name,
            String brand,
            @NotNull(message = "Category is required") Long categoryId,
            String description, String specifications, String sku, String tags,
            @NotNull(message = "Price is required") @DecimalMin(value = "1.00", message = "Price must be at least 1") BigDecimal price,
            @Min(value = 0, message = "Discount cannot be negative") @Max(value = 90, message = "Discount cannot exceed 90%") int discountPercent,
            @Min(value = 0, message = "Stock cannot be negative") int stock,
            List<String> images,
            @Min(0) int warrantyMonths, boolean returnable, Boolean active) {}
    public record PageDto<T>(List<T> items, int page, int size, long total, int totalPages) {}

    // ---------- cart / wishlist ----------
    public record CartItemReq(@NotNull Long productId, @Min(1) @Max(10) int quantity) {}
    public record QtyReq(@Min(1) @Max(10) int quantity) {}
    public record CartItemDto(Long id, ProductDto product, int quantity, BigDecimal lineTotal) {}
    public record CartDto(List<CartItemDto> items, int count, BigDecimal subtotal, BigDecimal discount,
                          BigDecimal shipping, BigDecimal tax, BigDecimal total) {}

    // ---------- orders ----------
    public record OrderReq(
            @NotBlank(message = "Name is required") String shipName,
            @NotBlank(message = "Phone is required") @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit mobile number") String shipPhone,
            @NotBlank(message = "Address is required") String shipAddress,
            @NotBlank(message = "City is required") String shipCity,
            String shipDistrict,
            @NotBlank(message = "State is required") String shipState,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "PIN code must be 6 digits") String shipPin,
            @NotNull(message = "Choose a payment method") PaymentMethod paymentMethod,
            Long productId, Integer quantity) {}
    public record OrderItemDto(Long id, Long productId, String name, String image, Long sellerId, String seller,
                               int quantity, BigDecimal mrp, BigDecimal unitPrice, BigDecimal lineTotal,
                               boolean returnable, int warrantyMonths, boolean canReturn,
                               ReturnStatus returnStatus, Long returnId, boolean reviewed) {}
    public record OrderDto(Long id, String invoiceNumber, OrderStatus status, PaymentMethod paymentMethod, PaymentStatus paymentStatus,
                           List<OrderItemDto> items, BigDecimal subtotal, BigDecimal discount, BigDecimal shipping,
                           BigDecimal tax, BigDecimal total,
                           String customerName, String customerEmail, String customerPhone,
                           String shipName, String shipPhone, String shipAddress, String shipCity, String shipDistrict,
                           String shipState, String shipPin,
                           LocalDateTime createdAt, LocalDate estimatedDelivery, LocalDateTime deliveredAt,
                           LocalDateTime returnDeadline, boolean returnWindowOpen, boolean canCancel) {}
    public record TrackingDto(OrderStatus status, LocalDateTime eventTime, String location, String description) {}
    public record StatusReq(@NotNull OrderStatus status, String location, String description) {}

    // ---------- payments ----------
    public record PaymentDto(Long id, Long orderId, PaymentMethod method, BigDecimal amount, PaymentStatus status,
                             String upiUri, String merchantUpi, boolean simulator) {}
    public record PaymentIdReq(@NotNull Long paymentId) {}
    public record PaymentCreateReq(@NotNull Long orderId) {}

    // ---------- returns / refunds / reviews ----------
    public record ReturnTrackingDto(ReturnStatus status, LocalDateTime eventTime, String description) {}
    public record ReturnDto(Long id, Long orderId, Long orderItemId, String productName, String productImage,
                            String customer, String reason, String comment, ReturnStatus status, List<String> images,
                            LocalDateTime requestedAt, String analysisNote, BigDecimal refundAmount,
                            List<ReturnTrackingDto> tracking) {}
    public record ReturnActionReq(@NotNull ReturnStatus status, String note) {}
    public record RefundDto(Long id, Long returnId, Long orderId, String customer, BigDecimal amount,
                            RefundStatus status, String destination, LocalDateTime createdAt, LocalDateTime completedAt) {}
    public record ReviewDto(Long id, Long productId, String productName, String userName, int rating, String title,
                            String body, boolean verifiedPurchase, List<String> images, LocalDateTime createdAt) {}

    // ---------- account ----------
    public record BankReq(@NotBlank(message = "Account holder name is required") String holderName,
                          @NotBlank(message = "Bank name is required") String bankName,
                          @NotBlank(message = "Account number is required") @Pattern(regexp = "^\\d{9,18}$", message = "Account number must be 9-18 digits") String accountNumber,
                          @NotBlank String confirmAccountNumber,
                          @NotBlank(message = "IFSC is required") @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Enter a valid IFSC code (e.g. HDFC0001234)") String ifsc,
                          @Pattern(regexp = "^$|^[\\w.\\-]{2,256}@[a-zA-Z]{2,64}$", message = "Enter a valid UPI ID") String upiId,
                          boolean primary) {}
    public record BankDto(Long id, String holderName, String bankName, String maskedAccount, String ifsc, String upiId,
                          boolean primary, boolean demo, boolean active) {}
    public record NotificationDto(Long id, NotificationType type, String message, boolean seen, LocalDateTime createdAt) {}
    public record ContactReq(@NotBlank String name, @NotBlank @Email String email, String phone,
                             @NotBlank String subject, @NotBlank @Size(max = 4000) String message) {}
    public record ChatReq(@NotBlank @Size(max = 1500) String message, Long orderId) {}
    public record ChatMsgDto(Long id, SupportSender sender, String message, boolean seen, LocalDateTime createdAt) {}
    public record AiReply(String reply, List<String> quickReplies) {}
    public record ConversationDto(Long userId, String userName, String userEmail, String lastMessage,
                                  LocalDateTime lastAt, long unread) {}

    // ---------- admin / seller ----------
    public record BannerReq(@NotBlank String saleTitle, String productName, String discountText, String imageUrl,
                            String ctaText, String link, String bgColor, int sortOrder, boolean active) {}
    public record PromotionReq(@NotNull PromotionType type, @NotBlank String name, String brandName, String description,
                               String bannerUrl, @Min(0) @Max(90) int discountPercent,
                               @NotNull LocalDateTime startDate, @NotNull LocalDateTime endDate,
                               boolean enabled, List<Long> productIds) {}
    public record PromotionDto(Long id, PromotionType type, String name, String brandName, String description,
                               String bannerUrl, int discountPercent, LocalDateTime startDate, LocalDateTime endDate,
                               boolean enabled, boolean live, List<Long> productIds, List<ProductDto> products) {}
    public record CategoryReq(@NotBlank String name, Long parentId) {}
    public record CommissionDto(Long id, Long sellerId, String sellerName, Long orderId, Long productId, String productName,
                                BigDecimal saleAmount, BigDecimal commissionPct, BigDecimal commissionAmount,
                                BigDecimal sellerAmount, CommissionStatus paymentStatus, LocalDateTime createdAt) {}
    public record AdminUserDto(Long id, String fullName, String email, String phone, Role role, AccountStatus status,
                               long orders, long products, BigDecimal sales, BigDecimal commission, LocalDateTime createdAt) {}
    public record CommissionRateReq(@NotNull @DecimalMin("0") @DecimalMax("50") BigDecimal percent) {}
    public record AnalysisReq(@NotNull Boolean approved, @NotBlank(message = "Analysis note is required") String note) {}
}
