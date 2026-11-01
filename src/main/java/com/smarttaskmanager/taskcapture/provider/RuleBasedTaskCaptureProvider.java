package com.smarttaskmanager.taskcapture.provider;

import com.smarttaskmanager.task.entity.RecurrenceType;
import com.smarttaskmanager.task.entity.TaskPriority;
import com.smarttaskmanager.task.entity.TaskStatus;
import com.smarttaskmanager.taskcapture.dto.ParsedTaskResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic rule-based parser for natural language task input.
 * This is NOT an AI model — it uses regex patterns and date parsing rules.
 */
@Slf4j
@Component
public class RuleBasedTaskCaptureProvider implements TaskCaptureProvider {

    private static final Pattern PRIORITY_PATTERN = Pattern.compile(
            "\\b(high|urgent|low|medium|normal)\\s+(?:priority)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "\\b(\\d+)\\s*(?:min(?:utes?)?|hrs?|h)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECURSION_PATTERN = Pattern.compile(
            "\\b(every|each)\\s+(daily|day|weekday|week|monthly|month|yearly|year)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECURSION_N_PATTERN = Pattern.compile(
            "\\b(every)\\s+(\\d+)\\s*(?:days?|weeks?|months?)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public ParsedTaskResult parse(String input, String timezone) {
        String normalized = input.trim();
        if (normalized.isEmpty()) {
            return new ParsedTaskResult("", null, TaskStatus.TODO, TaskPriority.MEDIUM,
                    null, null, null, null, null, "Input is empty");
        }

        List<String> ambiguities = new ArrayList<>();

        // Extract priority
        TaskPriority priority = extractPriority(normalized);

        // Extract duration
        Integer duration = extractDuration(normalized);

        // Extract recurrence
        RecurrenceInfo recurrence = extractRecurrence(normalized);

        // Extract due date
        Instant dueDate = extractDueDate(normalized, resolveZoneId(timezone), ambiguities);

        // Extract category hint (simple keyword matching)
        String category = extractCategory(normalized);

        // Build title by removing extracted metadata
        String title = buildTitle(normalized, priority, duration, recurrence, dueDate, category);

        // Build description from remaining context
        String description = buildDescription(normalized, title);

        if (ambiguities.isEmpty()) {
            ambiguities.add("Rule-based parser used; some details may not be captured accurately.");
        }

        return new ParsedTaskResult(
                title,
                description,
                TaskStatus.TODO,
                priority,
                dueDate,
                category,
                duration,
                recurrence.type,
                recurrence.interval,
                String.join(" ", ambiguities)
        );
    }

    @Override
    public String getType() {
        return "rulebased";
    }

    private TaskPriority extractPriority(String input) {
        Matcher m = PRIORITY_PATTERN.matcher(input);
        if (m.find()) {
            String val = m.group(1).toLowerCase();
            return switch (val) {
                case "high" -> TaskPriority.HIGH;
                case "urgent" -> TaskPriority.URGENT;
                case "low" -> TaskPriority.LOW;
                default -> TaskPriority.MEDIUM;
            };
        }
        return TaskPriority.MEDIUM;
    }

    private Integer extractDuration(String input) {
        Matcher m = DURATION_PATTERN.matcher(input);
        if (m.find()) {
            int value = Integer.parseInt(m.group(1));
            String fullMatch = m.group(0).toLowerCase();
            if (fullMatch.contains("hr") || fullMatch.contains("h")) {
                return value * 60;
            }
            return value;
        }
        return null;
    }

    private RecurrenceInfo extractRecurrence(String input) {
        // Check "every N days/weeks/months" first
        Matcher nMatcher = RECURSION_N_PATTERN.matcher(input);
        if (nMatcher.find()) {
            int interval = Integer.parseInt(nMatcher.group(2));
            String fullMatch = nMatcher.group(0).toLowerCase();
            RecurrenceType type;
            if (fullMatch.contains("day")) {
                type = RecurrenceType.EVERY_N_DAYS;
            } else if (fullMatch.contains("week")) {
                type = RecurrenceType.WEEKLY;
            } else if (fullMatch.contains("month")) {
                type = RecurrenceType.MONTHLY;
            } else {
                type = RecurrenceType.EVERY_N_DAYS;
            }
            return new RecurrenceInfo(type, interval);
        }

        // Check simple "every daily/weekly/monthly"
        Matcher m = RECURSION_PATTERN.matcher(input);
        if (m.find()) {
            String val = m.group(2).toLowerCase();
            return switch (val) {
                case "daily", "day" -> new RecurrenceInfo(RecurrenceType.DAILY, 1);
                case "weekday" -> new RecurrenceInfo(RecurrenceType.WEEKDAYS, 1);
                case "week" -> new RecurrenceInfo(RecurrenceType.WEEKLY, 1);
                case "monthly", "month" -> new RecurrenceInfo(RecurrenceType.MONTHLY, 1);
                case "yearly", "year" -> new RecurrenceInfo(RecurrenceType.YEARLY, 1);
                default -> null;
            };
        }
        return new RecurrenceInfo(null, null);
    }

    private Instant extractDueDate(String input, ZoneId zoneId, List<String> ambiguities) {
        String lower = input.toLowerCase();

        // "tomorrow at HH:MM"
        if (lower.contains("tomorrow")) {
            LocalTime time = extractTime(input);
            LocalDate date = LocalDate.now(zoneId).plusDays(1);
            return LocalDateTime.of(date, time).atZone(zoneId).toInstant();
        }

        // "today at HH:MM"
        if (lower.contains("today")) {
            LocalTime time = extractTime(input);
            LocalDate date = LocalDate.now(zoneId);
            return LocalDateTime.of(date, time).atZone(zoneId).toInstant();
        }

        // "next Monday/Tuesday/etc"
        Pattern dayPattern = Pattern.compile("\\bnext\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b",
                Pattern.CASE_INSENSITIVE);
        Matcher dayMatcher = dayPattern.matcher(input);
        if (dayMatcher.find()) {
            int dayOfWeek = dayOfWeekValue(dayMatcher.group(1).toLowerCase());
            LocalTime time = extractTime(input);
            LocalDate date = LocalDate.now(zoneId)
                    .with(TemporalAdjusters.next(java.time.DayOfWeek.of(dayOfWeek)));
            return LocalDateTime.of(date, time).atZone(zoneId).toInstant();
        }

        // "in N days"
        Pattern inDaysPattern = Pattern.compile("\\b(in)\\s+(\\d+)\\s+days?\\b", Pattern.CASE_INSENSITIVE);
        Matcher inDaysMatcher = inDaysPattern.matcher(input);
        if (inDaysMatcher.find()) {
            int days = Integer.parseInt(inDaysMatcher.group(2));
            LocalTime time = extractTime(input);
            LocalDate date = LocalDate.now(zoneId).plusDays(days);
            return LocalDateTime.of(date, time).atZone(zoneId).toInstant();
        }

        // "in N hours"
        Pattern inHoursPattern = Pattern.compile("\\b(in)\\s+(\\d+)\\s+hours?\\b", Pattern.CASE_INSENSITIVE);
        Matcher inHoursMatcher = inHoursPattern.matcher(input);
        if (inHoursMatcher.find()) {
            int hours = Integer.parseInt(inHoursMatcher.group(2));
            return Instant.now().plus(hours, ChronoUnit.HOURS);
        }

        // "at HH:MM" (today)
        if (lower.contains("at ")) {
            LocalTime time = extractTime(input);
            if (!time.equals(LocalTime.MIDNIGHT)) {
                LocalDate date = LocalDate.now(zoneId);
                return LocalDateTime.of(date, time).atZone(zoneId).toInstant();
            }
        }

        ambiguities.add("Could not parse a specific due date from the input.");
        return null;
    }

    private LocalTime extractTime(String input) {
        // "at 7 PM" or "at 19:00"
        Pattern timePattern = Pattern.compile(
                "\\b(?:at\\s+)?(\\d{1,2}):(\\d{2})\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = timePattern.matcher(input);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = Integer.parseInt(m.group(2));
            return LocalTime.of(hour, minute);
        }

        // "7 PM" or "7am"
        Pattern amPmPattern = Pattern.compile(
                "\\b(\\d{1,2})\\s*(am|pm)\\b", Pattern.CASE_INSENSITIVE);
        Matcher amPmMatcher = amPmPattern.matcher(input);
        if (amPmMatcher.find()) {
            int hour = Integer.parseInt(amPmMatcher.group(1));
            String amPm = amPmMatcher.group(2).toLowerCase();
            if (amPm.equals("pm") && hour < 12) {
                hour += 12;
            } else if (amPm.equals("am") && hour == 12) {
                hour = 0;
            }
            return LocalTime.of(hour, 0);
        }

        return LocalTime.MIDNIGHT;
    }

    private String extractCategory(String input) {
        String lower = input.toLowerCase();
        // Simple keyword-based category extraction
        if (lower.contains("work")) return "Work";
        if (lower.contains("personal")) return "Personal";
        if (lower.contains("study") || lower.contains("learning")) return "Study";
        if (lower.contains("health") || lower.contains("exercise") || lower.contains("gym")) return "Health";
        if (lower.contains("shopping") || lower.contains("grocery")) return "Shopping";
        return null;
    }

    private String buildTitle(String input, TaskPriority priority, Integer duration,
                              RecurrenceInfo recurrence, Instant dueDate, String category) {
        String title = input;

        // Remove priority phrases
        title = title.replaceAll("\\b(?:high|urgent|low|medium|normal)\\s+priority\\b", "").trim();
        // Remove duration phrases
        title = title.replaceAll("\\b\\d+\\s*(?:min|minutes|hrs|hours|h)\\b", "").trim();
        // Remove recurrence phrases
        title = title.replaceAll("\\b(?:every|each)\\s+(?:daily|day|weekday|week|monthly|month|yearly|year)\\b", "")
                .trim();
        title = title.replaceAll("\\bevery\\s+\\d+\\s*(?:days?|weeks?|months?)\\b", "").trim();
        // Remove date phrases
        title = title.replaceAll("\\b(?:tomorrow|today|next\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday))\\b", "")
                .trim();
        title = title.replaceAll("\\bin\\s+\\d+\\s+(?:days?|hours?)\\b", "").trim();
        // Remove time phrases
        title = title.replaceAll("\\bat\\s+\\d{1,2}:\\d{2}\\b", "").trim();
        title = title.replaceAll("\\b\\d{1,2}\\s*(?:am|pm)\\b", "").trim();

        // Capitalize first letter
        if (!title.isEmpty()) {
            title = title.substring(0, 1).toUpperCase() + title.substring(1);
        }

        // Clean up extra spaces and punctuation
        title = title.replaceAll("\\s+", " ").trim();
        if (title.endsWith(",") || title.endsWith(".")) {
            title = title.substring(0, title.length() - 1).trim();
        }

        // Truncate to max length
        if (title.length() > 150) {
            title = title.substring(0, 147) + "...";
        }

        return title.isEmpty() ? input : title;
    }

    private String buildDescription(String input, String title) {
        // Use the original input as description if it differs significantly from the title
        if (!input.equalsIgnoreCase(title) && input.length() > title.length() + 10) {
            return input;
        }
        return null;
    }

    private int dayOfWeekValue(String day) {
        return switch (day) {
            case "monday" -> 1;
            case "tuesday" -> 2;
            case "wednesday" -> 3;
            case "thursday" -> 4;
            case "friday" -> 5;
            case "saturday" -> 6;
            case "sunday" -> 7;
            default -> 1;
        };
    }

    private ZoneId resolveZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}', falling back to UTC", timezone);
            return ZoneOffset.UTC;
        }
    }

    private record RecurrenceInfo(RecurrenceType type, Integer interval) {}
}
