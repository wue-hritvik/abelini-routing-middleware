package com.abelini_routing_middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachePurgeRequest {
    private boolean purgeAll = false;
    private String url;
}
