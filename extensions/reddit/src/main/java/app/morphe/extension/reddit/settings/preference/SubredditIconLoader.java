/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.settings.preference;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.theme.ThemeUtils;

/**
 * Loads subreddit icons in the background and caches them in memory.
 */
public final class SubredditIconLoader {
    private static final int TIMEOUT_MILLIS = 5000;
    private static final int MAX_SIZE_PX = 96;

    private static final LruCache<String, Bitmap> cache = new LruCache<>(64);
    private static final Set<String> failedUrls = ConcurrentHashMap.newKeySet();

    /**
     * Views waiting for a download in progress, by url. Rows are rebuilt often, so one download serves all.
     * Only accessed on the main thread.
     */
    private static final Map<String, List<WeakReference<ImageView>>> waitingViews = new HashMap<>();

    private SubredditIconLoader() {
    }

    /**
     * Shows the icon of the url in the view, or a placeholder while loading or if there is none.
     * A view reused for another url before loading finishes keeps the newer icon.
     * Must be called on the main thread.
     */
    public static void load(ImageView view, @Nullable String url) {
        view.setTag(url);
        Bitmap cached = url == null ? null : cache.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }

        view.setImageDrawable(createPlaceholder());
        if (url == null || failedUrls.contains(url)) {
            return;
        }

        List<WeakReference<ImageView>> waiting = waitingViews.get(url);
        if (waiting != null) {
            waiting.add(new WeakReference<>(view));
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(new WeakReference<>(view));
        waitingViews.put(url, waiting);

        Utils.runOnBackgroundThread(() -> {
            Bitmap bitmap = download(url);
            if (bitmap == null) {
                failedUrls.add(url);
            } else {
                cache.put(url, bitmap);
            }

            Utils.runOnMainThread(() -> {
                List<WeakReference<ImageView>> views = waitingViews.remove(url);
                if (bitmap == null || views == null) {
                    return;
                }
                for (WeakReference<ImageView> reference : views) {
                    ImageView waitingView = reference.get();
                    if (waitingView != null && url.equals(waitingView.getTag())) {
                        waitingView.setImageBitmap(bitmap);
                    }
                }
            });
        });
    }

    private static ShapeDrawable createPlaceholder() {
        ShapeDrawable placeholder = new ShapeDrawable(new OvalShape());
        placeholder.getPaint().setColor(ThemeUtils.getAppForegroundColor());
        placeholder.setAlpha(0x33);
        return placeholder;
    }

    @Nullable
    private static Bitmap download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);

            try (InputStream stream = connection.getInputStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                if (bitmap != null && (bitmap.getWidth() > MAX_SIZE_PX || bitmap.getHeight() > MAX_SIZE_PX)) {
                    bitmap = Bitmap.createScaledBitmap(bitmap, MAX_SIZE_PX, MAX_SIZE_PX, true);
                }
                return bitmap;
            }
        } catch (Exception ex) {
            Logger.printDebug(() -> "Could not load subreddit icon: " + url, ex);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
