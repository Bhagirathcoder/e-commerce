# ByteBazaar — Shop More. Live Better.

Full-stack marketplace (customer + seller + admin) with an Amazon-inspired layout (not a copy): sticky navy header with
category search, secondary nav strip, right→left hero carousel with overlapping category tiles, horizontal product rows,
filter sidebar, product page with buy box, cart/checkout summary box, order timeline.

**Stack:** HTML5 · CSS3 · JavaScript (ES6+, Fetch) · Bootstrap 5 · Java 17 · Spring Boot 3.2 (MVC, Security, Data JPA/Hibernate, Validation, Mail) · JWT · BCrypt · OpenPDF · MySQL 8

```
Browser (HTML/CSS/JS) → fetch → REST API → Spring Boot → Service → JPA/Hibernate → MySQL
```

## 1. Run it

Prerequisites: **JDK 17+**, **Maven 3.8+**, **MySQL 8** running locally.

```bash
# 1. (optional) create the DB — the app also creates it automatically
mysql -u root -p < database/create_database.sql

# 2. configure credentials via environment variables
export DB_USERNAME=root
export DB_PASSWORD=your_mysql_password
export JWT_SECRET="put-a-long-random-string-here-at-least-32-characters"

# 3. start (run from the backend/ folder — it also serves ../frontend/)
cd backend
mvn spring-boot:run

# 4. open
http://localhost:8081
```

On the first start with an empty database, `DataSeeder` creates tables (Hibernate `ddl-auto=update`) and demo data.

### Demo logins (BCrypt-hashed at seed time)
| Role | Email | Password |
|---|---|---|
| Admin | admin@bytebazaar.com | Admin@123 |
| Seller | seller@bytebazaar.com | Seller@123 |
| Customer | user@bytebazaar.com | User@123 |

Demo bank accounts (clearly labelled **DEMO ACCOUNT — NOT A REAL BANK ACCOUNT**): admin `000000000001 / DEMO0000001 / admin@demo`, seller `000000000002 / DEMO0000002 / seller@demo`.

### Environment variables
| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection |
| `JWT_SECRET` | JWT signing key (**change it**) |
| `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD`, `MAIL_FROM` | SMTP. Without a host, emails (incl. password-reset links) are printed in the server log |
| `PAYMENT_SIMULATE` | `true` = demo UPI simulator; set `false` when a real gateway adapter is added |
| `PAYMENT_GATEWAY_KEY/SECRET`, `MERCHANT_UPI` | payment gateway settings (never hard-coded) |
| `AI_API_KEY` | optional LLM key for a custom `AiSupportService` |
| `UPLOAD_DIR`, `APP_BASE_URL` | upload folder, link base for emails |

## 2. What is implemented

**Customer:** register/login/logout, forgot & reset password (email token, 30 min), profile/address/photo, bank accounts (masked, primary), search (name, brand, category, description, SKU, tags — exact matches ranked first, related items after), filters + 7 sort modes, product page (gallery + zoom, specs, buy box, related/similar), wishlist, cart (+ move to wishlist), checkout, **COD**, **UPI** (QR + backend verification), orders, tracking timeline (date/time/location), invoice PDF, warranty PDF, cancel, **7-day return** (backend-calculated from delivery time) with photos, return tracking, product analysis, refund, verified-purchase reviews with photos, notifications (bell + unread count + email), 24×7 AI assistant (answers from the DB), manual admin chat, contact/ticket.

**Seller:** dashboard, profile, bank account, add/edit/delete(soft)/activate products, verification status, orders (only own products) + status updates (auto-notifies customer), commission & net earnings, invoices, notifications, **Sales, Stock & Revenue analytics**.

**Admin:** dashboard with cards + 7 charts, **Sales, Stock & Revenue analytics**, users/sellers (view/edit/block/unblock/delete), product verification, orders, returns workflow, refunds, commissions (rate, mark paid), festival sales & brand deals (auto-activate by date, extra discount applied to prices), banners, support inbox + contact messages, demo bank account, categories, settings.

### Sales / stock / revenue reports (seller & admin)
`GET /api/seller/analytics?days=30` · `GET /api/admin/analytics?days=30` — revenue, orders, units sold, average order value, delivered vs pending revenue, commission/net earnings (admin: refunded), daily trend, revenue by category / payment / seller, top products, and **stock** (units in stock, stock value, low-stock ≤5, out-of-stock, per-product levels). UI: *Sales, Stock & Revenue* in both sidebars (range 7/30/90/365 days, charts, CSV export) plus a compact overview on each dashboard.

## 3. Security highlights
BCrypt passwords · JWT, user re-loaded from DB on **every** request (blocked users/sellers are rejected at API level) · `/api/admin/**` = ADMIN, `/api/seller/**` = SELLER · ownership checks on orders, returns, bank accounts, products · DTOs + Bean Validation · payment success only via server-side gateway adapter · stock decrement under pessimistic row locks in one transaction · image upload validated by extension, MIME type and magic bytes (5 MB) · unpaid UPI orders auto-cancel after 30 min and release stock · password-reset endpoint doesn't reveal which emails exist.

## 4. Honest notes & deliberate simplifications
- **Not compiled/run by the author's environment** (no Maven/network was available while generating it): all Java files were syntax-checked with `javac`, all JS with `node --check`, but you may still hit small dependency/compile errors on first build — fix the reported line and continue.
- Tables are generated by Hibernate; demo data comes from `DataSeeder.java` (BCrypt at runtime) instead of a hand-written `data.sql`.
- Simplified data model vs. the original prompt: one `users` table with a `role` column (no `roles/user_roles/sellers`); `cart_items` only (no separate `cart`); brand is a text field; invoices/warranty are generated on demand (invoice number stored on the order; no `invoices`/`warranty` tables); seller payouts tracked as commission `PAID` status (no `seller_payouts` table); support tickets reuse `contact_messages`; returns are per order item and tracked in `returns/return_tracking` (order status stays in the fulfilment chain).
- UPI and refunds are **simulated**; plug a real gateway into `PaymentGateway` / `ReturnService.completeRefund`. Bank account numbers are stored as plain text for the demo — encrypt at rest in production.
- The JWT is kept in `localStorage` (simple for a demo); production should use HttpOnly cookies.
- Search/filter/sort run in memory after a DB filter — fine for thousands of products, add proper indexing/search (e.g. OpenSearch) beyond that.
