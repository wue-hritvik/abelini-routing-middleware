package com.abelini_routing_middleware.controller;

import com.abelini_routing_middleware.CommonService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;


@Log4j2
@Controller
public class CommonController {
    private final CommonService commonService;

    public CommonController(CommonService commonService) {
        this.commonService = commonService;
    }

    @RequestMapping("/**")
    public void proxyRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.info("proxy");
        if (request.getRequestURI().startsWith("/internal/")) {
            log.info("internal path received return error");
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Error while processing request.");
            return;
        }

        String targetUrl = commonService.resolveSeoToQuery(request, response);
        log.info("target url inside proxy ::: {}", targetUrl);
        if (response.getStatus() == HttpServletResponse.SC_MOVED_PERMANENTLY ||
            response.getStatus() == HttpServletResponse.SC_FOUND) {
            log.info("Redirect already handled, returning early.");
            return;
        }

        String host = request.getServerName();
        int port = request.getServerPort();

        String baseUrl = "https://" + host + (port == 80 || port == 443 ? "" : ":" + port);
        //String baseUrl = "https://abelane.com";
        String completeUrl = baseUrl + targetUrl;

        log.info("complete url: " + completeUrl);

        URL url = new URL(completeUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

//        connection.setRequestProperty("X-Robots-Tag", "noindex, nofollow");

        connection.setRequestMethod(request.getMethod());
        connection.setConnectTimeout(7000); // 7 seconds to connect
        connection.setReadTimeout(40000); //40 sec

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if ("Connection".equalsIgnoreCase(headerName) ||
                "Keep-Alive".equalsIgnoreCase(headerName) ||
                "Transfer-Encoding".equalsIgnoreCase(headerName)) {
                continue;
            }
            connection.setRequestProperty(headerName, request.getHeader(headerName));
        }

        if ("POST".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(request.getMethod()) || "PATCH".equalsIgnoreCase(request.getMethod())) {
            connection.setDoOutput(true);
            try (InputStream inputStream = request.getInputStream();
                 OutputStream outputStream = connection.getOutputStream()) {

                byte[] buffer = new byte[16384];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        }

        int responseCode = connection.getResponseCode();
        log.info("status :{}", responseCode);
        response.setStatus(responseCode);

        for (String headerKey : connection.getHeaderFields().keySet()) {
            if (headerKey == null) continue;
            if ("Location".equalsIgnoreCase(headerKey)) {
                log.info("redirect header found");
//                continue;
            }
            if ("Connection".equalsIgnoreCase(headerKey)
                || "Keep-Alive".equalsIgnoreCase(headerKey)
                || "Transfer-Encoding".equalsIgnoreCase(headerKey)) {
                continue;
            }
//            if ("X-Robots-Tag".equalsIgnoreCase(headerKey)) {
//                continue;
//            }
            for (String headerValue : connection.getHeaderFields().get(headerKey)) {
                log.info("Header inside proxy ::: {} ::: {}", headerKey, headerValue);
                response.setHeader(headerKey, headerValue);
            }
        }

        try (InputStream inputStream = responseCode >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();
             ServletOutputStream outputStream = response.getOutputStream()) {
            response.setStatus(responseCode);

            byte[] buffer = new byte[16384];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.flush();
        } catch (IOException e) {
            log.error("Error while proxying the response", e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error while processing request.");
            }
        }
    }

    @RequestMapping("/routing-value/**")
    public ResponseEntity<?> proxyPathRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            log.info("routing value");
            String targetUrl = commonService.resolveSeoToQuery(request, response);
            int status = response.getStatus();
            if (status == HttpServletResponse.SC_MOVED_PERMANENTLY || status == HttpServletResponse.SC_FOUND) {
                log.info("Redirect already handled, returning early.");
                return null;
            }

            String host = request.getServerName();
            int port = request.getServerPort();
            String baseUrl = "https://" + host + ((port == 80 || port == 443) ? "" : ":" + port);
            String completeUrl = baseUrl + targetUrl;

            URI uri = URI.create(completeUrl);
            log.info("full path in routing value ::: {}", uri.getPath());

            String fullPath = uri.getPath().replaceFirst("^/internal", "");

            Map<String, String> queryParams = parseQueryParams(uri.getQuery());

            log.info("all query params in routing value ::: {}", queryParams);

            String queryString = request.getQueryString();
            String hitUrl = (request.getRequestURL() + (queryString != null ? "?" + queryString : "")).replace("/routing-value", "");
            String hitUrlPath = request.getRequestURI().replace("/routing-value", "") + (queryString != null ? "?" + queryString : "");

            String hitKeyword = queryParams.getOrDefault("hitUrlKeyword", "");
            String hitQuery = queryParams.getOrDefault("hitUrlQuery", "");

            log.info("keyword {}", hitKeyword);
            log.info("query {}", hitQuery);

            // Prepare response
            JSONObject json = new JSONObject();
            json.put("url", completeUrl);
            json.put("path", fullPath);
            json.put("hitUrl", hitUrl);
            json.put("hitUrlPath", hitUrlPath);
            json.put("hitUrlKeyword", hitKeyword);
            json.put("hitUrlQuery", hitQuery);
            queryParams.forEach(json::put);

            @SuppressWarnings("unchecked")
            Set<String> keywords = (Set<String>) request.getAttribute("resolved_keywords");
            if (keywords == null || keywords.isEmpty()) {
                json.put("keywords", new JSONArray());
            } else {
                JSONArray keywordArray = new JSONArray();
                for (String keyword : keywords) {
                    if (keyword != null && !keyword.isBlank()) {
                        keywordArray.put(keyword);
                    }
                }
                json.put("keywords", keywordArray);
            }

            return ResponseEntity
                    .ok()
                    .body(json.toString());

        } catch (Exception e) {
            log.error("Error while proxying the response", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error while processing request.");
            return null;
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query != null && !query.isEmpty()) {
            Arrays.stream(query.split("&"))
                    .map(p -> p.split("=", 2))
                    .forEach(pair -> {
                        String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                        String value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                        params.put(key, value);
                    });
        }
        return params;
    }
}

