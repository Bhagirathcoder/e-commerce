package com.bytebazaar.service;

import com.bytebazaar.entity.*;
import com.bytebazaar.exception.InvalidRequestException;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

/** Invoice and warranty-certificate PDFs (OpenPDF). "Rs." is used because the built-in PDF fonts have no rupee glyph. */
@Service
public class PdfService {
    private static final Color NAVY = new Color(15, 39, 66);
    private static final Color ORANGE = new Color(255, 153, 0);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private final Font h1 = new Font(Font.HELVETICA, 20, Font.BOLD, NAVY);
    private final Font bold = new Font(Font.HELVETICA, 10, Font.BOLD);
    private final Font normal = new Font(Font.HELVETICA, 10);
    private final Font small = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);

    private void logo(Document d, String title) throws DocumentException {
        Paragraph p = new Paragraph();
        p.add(new Chunk("Byte", new Font(Font.HELVETICA, 26, Font.BOLD, NAVY)));
        p.add(new Chunk("Bazaar", new Font(Font.HELVETICA, 26, Font.BOLD, ORANGE)));
        d.add(p);
        d.add(new Paragraph("Shop More. Live Better.", small));
        d.add(new Paragraph(" "));
        d.add(new Paragraph(title, h1));
        d.add(new Paragraph(" "));
    }

    private PdfPCell cell(String t, Font f, boolean header) {
        PdfPCell c = new PdfPCell(new Phrase(t == null ? "-" : t, header ? new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE) : f));
        c.setPadding(5);
        if (header) c.setBackgroundColor(NAVY);
        return c;
    }

    private void kv(PdfPTable t, String k, String v) {
        PdfPCell a = new PdfPCell(new Phrase(k, bold)); a.setBorder(0); a.setPadding(2);
        PdfPCell b = new PdfPCell(new Phrase(v == null ? "-" : v, normal)); b.setBorder(0); b.setPadding(2);
        t.addCell(a); t.addCell(b);
    }

    private String rs(java.math.BigDecimal v) { return "Rs. " + v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }

    /** @param sellerId when not null, only that seller's lines are printed (seller copy). */
    public byte[] invoice(Order o, Long sellerId) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document d = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(d, out);
            d.open();
            logo(d, "TAX INVOICE");

            PdfPTable meta = new PdfPTable(new float[]{1.2f, 2.5f, 1.2f, 2.5f});
            meta.setWidthPercentage(100);
            kv(meta, "Invoice No:", o.getInvoiceNumber()); kv(meta, "Order ID:", "#" + o.getId());
            kv(meta, "Invoice Date:", o.getCreatedAt().format(DT)); kv(meta, "Payment:", o.getPaymentMethod() + " / " + o.getPaymentStatus());
            kv(meta, "Customer:", o.getUser().getFullName()); kv(meta, "Email:", o.getUser().getEmail());
            kv(meta, "Phone:", o.getUser().getPhone()); kv(meta, "", "");
            String addr = o.getShipName() + ", " + o.getShipAddress() + ", " + o.getShipCity()
                    + (o.getShipDistrict() == null || o.getShipDistrict().isBlank() ? "" : ", " + o.getShipDistrict())
                    + ", " + o.getShipState() + " - " + o.getShipPin() + " | Ph: " + o.getShipPhone();
            kv(meta, "Billing Address:", addr); kv(meta, "Shipping Address:", addr);
            d.add(meta);
            d.add(new Paragraph(" "));

            PdfPTable t = new PdfPTable(new float[]{3.2f, 2f, 0.8f, 1.4f, 1.6f});
            t.setWidthPercentage(100);
            for (String h : new String[]{"Product", "Seller", "Qty", "Unit Price", "Amount"}) t.addCell(cell(h, normal, true));
            java.math.BigDecimal sub = java.math.BigDecimal.ZERO, mrp = java.math.BigDecimal.ZERO;
            for (OrderItem i : o.getItems()) {
                if (sellerId != null && !i.getSeller().getId().equals(sellerId)) continue;
                t.addCell(cell(i.getProductName(), normal, false));
                t.addCell(cell(i.getSeller().getStoreName() != null ? i.getSeller().getStoreName() : i.getSeller().getFullName(), normal, false));
                t.addCell(cell(String.valueOf(i.getQuantity()), normal, false));
                t.addCell(cell(rs(i.getUnitPrice()), normal, false));
                t.addCell(cell(rs(i.lineTotal()), normal, false));
                sub = sub.add(i.lineTotal());
                mrp = mrp.add(i.getMrp().multiply(java.math.BigDecimal.valueOf(i.getQuantity())));
            }
            d.add(t);
            d.add(new Paragraph(" "));

            PdfPTable tot = new PdfPTable(new float[]{4, 2});
            tot.setWidthPercentage(45); tot.setHorizontalAlignment(Element.ALIGN_RIGHT);
            if (sellerId == null) {
                kv(tot, "Subtotal (MRP):", rs(o.getSubtotal())); kv(tot, "Discount:", "- " + rs(o.getDiscount()));
                kv(tot, "Shipping:", rs(o.getShipping())); kv(tot, "Tax (5%):", rs(o.getTax()));
                kv(tot, "TOTAL:", rs(o.getTotal()));
            } else {
                kv(tot, "Items MRP:", rs(mrp)); kv(tot, "Discount:", "- " + rs(mrp.subtract(sub)));
                kv(tot, "Seller total (before tax):", rs(sub));
            }
            d.add(tot);
            d.add(new Paragraph(" "));
            d.add(new Paragraph("Thank you for shopping with ByteBazaar. Returns accepted within 7 days of delivery on eligible items.", small));
            d.add(new Paragraph("This is a computer-generated invoice.", small));
            d.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new InvalidRequestException("Could not generate the invoice");
        }
    }

    public byte[] warranty(Order o, Long onlyItemId) {
        if (o.getStatus() != OrderStatus.DELIVERED || o.getDeliveredAt() == null)
            throw new InvalidRequestException("The warranty certificate is available after the order is delivered");
        boolean any = false;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document d = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(d, out);
            d.open();
            logo(d, "WARRANTY CERTIFICATE");
            for (OrderItem i : o.getItems()) {
                if (i.getWarrantyMonths() <= 0 || (onlyItemId != null && !i.getId().equals(onlyItemId))) continue;
                any = true;
                LocalDate start = o.getDeliveredAt().toLocalDate();
                LocalDate end = start.plusMonths(i.getWarrantyMonths());
                for (int u = 1; u <= i.getQuantity(); u++) {
                    PdfPTable t = new PdfPTable(new float[]{1.6f, 3.4f});
                    t.setWidthPercentage(100);
                    kv(t, "Product:", i.getProductName());
                    kv(t, "Order ID:", "#" + o.getId());
                    kv(t, "Serial Number:", String.format("BB%06d-%d-%d", o.getId(), i.getId(), u));
                    kv(t, "Purchase Date:", o.getCreatedAt().format(D));
                    kv(t, "Warranty Start:", start.format(D));
                    kv(t, "Warranty End:", end.format(D));
                    kv(t, "Warranty Period:", i.getWarrantyMonths() + " month(s)");
                    kv(t, "Seller:", i.getSeller().getStoreName() != null ? i.getSeller().getStoreName() : i.getSeller().getFullName());
                    kv(t, "Customer:", o.getUser().getFullName());
                    d.add(t);
                    d.add(new Paragraph(" "));
                }
            }
            if (!any) throw new InvalidRequestException("No item in this order has a warranty");
            d.add(new Paragraph("Warranty covers manufacturing defects only. Keep this certificate with your invoice " + o.getInvoiceNumber() + ".", small));
            d.close();
            return out.toByteArray();
        } catch (InvalidRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidRequestException("Could not generate the warranty certificate");
        }
    }
}
