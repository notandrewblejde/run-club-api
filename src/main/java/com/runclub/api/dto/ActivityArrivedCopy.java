package com.runclub.api.dto;

/** LLM or fallback title/body for an activity-sync notification. */
public record ActivityArrivedCopy(String title, String body) {}
