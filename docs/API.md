# ByteBazaar REST API (all JSON unless noted; `Authorization: Bearer <jwt>` where marked 🔒)

Errors: `{status, error, message, fieldErrors?}` with 400/401/403/404/409/500.

## Auth
POST `/api/auth/register` · POST `/api/auth/login` · POST `/api/auth/forgot-password` · POST `/api/auth/reset-password` · POST `/api/auth/logout` · 🔒 GET `/api/auth/me`
🔒 PUT `/api/user/profile` · PUT `/api/user/password` · POST `/api/user/profile-image` (multipart) · POST `/api/uploads` (multipart `file` → `{url}`)

## Catalog (public)
GET `/api/products?q&category&brand&minPrice&maxPrice&minRating&inStock&minDiscount&sort&page&size` (sort: relevance|priceAsc|priceDesc|newest|rating|popular|discount)
GET `/api/products/{id}` · `/api/products/search?q=` · `/api/products/category/{name}` · `/api/products/related/{id}` · `/api/products/{id}/reviews` · `/api/products/by-ids?ids=1,2`
GET `/api/categories` · `/api/brands` · `/api/banners` · `/api/promotions` · `/api/home` · `/api/reviews/recent`

## Customer 🔒
Cart: GET `/api/cart` · POST `/api/cart/items` `{productId,quantity}` · PUT/DELETE `/api/cart/items/{id}` · POST `/api/cart/items/{id}/move-to-wishlist`
Wishlist: GET `/api/wishlist` · POST/DELETE `/api/wishlist/{productId}`
Orders: POST `/api/orders` (cart checkout, or Buy Now with `productId`+`quantity`) · GET `/api/orders` · GET `/api/orders/{id}` · GET `/api/orders/{id}/tracking` · PUT `/api/orders/{id}/cancel` · GET `/api/orders/{id}/invoice` (PDF) · GET `/api/orders/{id}/warranty?itemId=` (PDF)
Payments: POST `/api/payments/create` `{orderId}` · POST `/api/payments/simulate` (demo) · POST `/api/payments/verify` `{paymentId}`
Returns: POST `/api/returns` (multipart: orderItemId, reason, comment, photos[]) · GET `/api/returns` · `/api/returns/{id}` · `/api/returns/{id}/tracking` · GET `/api/refunds`
Reviews: POST `/api/products/{id}/reviews` (multipart: rating, title, body, photos[])
Notifications: GET `/api/notifications` · PUT `/api/notifications/{id}/read` · PUT `/api/notifications/read-all` · DELETE `/api/notifications/{id}`
Bank: GET/POST `/api/bank-accounts` · PUT/DELETE `/api/bank-accounts/{id}` · PUT `/api/bank-accounts/{id}/primary` · PUT `/api/bank-accounts/{id}/active?value=`
Support: POST `/api/support/ai` · GET/POST `/api/support/messages` · POST `/api/contact` (public)

## Seller 🔒 ROLE_SELLER — `/api/seller/…`
`dashboard` · `analytics?days=` · `products` (GET/POST) · `products/{id}` (GET/PUT/DELETE) · `products/{id}/active?value=` · `orders` · `orders/{id}/status` · `returns` · `commission` · `bank-account`

## Admin 🔒 ROLE_ADMIN — `/api/admin/…`
`dashboard` · `analytics?days=` · `users` `sellers` (+ `{id}`, `{id}/block`, `{id}/unblock`, DELETE) · `products` (+ `/verify`, `/reject`, `/active`) · `categories` · `orders` (+ `/{id}/status`) · `returns` (+ `/{id}/status`) · `refunds` (+ `/{id}/complete`) · `commissions` (+ `/{id}/paid`) · `settings/commission` · `bank-account` · `banners` · `promotions` (festival sales & brand deals) · `support` (conversations, `/{userId}` thread & reply) · `contact-messages`
