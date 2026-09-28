package com.deepwater.api;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
class GraphQlErrorHandler {
    @GraphQlExceptionHandler
    GraphQLError responseStatus(ResponseStatusException exception) {
        return GraphqlErrorBuilder.newError()
                .message(exception.getReason() == null ? "Request could not be completed." : exception.getReason())
                .build();
    }
}
