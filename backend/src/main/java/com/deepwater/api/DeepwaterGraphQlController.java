package com.deepwater.api;

import com.deepwater.echo.EchoService;
import com.deepwater.echo.EchoService.DiagnosticAnswer;
import com.deepwater.echo.EchoService.EchoHome;
import com.deepwater.echo.EchoService.PracticeResult;
import com.deepwater.platform.PlatformService;
import graphql.GraphQLContext;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class DeepwaterGraphQlController {
    private final EchoService echo;
    private final PlatformService platform;

    public DeepwaterGraphQlController(EchoService echo, PlatformService platform) {
        this.echo = echo; this.platform = platform;
    }

    @QueryMapping
    EchoHome echoHome(GraphQLContext context) {
        return echo.home(platform.accountIfAuthenticated(context.get("authorization")));
    }

    @MutationMapping
    EchoHome startEcho(@Argument String goal, @Argument String selfReportedLevel,
                       @Argument List<DiagnosticAnswer> answers, GraphQLContext context) {
        return echo.start(account(context), goal, selfReportedLevel, answers);
    }

    @MutationMapping
    PracticeResult submitEchoPractice(@Argument String exerciseId, @Argument String answer,
                                      @Argument Boolean transferSelfCheck, GraphQLContext context) {
        return echo.practice(account(context), exerciseId, answer, transferSelfCheck);
    }

    private String account(GraphQLContext context) {
        return platform.requireAccount(context.get("authorization"));
    }
}
