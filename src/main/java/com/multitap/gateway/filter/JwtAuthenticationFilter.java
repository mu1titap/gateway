package com.multitap.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multitap.gateway.auth.JwtProvider;
import com.multitap.gateway.common.ApiResponse;
import com.multitap.gateway.common.exception.BaseResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        super(Config.class);
        this.jwtProvider = jwtProvider;
    }

    public static class Config {

    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            // Swagger 경로에 대해 필터를 적용하지 않음
            if (path.contains("/swagger-ui") || path.contains("/v3/api-docs")) {
                return chain.filter(exchange); // JWT 검증 없이 다음 필터로 전달
            }

            String authorizationHeader = request.getHeaders().getFirst("Authorization");
            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                return handleException(exchange, BaseResponseStatus.NO_JWT_TOKEN);
            }

            String token = authorizationHeader.replace("Bearer ", "");
            if (!jwtProvider.validateToken(token)) {
                return handleException(exchange, BaseResponseStatus.TOKEN_NOT_VALID);
            }

            return chain.filter(exchange);
        };
    }

    private Mono<Void> handleException(ServerWebExchange exchange, BaseResponseStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<String> apiResponse = new ApiResponse<>(HttpStatus.UNAUTHORIZED.value(), status.getCode(), status.getMessage());

        ObjectMapper objectMapper = new ObjectMapper();
        byte[] data;
        try {
            data = objectMapper.writeValueAsBytes(apiResponse);
        } catch (JsonProcessingException e) {
            data = new byte[0];
        }

        DataBuffer buffer = response.bufferFactory().wrap(data);
        return response.writeWith(Mono.just(buffer)).then(Mono.empty());
    }

}