package com.deepwater.platform;

import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DemoData {
    @Bean
    ApplicationRunner seedEchoContent(JdbcTemplate db) {
        return args -> {
            String lessonId = "v2-time-first";
            int lessonExists = db.queryForObject("select count(*) from echo_lessons where id=?", Integer.class, lessonId);
            if (lessonExists == 0) {
                db.update("insert into echo_lessons(id,title,level,topic,objective,explanation,source_reference,content_status,skill_key,sort_order) values(?,?,?,?,?,?,?,?,?,?)",
                        lessonId, "Start with time. Put the verb second.", "A0 · beginner", "Danish word order",
                        "Use a fronted time phrase and keep the finite verb in second constituent position.",
                        "In a Danish declarative main clause, the finite verb is typically in second constituent position. In “I dag arbejder jeg hjemme”, “I dag” is the first constituent and “arbejder” is the finite verb in the second. The subject “jeg” follows it. This is one useful main-clause pattern, not a rule for every Danish sentence.",
                        "Notion export: 5 August lesson; source example: “I dag arbejder jeg hjemme.”",
                        "Draft — Danish teacher review required", "danish.main-clause.v2", 1);
                addExercise(db, "v2-select", lessonId, 1, "SELECT",
                        "Which sentence places the finite verb second after a time phrase?", "A",
                        "Correct. “I dag” is the first constituent; “arbejder” is the finite verb in the second.",
                        "Look for the finite verb immediately after the opening time phrase. The target pattern is “I dag arbejder jeg hjemme.”",
                        new String[][]{{"A", "I dag arbejder jeg hjemme."}, {"B", "I dag jeg arbejder hjemme."}, {"C", "Jeg i dag arbejder hjemme."}});
                addExercise(db, "v2-fill", lessonId, 2, "TEXT",
                        "Complete the sentence with the finite verb: “I dag ___ jeg hjemme.”", "arbejder",
                        "Correct. The finite verb “arbejder” follows the opening time phrase.",
                        "In the lesson example, “I dag” comes first and “arbejder” is the next constituent.", new String[0][0]);
                addExercise(db, "v2-transfer", lessonId, 3, "TRANSFER",
                        "Write a new Danish sentence that begins with a time phrase. Then check: is the finite verb in second position?",
                        null, "", "", new String[0][0]);
            }
        };
    }

    private static void addExercise(JdbcTemplate db, String id, String lessonId, int position, String taskType,
                                    String prompt, String expectedAnswer, String feedbackCorrect,
                                    String feedbackIncorrect, String[][] options) {
        db.update("insert into echo_exercises(id,lesson_id,position,task_type,prompt,expected_answer,feedback_correct,feedback_incorrect) values(?,?,?,?,?,?,?,?)",
                id, lessonId, position, taskType, prompt, expectedAnswer, feedbackCorrect, feedbackIncorrect);
        for (int index = 0; index < options.length; index++) {
            String[] option = options[index];
            db.update("insert into echo_exercise_options(exercise_id,option_key,option_text,sort_order) values(?,?,?,?)",
                    id, option[0], option[1], index + 1);
        }
    }
}
