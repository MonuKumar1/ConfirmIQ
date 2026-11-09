package com.meridian.confirmiq.document;

import com.meridian.confirmiq.common.PageResponse;
import com.meridian.confirmiq.document.Document.DocType;
import com.meridian.confirmiq.document.Document.Direction;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "Document repository")
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentRepository repository;
    private final DocumentService service;

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<DocumentView> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) DocType docType,
                                             @RequestParam(required = false) Direction direction,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        Specification<Document> spec = (root, query, cb) -> {
            var trade = root.join("trade", JoinType.LEFT);
            List<Predicate> p = new ArrayList<>();
            if (docType != null) p.add(cb.equal(root.get("docType"), docType));
            if (direction != null) p.add(cb.equal(root.get("direction"), direction));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("fileName")), like),
                        cb.like(cb.lower(root.get("sha256")), like),
                        cb.like(cb.lower(trade.get("tradeRef")), like),
                        cb.like(cb.lower(trade.join("counterparty", JoinType.LEFT).get("legalName")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(spec, PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"))), DocumentView::of);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public DocumentView get(@PathVariable Long id) {
        return DocumentView.of(service.find(id));
    }

    @GetMapping("/{id}/content")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> content(@PathVariable Long id,
                                          @RequestParam(defaultValue = "false") boolean download) {
        Document d = service.find(id);
        return pdfResponse(d, service.content(d), download);
    }

    public static ResponseEntity<byte[]> pdfResponse(Document d, byte[] body, boolean download) {
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(d.getFileName()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-SHA256", d.getSha256())
                .body(body);
    }
}
