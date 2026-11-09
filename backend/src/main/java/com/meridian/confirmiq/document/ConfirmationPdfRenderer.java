package com.meridian.confirmiq.document;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.meridian.confirmiq.document.ConfirmationPdfModel.SsiLine;
import com.meridian.confirmiq.document.Document.Stage;
import com.meridian.confirmiq.product.TermField;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.meridian.confirmiq.document.PdfStyle.*;

/** Renders house confirmation paper (confirmations, amendments, cancellations) with OpenPDF. */
@Component
public class ConfirmationPdfRenderer {

    private static final DateTimeFormatter ISSUED =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter STAMP_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    public byte[] render(ConfirmationPdfModel m) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.lowagie.text.Document doc = new com.lowagie.text.Document(PageSize.A4, 48, 48, 92, 64);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new Letterhead(m));
        doc.addTitle(m.title() + " " + m.tradeRef());
        doc.addAuthor(m.bank().name());
        doc.addCreator("ConfirmIQ");
        doc.open();

        titleBlock(doc, m);
        addressBlock(doc, m);
        paragraph(doc, m.preamble(), 10);

        heading(doc, "1.  Economic Terms");
        doc.add(termsTable(m));

        if (!m.settlement().isEmpty()) {
            heading(doc, "2.  Settlement Instructions");
            doc.add(settlementTable(m));
        }

        if (!m.clauses().isEmpty()) {
            heading(doc, (m.settlement().isEmpty() ? "2" : "3") + ".  Terms and Conditions");
            int n = 1;
            for (String clause : m.clauses()) {
                Paragraph p = new Paragraph(n++ + ".  " + clause, BODY);
                p.setIndentationLeft(14);
                p.setFirstLineIndent(-14);
                p.setSpacingAfter(4);
                p.setLeading(12);
                doc.add(p);
            }
        }

        paragraph(doc, m.closing(), 8);
        doc.add(signatureAndVerification(writer, m));
        doc.close();
        return out.toByteArray();
    }

    private void titleBlock(com.lowagie.text.Document doc, ConfirmationPdfModel m) {
        Paragraph title = new Paragraph(m.title().toUpperCase(Locale.ENGLISH), TITLE);
        title.setSpacingAfter(2);
        doc.add(title);
        String meta = "Reference %s   ·   Document version %d   ·   Issued %s".formatted(
                m.tradeRef(), m.documentVersion(), ISSUED.format(m.issuedAt()));
        Paragraph sub = new Paragraph(meta, SUBTITLE);
        sub.setSpacingAfter(12);
        doc.add(sub);
    }

    private void addressBlock(com.lowagie.text.Document doc, ConfirmationPdfModel m) {
        PdfPTable t = new PdfPTable(new float[]{1, 1});
        t.setWidthPercentage(100);
        t.setSpacingAfter(12);

        PdfPCell to = boxCell();
        to.addElement(new Paragraph("TO", SMALL_BOLD));
        to.addElement(new Paragraph(m.counterpartyName(), BODY_BOLD));
        to.addElement(new Paragraph("LEI " + m.counterpartyLei(), SMALL));
        if (m.attentionName() != null) {
            to.addElement(new Paragraph("Attn: " + m.attentionName() + " (" + m.attentionEmail() + ")", SMALL));
        }
        t.addCell(to);

        PdfPCell from = boxCell();
        from.addElement(new Paragraph("FROM", SMALL_BOLD));
        from.addElement(new Paragraph(m.bank().name(), BODY_BOLD));
        from.addElement(new Paragraph(m.bank().address(), SMALL));
        from.addElement(new Paragraph("LEI " + m.bank().lei() + "   ·   BIC " + m.bank().bic(), SMALL));
        from.addElement(new Paragraph("FX Confirmations: " + m.bank().operationsEmail(), SMALL));
        t.addCell(from);
        doc.add(t);
    }

    private PdfPTable termsTable(ConfirmationPdfModel m) {
        PdfPTable t = new PdfPTable(new float[]{38, 62});
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        t.setSpacingAfter(10);
        for (TermField f : m.terms()) {
            PdfPCell label = new PdfPCell(new Phrase(f.label(), LABEL));
            label.setBackgroundColor(LABEL_BG);
            styleRow(label);
            t.addCell(label);
            PdfPCell value = new PdfPCell(new Phrase(f.value(), VALUE));
            styleRow(value);
            t.addCell(value);
        }
        return t;
    }

    private PdfPTable settlementTable(ConfirmationPdfModel m) {
        PdfPTable t = new PdfPTable(new float[]{21, 20, 23, 20, 16});
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        t.setSpacingAfter(10);
        t.setHeaderRows(1);
        for (String h : new String[]{"PAYMENT FLOW", "BENEFICIARY", "ACCOUNT WITH (BIC)", "ACCOUNT", "INTERMEDIARY"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, TABLE_HEAD));
            c.setBackgroundColor(NAVY);
            c.setBorderColor(NAVY);
            c.setPadding(5);
            t.addCell(c);
        }
        for (SsiLine s : m.settlement()) {
            for (String v : new String[]{s.flow(), s.beneficiary(), s.bank() + "\n" + s.bic(), s.account(),
                    s.intermediary() == null ? "—" : s.intermediary()}) {
                PdfPCell c = new PdfPCell(new Phrase(v, new Font(Font.HELVETICA, 7.5f)));
                styleRow(c);
                t.addCell(c);
            }
        }
        return t;
    }

    private PdfPTable signatureAndVerification(PdfWriter writer, ConfirmationPdfModel m) {
        PdfPTable t = new PdfPTable(new float[]{1, 1});
        t.setWidthPercentage(100);
        t.setSpacingBefore(12);
        t.setKeepTogether(true);

        PdfPCell ours = boxCell();
        ours.addElement(new Paragraph("For and on behalf of " + m.bank().name(), BODY_BOLD));
        if (m.stage() == Stage.FINAL) {
            Image stamp = Image.getInstance(eStamp(writer, m));
            stamp.setWidthPercentage(0); // keep natural size inside the composite cell
            stamp.setSpacingBefore(4);
            ours.addElement(stamp);
            ours.addElement(new Paragraph("Electronically issued — no wet signature required", SMALL));
            ours.addElement(new Paragraph("Prepared by " + m.preparedBy() + "   ·   Approved by " + m.approvedBy(), SMALL));
        } else {
            Paragraph draft = new Paragraph("DRAFT — pending four-eyes approval. Not for release to the counterparty.",
                    new Font(Font.HELVETICA, 8.5f, Font.BOLD, new Color(0xB4, 0x23, 0x18)));
            draft.setSpacingBefore(18);
            draft.setSpacingAfter(18);
            ours.addElement(draft);
            ours.addElement(new Paragraph("Prepared by " + m.preparedBy(), SMALL));
        }
        t.addCell(ours);

        PdfPCell theirs = boxCell();
        theirs.addElement(new Paragraph("Accepted and confirmed by " + m.counterpartyName(), BODY_BOLD));
        for (String line : new String[]{"Authorised signature", "Name and title", "Date"}) {
            Paragraph p = new Paragraph("\n______________________________________\n" + line, SMALL);
            p.setSpacingBefore(4);
            theirs.addElement(p);
        }
        t.addCell(theirs);

        PdfPCell qrCell = new PdfPCell();
        qrCell.setBorderColor(RULE);
        qrCell.setPadding(6);
        qrCell.setColspan(2);
        PdfPTable inner = new PdfPTable(new float[]{14, 86});
        inner.setWidthPercentage(100);
        try {
            Image qr = Image.getInstance(QrCodes.encode(m.qrPayload(), 240), null);
            qr.scaleToFit(64, 64);
            qr.setWidthPercentage(0);
            PdfPCell q = new PdfPCell(qr, false);
            q.setBorder(Rectangle.NO_BORDER);
            inner.addCell(q);
        } catch (IOException e) {
            throw new IllegalStateException("QR image failed", e);
        }
        PdfPCell text = new PdfPCell();
        text.setBorder(Rectangle.NO_BORDER);
        text.setVerticalAlignment(Element.ALIGN_MIDDLE);
        text.addElement(new Paragraph("DOCUMENT VERIFICATION", SMALL_BOLD));
        text.addElement(new Paragraph("Fingerprint " + m.fingerprint() + "   ·   Economic version "
                + m.economicVersion(), SMALL));
        text.addElement(new Paragraph("Please verify the terms above and return this confirmation signed within the "
                + "agreed timeframe by email to " + m.bank().operationsEmail() + " or via the Meridian Client Portal. "
                + "The code links this document to its terms; altered paper will be flagged on receipt.", SMALL));
        inner.addCell(text);
        qrCell.addElement(inner);
        t.addCell(qrCell);
        return t;
    }

    /** A circular electronic issue stamp drawn as vector graphics. */
    private PdfTemplate eStamp(PdfWriter writer, ConfirmationPdfModel m) {
        float size = 88;
        PdfTemplate tpl = writer.getDirectContent().createTemplate(size, size);
        float c = size / 2;
        tpl.setColorStroke(STAMP);
        tpl.setLineWidth(1.6f);
        tpl.circle(c, c, c - 2);
        tpl.stroke();
        tpl.setLineWidth(0.6f);
        tpl.circle(c, c, c - 6);
        tpl.stroke();
        BaseFont bold = font(BaseFont.HELVETICA_BOLD);
        BaseFont regular = font(BaseFont.HELVETICA);
        tpl.beginText();
        tpl.setColorFill(STAMP);
        centered(tpl, bold, 5.6f, "MERIDIAN CAPITAL", c, c + 17);
        centered(tpl, bold, 10f, "ISSUED", c, c + 2);
        centered(tpl, regular, 6f, STAMP_DATE.format(m.issuedAt()).toUpperCase(Locale.ENGLISH), c, c - 9);
        centered(tpl, regular, 5f, "FX OPS · LONDON", c, c - 20);
        tpl.endText();
        return tpl;
    }

    private static void centered(PdfContentByte cb, BaseFont f, float size, String text, float x, float y) {
        cb.setFontAndSize(f, size);
        cb.showTextAligned(Element.ALIGN_CENTER, text, x, y, 0);
    }

    private static void heading(com.lowagie.text.Document doc, String text) {
        Paragraph p = new Paragraph(text, H2);
        p.setSpacingBefore(6);
        p.setSpacingAfter(2);
        doc.add(p);
    }

    private static void paragraph(com.lowagie.text.Document doc, String text, float after) {
        if (text == null || text.isBlank()) {
            return;
        }
        Paragraph p = new Paragraph(text, BODY);
        p.setLeading(12.5f);
        p.setSpacingAfter(after);
        doc.add(p);
    }

    private static PdfPCell boxCell() {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(RULE);
        c.setPadding(8);
        c.setPaddingTop(4);
        return c;
    }

    private static void styleRow(PdfPCell c) {
        c.setBorderColor(RULE);
        c.setPaddingTop(4.5f);
        c.setPaddingBottom(5.5f);
        c.setPaddingLeft(6);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
    }

    private static final java.util.Map<String, BaseFont> FONTS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Shared font instances so every page and template references the same font resource. */
    static BaseFont font(String name) {
        return FONTS.computeIfAbsent(name, n -> {
            try {
                return BaseFont.createFont(n, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** Letterhead, footer with "Page x of y", and the DRAFT watermark. */
    private static final class Letterhead extends PdfPageEventHelper {

        private final ConfirmationPdfModel m;
        private final BaseFont bold = font(BaseFont.HELVETICA_BOLD);
        private final BaseFont regular = font(BaseFont.HELVETICA);
        private PdfTemplate totalPages;

        Letterhead(ConfirmationPdfModel m) {
            this.m = m;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, com.lowagie.text.Document document) {
            totalPages = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, com.lowagie.text.Document document) {
            Rectangle page = document.getPageSize();
            PdfContentByte cb = writer.getDirectContent();
            float left = document.leftMargin();
            float right = page.getWidth() - document.rightMargin();
            float top = page.getHeight();

            // Brand bar and monogram
            cb.setColorFill(NAVY);
            cb.rectangle(0, top - 6, page.getWidth(), 6);
            cb.fill();
            cb.setColorFill(NAVY);
            cb.rectangle(left, top - 58, 30, 30);
            cb.fill();
            cb.setColorFill(ACCENT);
            cb.rectangle(left, top - 58, 30, 4);
            cb.fill();

            cb.beginText();
            cb.setColorFill(Color.WHITE);
            cb.setFontAndSize(bold, 17);
            cb.showTextAligned(Element.ALIGN_CENTER, "M", left + 15, top - 50, 0);
            cb.setColorFill(NAVY);
            cb.setFontAndSize(bold, 13);
            cb.showTextAligned(Element.ALIGN_LEFT, m.bank().shortName().toUpperCase(Locale.ENGLISH), left + 40, top - 40, 0);
            cb.setColorFill(MUTED);
            cb.setFontAndSize(regular, 7.5f);
            cb.showTextAligned(Element.ALIGN_LEFT, "Global Markets  ·  FX Operations", left + 40, top - 52, 0);
            cb.setFontAndSize(bold, 7.5f);
            cb.setColorFill(NAVY);
            cb.showTextAligned(Element.ALIGN_RIGHT, m.tradeRef(), right, top - 40, 0);
            cb.setFontAndSize(regular, 7f);
            cb.setColorFill(MUTED);
            cb.showTextAligned(Element.ALIGN_RIGHT, m.stage() == Stage.DRAFT ? "DRAFT" : "ORIGINAL",
                    right, top - 51, 0);
            cb.endText();

            cb.setColorStroke(RULE);
            cb.setLineWidth(0.6f);
            cb.moveTo(left, top - 68);
            cb.lineTo(right, top - 68);
            cb.stroke();

            // Footer
            cb.moveTo(left, 46);
            cb.lineTo(right, 46);
            cb.stroke();
            cb.beginText();
            cb.setColorFill(MUTED);
            cb.setFontAndSize(regular, 6.5f);
            cb.showTextAligned(Element.ALIGN_LEFT, m.bank().name() + "  ·  " + m.bank().address(), left, 34, 0);
            cb.showTextAligned(Element.ALIGN_LEFT,
                    "Fictional institution for demonstration purposes. Generated by ConfirmIQ.", left, 25, 0);
            String pageText = "Page " + writer.getPageNumber() + " of ";
            float width = regular.getWidthPoint(pageText, 6.5f);
            cb.showTextAligned(Element.ALIGN_LEFT, pageText, right - width - 10, 34, 0);
            cb.endText();
            cb.addTemplate(totalPages, right - 10, 34);

            if (m.stage() == Stage.DRAFT) {
                PdfContentByte under = writer.getDirectContentUnder();
                under.saveState();
                PdfGState gs = new PdfGState();
                gs.setFillOpacity(0.07f);
                under.setGState(gs);
                under.beginText();
                under.setColorFill(new Color(0xB4, 0x23, 0x18));
                under.setFontAndSize(bold, 120);
                under.showTextAligned(Element.ALIGN_CENTER, "DRAFT", page.getWidth() / 2, page.getHeight() / 2 - 40, 45);
                under.endText();
                under.restoreState();
            }
        }

        @Override
        public void onCloseDocument(PdfWriter writer, com.lowagie.text.Document document) {
            totalPages.beginText();
            totalPages.setColorFill(MUTED);
            totalPages.setFontAndSize(regular, 6.5f);
            totalPages.showText(String.valueOf(writer.getPageNumber() - 1));
            totalPages.endText();
        }
    }
}
