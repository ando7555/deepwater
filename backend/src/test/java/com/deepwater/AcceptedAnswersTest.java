package com.deepwater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepwater.echo.EchoService;
import com.deepwater.echo.EchoService.ExerciseDraft;
import com.deepwater.echo.EchoService.LessonDraft;
import com.deepwater.platform.PlatformService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:accepted-answers;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
class AcceptedAnswersTest {
    @Autowired EchoService echo;
    @Autowired PlatformService platform;

    @Test
    void typedExerciseAcceptsTeacherListedAnswersAndHintsOnNearMisses() {
        String teacher = account("teacher");
        String learner = account("learner");
        var text = new ExerciseDraft("TEXT", "Translate: Today I work.", "Heute arbeite ich",
                List.of("Ich arbeite heute"), "Correct.", "Look at the verb position.", null);
        var lesson = echo.saveDraft(teacher, draft(text));
        assertThat(lesson.exercises().getFirst().acceptedAnswers()).containsExactly("Ich arbeite heute");
        echo.publish(teacher, lesson.id());
        String exerciseId = lesson.id() + "-1";

        assertThat(echo.practice(learner, exerciseId, "heute arbeite ich.", null).correct()).isTrue();
        assertThat(echo.practice(learner, exerciseId, "Ich arbeite heute!", null).correct()).isTrue();

        var order = echo.practice(learner, exerciseId, "Heute ich arbeite", null);
        assertThat(order.correct()).isFalse();
        assertThat(order.hint()).contains("word order");
        assertThat(echo.practice(learner, exerciseId, "Heute arbiete ich", null).hint()).isNull();
        assertThat(echo.practice(learner, exerciseId, "Heute arbeit ich", null).hint()).contains("spelling");
        assertThat(echo.practice(learner, exerciseId, "Something else entirely", null).hint()).isNull();
    }

    @Test
    void specialLetterSlipGetsItsOwnHint() {
        String teacher = account("teacher");
        var text = new ExerciseDraft("TEXT", "Danish for 'I go'", "jeg går", null, "Correct.", "Try again.", null);
        var lesson = echo.saveDraft(teacher, draft(text));
        echo.publish(teacher, lesson.id());
        var result = echo.practice(account("learner"), lesson.id() + "-1", "jeg gar", null);
        assertThat(result.correct()).isFalse();
        assertThat(result.hint()).contains("special letters");
    }

    @Test
    void acceptedAnswersAreOnlyForTypedExercises() {
        var select = new ExerciseDraft("SELECT", "Pick one", "A", List.of("B"), "", "",
                List.of(new EchoService.ExerciseOption("A", "x"), new EchoService.ExerciseOption("B", "y")));
        assertThatThrownBy(() -> echo.saveDraft(account("teacher"), draft(select)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("accepted answers");
    }

    private String account(String name) {
        return platform.register(name, name + "-" + UUID.randomUUID() + "@example.test", "a-long-password-1").get("accountId");
    }

    private LessonDraft draft(ExerciseDraft exercise) {
        return new LessonDraft(null, "de", "Accepted answers " + UUID.randomUUID(), "A1", "Word order",
                "Place the verb second.", "The finite verb is second.", "Test fixture", "test.accepted-answers",
                null, List.of(exercise), null);
    }
}
