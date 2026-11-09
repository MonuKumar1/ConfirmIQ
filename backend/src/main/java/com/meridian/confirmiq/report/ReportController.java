package com.meridian.confirmiq.report;

import com.meridian.confirmiq.confirmation.ConfirmationQueryService.BlotterFilter;
import com.meridian.confirmiq.confirmation.ConfirmationQueryService.Queue;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Reports & exports")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reports;

    @GetMapping("/blotter.csv")
    public ResponseEntity<byte[]> csv(@RequestParam(defaultValue = "ALL") Queue queue, @RequestParam(required = false) String q) {
        return file(reports.csv(filter(queue, q)), "confirmations-" + LocalDate.now() + ".csv", "text/csv");
    }

    @GetMapping("/blotter.xlsx")
    public ResponseEntity<byte[]> xlsx(@RequestParam(defaultValue = "ALL") Queue queue, @RequestParam(required = false) String q) {
        return file(reports.xlsx(filter(queue, q)), "confirmations-" + LocalDate.now() + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/outstanding.pdf")
    public ResponseEntity<byte[]> outstanding() {
        return file(reports.outstandingPdf(), "outstanding-confirmations-" + LocalDate.now() + ".pdf",
                MediaType.APPLICATION_PDF_VALUE);
    }

    private static BlotterFilter filter(Queue queue, String q) {
        return new BlotterFilter(queue, null, null, null, null, null, q);
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name, String type) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(body);
    }
}
