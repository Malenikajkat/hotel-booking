package org.example.apigateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    private final JwtAuthenticationGatewayFilterFactory jwtFilter;

    public GatewayConfig(JwtAuthenticationGatewayFilterFactory jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("hotel-management-service", r -> r
                        .path("/api/hotels/**")
                        .filters(f -> f
                                .rewritePath("/api/hotels/(?<segment>.*)", "/api/hotels/${segment}")
                                .filter(jwtFilter.apply(new JwtAuthenticationGatewayFilterFactory.Config()))
                        )
                        .uri("lb://hotel-management-service"))
                .route("hotel-management-service-rooms", r -> r
                        .path("/api/rooms", "/api/rooms/**",
                                "/api/rooms/recommend", "/api/rooms/search",
                                "/api/rooms/{id}", "/api/rooms/hotel/{hotelId}",
                                "/api/rooms/hotel/{hotelId}/available",
                                "/api/rooms/hotel/{hotelId}/available/**")
                        .filters(f -> f
                                .rewritePath("/api/rooms/(?<segment>.*)", "/api/rooms/${segment}")
                                .filter(jwtFilter.apply(new JwtAuthenticationGatewayFilterFactory.Config()))
                        )
                        .uri("lb://hotel-management-service"))
                .route("booking-service", r -> r
                        .path("/api/bookings/**")
                        .filters(f -> f
                                .rewritePath("/api/bookings/(?<segment>.*)", "/api/bookings/${segment}")
                                .filter(jwtFilter.apply(new JwtAuthenticationGatewayFilterFactory.Config()))
                        )
                        .uri("lb://booking-service"))
                .route("booking-service-user", r -> r
                        .path("/api/user/**")
                        .filters(f -> f
                                .rewritePath("/api/user/(?<segment>.*)", "/api/${segment}")
                                .filter(jwtFilter.apply(new JwtAuthenticationGatewayFilterFactory.Config()))
                        )
                        .uri("lb://booking-service"))
                .route("booking-service-booking", r -> r
                        .path("/api/booking/**")
                        .filters(f -> f
                                .rewritePath("/api/booking/(?<segment>.*)", "/api/${segment}")
                                .filter(jwtFilter.apply(new JwtAuthenticationGatewayFilterFactory.Config()))
                        )
                        .uri("lb://booking-service"))
                .build();
    }
}
