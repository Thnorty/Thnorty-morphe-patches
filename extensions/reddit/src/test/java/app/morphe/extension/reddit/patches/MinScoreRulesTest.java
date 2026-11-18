/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;

public class MinScoreRulesTest {

    @Test
    public void parseBasic() {
        assertEquals(Map.of("pics", 500, "funny", 1000), MinScoreRules.parse("pics=500, funny=1000"));
    }

    @Test
    public void parseNewlines() {
        assertEquals(Map.of("pics", 500, "funny", 1000), MinScoreRules.parse("pics=500\nfunny = 1000\n"));
    }

    @Test
    public void parseNormalizesNames() {
        assertEquals(Map.of("pics", 1, "aww", 2, "news", 3), MinScoreRules.parse("r/Pics=1, /r/aww=2,  NEWS =3"));
    }

    @Test
    public void parseSkipsInvalidEntries() {
        assertEquals(Map.of("ok", 5), MinScoreRules.parse("pics=99999999999, a=-5, b=, =500, pics500, ok=5"));
    }

    @Test
    public void parseLastDuplicateWins() {
        assertEquals(Map.of("pics", 20), MinScoreRules.parse("pics=10, PICS=20"));
    }

    @Test
    public void parseEmptyAndNull() {
        assertTrue(MinScoreRules.parse("").isEmpty());
        assertTrue(MinScoreRules.parse(null).isEmpty());
    }

    @Test
    public void hidesBelowMinimum() {
        assertTrue(MinScoreRules.shouldHide(Map.of("pics", 500), 0, "r/pics", 499, false, false, false));
    }

    @Test
    public void scoreEqualToMinimumIsShown() {
        assertFalse(MinScoreRules.shouldHide(Map.of("pics", 500), 0, "pics", 500, false, false, false));
    }

    @Test
    public void noRuleIsShown() {
        assertFalse(MinScoreRules.shouldHide(Map.of("pics", 500), 0, "aww", 1, false, false, false));
    }

    @Test
    public void unknownSubredditIsShown() {
        assertFalse(MinScoreRules.shouldHide(Map.of("pics", 500), 0, null, 1, false, false, false));
        assertFalse(MinScoreRules.shouldHide(Map.of("pics", 500), 0, "", 1, false, false, false));
    }

    @Test
    public void missingScoreIsShown() {
        assertFalse(MinScoreRules.shouldHide(Map.of("pics", 500), 0, "pics", null, false, false, false));
    }

    @Test
    public void hiddenScoreIsShown() {
        assertFalse(MinScoreRules.shouldHide(Map.of("pics", 500), 0, "pics", 1, true, false, false));
    }

    @Test
    public void promotedIsShown() {
        assertFalse(MinScoreRules.shouldHide(Map.of("pics", 500), 0, "pics", 1, false, true, false));
    }

    @Test
    public void pinnedIsShown() {
        assertFalse(MinScoreRules.shouldHide(Map.of("pics", 500), 0, "pics", 1, false, false, true));
    }

    @Test
    public void cachedReturnsSameInstanceUntilChanged() {
        Map<String, Integer> first = MinScoreRules.cached("pics=1");
        assertSame(first, MinScoreRules.cached("pics=1"));
        assertNotSame(first, MinScoreRules.cached("pics=2"));
    }

    @Test
    public void parseAcceptsCommaThousandsSeparator() {
        assertEquals(Map.of("pics", 1000, "funny", 12500), MinScoreRules.parse("pics=1,000, funny=12,500"));
    }

    @Test
    public void parseAcceptsOtherThousandsSeparators() {
        assertEquals(Map.of("pics", 1000, "funny", 2500, "aww", 3000),
                MinScoreRules.parse("pics=1.000\nfunny=2 500\naww=3_000"));
    }

    @Test
    public void subredditFeedIsFiltered() {
        assertTrue(MinScoreRules.isFilteredFeed("SUBREDDIT"));
    }

    @Test
    public void homeAndPopularFeedsAreNotFiltered() {
        assertFalse(MinScoreRules.isFilteredFeed("HOME"));
        assertFalse(MinScoreRules.isFilteredFeed("POPULAR"));
    }

    @Test
    public void personalFeedsAreNotFiltered() {
        assertFalse(MinScoreRules.isFilteredFeed("SAVED_POSTS"));
        assertFalse(MinScoreRules.isFilteredFeed("SUBMITTED_POSTS"));
        assertFalse(MinScoreRules.isFilteredFeed("HISTORY"));
        assertFalse(MinScoreRules.isFilteredFeed("QUEUE"));
    }

    @Test
    public void unknownFeedTypeIsNotFiltered() {
        assertFalse(MinScoreRules.isFilteredFeed(null));
    }

    @Test
    public void parseListKeepsTypedCasing() {
        assertEquals(List.of(new MinScoreRules.Rule("DotA2", 500), new MinScoreRules.Rule("pics", 1000)),
                MinScoreRules.parseList("r/DotA2=500, pics=1,000"));
    }

    @Test
    public void parseListDuplicateReplacesInPlace() {
        assertEquals(List.of(new MinScoreRules.Rule("PICS", 20), new MinScoreRules.Rule("aww", 1)),
                MinScoreRules.parseList("pics=10, aww=1, PICS=20"));
    }

    @Test
    public void formatJoinsRules() {
        assertEquals("DotA2=500, pics=1000", MinScoreRules.format(
                List.of(new MinScoreRules.Rule("DotA2", 500), new MinScoreRules.Rule("pics", 1000))));
    }

    @Test
    public void formatKeepsTypedCasing() {
        assertEquals("askhistorians=50", MinScoreRules.format(MinScoreRules.parseList("askhistorians=50")));
    }

    @Test
    public void formatEmptyIsEmptyString() {
        assertEquals("", MinScoreRules.format(List.of()));
    }

    @Test
    public void parseMinimumAcceptsPlainAndGrouped() {
        assertEquals(Integer.valueOf(1000), MinScoreRules.parseMinimum(" 1,000 "));
        assertEquals(Integer.valueOf(42), MinScoreRules.parseMinimum("42"));
    }

    @Test
    public void parseMinimumRejectsInvalid() {
        assertNull(MinScoreRules.parseMinimum("-5"));
        assertNull(MinScoreRules.parseMinimum("abc"));
        assertNull(MinScoreRules.parseMinimum(""));
        assertNull(MinScoreRules.parseMinimum(null));
        assertNull(MinScoreRules.parseMinimum("99999999999"));
    }

    @Test
    public void validSubredditNames() {
        assertTrue(MinScoreRules.isValidSubredditName("DotA2"));
        assertTrue(MinScoreRules.isValidSubredditName("Genshin_Impact"));
    }

    @Test
    public void invalidSubredditNames() {
        assertFalse(MinScoreRules.isValidSubredditName("a,b"));
        assertFalse(MinScoreRules.isValidSubredditName("a=b"));
        assertFalse(MinScoreRules.isValidSubredditName("two words"));
        assertFalse(MinScoreRules.isValidSubredditName(""));
        assertFalse(MinScoreRules.isValidSubredditName(null));
    }

    @Test
    public void stripPrefixKeepsCasing() {
        assertEquals("DotA2", MinScoreRules.stripPrefix(" /r/DotA2 "));
        assertNull(MinScoreRules.stripPrefix("r/"));
    }

    @Test
    public void defaultHidesSubredditWithoutRule() {
        assertTrue(MinScoreRules.shouldHide(Map.of(), 100, "aww", 99, false, false, false));
        assertFalse(MinScoreRules.shouldHide(Map.of(), 100, "aww", 100, false, false, false));
    }

    @Test
    public void ruleOverridesDefault() {
        assertFalse(MinScoreRules.shouldHide(Map.of("aww", 10), 100, "aww", 50, false, false, false));
        assertTrue(MinScoreRules.shouldHide(Map.of("aww", 500), 100, "aww", 200, false, false, false));
    }

    @Test
    public void zeroRuleExemptsFromDefault() {
        assertFalse(MinScoreRules.shouldHide(Map.of("aww", 0), 100, "aww", -5, false, false, false));
    }

    @Test
    public void zeroDefaultIsOff() {
        assertFalse(MinScoreRules.shouldHide(Map.of(), 0, "aww", -5, false, false, false));
    }

    @Test
    public void defaultDoesNotApplyToUnknownSubreddit() {
        assertFalse(MinScoreRules.shouldHide(Map.of(), 100, null, 1, false, false, false));
    }

    @Test
    public void defaultKeepsPinnedPromotedAndHiddenScore() {
        assertFalse(MinScoreRules.shouldHide(Map.of(), 100, "aww", 1, true, false, false));
        assertFalse(MinScoreRules.shouldHide(Map.of(), 100, "aww", 1, false, true, false));
        assertFalse(MinScoreRules.shouldHide(Map.of(), 100, "aww", 1, false, false, true));
    }
}
