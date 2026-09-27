package com.deepwater.api;

import com.deepwater.echo.EchoService;
import com.deepwater.echo.EchoService.EchoHome;
import com.deepwater.echo.EchoService.PracticeResult;
import com.deepwater.echo.EchoService.LessonDraft;
import com.deepwater.echo.EchoService.TeacherLesson;
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
    EchoHome startEcho(@Argument String goal, @Argument String languageCode, @Argument String selfReportedLevel,
                       GraphQLContext context) {
        return echo.start(account(context), goal, languageCode, selfReportedLevel);
    }

    @MutationMapping
    EchoHome chooseEchoLanguage(@Argument String languageCode, GraphQLContext context) {
        return echo.chooseLanguage(account(context), languageCode);
    }

    @MutationMapping
    PracticeResult submitEchoPractice(@Argument String exerciseId, @Argument String answer,
                                      @Argument Boolean transferSelfCheck, GraphQLContext context) {
        return echo.practice(account(context), exerciseId, answer, transferSelfCheck);
    }

    @QueryMapping
    List<TeacherLesson> teacherLessons(GraphQLContext context) {
        return echo.teacherLessons(platform.requireTeacher(context.get("authorization")));
    }

    @MutationMapping
    TeacherLesson saveEchoLessonDraft(@Argument LessonDraft input, GraphQLContext context) {
        return echo.saveDraft(platform.requireTeacher(context.get("authorization")), input);
    }

    @MutationMapping
    TeacherLesson publishEchoLesson(@Argument String lessonId, GraphQLContext context) {
        return echo.publish(platform.requireTeacher(context.get("authorization")), lessonId);
    }

    private String account(GraphQLContext context) {
        return platform.requireAccount(context.get("authorization"));
    }
}
