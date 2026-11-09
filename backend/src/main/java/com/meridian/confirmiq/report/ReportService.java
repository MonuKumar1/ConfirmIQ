package com.meridian.confirmiq.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.meridian.confirmiq.config.AppProperties;
import com.meridian.confirmiq.confirmation.ConfirmationQueryService;
import com.meridian.confirmiq.confirmation.ConfirmationQueryService.BlotterFilter;
import com.meridian.confirmiq.confirmation.ConfirmationQueryService.BlotterRow;
import com.meridian.confirmiq.confirmation.ConfirmationQueryService.Queue;
import com.meridian.confirmiq.product.TermFormat;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final ConfirmationQueryService query;
    private final AppProperties props;

    private static final List<Column> COLUMNS = List.of(
            new Column("Trade Ref", r -> r.tradeRef()),
            new Column("External Ref", r -> r.externalRef()),
            new Column("Product", r -> r.productType().getDisplayName()),
            new Column("Counterparty", r -> r.counterparty()),
            new Column("Trade Date", r -> String.valueOf(r.tradeDate())),
            new Column("Value Date", r -> String.valueOf(r.valueDate())),
            new Column("Pair", r -> r.currencyPair()),
            new Column("Buy Ccy", r -> r.buyCurrency()),
            new Column("Buy Amount", r -> r.buyAmount().toPlainString()),
            new Column("Sell Ccy", r -> r.sellCurrency()),
            new Column("Sell Amount", r -> r.sellAmount().toPlainString()),
            new Column("Rate", r -> r.rate().toPlainString()),
            new Column("Status", r -> r.status().name()),
            new Column("Sent (UTC)", r -> r.sentAt() == null ? "" : TS.format(r.sentAt())),
            new Column("SLA Due (UTC)", r -> r.slaDueAt() == null ? "" : TS.format(r.slaDueAt())),
            new Column("SLA Breached", r -> r.slaBreached() ? "Y" : "N"),
            new Column("Chasers", r -> String.valueOf(r.chaserCount())),
            new Column("Age (days)", r -> String.valueOf(r.ageDays())));

    public byte[] csv(BlotterFilter filter) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", COLUMNS.stream().map(Column::header).toList())).append("\r\n");
        for (BlotterRow r : query.all(filter)) {
            sb.append(String.join(",", COLUMNS.stream().map(c -> escape(c.value().apply(r))).toList())).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] xlsx(BlotterFilter filter) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Confirmations");
            CellStyle head = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            bold.setColor(IndexedColors.WHITE.getIndex());
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle num = wb.createCellStyle();
            num.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            CellStyle rate = wb.createCellStyle();
            rate.setDataFormat(wb.createDataFormat().getFormat("0.000000"));

            Row h = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(COLUMNS.get(i).header());
                c.setCellStyle(head);
            }
            int rowIdx = 1;
            for (BlotterRow r : query.all(filter)) {
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < COLUMNS.size(); i++) {
                    Cell c = row.createCell(i);
                    String header = COLUMNS.get(i).header();
                    switch (header) {
                        case "Buy Amount" -> { c.setCellValue(r.buyAmount().doubleValue()); c.setCellStyle(num); }
                        case "Sell Amount" -> { c.setCellValue(r.sellAmount().doubleValue()); c.setCellStyle(num); }
                        case "Rate" -> { c.setCellValue(r.rate().doubleValue()); c.setCellStyle(rate); }
                        default -> c.setCellValue(COLUMNS.get(i).value().apply(r));
                    }
                }
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(rowIdx - 1, 0), 0, COLUMNS.size() - 1));
            for (int i = 0; i < COLUMNS.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** End-of-day report of all unconfirmed trades, grouped implicitly by age (oldest first). */
    public byte[] outstandingPdf() {
        List<BlotterRow> rows = query.all(new BlotterFilter(Queue.ALL, null, null, null, null, null, null)).stream()
                .filter(r -> !r.status().isTerminal())
                .sorted((a, b) -> Long.compare(b.ageDays(), a.ageDays()))
                .toList();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 30, 30, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Color navy = new Color(0x10, 0x2A, 0x43);
        doc.add(new Paragraph(props.bank().shortName().toUpperCase(Locale.ENGLISH) + " · FX OPERATIONS",
                new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.BOLD, Color.GRAY)));
        doc.add(new Paragraph("Outstanding Confirmations Report",
                new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 15, com.lowagie.text.Font.BOLD, navy)));
        long breached = rows.stream().filter(BlotterRow::slaBreached).count();
        Paragraph meta = new Paragraph("As of %s UTC   ·   %d unconfirmed trades   ·   %d SLA breaches".formatted(
                TS.format(Instant.now()), rows.size(), breached),
                new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8.5f, com.lowagie.text.Font.NORMAL, Color.DARK_GRAY));
        meta.setSpacingAfter(10);
        doc.add(meta);

        String[] headers = {"Trade Ref", "Counterparty", "Product", "Trade Date", "Value Date", "Buy", "Sell", "Status", "Age", "SLA", "Chasers"};
        PdfPTable t = new PdfPTable(new float[]{12, 18, 10, 8, 8, 12, 12, 10, 4, 4, 4});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        com.lowagie.text.Font hf = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 7, com.lowagie.text.Font.BOLD, Color.WHITE);
        com.lowagie.text.Font cf = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 7);
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, hf));
            c.setBackgroundColor(navy);
            c.setPadding(4);
            t.addCell(c);
        }
        for (BlotterRow r : rows) {
            String[] v = {r.tradeRef(), r.counterparty(), r.productType().getDisplayName(), String.valueOf(r.tradeDate()),
                    String.valueOf(r.valueDate()), TermFormat.amount(r.buyCurrency(), r.buyAmount()),
                    TermFormat.amount(r.sellCurrency(), r.sellAmount()), r.status().name().replace('_', ' '),
                    "T+" + r.ageDays(), r.slaBreached() ? "BREACH" : "OK", String.valueOf(r.chaserCount())};
            for (String s : v) {
                PdfPCell c = new PdfPCell(new Phrase(s, cf));
                c.setPadding(3.5f);
                c.setBorderColor(new Color(0xD5, 0xDB, 0xE3));
                if (r.slaBreached()) c.setBackgroundColor(new Color(0xFD, 0xEC, 0xEA));
                t.addCell(c);
            }
        }
        doc.add(t);
        doc.close();
        return out.toByteArray();
    }

    private static String escape(String v) {
        if (v == null) return "";
        return v.contains(",") || v.contains("\"") || v.contains("\n") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    private record Column(String header, Function<BlotterRow, String> value) {}
}
