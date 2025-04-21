package com.abelini_routing_middleware.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeoDataRequest {
    public List<String> pathParts = new ArrayList<>();
    public int storeId = 1;
    public int languageId = 2;
    public String type = "keyword";
}
