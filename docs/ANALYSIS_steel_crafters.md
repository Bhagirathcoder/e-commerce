# Analysis of the uploaded `steel_crafters` project
Spring Boot 3.2.4 / Java 17 / JSP / MySQL via `JdbcTemplate`. ~700 lines in `AnnoController`, `AdminController`, `ContactController`.

| Issue | Where | Fixed in ByteBazaar by |
|---|---|---|
| SQL injection (string-built SQL) | login, signup, manage user, FAQ | Spring Data JPA + parameter binding |
| Plain-text passwords | `user_master` | BCrypt |
| No authentication layer, role only in UI flow | all URLs reachable directly | Spring Security + JWT + role rules |
| Global cart (no `user_id`), `truncate table cart` on order | cart/order | per-user `cart_items`, delete only own rows |
| Orders stored as one flat string | `save_order` | `orders` + `order_items` + tracking |
| No stock handling / transactions | checkout | pessimistic locks, `@Transactional` |
| Images as BLOB columns | `product_master.prod_img` | files in `uploads/`, URLs in DB |
| Dead code and open `/deleteAll` | `Student`, `StudentRepo`, main class | removed |

Migration hint: `user_master` → `users` (hash old passwords with BCrypt on first login/reset), `product_master` → `products` (+ export BLOB images to files), `save_order` → split into `orders`/`order_items`.
