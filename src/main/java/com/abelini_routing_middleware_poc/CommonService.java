package com.abelini_routing_middleware_poc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@Service
public class CommonService {

    private final DdSeoUrlRepository ddSeoUrlRepository;

    public CommonService(DdSeoUrlRepository ddSeoUrlRepository) {
        this.ddSeoUrlRepository = ddSeoUrlRepository;
    }

//    private static final Map<String, Map<String, String>> seoMap = new HashMap<>() {{
//        put("halo-rings", Map.of("type", "style", "value", "55"));
//        put("white-gold", Map.of("type", "metal", "value", "3"));
//        put("engagement-rings", Map.of("type", "category", "value", "23"));
//        put("diamond-rings", Map.of("type", "style", "value", "1228"));
//        put("channel-setting-round-full-eternity-diamond-ring-available-in-2.5mm-to-3.5mm-rinw1002", Map.of("type", "product", "value", "1228"));
//        put("gold", Map.of("type", "metal", "value", "92"));
//        put("round", Map.of("type", "shape", "value", "30"));
//    }};

//    @Cacheable(cacheNames = "seoToQuery", key = "#request.requestURL.toString() + ( #request.queryString != null ? '?' + #request.queryString : '' )")
//    public String resolveSeoToQuery(HttpServletRequest request, HttpServletResponse response) {
//        System.out.println("convert seo to url");
//
//        String path = request.getRequestURI();
//        String query = request.getQueryString();
//
//        int storeId = 0;
//        int languageId = 1;
//
//        // Split path by "/" and ignore empty parts
//        String[] parts = Arrays.stream(path.split("/")).filter(p -> !p.isBlank()).toArray(String[]::new);
//
//        String page = "category.html"; // Default
//        Map<String, String> queryParams = new LinkedHashMap<>();
//
//        if (parts.length > 0 && seoMap.containsKey(parts[parts.length - 1])) {
//            Map<String, String> last = seoMap.get(parts[parts.length - 1]);
//            if ("product".equals(last.get("type"))) {
//                page = "product.html";
//                queryParams.put("product_id", last.get("value"));
//            }
//        }
//
//        for (String part : parts) {
//            Map<String, String> data = seoMap.get(part);
//            if (data != null) {
//                switch (data.get("type")) {
//                    case "category":
//                        queryParams.put("category_id", data.get("value"));
//                        break;
//                    case "metal":
//                        queryParams.put("metal_id", data.get("value"));
//                        break;
//                    case "shape":
//                        queryParams.put("shape_id", data.get("value"));
//                        break;
//                    case "style":
//                        queryParams.put("style_id", data.get("value"));
//                        break;
//                    case "product":
//                        queryParams.put("product_id", data.get("value"));
//                        break;
//                    // Add more types as needed
//                }
//            }
//        }
//
//        if (query != null) {
//            for (String pair : query.split("&")) {
//                String[] kv = pair.split("=");
//                if (kv.length == 2 && !queryParams.containsKey(kv[0])) {
//                    queryParams.put(kv[0], kv[1]);
//                }
//            }
//        }
//
//        // Build final query string
//        StringBuilder queryString = new StringBuilder(page);
//        if (!queryParams.isEmpty()) {
//            queryString.append("?");
//            queryParams.forEach((k, v) -> queryString.append(k).append("=").append(v).append("&"));
//            queryString.setLength(queryString.length() - 1); // remove trailing &
//        }
//
//        System.out.println("Final URL: " + queryString);
//        return "/" + queryString;
//    }

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
                    return null; // Return null to avoid further processing
                } catch (Exception e) {
                    e.printStackTrace();
                    return null; // In case of an error, return null
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

                List<DdSeoUrl> pageFind = ddSeoUrlRepository.findByKeywordAndStoreIdAndLanguageId(pathParts.get(0), storeId, languageId);

                if (pathParts.get(0).equals("product") || !pageFind.isEmpty()) {
                    String key = pathParts.get(0).equals("product") ? "product_id" : pageFind.get(0).getKey();

                    switch (key) {
                        case "category_id":
                            page = "category.html";
                            break;
                        case "product_id":
                            page = "product.html";
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

                    List<DdSeoUrl> dataList = ddSeoUrlRepository.findAllByKeywordInAndStoreIdAndLanguageId(pathParts, storeId, languageId);

                    if (queryPart != null && !queryPart.isBlank()) {
                        Set<String> allValueParts = Arrays.stream(queryPart.split("&"))
                                .map(p -> p.split("=", 2))
                                .filter(kv -> kv.length == 2)
                                .flatMap(kv -> Arrays.stream(kv[1].split("_")))
                                .filter(v -> !v.isBlank())
                                .collect(Collectors.toSet());

                        List<DdSeoUrl> valueList = ddSeoUrlRepository.findAllByValueInAndStoreIdAndLanguageId(allValueParts, storeId, languageId);
                        if (!valueList.isEmpty()) {
                            dataList.addAll(valueList);
                        }
                    }

                    for (DdSeoUrl data : dataList) {
                        filterMap.computeIfAbsent(data.getKey(), k -> new ArrayList<>()).add(data.getValue());
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

            String result = "/internal" + (queryString.toString().startsWith("/") ? queryString : "/" + queryString);
            System.out.println("Final URL: " + result);
            return result;
        } catch (Exception e) {
            System.out.println("Error occurred while converting");
            return "/internal/";
        }
    }


    public String handleHtml(String page, HttpServletRequest request, Model model) {
//        String requestUrl = request.getRequestURL().toString();
        String query = request.getQueryString() != null ? "?" + request.getQueryString() : "";
//        log.info("page: " + page);
//        log.info("requestUrl: " + requestUrl);
//        log.info("query: " + query);
//        String fullUrl = requestUrl + query;
//        log.info("full url: " + fullUrl);
//        return "redirect:" + fullUrl;

        if (!query.isBlank()) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    model.addAttribute(kv[0], kv[1]);
                }
            }
        }

        model.asMap().forEach((k, v) -> log.info(k + " = " + v));

        return page.replace(".html", "");
    }
}
