package com.deepwater.platform;

import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Imports all checked-in catalog lessons as private drafts on empty databases.
 * All lesson text lives in the JSON files under echo/catalog/; runtime edits are stored in SQL.
 */
@Configuration
class DemoData {
    @Bean
    ApplicationRunner importStarterDraft(JdbcTemplate db, ObjectMapper json, TransactionTemplate transactions) {
        return args -> {
            var resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
            Resource[] catalogs = resolver.getResources("classpath:echo/catalog/*.json");
            for (Resource resource : catalogs) {
                transactions.executeWithoutResult(status -> {
                    try {
                        LessonSeed lesson = json.readValue(resource.getInputStream().readAllBytes(), LessonSeed.class);
                        Integer exists = db.queryForObject("select count(*) from echo_lessons where id=?", Integer.class, lesson.id());
                        if (exists != null && exists > 0) return;
                        db.update("insert into echo_lessons(id,language_code,title,level,topic,objective,explanation," +
                                  "source_reference,content_status,skill_key,sort_order,review_status,version) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                lesson.id(), lesson.languageCode(), lesson.title(), lesson.level(), lesson.topic(),
                                lesson.objective(), lesson.explanation(), lesson.sourceReference(),
                                "Starter catalog — teacher review required",
                                lesson.skillKey(), nextSortOrder(db, lesson.languageCode()), "DRAFT", 1);
                        int position = 0;
                        for (ExerciseSeed exercise : lesson.exercises()) {
                            String exerciseId = lesson.id() + "-" + (++position);
                            db.update("insert into echo_exercises(id,lesson_id,position,task_type,prompt,expected_answer," +
                                      "feedback_correct,feedback_incorrect) values(?,?,?,?,?,?,?,?)",
                                    exerciseId, lesson.id(), position, exercise.taskType(), exercise.prompt(),
                                    exercise.expectedAnswer(), text(exercise.feedbackCorrect()), text(exercise.feedbackIncorrect()));
                            com.deepwater.echo.EchoService.saveAcceptedAnswers(db, exerciseId, exercise.acceptedAnswers());
                            int optionPosition = 0;
                            for (OptionSeed option : exercise.options() == null ? List.<OptionSeed>of() : exercise.options()) {
                                db.update("insert into echo_exercise_options(exercise_id,option_key,option_text,sort_order) values(?,?,?,?)",
                                        exerciseId, option.key(), option.text(), ++optionPosition);
                            }
                        }
                    } catch (Exception ex) {
                        status.setRollbackOnly();
                        throw new IllegalStateException("Could not import catalog lesson: " + resource.getFilename(), ex);
                    }
                });
            }
        };
    }

    private static int nextSortOrder(JdbcTemplate db, String languageCode) {
        Integer max = db.queryForObject(
            "select coalesce(max(sort_order),0) from echo_lessons where language_code=?",
            Integer.class, languageCode);
        return (max == null ? 0 : max) + 1;
    }

    private static String text(String value) { return value == null ? "" : value; }

    record LessonSeed(String id, String languageCode, String title, String level, String topic,
                      String objective, String explanation, String sourceReference, String skillKey,
                      List<ExerciseSeed> exercises) {}
    record ExerciseSeed(String taskType, String prompt, String expectedAnswer, List<String> acceptedAnswers, String feedbackCorrect,
                        String feedbackIncorrect, List<OptionSeed> options) {}
    record OptionSeed(String key, String text) {}
}
