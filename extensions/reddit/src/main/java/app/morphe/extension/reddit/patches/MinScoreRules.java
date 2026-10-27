/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses per-subreddit minimum score rules and decides if a post is hidden.
 * Rule format: {@code pics=500, funny=1000} (comma or newline separated).
 */
public final class MinScoreRules {

    /**
     * Only subreddit feeds are filtered. Home and other mixed feeds load posts through several paths
     * that do not all carry the score, so filtering them would be incomplete.
     */
    private static final String FILTERED_FEED_TYPE = "SUBREDDIT";

    /**
     * A number with thousands separators, such as 1,000 or 12.500 or 2 500.
     */
    private static final Pattern GROUPED_NUMBER = Pattern.compile("\\d{1,3}([,. _']\\d{3})+");
    private static final Pattern SUBREDDIT_NAME = Pattern.compile("[A-Za-z0-9_]{1,50}");
    private static final Pattern THREE_DIGITS = Pattern.compile("\\d{3}");
    private static final Pattern TRAILING_DIGIT = Pattern.compile("[^=]*=\\s*[\\d,]*\\d");

    @Nullable
    private static String cachedRaw;
    private static Map<String, Integer> cachedRules = Collections.emptyMap();

    private MinScoreRules() {
    }

    /**
     * A rule as typed: subreddit name without 'r/' prefix (original casing) and its minimum score.
     */
    public record Rule(String name, int minimum) {
    }

    /**
     * @return Rules keyed by normalized subreddit name. Invalid entries are skipped.
     */
    public static Map<String, Integer> parse(@Nullable String raw) {
        Map<String, Integer> rules = new LinkedHashMap<>();
        for (Rule rule : parseList(raw)) {
            rules.put(normalizeName(rule.name), rule.minimum);
        }
        return rules;
    }

    /**
     * @return Rules in order of first appearance. A later rule for the same subreddit
     *         (ignoring case) replaces the earlier one in place. Invalid entries are skipped.
     */
    public static List<Rule> parseList(@Nullable String raw) {
        Map<String, Rule> rules = new LinkedHashMap<>();
        if (raw == null) {
            return new ArrayList<>();
        }

        for (String entry : splitEntries(raw)) {
            final int separator = entry.indexOf('=');
            if (separator < 0) {
                continue;
            }

            String name = stripPrefix(entry.substring(0, separator));
            Integer minimum = parseMinimum(entry.substring(separator + 1));
            if (name != null && minimum != null) {
                rules.put(name.toLowerCase(Locale.ROOT), new Rule(name, minimum));
            }
        }

        return new ArrayList<>(rules.values());
    }

    /**
     * @return The rules in the setting format, such as 'DotA2=500, pics=1000'.
     */
    public static String format(List<Rule> rules) {
        StringBuilder builder = new StringBuilder();
        for (Rule rule : rules) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(rule.name).append('=').append(rule.minimum);
        }
        return builder.toString();
    }

    /**
     * @return The minimum score, also with thousands separators ('1,000'), or null if invalid or negative.
     */
    @Nullable
    public static Integer parseMinimum(@Nullable String value) {
        if (value == null) {
            return null;
        }

        String digits = value.trim();
        if (GROUPED_NUMBER.matcher(digits).matches()) {
            digits = digits.replaceAll("[,. _']", "");
        }

        try {
            final int minimum = Integer.parseInt(digits);
            return minimum >= 0 ? minimum : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Splits on commas and new lines, but rejoins a comma thousands separator ('pics=1,000').
     */
    private static List<String> splitEntries(String raw) {
        List<String> entries = new ArrayList<>();
        for (String piece : raw.split("\n", -1)) {
            for (String part : piece.split(",", -1)) {
                final int last = entries.size() - 1;
                if (last >= 0 && THREE_DIGITS.matcher(part).matches()
                        && TRAILING_DIGIT.matcher(entries.get(last)).matches()) {
                    entries.set(last, entries.get(last) + "," + part);
                } else {
                    entries.add(part);
                }
            }
            // A new line always starts a new entry.
            entries.add("");
        }
        return entries;
    }

    /**
     * @return The rules for this raw setting value, parsed only when the value changes.
     */
    public static synchronized Map<String, Integer> cached(@Nullable String raw) {
        if (raw == null ? cachedRaw != null : !raw.equals(cachedRaw)) {
            cachedRaw = raw;
            cachedRules = Collections.unmodifiableMap(parse(raw));
        }
        return cachedRules;
    }

    /**
     * @return Lowercase subreddit name without an 'r/' or '/r/' prefix, or null if empty.
     */
    @Nullable
    public static String normalizeName(@Nullable String name) {
        String stripped = stripPrefix(name);
        return stripped == null ? null : stripped.toLowerCase(Locale.ROOT);
    }

    /**
     * @return Subreddit name without an 'r/' or '/r/' prefix in its original casing, or null if empty.
     */
    @Nullable
    public static String stripPrefix(@Nullable String name) {
        if (name == null) {
            return null;
        }

        String stripped = name.trim();
        if (stripped.startsWith("/")) {
            stripped = stripped.substring(1);
        }
        if (stripped.regionMatches(true, 0, "r/", 0, 2)) {
            stripped = stripped.substring(2);
        }
        stripped = stripped.trim();

        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * @return If the name only has the characters a subreddit name can have,
     *         so it can never break the rule format.
     */
    public static boolean isValidSubredditName(@Nullable String name) {
        return name != null && SUBREDDIT_NAME.matcher(name).matches();
    }

    /**
     * @param feedType Name of the feed type enum, or null if unknown.
     * @return If posts in this feed are filtered.
     */
    public static boolean isFilteredFeed(@Nullable String feedType) {
        return FILTERED_FEED_TYPE.equals(feedType);
    }

    /**
     * @param defaultMinimum Minimum for subreddits without a rule. A minimum of 0 never hides.
     */
    public static boolean shouldHide(Map<String, Integer> rules, int defaultMinimum, @Nullable String subreddit,
                                     @Nullable Integer score, boolean scoreHidden,
                                     boolean promoted, boolean pinned) {
        if (score == null || scoreHidden || promoted || pinned) {
            return false;
        }

        String name = normalizeName(subreddit);
        if (name == null) {
            return false;
        }

        Integer rule = rules.get(name);
        final int minimum = rule != null ? rule : defaultMinimum;
        return minimum > 0 && score < minimum;
    }
}
