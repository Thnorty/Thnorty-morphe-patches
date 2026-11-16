/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

import app.morphe.extension.reddit.settings.Settings;
import app.morphe.extension.shared.Logger;

@SuppressWarnings("unused")
public final class MinScoreFilterPatch {

    /**
     * Implemented by the GraphQL feed edge class during patching.
     */
    public interface FeedEdgeAccess {
        Object patch_getCellGroup();
    }

    /**
     * Implemented by the GraphQL cell group fragment class during patching.
     */
    public interface CellGroupAccess {
        Object patch_getAdPayload();

        List<?> patch_getCells();
    }

    /**
     * Implemented by the GraphQL cell class (a union of all cell fragments) during patching.
     */
    public interface CellAccess {
        Object patch_getActionCell();

        Object patch_getMetadataCell();
    }

    /**
     * Implemented by the GraphQL action cell fragment class during patching.
     */
    public interface ActionCellAccess {
        int patch_getScore();

        boolean patch_isScoreHidden();
    }

    /**
     * Implemented by the GraphQL metadata cell fragment class during patching.
     */
    public interface MetadataCellAccess {
        /**
         * @return 'r/subreddit' in mixed feeds, 'u/author' in subreddit feeds.
         */
        String patch_getDetailsString();

        /**
         * @return Icon of the subreddit in mixed feeds, of the author in subreddit feeds. Can be null.
         */
        String patch_getIconPath();

        List<?> patch_getStatusIndicators();
    }

    /**
     * Implemented by the GraphQL edge mapping context class during patching.
     */
    public interface FeedContextAccess {
        /**
         * @return The FeedType enum of the feed being mapped, or null.
         */
        Object patch_getFeedType();
    }

    /**
     * Implemented by the subreddit feed fetch parameters class during patching.
     */
    public interface SubredditFeedParamsAccess {
        String patch_getSubredditName();
    }

    private static final String SUBREDDIT_PREFIX = "r/";
    private static final String PINNED_INDICATOR = "PINNED";

    /**
     * Subreddit of the feed whose cell groups are currently being mapped on this thread.
     */
    private static final ThreadLocal<String> subredditFeed = new ThreadLocal<>();

    /**
     * @return If this patch was included during patching.
     */
    public static boolean isPatchIncluded() {
        return false;  // Modified during patching.
    }

    /**
     * Injection point.
     */
    public static void setSubredditFeed(Object params) {
        try {
            subredditFeed.set(((SubredditFeedParamsAccess) params).patch_getSubredditName());
        } catch (Throwable ex) {
            Logger.printException(() -> "setSubredditFeed failure", ex);
        }
    }

    /**
     * Injection point.
     */
    public static void clearSubredditFeed() {
        subredditFeed.remove();
    }

    /**
     * Injection point.
     *
     * @param context GraphQL edge mapping context, or null.
     * @return If posts of this feed are filtered.
     */
    public static boolean isFilteredFeed(@Nullable Object context) {
        try {
            Object feedType = context == null ? null : ((FeedContextAccess) context).patch_getFeedType();
            return MinScoreRules.isFilteredFeed(feedType instanceof Enum<?> feedTypeEnum ? feedTypeEnum.name() : null);
        } catch (Throwable ex) {
            Logger.printException(() -> "isFilteredFeed failure", ex);
        }
        return true;
    }

    /**
     * Injection point. Called for the edges of every feed.
     * Remembers the subreddits of posts in mixed feeds (Home, Popular) for the rules dialog.
     */
    public static void recordSeenSubreddits(Object edge) {
        try {
            CellGroupAccess cellGroup = (CellGroupAccess) ((FeedEdgeAccess) edge).patch_getCellGroup();
            List<?> cells = cellGroup == null ? null : cellGroup.patch_getCells();
            if (cells == null) {
                return;
            }

            for (Object cell : cells) {
                if (cell instanceof CellAccess cellAccess
                        && cellAccess.patch_getMetadataCell() instanceof MetadataCellAccess metadataCell) {
                    String details = metadataCell.patch_getDetailsString();
                    // Subreddit feeds show the author instead, with the author's icon.
                    if (details != null && details.startsWith(SUBREDDIT_PREFIX)) {
                        SeenSubredditsStore.record(details.substring(SUBREDDIT_PREFIX.length()),
                                metadataCell.patch_getIconPath());
                    }
                }
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "recordSeenSubreddits failure", ex);
        }
    }

    /**
     * Injection point.
     *
     * @return If the feed post of this GraphQL feed edge should be hidden.
     */
    public static boolean shouldHide(Object edge) {
        try {
            CellGroupAccess cellGroup = (CellGroupAccess) ((FeedEdgeAccess) edge).patch_getCellGroup();
            List<?> cells = cellGroup == null ? null : cellGroup.patch_getCells();
            if (cells == null) {
                return false;
            }

            final boolean promoted = cellGroup.patch_getAdPayload() != null;
            Integer score = null;
            boolean scoreHidden = false;
            boolean pinned = false;
            String subreddit = null;

            for (Object cell : cells) {
                if (!(cell instanceof CellAccess cellAccess)) {
                    continue;
                }

                if (cellAccess.patch_getActionCell() instanceof ActionCellAccess actionCell) {
                    score = actionCell.patch_getScore();
                    scoreHidden = actionCell.patch_isScoreHidden();
                }

                if (cellAccess.patch_getMetadataCell() instanceof MetadataCellAccess metadataCell) {
                    String details = metadataCell.patch_getDetailsString();
                    if (details != null && details.startsWith(SUBREDDIT_PREFIX)) {
                        subreddit = details;
                    }
                    pinned |= containsPinned(metadataCell.patch_getStatusIndicators());
                }
            }

            Map<String, Integer> rules = MinScoreRules.cached(Settings.MIN_SCORE_RULES.get());
            final int defaultMinimum = Settings.MIN_SCORE_DEFAULT.get();
            if (rules.isEmpty() && defaultMinimum <= 0) {
                return false;
            }

            if (subreddit == null) {
                subreddit = subredditFeed.get();
            }

            final boolean hide = MinScoreRules.shouldHide(rules, defaultMinimum, subreddit, score, scoreHidden,
                    promoted, pinned);
            if (hide) {
                final String hiddenSubreddit = subreddit;
                final Integer hiddenScore = score;
                Logger.printDebug(() -> "Hiding post in " + hiddenSubreddit + " with score " + hiddenScore);
            }
            return hide;
        } catch (Throwable ex) {
            Logger.printException(() -> "shouldHide failure", ex);
        }

        return false;
    }

    private static boolean containsPinned(@Nullable List<?> statusIndicators) {
        if (statusIndicators != null) {
            for (Object indicator : statusIndicators) {
                if (PINNED_INDICATOR.equals(String.valueOf(indicator))) {
                    return true;
                }
            }
        }
        return false;
    }
}
