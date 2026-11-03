package com.deepwater.harbor;

import java.util.List;

/**
 * The result of checking a CV's styling and formatting: a 0–100 score and one line per check.
 * Each item's status is PASS, WARN (worth improving) or FAIL (likely to hurt the application).
 */
public record CvCheck(int score, List<Item> items) {
    public record Item(String id, String status, String title, String detail) {}
}
