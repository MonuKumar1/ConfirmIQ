package com.meridian.confirmiq.counterparty;

import com.meridian.confirmiq.counterparty.CounterpartyDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Counterparty static data")
@RestController
@RequestMapping("/api/v1/counterparties")
@RequiredArgsConstructor
public class CounterpartyController {

    private final CounterpartyService service;

    @GetMapping
    public List<CounterpartySummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public CounterpartyDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/house-ssis")
    public List<SsiView> houseInstructions() {
        return service.houseInstructions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public CounterpartyDetail create(@Valid @RequestBody CounterpartyRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public CounterpartyDetail update(@PathVariable Long id, @Valid @RequestBody CounterpartyRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/contacts")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public CounterpartyDetail addContact(@PathVariable Long id, @Valid @RequestBody ContactRequest request) {
        return service.addContact(id, request);
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public CounterpartyDetail removeContact(@PathVariable Long id, @PathVariable Long contactId) {
        return service.removeContact(id, contactId);
    }

    @PostMapping("/{id}/ssis")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public CounterpartyDetail addSsi(@PathVariable Long id, @Valid @RequestBody SsiRequest request) {
        return service.addSsi(id, request);
    }

    @DeleteMapping("/{id}/ssis/{ssiId}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public CounterpartyDetail deactivateSsi(@PathVariable Long id, @PathVariable Long ssiId) {
        return service.deactivateSsi(id, ssiId);
    }
}
