package com.deepwater.api;

import com.deepwater.echo.EchoCardService;
import com.deepwater.echo.EchoCardService.EchoCard;
import com.deepwater.echo.EchoService;
import com.deepwater.echo.EchoService.EchoHome;
import com.deepwater.echo.EchoService.AudioPracticeResult;
import com.deepwater.echo.EchoService.PracticeResult;
import com.deepwater.echo.EchoService.LessonDraft;
import com.deepwater.echo.EchoService.TeacherLesson;
import com.deepwater.platform.PersonProfileService;
import com.deepwater.platform.PersonProfileService.PersonProfile;
import com.deepwater.platform.PersonProfileService.PersonProfileInput;
import com.deepwater.platform.PlatformService;
import graphql.GraphQLContext;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class DeepwaterGraphQlController {
    private final EchoService echo;
    private final EchoCardService cards;
    private final PlatformService platform;
    private final PersonProfileService profiles;

    public DeepwaterGraphQlController(EchoService echo, EchoCardService cards, PlatformService platform,
                                      PersonProfileService profiles) {
        this.echo = echo; this.cards = cards; this.platform = platform; this.profiles = profiles;
    }

    @QueryMapping
    PersonProfile myProfile(GraphQLContext context) {
        return profiles.profile(account(context));
    }

    @MutationMapping
    PersonProfile updateMyProfile(@Argument PersonProfileInput input, GraphQLContext context) {
        return profiles.update(account(context), input);
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

    @MutationMapping
    AudioPracticeResult submitEchoAudioEvidence(@Argument String lessonId, @Argument String taskType,
            @Argument String answer, @Argument Boolean transcriptUsed, @Argument Integer confidence,
            GraphQLContext context) {
        return echo.audioEvidence(account(context), lessonId, taskType, answer,
                Boolean.TRUE.equals(transcriptUsed), confidence == null ? 0 : confidence);
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

    @MutationMapping
    TeacherLesson unpublishEchoLesson(@Argument String lessonId, GraphQLContext context) {
        return echo.unpublish(platform.requireTeacher(context.get("authorization")), lessonId);
    }

    @QueryMapping
    List<EchoCard> echoCards(GraphQLContext context) {
        return cards.cards(account(context));
    }

    @MutationMapping
    EchoCard saveEchoCard(@Argument String front, @Argument String back, @Argument String lessonId, GraphQLContext context) {
        return cards.save(account(context), front, back, lessonId);
    }

    @MutationMapping
    List<EchoCard> saveEchoCards(@Argument List<EchoCardService.CardInput> cards, GraphQLContext context) {
        return this.cards.saveAll(account(context), cards);
    }

    @MutationMapping
    EchoCard reviewEchoCard(@Argument String cardId, @Argument Boolean knewIt, @Argument EchoCardService.Grade grade,
                            @Argument String answer, GraphQLContext context) {
        if (grade == null && knewIt == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say how the review went.");
        EchoCardService.Grade resolved = grade != null ? grade : Boolean.TRUE.equals(knewIt) ? EchoCardService.Grade.KNEW : EchoCardService.Grade.MISSED;
        return cards.review(account(context), cardId, resolved, answer);
    }

    @MutationMapping
    boolean deleteEchoCard(@Argument String cardId, GraphQLContext context) {
        return cards.delete(account(context), cardId);
    }

    private String account(GraphQLContext context) {
        return platform.requireAccount(context.get("authorization"));
    }
}
