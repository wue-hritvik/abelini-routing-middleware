package com.abelini_routing_middleware;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;


@SpringBootApplication
@EnableCaching
public class AbeliniRoutingMiddlewareApplication {

    public static void main(String[] args) {
        SpringApplication.run(AbeliniRoutingMiddlewareApplication.class, args);
    }

}

//http://localhost:8080/engagement-rings/halo-rings/white-gold
//http://localhost:8080/product/channel-setting-round-full-eternity-diamond-ring-available-in-2.5mm-to-3.5mm-rinw1002

//http://localhost:8080/engagement-rings/classic-solitaire/yellow-gold?filter_param=1.6_3.177_4.50
//Final URL: /internal/category.html?category_id=1&filter_id=5.58,1.5,4.50,3.177,1.6