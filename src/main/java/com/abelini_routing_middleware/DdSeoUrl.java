package com.abelini_routing_middleware;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "dd_seo_url",
        indexes = {
                @Index(name = "idx_keyword", columnList = "keyword"),
                @Index(name = "idx_query", columnList = "`key`, value"),
                @Index(name = "idx_language_id", columnList = "language_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "unique_language_id_keyword", columnNames = {"language_id", "`key`"})
        }
)
public class DdSeoUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seo_url_id", nullable = false)
    private Integer seoUrlId;

    @Column(name = "store_id", nullable = false)
    private int storeId;

    @Column(name = "language_id", nullable = false)
    private int languageId;

    @Column(name = "`key`", nullable = false, length = 64)
    private String key;

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "keyword", nullable = false)
    private String keyword;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "status", columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean status;

    @Column(name = "shopify_id")
    private String shopifyId;

    @Column(name = "shopify_full_id")
    private String shopifyFullId;
}
