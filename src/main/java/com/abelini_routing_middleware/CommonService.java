package com.abelini_routing_middleware;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@Service
public class CommonService {

    private final DdSeoUrlRepository ddSeoUrlRepository;

    public CommonService(DdSeoUrlRepository ddSeoUrlRepository) {
        this.ddSeoUrlRepository = ddSeoUrlRepository;
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
}
