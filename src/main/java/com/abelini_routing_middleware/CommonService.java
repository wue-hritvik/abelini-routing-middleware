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

import java.io.IOException;
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
    @Value("${page.article}")
    private String pageArticle;
    @Value("${page.author}")
    private String pageAuthor;
    @Value("${page.blog.category}")
    private String pageBlogCategory;
    @Value("${page.category}")
    private String pageCategory;
    @Value("${page.customer.story}")
    private String pageCustomerStory;
    @Value("${page.information}")
    private String pageInformation;
    @Value("${page.product.feed}")
    private String pageProductFeed;
    @Value("${page.product}")
    private String pageProduct;
    @Value("${page.static}")
    private String pageStatic;
    @Value("${page.default}")
    private String pageDefault;

    public CommonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Cacheable(cacheNames = "seoToQuery", key = "#request.requestURL.toString() + ( #request.queryString != null ? '?' + #request.queryString : '' )")
    public String resolveSeoToQuery(HttpServletRequest request, HttpServletResponse response) {
        try {
            log.info("convert seo to url");

            String path = request.getRequestURI();

            if (path.startsWith("/internal")) {
                return path;
            }

            if (path.contains("diamond-rings/classic-solitaire")) {
                String replaceLink = path.replace("diamond-rings/classic-solitaire", "engagement-rings/classic-solitaire");

                try {
                    response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
                    response.setHeader("Location", replaceLink);
                    response.flushBuffer(); // Important: make sure the response is sent
                    return null; // return here to STOP further processing
                } catch (Exception e) {
                    e.printStackTrace();
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Redirection failed");
                    return null;
                }
            }


            String fragment = null;
            String queryPart = request.getQueryString();

            String fullUrl = request.getRequestURL().toString() + (queryPart != null ? "?" + queryPart : "");
            if (fullUrl.contains("#")) {
                fragment = fullUrl.substring(fullUrl.indexOf("#"));
                fullUrl = fullUrl.substring(0, fullUrl.indexOf("#"));
                path = new URI(fullUrl).getPath();
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
                String page;

                List<SeoDataResponseDTO> pageFind = fetchSeoData(List.of(pathParts.get(0)), storeId, languageId, "keyword");

                if (pathParts.get(0).equals("product") || !pageFind.isEmpty()) {
                    String key = pathParts.get(0).equals("product") ? "product_id" : pageFind.get(0).getKey();

                    page = switch (key) {
                        case "category_id" -> pageCategory;
                        case "product_id" -> pageProduct;
                        case "article_id" -> pageArticle;
                        case "information_id" -> pageInformation;
                        case "author_id" -> pageAuthor;
                        case "blog_category_id" -> pageBlogCategory;
                        case "customer_story_id" -> pageCustomerStory;
                        case "product_feed_id" -> pageProductFeed;
                        case "static_page_id" -> pageStatic;
                        default -> pageDefault;
                    };

                    List<SeoDataResponseDTO> dataList = fetchSeoData(pathParts, storeId, languageId, "keyword");

                    //condition: bread crumbs
                    if (pathParts.size() >= 2) {
                        List<String> bcParts = pathParts.subList(1, Math.min(3, pathParts.size()));
                        List<SeoDataResponseDTO> breadcrumbSeoData = fetchSeoData(bcParts, storeId, languageId, "keyword");
                        List<String> bcShopifyIds = breadcrumbSeoData.stream()
                                .map(SeoDataResponseDTO::getShopifyId)
                                .filter(Objects::nonNull)
                                .toList();
                        if (!bcShopifyIds.isEmpty()) {
                            filterMap.put("filter_breadcrumb", new ArrayList<>(bcShopifyIds));
                        }
                    }

                    // Query param parsing
                    Map<String, String> rawQueryParams = new HashMap<>();
                    if (queryPart != null && !queryPart.isBlank()) {
                        for (String param : queryPart.split("&")) {
                            String[] kv = param.split("=", 2);
                            if (kv.length == 2) {
                                rawQueryParams.put(kv[0], kv[1]);
                            }
                        }

                        // Condition Handle sort => sort_by
                        if (rawQueryParams.containsKey("sort")) {
                            String sortValue = rawQueryParams.remove("sort");
                            String orderValue = rawQueryParams.remove("order");
                            String convertedSort = fetchSortValue(sortValue, orderValue);
                            if (convertedSort != null) {
                                rawQueryParams.put("sort_by", convertedSort);
                            }
                        }

                        // Get all filter param values
                        List<String> allValueParts = rawQueryParams.entrySet().stream()
                                .filter(e -> e.getKey().equals("filter_param"))
                                .flatMap(e -> Arrays.stream(e.getValue().split("_")))
                                .filter(v -> !v.isBlank())
                                .distinct()
                                .toList();

                        rawQueryParams.remove("filter_param");

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

                    //condition: all others param
                    for (Map.Entry<String, String> entry : rawQueryParams.entrySet()) {
                        if (!queryParams.containsKey(entry.getKey())) {
                            queryParams.put(entry.getKey(), entry.getValue());
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

            //condition: add fragment if any
            if (fragment != null) {
                queryString.append(fragment);
            }

            return "/internal" + (queryString.toString().startsWith("/") ? queryString : "/" + queryString);
        } catch (Exception e) {
            log.error("Error occurred while converting seo to query");
            return "/internal/";
        }
    }

    private String fetchSortValue(String sortValue, String orderValue) {
        if (sortValue == null) return null;

        sortValue = sortValue.trim().toLowerCase();
        orderValue = orderValue != null ? orderValue.trim().toUpperCase() : "";

        switch (sortValue) {
            case "p.sold":
                if ("DESC".equals(orderValue)) return "BEST_SELLING";
                break;

            case "p.price":
                if ("ASC".equals(orderValue)) return "PRICE_ASC";
                if ("DESC".equals(orderValue)) return "PRICE_DESC";
                break;

            case "p.product_id":
                if ("DESC".equals(orderValue)) return "CREATED_DESC";
                break;

            case "manual":
                return "MANUAL";

            case "created":
                return "CREATED";

            case "alpha":
                if ("ASC".equals(orderValue)) return "ALPHA_ASC";
                if ("DESC".equals(orderValue)) return "ALPHA_DESC";
                break;
        }

        return null;
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
