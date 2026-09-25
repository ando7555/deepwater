package com.deepwater.platform;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.server.WebGraphQlInterceptor;

@Configuration
class GraphQlConfiguration {
    @Bean
    WebGraphQlInterceptor authorizationContext() {
        return (request, chain) -> {
            String authorization = request.getHeaders().getFirst("Authorization");
            if (authorization != null) {
                request.configureExecutionInput((input, builder) -> {
                    builder.graphQLContext(context -> context.put("authorization", authorization));
                    return builder.build();
                });
            }
            return chain.next(request);
        };
    }
}
