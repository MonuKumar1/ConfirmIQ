package com.meridian.confirmiq.template;

import com.meridian.confirmiq.document.DocumentService;
import com.meridian.confirmiq.template.TemplateService.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Confirmation templates")
@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService service;
    private final DocumentService documents;

    @GetMapping
    public List<TemplateSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public TemplateDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public TemplateDetail createVersion(@PathVariable Long id, @Valid @RequestBody VersionRequest request) {
        return service.createVersion(id, request);
    }

    @PostMapping("/{id}/versions/{version}/activate")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public TemplateDetail activate(@PathVariable Long id, @PathVariable int version) {
        return service.activate(id, version);
    }

    /** Renders a specimen document for a template version using a representative trade. */
    @GetMapping(value = "/{id}/versions/{version}/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> preview(@PathVariable Long id, @PathVariable int version) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=template-preview.pdf")
                .body(documents.previewTemplate(id, version));
    }
}
