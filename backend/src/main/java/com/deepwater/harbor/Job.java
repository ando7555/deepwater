package com.deepwater.harbor;

import java.time.Instant;
import java.util.List;

/**
 * A job posting as Harbor stores it. {@code source} says where it came from: SAMPLE postings are
 * written for the demo and have no employer behind them; ARBEITNOW postings come from that public job board.
 * {@code visaSignal} is what the posting itself says about visa sponsorship: OFFERED, NOT_OFFERED or UNKNOWN.
 */
public record Job(String id, String source, String title, String company, String countryCode, String location,
                  boolean remote, String visaSignal, String description, List<String> tags, String url, Instant postedAt) {}
