package com.abelini_routing_middleware.controller;

import com.abelini_routing_middleware.CommonService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;


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

        if (response.getStatus() == HttpServletResponse.SC_MOVED_PERMANENTLY ||
                response.getStatus() == HttpServletResponse.SC_FOUND) {
            log.info("Redirect already handled, returning early.");
            return;
        }

        String host = request.getServerName();
        int port = request.getServerPort();

        String baseUrl = "https://" + host + (port == 80 || port == 443 ? "" : ":" + port);
        String completeUrl = baseUrl + targetUrl;

        log.info("complete url: " + completeUrl);

        URL url = new URL(completeUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod(request.getMethod());
        connection.setConnectTimeout(5000); // 5 seconds to connect
        connection.setReadTimeout(30000); //30 sec

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
            if ("Location".equalsIgnoreCase(headerKey)) {
                log.info("redirect header found");
//                continue; // Skip redirection headers if necessary
            }
            for (String headerValue : connection.getHeaderFields().get(headerKey)) {
                response.setHeader(headerKey, headerValue);
            }
        }

        try (InputStream inputStream = connection.getInputStream();
             OutputStream outputStream = response.getOutputStream()) {

            // Set a buffer size for efficient streaming
            byte[] buffer = new byte[16384];
            int bytesRead;

            // Read and write the data in chunks
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            log.error("Error while proxying the response", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error while processing request.");
        }
    }

    @RequestMapping("/routing-value/**")
    public ResponseEntity<?> proxyPathRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            log.info("routing value");

            String targetUrl = commonService.resolveSeoToQuery(request, response);

            if (response.getStatus() == HttpServletResponse.SC_MOVED_PERMANENTLY ||
                    response.getStatus() == HttpServletResponse.SC_FOUND) {
                log.info("Redirect already handled, returning early.");
                return null;
            }

            String host = request.getServerName();
            int port = request.getServerPort();

            String baseUrl = "https://" + host + (port == 80 || port == 443 ? "" : ":" + port);
            String completeUrl = baseUrl + targetUrl;

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("url", completeUrl);
            jsonObject.put("path", targetUrl.replace("/internal", ""));
            return ResponseEntity.ok(jsonObject.toString());
        } catch (Exception e) {
            log.error("Error while proxying the response", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error while processing request.");
            return null;
        }
    }
}
