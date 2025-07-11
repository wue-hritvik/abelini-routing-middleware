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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Log4j2
@Service
public class CommonService {
    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${seo.data.api.url}")
    private String API_URL;
    @Value("${abelini_jwt_token}")
    public String jwtTokenAbelini;

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
    @Value("${page.customer.story.key}")
    private String pageCustomerStoryKey;
    @Value("${page.information}")
    private String pageInformation;
    @Value("${page.product}")
    private String pageProduct;
    @Value("${page.static}")
    private String pageStatic;
    @Value("${page.default}")
    private String pageDefault;
    @Value("${page.address.form}")
    private String pageAddressForm;
    @Value("${page.order.info}")
    private String pageOrderInfo;
    @Value("${page.complete.review}")
    private String pageCompleteReview;
    @Value("${page.diamond.details}")
    private String pageDiamondDetails;
    @Value("${page.search}")
    private String pageSearch;

    private static final Map<String, String> SEO_PATH_TO_INTERNAL_URL = Map.ofEntries(
            // Map.entry("/engagement-rings/view-all", "/internal/information-article.html?static_page_id=156697461076"),
            // Map.entry("/diamond-rings/eternity-rings/view-all", "/internal/information-article.html?static_page_id=156702015828"),
            // Map.entry("/diamond-rings", "/internal/information-article.html?static_page_id=156698673492"),
            // Map.entry("/earrings/view-all", "/internal/information-article.html?static_page_id=156717547860"),
            // Map.entry("/pendants", "/internal/information-article.html?static_page_id=156693299540"),

            Map.entry("/engagement-rings/view-all", "/internal/information-article.php?static_page_id=156697461076"),
            Map.entry("/diamond-rings/eternity-rings/view-all", "/internal/information-article.php?static_page_id=156702015828"),
            Map.entry("/diamond-rings", "/internal/information-article.php?static_page_id=156698673492"),
            Map.entry("/earrings/view-all", "/internal/information-article.php?static_page_id=156717547860"),
            Map.entry("/pendants", "/internal/information-article.php?static_page_id=156693299540"),

            Map.entry("/login", "/internal/login.php"),
            Map.entry("/not-found", "/internal/not-found.php"),
            Map.entry("/start-with-setting", "/internal/start-with-setting.php"),
            Map.entry("/account/order-list", "/internal/account/order-list.php"),
            Map.entry("/checkout/cart", "/internal/checkout/cart.php"),
            Map.entry("/checkout/wishlist", "/internal/checkout/wishlist.php"),
            Map.entry("/choose-diamond", "/internal/choose-diamond.php"),
            Map.entry("/account/address-list", "/internal/account/address-list.php"),

            Map.entry("/product/bespoke", "/internal/product/bespoke.php"),

            Map.entry("/sitemap/engagement-rings", "/internal/sitemap/sitemap_list.php?category_id=1"),
            Map.entry("/sitemap/diamond-rings", "/internal/sitemap/sitemap_list.php?category_id=2"),
            Map.entry("/sitemap/wedding-rings", "/internal/sitemap/sitemap_list.php?category_id=3"),
            Map.entry("/sitemap/earrings", "/internal/sitemap/sitemap_list.php?category_id=4"),
            Map.entry("/sitemap/pendants", "/internal/sitemap/sitemap_list.php?category_id=5"),
            Map.entry("/sitemap/bracelets", "/internal/sitemap/sitemap_list.php?category_id=6"),
            Map.entry("/sitemap", "/internal/sitemap/sitemap.php"),

            Map.entry("/account", "/internal/account.php")
    );

    public CommonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    //todo enable cashing
    //@Cacheable(cacheNames = "seoToQuery", key = "#request.requestURL.toString() + ( #request.queryString != null ? '?' + #request.queryString : '' )")
    public String resolveSeoToQuery(HttpServletRequest request, HttpServletResponse response) {
        try {
            log.info("convert seo to url {}", request.getRequestURI());

            String path = request.getRequestURI().replace("/routing-value", "");
            String queryPart = request.getQueryString();
            if (!request.getRequestURI().startsWith("/routing-value")) {
                log.info("inside startswith");

                if (queryPart == null || queryPart.isEmpty()) {
                    queryPart = "hitUrlKeyword=" + request.getRequestURI();
                } else {
                    queryPart += "&hitUrlKeyword=" + request.getRequestURI();
                }
            }

            if (path.startsWith("/internal")) {
                if (queryPart != null && !queryPart.isEmpty()) {
                    if (path.contains("?")) {
                        path += "&" + queryPart;
                    } else {
                        path += "?" + queryPart;
                    }
                }
                return path;
            }
            log.info("hitUrlKeyword inside resolveSeoToQuery ::: {}", request.getRequestURI());
            String hitUrl = (request.getRequestURL() + (queryPart != null ? "?" + queryPart : "")).replace("/routing-value", "");
            String hitUrlPathFull = request.getRequestURI().replace("/routing-value", "") + (queryPart != null ? "?" + queryPart : "");
            response.setHeader("hitUrl", hitUrl);
            response.setHeader("hitUrlPath", request.getRequestURI().replace("/routing-value", ""));
            response.setHeader("hitUrlPathFull", hitUrlPathFull);

            String mappedUrl = SEO_PATH_TO_INTERNAL_URL.get(path);
            if (mappedUrl != null) {
                if (queryPart != null && !queryPart.isEmpty()) {
                    if (mappedUrl.contains("?")) {
                        mappedUrl += "&" + queryPart;
                    } else {
                        mappedUrl += "?" + queryPart;
                    }
                }
                return mappedUrl;
            }

            String fullUrl = request.getRequestURL().toString() + (queryPart != null ? "?" + queryPart : "");

            if (path.contains("diamond-rings/classic-solitaire")) {
                String replaceLink = path.replace("diamond-rings/classic-solitaire", "engagement-rings/classic-solitaire");

                try {
                    response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
                    response.setHeader("Location", replaceLink);
                    response.flushBuffer(); // Important: make sure the response is sent
                    return null; // return here to STOP further processing
                } catch (Exception e) {
                    log.error("Error while redirect : {}", e.getMessage(), e);
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Redirection failed");
                    return null;
                }
            }

            String fragment = null;

            if (fullUrl.contains("#")) {
                fragment = fullUrl.substring(fullUrl.indexOf("#"));
                fullUrl = fullUrl.substring(0, fullUrl.indexOf("#"));
                path = new URI(fullUrl).getPath();
            }

            // Default storeId and languageId
            int storeId = 0;
            int languageId = 1;

            Map<String, List<String>> filterMap = new HashMap<>();

            AtomicReference<String> stoneType = new AtomicReference<>(null);

            List<String> pathParts = Arrays.stream(path.split("/"))
                    .filter(p -> !p.isBlank())
                    .map(part -> {
                        if (part.contains("-lbg")) {
                            stoneType.set("lbg");
                            return part.replace("-lbg", "");
                        } else if (part.contains("-msnt")) {
                            stoneType.set("msnt");
                            return part.replace("-msnt", "");
                        }
                        return part;
                    })
                    .collect(Collectors.toList());

            StringBuilder queryString = new StringBuilder();

            if (!pathParts.isEmpty()) {
                String page = pageDefault;

                List<SeoDataResponseDTO> pageFind = fetchSeoData(List.of(pathParts.get(0)), storeId, languageId, "keyword");

                if (pathParts.get(0).equals("product")
                    || pathParts.get(0).equals("blog")
                    || pathParts.get(0).equals("customer-story")
                    || pathParts.get(0).equals("authors")
                    || pathParts.get(0).equals("account")
                    || pathParts.get(0).equals("complete-review")
                    || pathParts.get(0).equals("diamond-details")
                    || pathParts.get(0).equals("search")
                    || !pageFind.isEmpty()) {
                    String key = "";

                    switch (pathParts.get(0)) {
                        case "product" -> {
                            key = "product_id";
                            pathParts.remove(0);
                        }
                        case "blog" -> {
                            key = "blog";
                            pathParts.remove(0);
                        }
                        case "customer-story" -> {
                            key = "customer_story";
                            pathParts.remove(0);
                        }
                        case "authors" -> {
                            key = "author_id";
                            pathParts.remove(0);
                        }
                        case "account" -> {
                            pathParts.remove("account");
                            String subPath = pathParts.get(0);
                            if (subPath != null) {
                                switch (subPath) {
                                    case "address-form" -> key = "address_id";
                                    case "order-info" -> key = "order_id";
                                }
                            }
                        }
                        case "complete-review" -> key = "complete-review_id";
                        case "diamond-details" -> key = "diamond-details_id";
                        case "search" -> {
                            pathParts.remove(0);
                            key = "search_id";
                            if (!pathParts.isEmpty()) {
                                String searchQuery = path.replace("/search/", "");
                                if (queryPart == null || queryPart.isEmpty()) {
                                    queryPart = "q=" +searchQuery;
                                } else {
                                    queryPart += "&q=" + searchQuery;
                                }
                            }
                        }
                        default -> key = pageFind.get(0).getKey();
                    }

                    List<SeoDataResponseDTO> dataList = fetchSeoData(pathParts, storeId, languageId, "keyword");

                    if (("blog".equals(key) || "customer_story".equals(key)) && !dataList.isEmpty()) {
                        key = dataList.get(0).getKey();
                    }

                    page = switch (key) {
                        case "category_id" -> pageCategory;
                        case "product_id" -> pageProduct;
                        case "article_id" -> pageArticle;
                        case "information_id" -> pageInformation;
                        case "author_id" -> pageAuthor;
                        case "blog_category_id", "blog" -> pageBlogCategory;
                        case "customer_story_id" -> pageCustomerStoryKey;
                        case "customer_story" -> pageCustomerStory;
                        case "static_page_id" -> pageStatic;
                        case "address_id" -> pageAddressForm;
                        case "order_id" -> pageOrderInfo;
                        case "complete-review_id" -> pageCompleteReview;
                        case "diamond-details_id" -> pageDiamondDetails;
                        case "search_id" -> pageSearch;
                        default -> pageDefault;
                    };

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


                        if (rawQueryParams.containsKey("filter_param")) {
                            List<String> tempArr = new ArrayList<>();
                            tempArr.add(rawQueryParams.get("filter_param"));
                            filterMap.put("filter_param", tempArr);
                        }

                        rawQueryParams.remove("filter_param");

                        List<SeoDataResponseDTO> valueList = fetchSeoData(allValueParts, storeId, languageId, "value");
                        if (!valueList.isEmpty()) {
                            dataList.addAll(valueList);
                        }
                    }

                    Set<String> keywords = dataList.stream()
                            .map(SeoDataResponseDTO::getKeyword)
                            .filter(Objects::nonNull)
                            .filter(k -> !k.isBlank())
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    request.setAttribute("resolved_keywords", keywords);
                    response.setHeader("X-Keywords", String.join(",", keywords));

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
                        String entryKey = entry.getKey();
                        String newValue = entry.getValue();

                        if (queryParams.containsKey(entryKey)) {
                            Set<String> values = new LinkedHashSet<>(Arrays.asList(queryParams.get(entryKey).split(",")));
                            values.addAll(Arrays.asList(newValue.split(",")));
                            queryParams.put(entryKey, String.join(",", values));
                        } else {
                            queryParams.put(entryKey, newValue);
                        }
                    }

                    // Step 3: Merge stoneType into queryParams if not null
                    if (stoneType.get() != null && !stoneType.get().isBlank()) {
                        String stoneKey = "stone_type";
                        String stoneValue = stoneType.get();

                        if (queryParams.containsKey(stoneKey)) {
                            Set<String> values = new LinkedHashSet<>(Arrays.asList(queryParams.get(stoneKey).split(",")));
                            values.add(stoneValue); // ensure uniqueness
                            queryParams.put(stoneKey, String.join(",", values));
                        } else {
                            queryParams.put(stoneKey, stoneValue);
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

//    private String fetchRedirectUrl(String path) {
//        //todo
//        return null;
//    }

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

            case "p.sort_order":
                if ("ASC".equals(orderValue)) return "MANUAL";
                break;
//
//            case "created":
//                return "CREATED";
//                break;
//
//            case "alpha":
//                if ("ASC".equals(orderValue)) return "ALPHA_ASC";
//                if ("DESC".equals(orderValue)) return "ALPHA_DESC";
//                break;
            default:
                return null;
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
                    .header("Authorization", jwtTokenAbelini)
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

