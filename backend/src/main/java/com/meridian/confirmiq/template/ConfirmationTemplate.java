package com.meridian.confirmiq.template;

import com.meridian.confirmiq.counterparty.Counterparty;
import com.meridian.confirmiq.trade.ProductType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A confirmation template for a product, optionally overridden for a specific counterparty. */
@Entity
@Table(name = "confirmation_template")
@Getter
@Setter
public class ConfirmationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;
    private String name;

    @Enumerated(EnumType.STRING)
    private ProductType productType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id")
    private Counterparty counterparty;

    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("version DESC")
    private List<TemplateVersion> versions = new ArrayList<>();

    public void addVersion(TemplateVersion v) {
        v.setTemplate(this);
        versions.add(v);
    }
}
