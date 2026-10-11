package com.bytebazaar.service;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import com.bytebazaar.exception.*;
import com.bytebazaar.repository.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final PromotionService promos;
    private final ProductMapper mapper;
    private final NotificationService notif;

    // ------------------------------------------------------------------ public catalog
    @Transactional(readOnly = true)
    public PageDto<ProductDto> search(String q, String category, String brand, BigDecimal minPrice, BigDecimal maxPrice,
                                      Double minRating, Boolean inStock, Integer minDiscount, String sort, int page, int size) {
        Map<Long, Integer> live = promos.liveDiscountMap();
        final String query = q == null ? "" : q.trim().toLowerCase();

        Specification<Product> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("verification"), VerificationStatus.VERIFIED));
            ps.add(cb.isTrue(root.get("active")));
            ps.add(cb.isFalse(root.get("deleted")));
            Join<Product, Category> cat = root.join("category", JoinType.LEFT);
            Join<Category, Category> parent = cat.join("parent", JoinType.LEFT);
            if (!query.isEmpty()) {
                List<Predicate> terms = new ArrayList<>();
                for (String t : query.split("\\s+")) {
                    String like = "%" + t + "%";
                    terms.add(cb.or(
                            cb.like(cb.lower(root.get("name")), like),
                            cb.like(cb.lower(root.get("brand")), like),
                            cb.like(cb.lower(root.get("sku")), like),
                            cb.like(cb.lower(root.get("tags")), like),
                            cb.like(cb.lower(root.get("description")), like),
                            cb.like(cb.lower(cat.get("name")), like),
                            cb.like(cb.lower(parent.get("name")), like)));
                }
                ps.add(cb.and(terms.toArray(new Predicate[0])));
            }
            if (category != null && !category.isBlank()) {
                String c = category.trim().toLowerCase();
                ps.add(cb.or(cb.equal(cb.lower(cat.get("name")), c), cb.equal(cb.lower(parent.get("name")), c)));
            }
            if (brand != null && !brand.isBlank()) ps.add(cb.equal(cb.lower(root.get("brand")), brand.trim().toLowerCase()));
            if (minRating != null) ps.add(cb.greaterThanOrEqualTo(root.<Double>get("avgRating"), minRating));
            if (inStock != null) ps.add(inStock ? cb.greaterThan(root.<Integer>get("stock"), 0) : cb.equal(root.get("stock"), 0));
            return cb.and(ps.toArray(new Predicate[0]));
        };

        List<Product> list = products.findAll(spec).stream().filter(p -> {
            int d = ProductMapper.effectiveDiscount(p, live);
            BigDecimal fp = p.finalPrice(d);
            if (minPrice != null && fp.compareTo(minPrice) < 0) return false;
            if (maxPrice != null && fp.compareTo(maxPrice) > 0) return false;
            return minDiscount == null || d >= minDiscount;
        }).collect(Collectors.toCollection(ArrayList::new));

        Comparator<Product> cmp;
        switch (sort == null ? "relevance" : sort) {
            case "priceAsc" -> cmp = Comparator.comparing((Product p) -> p.finalPrice(ProductMapper.effectiveDiscount(p, live)));
            case "priceDesc" -> cmp = Comparator.comparing((Product p) -> p.finalPrice(ProductMapper.effectiveDiscount(p, live))).reversed();
            case "newest" -> cmp = Comparator.comparing(Product::getCreatedAt).reversed();
            case "rating" -> cmp = Comparator.comparingDouble(Product::getAvgRating).reversed();
            case "popular" -> cmp = Comparator.comparingInt(Product::getSoldCount).reversed();
            case "discount" -> cmp = Comparator.comparingInt((Product p) -> ProductMapper.effectiveDiscount(p, live)).reversed();
            default -> cmp = Comparator.comparingInt((Product p) -> -score(p, query))
                    .thenComparing(Comparator.comparingInt(Product::getSoldCount).reversed());
        }
        list.sort(cmp);

        int s = Math.max(1, Math.min(size, 60));
        int pg = Math.max(1, page);
        int from = Math.min((pg - 1) * s, list.size());
        List<ProductDto> items = list.subList(from, Math.min(from + s, list.size())).stream()
                .map(p -> mapper.toDto(p, live)).toList();
        return new PageDto<>(items, pg, s, list.size(), (int) Math.ceil(list.size() / (double) s));
    }

    /** Exact name matches rank first, then name-prefix, then name-contains, category/brand, tags, description. */
    private int score(Product p, String q) {
        if (q.isEmpty()) return 0;
        String n = p.getName().toLowerCase();
        int sc = 0;
        if (n.equals(q)) sc += 100;
        else if (n.startsWith(q)) sc += 80;
        else if (n.contains(q)) sc += 60;
        if (p.getCategory().getName().toLowerCase().contains(q)) sc += 40;
        if (p.getBrand() != null && p.getBrand().toLowerCase().contains(q)) sc += 35;
        if (p.getTags() != null && p.getTags().toLowerCase().contains(q)) sc += 15;
        if (p.getDescription() != null && p.getDescription().toLowerCase().contains(q)) sc += 5;
        return sc;
    }

    @Transactional(readOnly = true)
    public ProductDto get(Long id, User viewer) {
        Product p = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        boolean visible = p.getVerification() == VerificationStatus.VERIFIED && p.isActive() && !p.isDeleted();
        boolean owner = viewer != null && (viewer.getRole() == Role.ADMIN || p.getSeller().getId().equals(viewer.getId()));
        if (!visible && !owner) throw new ProductNotFoundException(id);
        return mapper.toDto(p, promos.liveDiscountMap());
    }

    @Transactional(readOnly = true)
    public List<ProductDto> related(Long id) {
        Product base = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        Map<Long, Integer> live = promos.liveDiscountMap();
        Set<String> baseTags = tagSet(base.getTags());
        baseTags.add(base.getCategory().getName().toLowerCase());
        Long parentId = base.getCategory().getParent() == null ? null : base.getCategory().getParent().getId();

        record Scored(Product p, int s) {}
        return products.findByVerificationAndActiveTrueAndDeletedFalse(VerificationStatus.VERIFIED).stream()
                .filter(p -> !p.getId().equals(id))
                .map(p -> {
                    int s = 0;
                    if (p.getCategory().getId().equals(base.getCategory().getId())) s += 6;
                    else if (parentId != null && p.getCategory().getParent() != null && p.getCategory().getParent().getId().equals(parentId)) s += 2;
                    if (base.getBrand() != null && base.getBrand().equalsIgnoreCase(p.getBrand())) s += 2;
                    Set<String> t = tagSet(p.getTags());
                    for (String x : t) if (baseTags.contains(x)) s += 3;
                    return new Scored(p, s);
                })
                .filter(x -> x.s() >= 3)
                .sorted(Comparator.comparingInt(Scored::s).reversed().thenComparing(x -> -x.p().getAvgRating()))
                .limit(12).map(x -> mapper.toDto(x.p(), live)).toList();
    }

    private Set<String> tagSet(String tags) {
        Set<String> s = new HashSet<>();
        if (tags != null) for (String t : tags.split(",")) if (!t.isBlank()) s.add(t.trim().toLowerCase());
        return s;
    }

    @Transactional(readOnly = true)
    public List<ProductDto> byIds(Collection<Long> ids) {
        Map<Long, Integer> live = promos.liveDiscountMap();
        return products.findAllById(ids).stream()
                .filter(p -> p.getVerification() == VerificationStatus.VERIFIED && p.isActive() && !p.isDeleted())
                .map(p -> mapper.toDto(p, live)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> home() {
        Map<Long, Integer> live = promos.liveDiscountMap();
        List<Product> all = products.findByVerificationAndActiveTrueAndDeletedFalse(VerificationStatus.VERIFIED);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trending", top(all, Comparator.comparingInt(Product::getSoldCount).reversed(), 12, live));
        m.put("bestSellers", top(all, Comparator.comparingInt(Product::getReviewCount).reversed(), 12, live));
        m.put("deals", top(all, Comparator.comparingInt((Product p) -> ProductMapper.effectiveDiscount(p, live)).reversed(), 12, live));
        m.put("recommended", top(all, Comparator.comparingDouble(Product::getAvgRating).reversed(), 12, live));
        m.put("verified", top(all, Comparator.comparing(Product::getCreatedAt).reversed(), 12, live));
        return m;
    }

    private List<ProductDto> top(List<Product> all, Comparator<Product> c, int n, Map<Long, Integer> live) {
        return all.stream().sorted(c).limit(n).map(p -> mapper.toDto(p, live)).toList();
    }

    @Transactional(readOnly = true)
    public List<String> brands() { return products.brands(); }

    // ------------------------------------------------------------------ categories
    @Transactional(readOnly = true)
    public List<CategoryDto> categoryTree() {
        return categories.findByParentIsNullOrderByIdAsc().stream().map(c -> new CategoryDto(c.getId(), c.getName(), null,
                categories.findByParentOrderByIdAsc(c).stream().map(ch -> new CategoryDto(ch.getId(), ch.getName(), c.getId(), List.of())).toList())).toList();
    }

    public CategoryDto addCategory(CategoryReq r) {
        Category parent = r.parentId() == null ? null : categories.findById(r.parentId()).orElseThrow(() -> new NotFoundException("Parent category not found"));
        Category c = categories.save(new Category(r.name().trim(), parent));
        return new CategoryDto(c.getId(), c.getName(), parent == null ? null : parent.getId(), List.of());
    }

    public void deleteCategory(Long id) {
        Category c = categories.findById(id).orElseThrow(() -> new NotFoundException("Category not found"));
        if (!categories.findByParentOrderByIdAsc(c).isEmpty()) throw new ConflictException("Delete the sub-categories first");
        if (products.countByCategory(c) > 0) throw new ConflictException("Category is used by products and cannot be deleted");
        categories.delete(c);
    }

    // ------------------------------------------------------------------ seller / admin management
    @Transactional(readOnly = true)
    public List<ProductDto> forSeller(User seller) {
        Map<Long, Integer> live = promos.liveDiscountMap();
        return products.findBySellerAndDeletedFalseOrderByIdDesc(seller).stream().map(p -> mapper.toDto(p, live)).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductDto> forAdmin() {
        Map<Long, Integer> live = promos.liveDiscountMap();
        return products.findByDeletedFalseOrderByIdDesc().stream().map(p -> mapper.toDto(p, live)).toList();
    }

    public ProductDto create(User owner, ProductReq r) {
        Product p = new Product();
        p.setSeller(owner);
        apply(p, r, true);
        // Sellers' products wait for admin verification; admin-created products are verified immediately.
        p.setVerification(owner.getRole() == Role.ADMIN ? VerificationStatus.VERIFIED : VerificationStatus.PENDING_VERIFICATION);
        products.save(p);
        if (owner.getRole() != Role.ADMIN)
            notif.notifyAdmins(NotificationType.ACCOUNT, "New product \"" + p.getName() + "\" is waiting for verification.");
        return mapper.toDto(p, promos.liveDiscountMap());
    }

    public ProductDto update(User actor, Long id, ProductReq r) {
        Product p = products.findById(id).filter(x -> !x.isDeleted()).orElseThrow(() -> new ProductNotFoundException(id));
        assertCanManage(actor, p);
        boolean contentChanged = !Objects.equals(p.getName(), r.name()) || !Objects.equals(p.getDescription(), r.description())
                || !p.getCategory().getId().equals(r.categoryId()) || !Objects.equals(p.getBrand(), r.brand());
        apply(p, r, false);
        if (actor.getRole() == Role.SELLER && contentChanged && p.getVerification() == VerificationStatus.VERIFIED) {
            p.setVerification(VerificationStatus.PENDING_VERIFICATION);   // re-verify when listing content changes
            notif.notifyAdmins(NotificationType.ACCOUNT, "Product \"" + p.getName() + "\" was edited and needs re-verification.");
        }
        return mapper.toDto(p, promos.liveDiscountMap());
    }

    private void apply(Product p, ProductReq r, boolean isNew) {
        Category c = categories.findById(r.categoryId()).orElseThrow(() -> new InvalidRequestException("Unknown category"));
        p.setName(r.name().trim()); p.setBrand(r.brand()); p.setCategory(c);
        p.setDescription(r.description()); p.setSpecifications(r.specifications()); p.setTags(r.tags());
        p.setPrice(r.price()); p.setDiscountPercent(r.discountPercent()); p.setStock(r.stock());
        p.setWarrantyMonths(r.warrantyMonths()); p.setReturnable(r.returnable());
        if (r.images() != null) {
            p.getImages().clear();
            r.images().stream().filter(s -> s != null && s.startsWith("/uploads/")).limit(8).forEach(p.getImages()::add);
        }
        if (r.active() != null) p.setActive(r.active());
        String sku = r.sku() == null ? "" : r.sku().trim();
        if (sku.isEmpty()) {
            if (isNew || p.getSku() == null) sku = "BB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            else sku = p.getSku();
        }
        if (!sku.equals(p.getSku()) && products.existsBySku(sku)) throw new ConflictException("SKU already exists: " + sku);
        p.setSku(sku);
    }

    private void assertCanManage(User actor, Product p) {
        if (actor.getRole() == Role.ADMIN) return;
        if (!p.getSeller().getId().equals(actor.getId())) throw new ForbiddenException("You can only manage your own products");
    }

    public ProductDto setActive(User actor, Long id, boolean active) {
        Product p = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        assertCanManage(actor, p);
        p.setActive(active);
        return mapper.toDto(p, promos.liveDiscountMap());
    }

    /** Soft-deletes (keeps order history intact). */
    public void delete(User actor, Long id) {
        Product p = products.findById(id).filter(x -> !x.isDeleted()).orElseThrow(() -> new ProductNotFoundException(id));
        assertCanManage(actor, p);
        p.setDeleted(true); p.setActive(false);
    }

    public ProductDto verify(Long id, boolean approve) {
        Product p = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        p.setVerification(approve ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED);
        notif.notify(p.getSeller(), NotificationType.ACCOUNT, "Your product \"" + p.getName() + "\" was "
                + (approve ? "verified and is now live on ByteBazaar." : "rejected. Please review the listing and resubmit."));
        return mapper.toDto(p, promos.liveDiscountMap());
    }
}
