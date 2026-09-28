package com.deepwater.platform;

import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Imports the checked-in Danish starter package as a private draft on empty databases.
 * All lesson text lives in the JSON package; runtime edits are stored in SQL.
 */
@Configuration
class DemoData {
    @Bean
    ApplicationRunner importStarterDraft(JdbcTemplate db, ObjectMapper json, TransactionTemplate transactions) {
        return args -> transactions.executeWithoutResult(status -> {
            String lessonId = "v2-time-first";
            Integer exists = db.queryForObject("select count(*) from echo_lessons where id=?", Integer.class, lessonId);
            if (exists != null && exists > 0) return;
            try {
                var resource = new ClassPathResource("echo/catalog/v2-time-first.da.json");
                LessonSeed lesson = json.readValue(resource.getInputStream().readAllBytes(), LessonSeed.class);
                db.update("insert into echo_lessons(id,language_code,title,level,topic,objective,explanation,source_reference,content_status,skill_key,sort_order,review_status,version) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        lesson.id(), lesson.languageCode(), lesson.title(), lesson.level(), lesson.topic(), lesson.objective(),
                        lesson.explanation(), lesson.sourceReference(), "Starter catalog — teacher review required",
                        lesson.skillKey(), 1, "DRAFT", 1);
                int position = 0;
                for (ExerciseSeed exercise : lesson.exercises()) {
                    String exerciseId = lessonId + "-" + (++position);
                    db.update("insert into echo_exercises(id,lesson_id,position,task_type,prompt,expected_answer,feedback_correct,feedback_incorrect) values(?,?,?,?,?,?,?,?)",
                            exerciseId, lessonId, position, exercise.taskType(), exercise.prompt(), exercise.expectedAnswer(),
                            text(exercise.feedbackCorrect()), text(exercise.feedbackIncorrect()));
                    int optionPosition = 0;
                    for (OptionSeed option : exercise.options() == null ? List.<OptionSeed>of() : exercise.options()) {
                        db.update("insert into echo_exercise_options(exercise_id,option_key,option_text,sort_order) values(?,?,?,?)",
                                exerciseId, option.key(), option.text(), ++optionPosition);
                    }
                }
            } catch (Exception ex) {
                status.setRollbackOnly();
                throw new IllegalStateException("Could not import the Danish starter lesson draft.", ex);
            }
        });
    }

    private static String text(String value) { return value == null ? "" : value; }

    record LessonSeed(String id, String languageCode, String title, String level, String topic,
                      String objective, String explanation, String sourceReference, String skillKey,
                      List<ExerciseSeed> exercises) {}
    record ExerciseSeed(String taskType, String prompt, String expectedAnswer, String feedbackCorrect,
                        String feedbackIncorrect, List<OptionSeed> options) {}
    record OptionSeed(String key, String text) {}
}
