/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.settings.preference;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Dialog;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Outline;
import android.preference.Preference;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import app.morphe.extension.reddit.patches.MinScoreRules;
import app.morphe.extension.reddit.patches.MinScoreRules.Rule;
import app.morphe.extension.reddit.patches.SeenSubreddits;
import app.morphe.extension.reddit.patches.SeenSubredditsStore;
import app.morphe.extension.reddit.settings.Settings;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.theme.ThemeUtils;
import app.morphe.extension.shared.ui.CustomDialog;
import app.morphe.extension.shared.ui.Dim;

/**
 * Lists the minimum score rules with subreddit icons, and adds rules from subreddits seen in feeds.
 */
@SuppressWarnings("deprecation")
public final class MinScoreRulesPreference extends Preference {
    private static final int SUGGESTION_LIMIT = 50;
    private static final int LIST_MAX_SCREEN_PERCENT = 45;

    /**
     * Height of a row from {@link #createRow}: a 32 dp icon with 8 dp padding above and below.
     */
    private static final int ROW_HEIGHT = Dim.dp48;

    public MinScoreRulesPreference(Context context) {
        super(context);

        setTitle(str("morphe_min_score_rules_title"));
        refreshSummary();

        setOnPreferenceClickListener(preference -> {
            showRulesDialog();
            return true;
        });
    }

    private void refreshSummary() {
        List<Rule> rules = MinScoreRules.parseList(Settings.MIN_SCORE_RULES.get());
        final int defaultMinimum = Settings.MIN_SCORE_DEFAULT.get();
        if (rules.isEmpty() && defaultMinimum <= 0) {
            setSummary(str("morphe_min_score_rules_summary_empty"));
            return;
        }

        StringBuilder summary = new StringBuilder();
        for (Rule rule : rules) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(displayName(rule.name())).append(" ≥ ").append(rule.minimum());
        }
        if (defaultMinimum > 0) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(str("morphe_min_score_default_summary", String.valueOf(defaultMinimum)));
        }
        setSummary(summary.toString());
    }

    /**
     * @return The casing of the subreddit as last seen in a feed, otherwise the name as typed.
     */
    private static String displayName(String name) {
        SeenSubreddits.Entry entry = SeenSubredditsStore.get().find(name);
        return entry != null ? entry.name() : name;
    }

    @Nullable
    private static String iconUrl(String name) {
        SeenSubreddits.Entry entry = SeenSubredditsStore.get().find(name);
        return entry != null ? entry.iconUrl() : null;
    }

    private void showRulesDialog() {
        Context context = getContext();
        List<Rule> rules = MinScoreRules.parseList(Settings.MIN_SCORE_RULES.get());
        final int[] defaultMinimum = {Settings.MIN_SCORE_DEFAULT.get()};

        // The first row is the default minimum, the other rows are the rules.
        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return rules.size() + 1;
            }

            @Override
            public Object getItem(int position) {
                return position == 0 ? defaultMinimum[0] : rules.get(position - 1);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (position == 0) {
                    return createRow(context, null, str("morphe_min_score_default_title"),
                            defaultMinimum[0] > 0
                                    ? String.valueOf(defaultMinimum[0])
                                    : str("morphe_min_score_default_off"));
                }

                Rule rule = rules.get(position - 1);
                LinearLayout row = createRow(context, iconUrl(rule.name()), "r/" + displayName(rule.name()),
                        String.valueOf(rule.minimum()));

                TextView remove = createText(context, "✕", 18, 0.7f);
                remove.setPadding(Dim.dp16, Dim.dp4, Dim.dp4, Dim.dp4);
                remove.setFocusable(false);
                remove.setOnClickListener(view -> {
                    rules.remove(position - 1);
                    notifyDataSetChanged();
                });
                row.addView(remove);
                return row;
            }
        };

        ListView listView = new ListView(context);
        listView.setAdapter(adapter);
        listView.setDivider(null);
        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                setListHeight(listView, adapter.getCount());
            }
        });
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                showMinimumDialog(context, str("morphe_min_score_default_minimum_title"), defaultMinimum[0],
                        minimum -> {
                            defaultMinimum[0] = minimum;
                            adapter.notifyDataSetChanged();
                        });
                return;
            }

            Rule rule = rules.get(position - 1);
            showMinimumDialog(context, str("morphe_min_score_minimum_title", displayName(rule.name())),
                    rule.minimum(), minimum -> {
                        rules.set(position - 1, new Rule(rule.name(), minimum));
                        adapter.notifyDataSetChanged();
                    });
        });

        Pair<Dialog, LinearLayout> dialogPair = CustomDialog.create(
                context,
                str("morphe_min_score_rules_title"),
                str("morphe_min_score_rules_dialog_message"),
                null,
                null,
                () -> {
                    List<Rule> saved = new ArrayList<>();
                    for (Rule rule : rules) {
                        saved.add(new Rule(displayName(rule.name()), rule.minimum()));
                    }
                    Settings.MIN_SCORE_RULES.save(MinScoreRules.format(saved));
                    Settings.MIN_SCORE_DEFAULT.save(defaultMinimum[0]);
                    refreshSummary();
                },
                () -> {},
                str("morphe_min_score_add"),
                () -> showAddDialog(context, name ->
                        showMinimumDialog(context, str("morphe_min_score_minimum_title", displayName(name)), null,
                                minimum -> {
                                    addOrReplace(rules, new Rule(name, minimum));
                                    adapter.notifyDataSetChanged();
                                })),
                false
        );

        LinearLayout mainLayout = dialogPair.second;
        mainLayout.addView(listView, mainLayout.getChildCount() - 1);
        setListHeight(listView, adapter.getCount());
        dialogPair.first.show();
    }

    /**
     * The dialog wraps its content, so the list needs an explicit height:
     * all rows, but at most part of the screen (the list scrolls beyond that).
     */
    private static void setListHeight(ListView listView, int rowCount) {
        final int maxHeight = Dim.getScreenHeight() * LIST_MAX_SCREEN_PERCENT / 100;
        listView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(rowCount * ROW_HEIGHT, maxHeight)));
    }

    /**
     * Adds the rule, or replaces the rule of the same subreddit (ignoring case) in place.
     */
    private static void addOrReplace(List<Rule> rules, Rule rule) {
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).name().equalsIgnoreCase(rule.name())) {
                rules.set(i, rule);
                return;
            }
        }
        rules.add(rule);
    }

    /**
     * Search of subreddits seen in feeds. The first row uses the typed name if it was not seen.
     */
    private static void showAddDialog(Context context, Consumer<String> onPicked) {
        EditText search = CustomDialog.createEditText(context);
        search.setHint(str("morphe_min_score_search_hint"));
        search.setSingleLine(true);

        // Null is the typed name row.
        List<SeenSubreddits.Entry> rows = new ArrayList<>();
        final String[] typedName = {null};

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return rows.size();
            }

            @Override
            public SeenSubreddits.Entry getItem(int position) {
                return rows.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                SeenSubreddits.Entry entry = getItem(position);
                return entry == null
                        ? createRow(context, null, str("morphe_min_score_use_typed", typedName[0]), null)
                        : createRow(context, entry.iconUrl(), "r/" + entry.name(), null);
            }
        };

        Runnable updateRows = () -> {
            String query = MinScoreRules.stripPrefix(search.getText().toString());
            typedName[0] = query;
            rows.clear();
            if (MinScoreRules.isValidSubredditName(query) && SeenSubredditsStore.get().find(query) == null) {
                rows.add(null);
            }
            rows.addAll(SeenSubredditsStore.get().search(query == null ? "" : query, SUGGESTION_LIMIT));
            adapter.notifyDataSetChanged();
        };
        updateRows.run();

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateRows.run();
            }
        });

        ListView listView = new ListView(context);
        listView.setAdapter(adapter);
        listView.setDivider(null);

        Pair<Dialog, LinearLayout> dialogPair = CustomDialog.create(
                context,
                str("morphe_min_score_add"),
                null,
                null,
                null,
                () -> {
                    if (MinScoreRules.isValidSubredditName(typedName[0])) {
                        onPicked.accept(typedName[0]);
                    }
                },
                () -> {},
                null,
                null,
                false
        );

        Dialog dialog = dialogPair.first;
        listView.setOnItemClickListener((parent, view, position, id) -> {
            SeenSubreddits.Entry entry = rows.get(position);
            dialog.dismiss();
            onPicked.accept(entry == null ? typedName[0] : entry.name());
        });

        LinearLayout mainLayout = dialogPair.second;
        mainLayout.addView(search, mainLayout.getChildCount() - 1);
        mainLayout.addView(listView, mainLayout.getChildCount() - 1);
        // A fixed height, so the dialog does not jump while typing.
        setListHeight(listView, Integer.MAX_VALUE / ROW_HEIGHT);
        dialog.show();
    }

    private static void showMinimumDialog(Context context, String title, @Nullable Integer current,
                                          IntConsumer onSaved) {
        showMinimumTextDialog(context, title, current == null ? "" : String.valueOf(current), onSaved);
    }

    /**
     * Invalid input shows a toast and opens the dialog again with the same text.
     */
    private static void showMinimumTextDialog(Context context, String title, String text, IntConsumer onSaved) {
        EditText input = CustomDialog.createEditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(text);
        input.setSelection(text.length());

        CustomDialog.create(
                context,
                title,
                null,
                input,
                null,
                () -> {
                    String value = input.getText().toString();
                    Integer minimum = MinScoreRules.parseMinimum(value);
                    if (minimum == null) {
                        Utils.showToastShort(str("morphe_min_score_minimum_invalid"));
                        showMinimumTextDialog(context, title, value, onSaved);
                    } else {
                        onSaved.accept(minimum);
                    }
                },
                () -> {},
                null,
                null,
                false
        ).first.show();
    }

    private static LinearLayout createRow(Context context, @Nullable String iconUrl, String title,
                                          @Nullable String trailing) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Dim.dp8, Dim.dp8, Dim.dp8, Dim.dp8);

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        icon.setClipToOutline(true);
        SubredditIconLoader.load(icon, iconUrl);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(Dim.dp32, Dim.dp32);
        iconParams.setMarginEnd(Dim.dp12);
        row.addView(icon, iconParams);

        TextView titleView = createText(context, title, 16, 1);
        titleView.setSingleLine(true);
        row.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        if (trailing != null) {
            row.addView(createText(context, trailing, 16, 0.7f));
        }
        return row;
    }

    private static TextView createText(Context context, String text, float sizeSp, float alpha) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(ThemeUtils.getAppForegroundColor());
        view.setAlpha(alpha);
        return view;
    }
}
