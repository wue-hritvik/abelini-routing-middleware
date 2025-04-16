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

import java.io.IOException;
import java.util.Collections;


@Log4j2
@Controller
public class CommonController {
    private final CommonService commonService;
    private final RestTemplate restTemplate;

    public CommonController(CommonService commonService, RestTemplate restTemplate) {
        this.commonService = commonService;
        this.restTemplate = restTemplate;
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


    @RequestMapping("/**")
    public void handleAll(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (request.getRequestURI().startsWith("/internal/")) {
            log.info("internal path received return error");
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Internal path should be handled by Nginx.");
            return;
        }
        String targetUrl = commonService.resolveSeoToQuery(request, response);
//        RequestDispatcher dispatcher = request.getRequestDispatcher(targetUrl);
//        dispatcher.forward(request, response);
////         response.sendRedirect(targetUrl);

        String scheme = request.getScheme();  // e.g., "http" or "https"
        String host = request.getServerName();  // e.g., "localhost"
        int port = request.getServerPort();  // Get the port (e.g., 9092)

        // Construct the base URL with the port number (only if it's not 80 for HTTP or 443 for HTTPS)
        String baseUrl = scheme + "://" + host + (port == 80 || port == 443 ? "" : ":" + port);

        HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames()).forEach(headerName -> {
            headers.set(headerName, request.getHeader(headerName));
        });

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Make the actual HTTP call to App2
        String completeUrl = baseUrl + targetUrl;
//        String completeUrl = "https://route.whereuelevate.sbs" + targetUrl;
        log.info("complete url: " + completeUrl);

        ResponseEntity<byte[]> responseEntity = restTemplate.exchange(completeUrl, HttpMethod.GET, entity, byte[].class);

        // Set status
        response.setStatus(responseEntity.getStatusCodeValue());

        responseEntity.getHeaders().forEach((key, values) -> {
            for (String value : values) {
                if ("Location".equalsIgnoreCase(key)) {
                    // Skip the Location header (used in redirects)
                    continue;
                }
                // Only set headers that are not related to redirection
                response.setHeader(key, value);
            }
        });

        // Write body
        byte[] body = responseEntity.getBody();
        response.getOutputStream().write(body != null ? body : new byte[0]);

        response.flushBuffer();
    }
}
