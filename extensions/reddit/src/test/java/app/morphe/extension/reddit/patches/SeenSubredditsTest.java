/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SeenSubredditsTest {

    private static List<String> names(List<SeenSubreddits.Entry> entries) {
        List<String> names = new ArrayList<>();
        for (SeenSubreddits.Entry entry : entries) {
            names.add(entry.name());
        }
        return names;
    }

    @Test
    public void recordNewReturnsTrue() {
        SeenSubreddits seen = new SeenSubreddits(10);
        assertTrue(seen.record("DotA2", "https://i/1.png"));
        assertEquals(new SeenSubreddits.Entry("DotA2", "https://i/1.png"), seen.find("dota2"));
    }

    @Test
    public void recordSameReturnsFalse() {
        SeenSubreddits seen = new SeenSubreddits(10);
        seen.record("pics", null);
        assertFalse(seen.record("pics", null));
    }

    @Test
    public void recordMergesCaseInsensitively() {
        SeenSubreddits seen = new SeenSubreddits(10);
        seen.record("dota2", null);
        assertTrue(seen.record("DotA2", "u"));
        assertEquals(1, seen.search("", 10).size());
        assertEquals("DotA2", seen.find("DOTA2").name());
    }

    @Test
    public void recordRejectsNamesWithSeparators() {
        SeenSubreddits seen = new SeenSubreddits(10);
        assertFalse(seen.record("a\tb", null));
        assertFalse(seen.record("a\nb", null));
        assertFalse(seen.record(" ", null));
        assertTrue(seen.search("", 10).isEmpty());
    }

    @Test
    public void capacityEvictsLeastRecent() {
        SeenSubreddits seen = new SeenSubreddits(2);
        seen.record("a", null);
        seen.record("b", null);
        seen.record("a", null);
        seen.record("c", null);
        assertNull(seen.find("b"));
        assertNotNull(seen.find("a"));
    }

    @Test
    public void searchEmptyQueryReturnsMostRecent() {
        SeenSubreddits seen = new SeenSubreddits(10);
        seen.record("a", null);
        seen.record("b", null);
        assertEquals(List.of("b", "a"), names(seen.search("", 10)));
    }

    @Test
    public void searchPrefixFirstThenContains() {
        SeenSubreddits seen = new SeenSubreddits(10);
        seen.record("DotA2", null);
        seen.record("learndota", null);
        assertEquals(List.of("DotA2", "learndota"), names(seen.search("dota", 10)));
    }

    @Test
    public void searchRespectsLimit() {
        SeenSubreddits seen = new SeenSubreddits(10);
        seen.record("a1", null);
        seen.record("a2", null);
        assertEquals(1, seen.search("a", 1).size());
    }

    @Test
    public void serializeRoundTrip() {
        SeenSubreddits seen = new SeenSubreddits(10);
        seen.record("pics", null);
        seen.record("DotA2", "https://i/1.png");
        SeenSubreddits restored = SeenSubreddits.deserialize(seen.serialize(), 10);
        assertEquals(seen.search("", 10), restored.search("", 10));
    }

    @Test
    public void deserializeSkipsMalformedLines() {
        SeenSubreddits restored = SeenSubreddits.deserialize("DotA2\thttps://i\n\n\tnoName\npics\nx\ty\tz\n", 10);
        assertEquals(List.of("DotA2", "pics"), names(restored.search("", 10)));
    }
}
