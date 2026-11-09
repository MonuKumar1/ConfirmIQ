package com.meridian.confirmiq.inbound;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.meridian.confirmiq.config.AppProperties;
import com.meridian.confirmiq.product.ProductTermsService;
import com.meridian.confirmiq.product.TermField;
import com.meridian.confirmiq.product.TermFormat;
import com.meridian.confirmiq.trade.Trade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Demo utility: produces the counterparty's own confirmation ("their paper") for a trade,
 * optionally with a deliberate break, so the matching flow can be exercised end to end.
 */
@Component
@RequiredArgsConstructor
public class CounterpartyPaperSimulator {

    private final ProductTermsService terms;
    private final AppProperties props;

    public enum Break { NONE, RATE, VALUE_DATE, AMOUNT }

    public byte[] render(Trade trade, Break breakField) {
        List<TermField> fields = terms.termsOf(trade).stream().map(f -> mutate(f, breakField)).toList();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 56, 56, 56, 56);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Color brand = new Color(0x3D, 0x3D, 0x3D);
        doc.add(new Paragraph(trade.getCounterparty().getLegalName().toUpperCase(),
                new Font(Font.TIMES_ROMAN, 15, Font.BOLD, brand)));
        doc.add(new Paragraph("Treasury Operations · LEI " + trade.getCounterparty().getLei(),
                new Font(Font.TIMES_ROMAN, 9, Font.ITALIC, Color.GRAY)));
        Paragraph title = new Paragraph("Confirmation of Foreign Exchange Transaction",
                new Font(Font.TIMES_ROMAN, 12, Font.BOLD));
        title.setSpacingBefore(18);
        title.setSpacingAfter(8);
        doc.add(title);
        doc.add(new Paragraph("To: " + props.bank().name() + " – FX Confirmations", new Font(Font.TIMES_ROMAN, 10)));
        Paragraph intro = new Paragraph("We are pleased to confirm the following transaction entered into between us. "
                + "Please advise immediately if these details differ from your records.", new Font(Font.TIMES_ROMAN, 10));
        intro.setSpacingAfter(12);
        doc.add(intro);

        PdfPTable table = new PdfPTable(new float[]{40, 60});
        table.setWidthPercentage(100);
        Font label = new Font(Font.TIMES_ROMAN, 10);
        Font value = new Font(Font.TIMES_ROMAN, 10, Font.BOLD);
        addRow(table, "Our Reference", "TRS-" + (700000 + trade.getId()), label, value);
        for (TermField f : fields) {
            addRow(table, f.counterpartyLabel(), f.value(), label, value);
        }
        doc.add(table);

        Paragraph sign = new Paragraph("\n\nAuthorised Signatory\n" + trade.getCounterparty().getLegalName(),
                new Font(Font.TIMES_ROMAN, 10, Font.ITALIC));
        doc.add(sign);
        doc.close();
        return out.toByteArray();
    }

    private static void addRow(PdfPTable t, String l, String v, Font lf, Font vf) {
        PdfPCell a = new PdfPCell(new Phrase(l, lf));
        PdfPCell b = new PdfPCell(new Phrase(v, vf));
        for (PdfPCell c : new PdfPCell[]{a, b}) {
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColor(Color.LIGHT_GRAY);
            c.setPadding(5);
            t.addCell(c);
        }
    }

    private static TermField mutate(TermField f, Break b) {
        String v = switch (b) {
            case RATE -> f.key().equals("rate")
                    ? TermFormat.rate(TermFormat.parseRate(f.value()).add(new BigDecimal("0.0015"))) : null;
            case VALUE_DATE -> f.key().equals("valueDate")
                    ? TermFormat.date(nextWeekday(TermFormat.parseDate(f.value()))) : null;
            case AMOUNT -> {
                if (!f.key().equals("buyAmount")) yield null;
                var a = TermFormat.parseAmount(f.value());
                yield TermFormat.amount(a.currency(), a.amount().add(new BigDecimal("2500")));
            }
            case NONE -> null;
        };
        return v == null ? f : new TermField(f.key(), f.label(), f.counterpartyLabel(), v, f.type());
    }

    private static LocalDate nextWeekday(LocalDate d) {
        LocalDate n = d.plusDays(1);
        while (n.getDayOfWeek().getValue() >= 6) {
            n = n.plusDays(1);
        }
        return n;
    }
}
