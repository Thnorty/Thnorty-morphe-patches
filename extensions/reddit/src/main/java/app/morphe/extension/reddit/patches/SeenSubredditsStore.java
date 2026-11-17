/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

import app.morphe.extension.reddit.settings.Settings;
import app.morphe.extension.shared.Utils;

/**
 * Process wide {@link SeenSubreddits}, loaded from and saved to a hidden setting.
 */
public final class SeenSubredditsStore {
    private static final long SAVE_DELAY_MILLIS = 5000;

    private static final AtomicBoolean savePending = new AtomicBoolean();

    @Nullable
    private static volatile SeenSubreddits seen;

    private SeenSubredditsStore() {
    }

    public static SeenSubreddits get() {
        SeenSubreddits current = seen;
        if (current == null) {
            synchronized (SeenSubredditsStore.class) {
                current = seen;
                if (current == null) {
                    current = SeenSubreddits.deserialize(Settings.MIN_SCORE_SEEN_SUBREDDITS.get(),
                            SeenSubreddits.DEFAULT_CAPACITY);
                    seen = current;
                }
            }
        }
        return current;
    }

    /**
     * Records a subreddit seen in a feed. Saves at most once per {@link #SAVE_DELAY_MILLIS},
     * and only after something new was recorded.
     */
    public static void record(String name, @Nullable String iconUrl) {
        if (get().record(name, iconUrl) && savePending.compareAndSet(false, true)) {
            // Saving writes the preferences file synchronously, so keep it off the main thread.
            Utils.runOnMainThreadDelayed(() -> Utils.runOnBackgroundThread(() -> {
                savePending.set(false);
                Settings.MIN_SCORE_SEEN_SUBREDDITS.save(get().serialize());
            }), SAVE_DELAY_MILLIS);
        }
    }
}
