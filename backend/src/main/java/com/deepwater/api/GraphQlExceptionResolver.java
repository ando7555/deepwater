package com.deepwater.api;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

@Component
class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {
    @Override
    protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
        String message = exception.getMessage() == null ? "Request could not be completed." : exception.getMessage();
        return GraphqlErrorBuilder.newError(environment)
                .errorType(ErrorType.DataFetchingException)
                .message(message)
                .build();
    }
}
