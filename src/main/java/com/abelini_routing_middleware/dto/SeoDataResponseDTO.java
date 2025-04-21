package com.abelini_routing_middleware.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeoDataResponseDTO {
    @JsonProperty("seo_url_id")
    private String seoUrlId;

    @JsonProperty("store_id")
    private String storeId;

    @JsonProperty("language_id")
    private String languageId;

    private String key;
    private String value;
    private String keyword;

    @JsonProperty("shopify_id")
    private String shopifyId;

    @JsonProperty("sort_order")
    private String sortOrder;

    private String status;
}
