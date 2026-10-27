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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Subreddits seen in feeds, most recently seen first, with their casing and icon.
 * Serialized as one {@code name<TAB>iconUrl} line per subreddit.
 */
public final class SeenSubreddits {

    public static final int DEFAULT_CAPACITY = 300;

    public record Entry(String name, @Nullable String iconUrl) {
    }

    private final int capacity;

    /**
     * Keyed by lowercase name, least recently seen first.
     */
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    public SeenSubreddits(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Marks a subreddit as the most recently seen.
     *
     * @return If the subreddit is new, or its casing or icon changed.
     */
    public synchronized boolean record(String name, @Nullable String iconUrl) {
        if (name == null || name.trim().isEmpty() || name.indexOf('\t') >= 0 || name.indexOf('\n') >= 0) {
            return false;
        }
        if (iconUrl != null && (iconUrl.trim().isEmpty() || iconUrl.indexOf('\t') >= 0 || iconUrl.indexOf('\n') >= 0)) {
            iconUrl = null;
        }

        String key = name.toLowerCase(Locale.ROOT);
        Entry entry = new Entry(name, iconUrl);
        Entry previous = entries.remove(key);
        entries.put(key, entry);

        Iterator<String> leastRecent = entries.keySet().iterator();
        while (entries.size() > capacity) {
            leastRecent.next();
            leastRecent.remove();
        }

        return !entry.equals(previous);
    }

    @Nullable
    public synchronized Entry find(String name) {
        return name == null ? null : entries.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * @return Subreddits containing the query (ignoring case): names starting with the query first,
     *         each group most recently seen first. An empty query returns the most recently seen.
     */
    public synchronized List<Entry> search(String query, int limit) {
        String lowercaseQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Entry> prefixMatches = new ArrayList<>();
        List<Entry> otherMatches = new ArrayList<>();

        for (Entry entry : mostRecentFirst()) {
            String key = entry.name.toLowerCase(Locale.ROOT);
            if (key.startsWith(lowercaseQuery)) {
                prefixMatches.add(entry);
            } else if (key.contains(lowercaseQuery)) {
                otherMatches.add(entry);
            }
        }

        prefixMatches.addAll(otherMatches);
        return prefixMatches.size() > limit ? new ArrayList<>(prefixMatches.subList(0, limit)) : prefixMatches;
    }

    public synchronized String serialize() {
        StringBuilder builder = new StringBuilder();
        for (Entry entry : mostRecentFirst()) {
            builder.append(entry.name);
            if (entry.iconUrl != null) {
                builder.append('\t').append(entry.iconUrl);
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    public static SeenSubreddits deserialize(@Nullable String raw, int capacity) {
        SeenSubreddits seen = new SeenSubreddits(capacity);
        if (raw == null) {
            return seen;
        }

        String[] lines = raw.split("\n");
        // Lines are most recent first, so record them from the end.
        for (int i = lines.length - 1; i >= 0; i--) {
            String[] fields = lines[i].split("\t", -1);
            if (fields.length == 1) {
                seen.record(fields[0], null);
            } else if (fields.length == 2) {
                seen.record(fields[0], fields[1]);
            }
        }
        return seen;
    }

    private List<Entry> mostRecentFirst() {
        List<Entry> list = new ArrayList<>(entries.values());
        Collections.reverse(list);
        return list;
    }
}
