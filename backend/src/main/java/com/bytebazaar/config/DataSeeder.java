package com.bytebazaar.config;

import com.bytebazaar.entity.*;
import com.bytebazaar.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds demo data on first start (empty database). Passwords are hashed with BCrypt at runtime,
 * so no hash is hard-coded. All bank details are clearly DEMO values - never real banking information.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {
    private final UserRepository users;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final BankAccountRepository banks;
    private final BannerRepository banners;
    private final PromotionRepository promotions;
    private final SettingRepository settings;
    private final PasswordEncoder encoder;

    private final Map<String, Category> cat = new HashMap<>();
    private User seller;

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) return;
        log.info("Empty database detected - seeding ByteBazaar demo data...");

        User admin = user("ByteBazaar Admin", "admin@bytebazaar.com", "9000000001", "Admin@123", Role.ADMIN, "ByteBazaar HQ");
        seller = user("Demo Seller", "seller@bytebazaar.com", "9000000002", "Seller@123", Role.SELLER, "TechNest Retail");
        User cust = user("Demo Customer", "user@bytebazaar.com", "9000000003", "User@123", Role.USER, null);
        cust.setAddress("12 MG Road"); cust.setCity("Cuttack"); cust.setState("Odisha"); cust.setPin("753001");

        bank(admin, "ByteBazaar Admin", "000000000001", "DEMO0000001", "admin@demo");
        bank(seller, "ByteBazaar Seller", "000000000002", "DEMO0000002", "seller@demo");

        settings.save(new Setting("commission.percent", "10"));
        seedCategories();
        seedProducts();
        seedBanners();
        seedPromotions();
        log.info("Seeding complete. Demo logins: admin@bytebazaar.com / Admin@123, seller@bytebazaar.com / Seller@123, user@bytebazaar.com / User@123");
    }

    private User user(String name, String email, String phone, String pw, Role role, String store) {
        User u = new User();
        u.setFullName(name); u.setEmail(email); u.setPhone(phone); u.setPasswordHash(encoder.encode(pw)); u.setRole(role); u.setStoreName(store);
        u.setCity("Bhubaneswar"); u.setState("Odisha"); u.setPin("751001"); u.setAddress("ByteBazaar Demo Street");
        return users.save(u);
    }

    private void bank(User u, String holder, String acc, String ifsc, String upi) {
        BankAccount b = new BankAccount();
        b.setUser(u); b.setHolderName(holder); b.setBankName("Demo Bank"); b.setAccountNumber(acc); b.setIfsc(ifsc); b.setUpiId(upi);
        b.setPrimaryAccount(true); b.setDemo(true);
        banks.save(b);
    }

    private void seedCategories() {
        Map<String, String[]> tree = new LinkedHashMap<>();
        tree.put("Clothing", new String[]{"Men's Clothing", "Women's Clothing", "Kids Clothing", "Shoes", "Fashion Accessories"});
        tree.put("Electronics", new String[]{"Mobile", "Laptop", "Headphones", "Television", "Smartwatch", "Camera", "Accessories"});
        tree.put("Toys", new String[]{"Kids Toys", "Educational Toys", "Games", "Remote Control Toys"});
        tree.put("Furniture", new String[]{"Sofa", "Chair", "Table", "Bed", "Wardrobe", "Storage"});
        tree.put("Beauty", new String[]{"Makeup", "Skincare", "Hair Care", "Personal Care"});
        tree.put("Home Decor", new String[]{"Lighting", "Wall Decor", "Kitchen Decor", "Decorative Items"});
        tree.put("Sports", new String[]{"Cricket", "Football", "Fitness", "Gym Equipment", "Sports Accessories"});
        tree.forEach((parent, kids) -> {
            Category p = categories.save(new Category(parent, null));
            cat.put(parent, p);
            for (String k : kids) cat.put(k, categories.save(new Category(k, p)));
        });
    }

    /** category, name, brand, MRP, discount%, stock, rating, reviews, sold, warrantyMonths, returnable, tags, description */
    private void p(String c, String name, String brand, int mrp, int disc, int stock, double rating, int reviews, int sold,
                   int warranty, boolean returnable, String tags, String desc) {
        Product x = new Product();
        x.setSeller(seller); x.setCategory(cat.get(c)); x.setName(name); x.setBrand(brand);
        x.setPrice(BigDecimal.valueOf(mrp)); x.setDiscountPercent(disc); x.setStock(stock);
        x.setAvgRating(rating); x.setReviewCount(reviews); x.setSoldCount(sold);
        x.setWarrantyMonths(warranty); x.setReturnable(returnable); x.setTags(tags); x.setDescription(desc);
        x.setSpecifications("Brand: " + brand + "\nWarranty: " + (warranty > 0 ? warranty + " months" : "No warranty")
                + "\nReturn: " + (returnable ? "7 days" : "Not returnable") + "\nSold by: TechNest Retail");
        x.setVerification(VerificationStatus.VERIFIED);
        x.setSku("BB-" + String.format("%04d", products.count() + 1));
        x.setCreatedAt(LocalDateTime.now().minusDays((long) (Math.random() * 30)));
        products.save(x);
    }

    private void seedProducts() {
        // Electronics
        p("Laptop", "Zenbook Pro 15 Laptop, 16GB RAM, 512GB SSD", "Asus", 74999, 18, 25, 4.5, 812, 540, 24, true, "laptop,notebook,computer,asus", "Powerful 15-inch laptop with a fast SSD, all-day battery and a vivid display.");
        p("Laptop", "AeroBook 14 Ultralight Laptop, 8GB RAM", "HP", 52999, 22, 40, 4.3, 530, 410, 12, true, "laptop,notebook,computer,hp", "Lightweight everyday laptop for study and work.");
        p("Accessories", "Padded Laptop Backpack 15.6 inch", "Wildcraft", 2499, 45, 120, 4.4, 2210, 1850, 6, true, "laptop,bag,backpack,laptop bag", "Water-resistant laptop bag with padded compartments.");
        p("Accessories", "Aluminium Laptop Stand, Adjustable", "Portronics", 1499, 50, 90, 4.2, 960, 880, 6, true, "laptop,stand,laptop stand,desk", "Ergonomic foldable laptop stand with cooling gap.");
        p("Accessories", "Wireless Optical Mouse 2.4GHz", "Logitech", 899, 33, 300, 4.5, 6400, 5200, 12, true, "laptop,mouse,wireless,computer", "Silent-click wireless mouse with 18-month battery life.");
        p("Accessories", "Mechanical Keyboard, Blue Switch, RGB", "Redragon", 2999, 40, 75, 4.4, 3100, 2300, 12, true, "laptop,keyboard,mechanical,computer,gaming", "Tactile mechanical keyboard with RGB backlight.");
        p("Mobile", "Nova 5G Smartphone 8GB/128GB", "Samsung", 24999, 28, 60, 4.4, 4300, 3900, 12, true, "mobile,phone,smartphone,5g,android", "5G smartphone with AMOLED display and 50MP camera.");
        p("Mobile", "Pixel-style Budget Phone 6GB/64GB", "Redmi", 11999, 20, 150, 4.1, 8800, 7600, 12, true, "mobile,phone,smartphone,android", "Value smartphone with a big 5000mAh battery.");
        p("Headphones", "Bass+ Wireless Over-Ear Headphones", "boAt", 3999, 62, 200, 4.2, 15400, 14000, 12, true, "headphones,wireless,bluetooth,laptop,audio", "Bluetooth headphones with 40-hour playback.");
        p("Headphones", "ANC True Wireless Earbuds", "Sony", 9990, 35, 80, 4.6, 2900, 2100, 12, true, "earbuds,headphones,anc,bluetooth,audio", "Noise-cancelling earbuds with a charging case.");
        p("Television", "55-inch 4K Ultra HD Smart LED TV", "LG", 62990, 38, 15, 4.5, 1900, 760, 24, true, "tv,television,smart tv,4k", "4K smart TV with built-in streaming apps.");
        p("Smartwatch", "FitPulse AMOLED Smartwatch", "Noise", 4999, 68, 180, 4.1, 9700, 8800, 6, true, "smartwatch,watch,fitness,wearable", "Smartwatch with heart-rate, SpO2 and 100+ sports modes.");
        p("Camera", "24MP Mirrorless Camera with 18-55mm Lens", "Canon", 54990, 15, 12, 4.6, 640, 280, 24, true, "camera,mirrorless,photography,lens", "Mirrorless camera for photo and 4K video.");
        // Clothing
        p("Men's Clothing", "Regular Fit Cotton Casual Shirt", "Allen Solly", 1999, 55, 200, 4.2, 3400, 3000, 0, true, "shirt,men,cotton,casual,clothing", "Soft cotton shirt for office and weekends.");
        p("Women's Clothing", "Embroidered Cotton Kurti", "Biba", 1799, 50, 160, 4.3, 2800, 2500, 0, true, "kurti,women,cotton,ethnic,clothing", "Comfortable cotton kurti with fine embroidery.");
        p("Kids Clothing", "Kids Cotton T-Shirt Pack of 3", "Max", 999, 40, 220, 4.4, 1500, 1300, 0, true, "kids,tshirt,cotton,clothing", "Pack of three soft cotton tees.");
        p("Shoes", "Lightweight Running Shoes", "Puma", 4499, 52, 140, 4.3, 5200, 4600, 3, true, "shoes,running,sports,men,sneakers", "Cushioned running shoes with breathable mesh.");
        p("Fashion Accessories", "Genuine Leather Wallet for Men", "Wildhorn", 1299, 60, 250, 4.2, 4100, 3700, 0, true, "wallet,leather,men,accessories", "Slim leather wallet with card slots.");
        // Toys
        p("Kids Toys", "Building Blocks Set 500 Pieces", "Funskool", 1299, 35, 130, 4.5, 2300, 1900, 0, true, "toys,blocks,kids,building", "Colourful blocks to build creative models.");
        p("Educational Toys", "Alphabet & Numbers Learning Board", "Skillmatics", 799, 30, 170, 4.4, 1800, 1500, 0, true, "toys,educational,learning,kids", "Fun learning board for ages 3 to 6.");
        p("Games", "Classic Strategy Board Game", "Hasbro", 1599, 25, 90, 4.3, 1200, 900, 0, true, "games,board game,family,toys", "Family board game for 2-6 players.");
        p("Remote Control Toys", "Rechargeable RC Racing Car 1:16", "Hot Wheels", 2299, 45, 60, 4.1, 760, 600, 3, true, "rc,remote control,car,toys", "Fast remote-control car with LED lights.");
        // Furniture
        p("Sofa", "3-Seater Fabric Sofa, Grey", "Nilkamal", 28999, 40, 10, 4.2, 410, 190, 12, true, "sofa,furniture,living room,couch", "Comfortable 3-seater sofa with solid wood frame.");
        p("Chair", "Ergonomic Mesh Office Chair", "Green Soul", 9999, 55, 45, 4.3, 2600, 1700, 12, true, "chair,office,ergonomic,laptop,furniture", "Mesh-back chair with lumbar support and adjustable arms.");
        p("Table", "Engineered Wood Study Table", "Home Centre", 6999, 38, 35, 4.1, 980, 700, 12, true, "table,study,desk,laptop,furniture", "Study table with storage shelf.");
        // Beauty
        p("Skincare", "Vitamin C Face Serum 30ml", "Minimalist", 699, 15, 300, 4.5, 8700, 7300, 0, false, "skincare,serum,face,vitamin c,beauty", "Brightening serum for daily use.");
        p("Makeup", "Matte Lipstick Combo Set of 5", "Lakme", 1250, 36, 210, 4.2, 6100, 5400, 0, false, "makeup,lipstick,beauty,matte", "Long-lasting matte lipsticks in five shades.");
        p("Hair Care", "Onion Hair Oil 250ml", "Mamaearth", 549, 20, 400, 4.3, 12000, 10300, 0, false, "hair,oil,hair care,beauty", "Hair oil with onion and plant keratin.");
        // Home Decor
        p("Lighting", "LED String Fairy Lights 10m (Diwali Special)", "Philips", 799, 50, 500, 4.3, 7200, 6800, 6, true, "lights,led,diwali,decor,lighting", "Warm white string lights for festive decoration.");
        p("Wall Decor", "Framed Canvas Wall Art Set of 3", "Art Street", 2499, 58, 80, 4.4, 1100, 850, 0, true, "wall decor,art,frame,home", "Modern canvas prints with ready-to-hang frames.");
        p("Kitchen Decor", "Ceramic Dinner Set 24 Pieces", "Cello", 3499, 44, 70, 4.2, 1900, 1400, 0, true, "kitchen,dinner set,ceramic,home", "Elegant 24-piece ceramic dinner set.");
        // Sports
        p("Cricket", "English Willow Cricket Bat", "SG", 6999, 30, 40, 4.4, 520, 390, 0, true, "cricket,bat,sports,willow", "Grade-2 English willow bat for leather ball.");
        p("Football", "Match Football Size 5", "Nivia", 1299, 32, 150, 4.2, 1700, 1500, 0, true, "football,ball,sports", "Durable size-5 football for turf and ground.");
        p("Fitness", "Anti-Skid Yoga Mat 6mm", "Boldfit", 999, 55, 260, 4.3, 5400, 4800, 0, true, "yoga,mat,fitness,sports,gym", "Cushioned yoga mat with carry strap.");
        p("Gym Equipment", "Adjustable Dumbbell Set 20kg", "Kore", 3999, 40, 55, 4.4, 2100, 1200, 12, true, "dumbbell,gym,fitness,weights", "Adjustable dumbbells for home workouts.");
        p("Sports Accessories", "Sports Water Bottle 1L", "Cosco", 399, 25, 500, 4.1, 3300, 3000, 0, true, "bottle,sports,gym,accessories", "Leak-proof 1-litre sports bottle.");
    }

    private void seedBanners() {
        Object[][] b = {
                {"GREAT FESTIVAL SALE", "Up to 70% OFF on Electronics", "Up to 70% OFF", "#0f2742", "/products.html?category=Electronics", 1},
                {"DIWALI DHAMAKA", "Decor lights & home essentials", "From Rs. 399", "#4a1d6b", "/products.html?category=Home%20Decor", 2},
                {"FASHION FIESTA", "Top brands, flat 50% off", "50% OFF", "#7a2e1d", "/products.html?category=Clothing", 3},
                {"GAME ON", "Cricket, fitness & gym gear", "Up to 55% OFF", "#0b5d4b", "/products.html?category=Sports", 4}};
        for (Object[] r : b) {
            Banner x = new Banner();
            x.setSaleTitle((String) r[0]); x.setProductName((String) r[1]); x.setDiscountText((String) r[2]);
            x.setBgColor((String) r[3]); x.setLink((String) r[4]); x.setSortOrder((Integer) r[5]);
            banners.save(x);
        }
    }

    private void seedPromotions() {
        Promotion f = new Promotion();
        f.setType(PromotionType.FESTIVAL); f.setName("Mega Festival Sale"); f.setDescription("Extra savings on selected electronics");
        f.setDiscountPercent(25); f.setStartDate(LocalDateTime.now().minusDays(1)); f.setEndDate(LocalDateTime.now().plusDays(30));
        Set<Product> set = new HashSet<>();
        products.findAll().stream().filter(x -> "Laptop".equals(x.getCategory().getName()) || "Headphones".equals(x.getCategory().getName())).forEach(set::add);
        f.setProducts(set);
        promotions.save(f);

        Promotion bd = new Promotion();
        bd.setType(PromotionType.BRAND); bd.setName("Electronics Deals"); bd.setBrandName("boAt"); bd.setDescription("Brand deal on audio gear");
        bd.setDiscountPercent(70); bd.setStartDate(LocalDateTime.now().minusDays(1)); bd.setEndDate(LocalDateTime.now().plusDays(14));
        Set<Product> s2 = new HashSet<>();
        products.findAll().stream().filter(x -> "boAt".equals(x.getBrand())).forEach(s2::add);
        bd.setProducts(s2);
        promotions.save(bd);
    }
}
