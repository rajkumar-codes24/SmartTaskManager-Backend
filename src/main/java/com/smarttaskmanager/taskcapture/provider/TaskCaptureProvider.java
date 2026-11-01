package com.smarttaskmanager.taskcapture.provider;

import com.smarttaskmanager.taskcapture.dto.ParsedTaskResult;

/**
 * Strategy interface for parsing natural language into structured task data.
 * Implementations can use AI models or rule-based parsing.
 */
public interface TaskCaptureProvider {

    /**
     * Parse natural language input into a structured task result.
     *
     * @param input the natural language text
     * @param timezone the user's timezone for date/time interpretation
     * @return parsed task data with extracted fields
     */
    ParsedTaskResult parse(String input, String timezone);

    /**
     * Returns the provider type identifier.
     */
    String getType();
}
