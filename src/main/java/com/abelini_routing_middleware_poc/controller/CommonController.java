package com.abelini_routing_middleware_poc.controller;

import com.abelini_routing_middleware_poc.CommonService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;


@Log4j2
@Controller
public class CommonController {
    private final CommonService commonService;
    private final RestTemplate restTemplate;
    private final WebClient webClient;

    public CommonController(CommonService commonService, RestTemplate restTemplate, WebClient webClient) {
        this.commonService = commonService;
        this.restTemplate = restTemplate;
        this.webClient = webClient;
    }


    //    @ResponseBody
//    @RequestMapping(value = "/internal/**", method = RequestMethod.GET)
//    public Object handleInternalPage(HttpServletRequest request, Model model) {
//        String path = request.getRequestURI();
//        log.info("Internal path caught: " + path);
//
//        if (path.contains(".html")) {
//            return commonService.handleHtml(path.replace("/internal/", ""), request, model);
//        }
//
//        return ResponseEntity.notFound().build();
//    }

    @RequestMapping("/1/**")
    public void proxyRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.info("1 proxy");
        if (request.getRequestURI().startsWith("/internal/")) {
            log.info("internal path received return error");
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Internal path should be handled by Nginx.");
            return;
        }

        String targetUrl = commonService.resolveSeoToQuery(request, response);

        String host = request.getServerName();
        int port = request.getServerPort();

        String baseUrl = "https://" + host + (port == 80 || port == 443 ? "" : ":" + port);
        String completeUrl = baseUrl + targetUrl;
//        String completeUrl = "https://route.whereuelevate.sbs" + targetUrl;
        log.info("complete url: " + completeUrl);

        // Create an HttpURLConnection to the target URL
        URL url = new URL(completeUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Set the same HTTP method as the incoming request
        connection.setRequestMethod(request.getMethod());
        connection.setConnectTimeout(5000); // 5 seconds to connect
        connection.setReadTimeout(30000); //30 sec

        // Copy all incoming request headers to the outgoing request
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

        if ("POST".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(request.getMethod())) {
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

        log.info("headers received :{}", connection.getHeaderFields());
        // Copy the headers from the response to the outgoing response
        for (String headerKey : connection.getHeaderFields().keySet()) {
            if ("Location".equalsIgnoreCase(headerKey)) {
                log.info("redirect header found");
//                continue; // Skip redirection headers if necessary
            }
            for (String headerValue : connection.getHeaderFields().get(headerKey)) {
                response.setHeader(headerKey, headerValue);
            }
        }

        // Stream the response data directly from the connection to the client
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

    @RequestMapping("/2/**")
    public ResponseEntity<byte[]> handleAll2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.info("2 proxy");
        if (request.getRequestURI().startsWith("/internal/")) {
            log.info("internal path received return error");
            return ResponseEntity.status(HttpServletResponse.SC_NOT_FOUND)
                    .body("Internal path should be handled by Nginx.".getBytes());
        }

        String targetUrl = commonService.resolveSeoToQuery(request, response);

        String host = request.getServerName();
        int port = request.getServerPort();

        String baseUrl = "https://" + host + (port == 80 || port == 443 ? "" : ":" + port);
        String completeUrl = baseUrl + targetUrl;
//        String completeUrl = "https://route.whereuelevate.sbs" + targetUrl;
        log.info("complete url: " + completeUrl);

        try {
            // Make the actual HTTP call to App2
            ResponseEntity<byte[]> responseEntity = restTemplate.exchange(completeUrl, HttpMethod.GET,
                    new HttpEntity<>(createHeadersFromRequest(request)), byte[].class);

            int status = responseEntity.getStatusCodeValue();
            log.info("status: " + status);

            // Log headers if necessary
            log.info("response entity header : {}", responseEntity.getHeaders());

            return ResponseEntity.status(status)
                    .headers(responseEntity.getHeaders())
                    .body(responseEntity.getBody());

        } catch (Exception ex) {
            log.error("Error while making internal call to {}", completeUrl, ex);
            return ResponseEntity.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                    .body(("Error while processing request.").getBytes());
        }
    }

    // Helper method to extract headers from the HttpServletRequest
    private HttpHeaders createHeadersFromRequest(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.set(headerName, request.getHeader(headerName));
        }
        return headers;
    }

    @RequestMapping("/**")
    public void handleAll(HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.info("all proxy");
        if (request.getRequestURI().startsWith("/internal/")) {
            log.info("internal path received return error");
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Internal path should be handled by Nginx.");
            return;
        }
        String targetUrl = commonService.resolveSeoToQuery(request, response);
//        RequestDispatcher dispatcher = request.getRequestDispatcher(targetUrl);
//        dispatcher.forward(request, response);
////         response.sendRedirect(targetUrl);

        String host = request.getServerName();  // e.g., "localhost"
        int port = request.getServerPort();  // Get the port (e.g., 9092)

        // Construct the base URL with the port number (only if it's not 80 for HTTP or 443 for HTTPS)
        String baseUrl = "https://" + host + (port == 80 || port == 443 ? "" : ":" + port);

        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.set(headerName, request.getHeader(headerName));
        }
        HttpEntity<?> entity = new HttpEntity<>(headers);


        // Make the actual HTTP call to App2
        String completeUrl = baseUrl + targetUrl;
//        String completeUrl = "https://route.whereuelevate.sbs" + targetUrl;
        log.info("complete url: " + completeUrl);

        try {
            ResponseEntity<byte[]> responseEntity = restTemplate.exchange(completeUrl, HttpMethod.GET, entity, byte[].class);

            int status = responseEntity.getStatusCodeValue();
            log.info("status: " + status);
            // Set status
            response.setStatus(status);
            response.setContentType(responseEntity.getHeaders().getContentType().toString());

            log.info("response entity header : {}", responseEntity.getHeaders());
            responseEntity.getHeaders().forEach((key, values) -> {
                for (String value : values) {
                    if ("Location".equalsIgnoreCase(key)) {
                        log.info("redirect header found");
                        // Skip the Location header (used in redirects)
//                        continue;
                    }
                    // Only set headers that are not related to redirection
                    response.setHeader(key, value);
                }
            });

            // Write body
            byte[] body = responseEntity.getBody();
            log.info("body length ::{}", body != null ? body.length : null);
            response.getOutputStream().write(body != null && body.length > 0 ? body : new byte[0]);
        } catch (Exception ex) {
            log.error("Error while making internal call to {}", completeUrl, ex);
            response.sendError(500, "Error while processing request.");
        }

        response.flushBuffer();
    }

//    @RequestMapping("/**")
//    public Mono<Void> handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
//        String uri = request.getRequestURI();
//        if (uri.startsWith("/internal/")) {
//            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Internal path should be handled by Nginx.");
//            return Mono.empty();
//        }
//
//        // Rewrite to internal URL
//        String targetPath = commonService.resolveSeoToQuery(request, response);
//
//        // Prepare headers
//        HttpHeaders headers = new HttpHeaders();
//        Collections.list(request.getHeaderNames())
//                .forEach(name -> headers.set(name, request.getHeader(name)));
//
//        String completeUrl = "https://route.whereuelevate.sbs" + targetPath;
//        log.info("Complete URL: " + completeUrl);
//
//        // Forward the request asynchronously to App2
//        return forward(completeUrl, headers)
//                .flatMap(proxyResponse -> {
//                    // Forward status code and headers from the proxy response to the client
//                    response.setStatus(proxyResponse.getStatusCodeValue());
//
//                    proxyResponse.getHeaders().forEach((key, values) -> {
//                        for (String value : values) {
//                            response.addHeader(key, value);
//                        }
//                    });
//
//                    byte[] body = proxyResponse.getBody();
//                    if (body != null) {
//                        return Mono.fromRunnable(() -> {
//                            try {
//                                response.getOutputStream().write(body);
//                                response.flushBuffer();// Forward body as-is to client
//                            } catch (IOException e) {
//                                log.error("Error writing response body", e);
//                            }
//                        });
//                    }
//                    return Mono.empty();  // If no body, just complete the response
//                });
//    }
//
//    public Mono<ResponseEntity<byte[]>> forward(String fullPathWithQuery, HttpHeaders incomingHeaders) {
//        return webClient.get()
//                .uri(fullPathWithQuery)
//                .headers(headers -> headers.addAll(incomingHeaders)) // Forward incoming headers
//                .exchangeToMono(this::buildResponseEntity);
//    }
//
//    private Mono<ResponseEntity<byte[]>> buildResponseEntity(ClientResponse response) {
//        return response.bodyToMono(byte[].class)
//                .map(body -> {
//                    HttpHeaders headers = new HttpHeaders();
//                    headers.putAll(response.headers().asHttpHeaders());
//
//                    return ResponseEntity
//                            .status(response.statusCode()) // Forward the same status as App2
//                            .headers(headers)
//                            .body(body);  // Forward the same body as App2
//                });
//    }
}
