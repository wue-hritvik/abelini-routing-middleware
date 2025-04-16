package com.abelini_routing_middleware_poc.controller;

import com.abelini_routing_middleware_poc.CommonService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;


@Log4j2
@Controller
public class CommonController {
    private final CommonService commonService;

    public CommonController(CommonService commonService) {
        this.commonService = commonService;
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
        String targetUrl = commonService.resolveSeoToQuery(request, response);
        RequestDispatcher dispatcher = request.getRequestDispatcher(targetUrl);
        dispatcher.forward(request, response);
//         response.sendRedirect(targetUrl);
    }
}
