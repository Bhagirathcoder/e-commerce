# Test checklist (manual)
1. **Customer** (`user@bytebazaar.com`): search "laptop" → exact laptops first, then bags/stand/mouse/keyboard · filter + sort · open a product (zoom, related) · add to wishlist/cart · checkout with **COD** → order appears, stock reduced, seller+admin notified.
2. **UPI**: checkout with UPI → payment page → *Pay Now* (demo) → server verifies → order CONFIRMED. Leave one unpaid → auto-cancel after 30 min, stock returns.
3. **Seller** (`seller@bytebazaar.com`): Orders → Update status CONFIRMED → SHIPPING → OUT_FOR_DELIVERY → DELIVERED (customer gets notification + email log). Commission appears. Add a product → shows *Pending verification*.
4. **Admin**: verify the product (now searchable) · Commissions → mark paid · Block the customer → their next request returns 403 and login shows the blocked message; unblock.
5. **Returns**: customer → delivered order → *Return Product* (photos) → Admin Returns: Approve → Schedule pickup → Picked up → Received → Product analysis → Approve & initiate refund → Refunds: Complete. Tracking shows each step with date/time. After 7 days (change `delivered_at` in MySQL to test) the button becomes *Return Period Expired* and the API returns 400.
6. **Reviews**: only after DELIVERED; second review on the same product → 409.
7. **Invoice/Warranty PDFs** from Order details (warranty only for products with a warranty, after delivery).
8. **Analytics**: Seller/Admin → *Sales, Stock & Revenue* — change range, check low-stock alert, export CSV.
9. **Security**: customer token on `/api/admin/users` → 403; seller B editing seller A's product → 403; customer reading another user's order → 403.
