package com.abelini_routing_middleware;

import com.abelini_routing_middleware.dto.SeoDataRequest;
import com.abelini_routing_middleware.dto.SeoDataResponseDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@Service
public class CommonService {
    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${seo.data.api.url}")
    private String API_URL;

    public CommonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Cacheable(cacheNames = "seoToQuery", key = "#request.requestURL.toString() + ( #request.queryString != null ? '?' + #request.queryString : '' )")
    public String resolveSeoToQuery(HttpServletRequest request, HttpServletResponse response) {
        try {
            System.out.println("convert seo to url");

            String path = request.getRequestURI();

            String queryPart = request.getQueryString();

            if (path.startsWith("/internal")) {
                return path;
            }

            if (path.contains("diamond-rings/classic-solitaire")) {
                String replaceLink = path.replace("diamond-rings/classic-solitaire", "engagement-rings/classic-solitaire");

                // Perform 301 redirect
                try {
                    response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
                    response.setHeader("Location", replaceLink);
                    return "/internal/";
                } catch (Exception e) {
                    e.printStackTrace();
                    return "/internal/";
                }
            }

            // Default storeId and languageId
            int storeId = 0;
            int languageId = 1;

            Map<String, List<String>> filterMap = new HashMap<>();

            List<String> pathParts = Arrays.stream(path.split("/"))
                    .filter(p -> !p.isBlank())
                    .map(part -> part.replace("-lbg", "").replace("-msnt", ""))
                    .collect(Collectors.toList());

            StringBuilder queryString = new StringBuilder();

            if (!pathParts.isEmpty()) {
                String page = "index.html";

                List<SeoDataResponseDTO> pageFind = fetchSeoData(List.of(pathParts.get(0)), storeId, languageId, "keyword");

                if (pathParts.get(0).equals("product") || !pageFind.isEmpty()) {
                    String key = pathParts.get(0).equals("product") ? "product_id" : pageFind.get(0).getKey();

                    switch (key) {
                        case "category_id":
                            page = "engagement-rings/classic-solitaire/diamonds.html";
                            break;
                        case "product_id":
                            page = "product/4-claw-set-round-brilliant-cut-solitaire-diamond-engagement-rings-uk-rine3044.html";
                            break;
                        case "manufacturer_id":
                            page = "manufacturer.html";
                            break;
                        case "information_id":
                            page = "information.html";
                            break;

                        default:
                            break;
                    }

                    List<SeoDataResponseDTO> dataList = fetchSeoData(pathParts, storeId, languageId, "keyword");

                    if (queryPart != null && !queryPart.isBlank()) {
                        List<String> allValueParts = Arrays.stream(queryPart.split("&"))
                                .map(p -> p.split("=", 2))
                                .filter(kv -> kv.length == 2)
                                .flatMap(kv -> Arrays.stream(kv[1].split("_")))
                                .filter(v -> !v.isBlank())
                                .distinct()
                                .collect(Collectors.toList());

                        List<SeoDataResponseDTO> valueList = fetchSeoData(allValueParts, storeId, languageId, "value");
                        if (!valueList.isEmpty()) {
                            dataList.addAll(valueList);
                        }
                    }

                    for (SeoDataResponseDTO data : dataList) {
                        filterMap.computeIfAbsent(data.getKey(), k -> new ArrayList<>()).add(data.getShopifyId());
                    }

                    Map<String, String> queryParams = new LinkedHashMap<>();

                    for (Map.Entry<String, List<String>> entry : filterMap.entrySet()) {
                        if (!entry.getValue().isEmpty()) {
                            String combined = String.join(",", entry.getValue());
                            queryParams.put(entry.getKey(), combined);
                        }
                    }

                    queryString.append(page);
                    if (!queryParams.isEmpty()) {
                        queryString.append("?");
                        queryParams.forEach((k, v) -> queryString.append(k).append("=").append(v).append("&"));
                        queryString.setLength(queryString.length() - 1);
                    }
                } else {
                    queryString.append(path);
                    if (queryPart != null && !queryPart.isBlank()) {
                        queryString.append("?").append(queryPart);
                    }
                }
            } else {
                queryString.append(path);
                if (queryPart != null && !queryPart.isBlank()) {
                    queryString.append("?").append(queryPart);
                }
            }

            return "/internal" + (queryString.toString().startsWith("/") ? queryString : "/" + queryString);
        } catch (Exception e) {
            log.error("Error occurred while converting seo to query");
            return "/internal/";
        }
    }

    public List<SeoDataResponseDTO> fetchSeoData(List<String> pathParts, int storeId, int languageId, String type) {
        try {
            SeoDataRequest requestBody = new SeoDataRequest(pathParts, storeId, languageId, type);
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {
                });
            }
            log.error("Failed to fetch SEO data: " + response.statusCode());
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("exception while fetch SEO data: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
