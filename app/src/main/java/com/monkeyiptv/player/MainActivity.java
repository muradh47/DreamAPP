package com.monkeyiptv.player;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.database.Cursor;
import android.net.Uri;
import android.graphics.Color;
import android.widget.*;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.view.View;
import android.view.Gravity;
import android.graphics.drawable.GradientDrawable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.*;
import java.io.*;

public class MainActivity extends Activity {
    private LinearLayout root;
    private ExoPlayer player;
    private final android.os.Handler epgHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable epgRefresh;
    private int epgRequest = 0;
    private String username, password;
    private String currentType = "live";
    private String currentCategory = "";
    private String server = BuildConfig.SERVER_URL.replaceAll("/$", "");
    private static final int UPDATE_INSTALL_REQUEST = 4701;
    private TextView updateInstallStatus;
    private long updateDownloadId = -1;
    private Uri pendingUpdateApk;
    private BroadcastReceiver updateReceiver;

    private static final String RELEASES_API = "https://api.github.com/repos/muradh47/DreamAPP/releases/latest";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10, 15, 31));
        getWindow().setNavigationBarColor(Color.rgb(10, 15, 31));
        username = getPreferences(0).getString("username", "");
        password = getPreferences(0).getString("password", "");
        if (!username.isEmpty() && !password.isEmpty()) showHome(); else showLogin();
    }

    private TextView text(String value) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextColor(Color.WHITE); view.setTextSize(19);
        view.setPadding(14, 16, 14, 16); return view;
    }

    private void screen(String title) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(24));
        root.setBackgroundColor(Color.rgb(13, 18, 30));
        scroll.addView(root); setContentView(scroll);
        TextView heading = text(title);
        heading.setTextSize(27); heading.setPadding(dp(8), dp(8), dp(8), dp(18)); root.addView(heading);
    }

    private Button button(String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setOnClickListener(v -> action.run());
        root.addView(b);
        return b;
    }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }

    private Button tile(String title, int color, Runnable action) {
        Button b = new Button(this);
        b.setText(title.replace("\\\\n", "\\n")); b.setTextSize(19); b.setAllCaps(false);
        b.setGravity(Gravity.CENTER); b.setPadding(dp(8), dp(8), dp(8), dp(8));
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setTextColor(Color.WHITE); b.setFocusable(true);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{color, darken(color)});
        bg.setCornerRadius(dp(12)); b.setBackground(bg);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void addTile(LinearLayout row, String title, int color, int height, Runnable action) {
        Button b = tile(title, color, action);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(height), 1f);
        p.setMargins(dp(5), dp(5), dp(5), dp(5)); row.addView(b, p);
    }

    private int darken(int c) { return Color.rgb((int)(Color.red(c)*0.70), (int)(Color.green(c)*0.70), (int)(Color.blue(c)*0.78)); }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    private void showLogin() {
        screen("DREAM IPTV");
        TextView welcome = text("Welcome to Dream IPTV");
        welcome.setTextSize(26);
        welcome.setGravity(Gravity.CENTER);
        root.addView(welcome);
        TextView subtitle = text("Sign in to enjoy your entertainment");
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextColor(Color.LTGRAY);
        root.addView(subtitle);

        EditText user = loginField("Username", false);
        android.content.SharedPreferences prefs = getPreferences(0);
        boolean rememberEnabled = prefs.getBoolean("remember_login", false);
        if (rememberEnabled) {
            user.setText(prefs.getString("saved_username", ""));
        }
        CheckBox remember = new CheckBox(this);
        remember.setText("Remember username and password");
        remember.setTextColor(Color.WHITE);
        remember.setChecked(rememberEnabled);
        root.addView(remember);
        EditText pass = loginField("Password", true);
        if (rememberEnabled) pass.setText(prefs.getString("saved_password", ""));
        TextView status = text("");
        status.setGravity(Gravity.CENTER);
        root.addView(status);
        Button login = tile("SIGN IN", Color.rgb(20, 122, 196), () -> {
            String u = user.getText().toString().trim(), p = pass.getText().toString();
            if (u.isEmpty() || p.isEmpty()) { status.setText("Enter username and password"); return; }
            status.setText("Connecting...");
            new Thread(() -> {
                try {
                    JSONObject response = new JSONObject(fetch(api(u, p, "")));
                    JSONObject info = response.optJSONObject("user_info");
                    if (info == null || !"Active".equalsIgnoreCase(info.optString("status")))
                        throw new IOException("Login failed or account inactive");
                    username = u; password = p;
                    boolean saveUsername = remember.isChecked();
                    android.content.SharedPreferences.Editor editor = getPreferences(0).edit()
                        .putString("username", u).putString("password", p)
                        .putBoolean("remember_login", saveUsername);
                    if (saveUsername) editor.putString("saved_username", u).putString("saved_password", p);
                    else editor.remove("saved_username").remove("saved_password");
                    editor.apply();
                    runOnUiThread(this::showHome);
                } catch (Exception ex) { runOnUiThread(() -> status.setText("Login failed: " + ex.getMessage())); }
            }).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(60));
        lp.setMargins(dp(12), dp(18), dp(12), dp(12));
        root.addView(login, lp);
        TextView note = text("Your server is configured automatically.");
        note.setGravity(Gravity.CENTER);
        note.setTextSize(14);
        note.setTextColor(Color.LTGRAY);
        root.addView(note);
    }

    private EditText loginField(String hint, boolean passwordField) {
        EditText field = new EditText(this);
        field.setHint(hint); field.setSingleLine(true);
        if (passwordField) field.setInputType(129);
        field.setTextColor(Color.WHITE); field.setHintTextColor(Color.LTGRAY);
        field.setTextSize(18); field.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(29, 40, 65));
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), Color.rgb(67, 87, 121));
        field.setBackground(background);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(62));
        lp.setMargins(dp(12), dp(10), dp(12), dp(10));
        root.addView(field, lp);
        return field;
    }

    private void showHome() {
        releasePlayer();
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        getWindow().getDecorView().setSystemUiVisibility(0);
        screen("DREAM IPTV");
        root.setPadding(dp(28), dp(12), dp(28), dp(20));
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(5, 22, 56), Color.rgb(8, 16, 37), Color.rgb(4, 12, 29)}));
        TextView heading = (TextView) root.getChildAt(0);
        heading.setTextSize(30);
        heading.setGravity(Gravity.CENTER);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(dp(8), dp(4), dp(8), dp(4));
        TextView intro = text("Select an option to continue");
        intro.setTextSize(16);
        intro.setGravity(Gravity.CENTER);
        intro.setTextColor(Color.rgb(192, 211, 237));
        intro.setPadding(dp(8), 0, dp(8), dp(12));
        root.addView(intro);

        // TV-first home: three primary sections and four compact shortcuts.
        LinearLayout primary = row();
        Button live = homeTile("▣\\nLIVE TV", Color.rgb(5, 91, 211), () -> showCategories("live"));
        addHomeTile(primary, live, 185);
        addHomeTile(primary, homeTile("▶\\nMOVIES", Color.rgb(204, 35, 46),
            () -> showCategories("vod")), 185);
        addHomeTile(primary, homeTile("▤\\nSERIES", Color.rgb(104, 32, 174),
            () -> showCategories("series")), 185);

        LinearLayout shortcuts = row();
        addHomeTile(shortcuts, homeTile("◷  CATCH UP", Color.rgb(16, 54, 98),
            () -> showCategories("live")), 92);
        addHomeTile(shortcuts, homeTile("▦  TV GUIDE", Color.rgb(16, 54, 98),
            this::showGuide), 92);
        addHomeTile(shortcuts, homeTile("♡  FAVOURITES", Color.rgb(16, 54, 98),
            () -> showCategories("live")), 92);
        addHomeTile(shortcuts, homeTile("⚙  SETTINGS", Color.rgb(16, 54, 98),
            this::showHomeSettings), 92);
        live.requestFocus();
    }

    private Button homeTile(String label, int color, Runnable action) {
        Button tile = tile(label, color, action);
        tile.setTextSize(21);
        tile.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tile.setLineSpacing(dp(4), 1f);
        tile.setOnFocusChangeListener((view, focused) -> {
            GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{focused ? Color.rgb(28, 116, 245) : color, darken(color)});
            background.setCornerRadius(dp(18));
            background.setStroke(dp(focused ? 3 : 1),
                focused ? Color.rgb(110, 210, 255) : Color.rgb(55, 86, 132));
            tile.setBackground(background);
            tile.setScaleX(focused ? 1.02f : 1f);
            tile.setScaleY(focused ? 1.02f : 1f);
        });
        return tile;
    }

    private void addHomeTile(LinearLayout parent, Button tile, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(height), 1f);
        params.setMargins(dp(6), dp(6), dp(6), dp(6));
        parent.addView(tile, params);
    }

    private void showHomeSettings() {
        screen("SETTINGS");
        button("← HOME", this::showHome);
        button("CHECK FOR UPDATES", this::checkUpdates);
        button("LOG OUT", () -> {
            getPreferences(0).edit().remove("username").remove("password").apply();
            username = "";
            password = "";
            showLogin();
        });
    }

    private void showGuide() {
        screen("Loading TV guide...");
        new Thread(() -> {
            try {
                JSONArray channels = new JSONArray(fetch(api(username, password, "get_live_streams")));
                runOnUiThread(() -> {
                    screen("TV GUIDE");
                    button("← Home", this::showHome);
                    for (int i = 0; i < channels.length(); i++) {
                        JSONObject ch = channels.optJSONObject(i);
                        if (ch == null) continue;
                        String id = ch.optString("stream_id");
                        if (id.isEmpty()) continue;
                        button(ch.optString("name", "Channel") + "  →", () -> showEpg(id, ch.optString("name")));
                    }
                });
            } catch (Exception ex) { runOnUiThread(() -> {
                screen("TV guide unavailable"); root.addView(text(ex.getMessage()));
                button("← Home", this::showHome);
            }); }
        }).start();
    }

    private void showChannel(String id, String name) {
        showEpg(id, name, false);
    }

    private void showEpg(String id, String channel) {
        showEpg(id, channel, true);
    }

    private void showEpg(String id, String channel, boolean fromGuide) {
        releasePlayer();
        showFullscreenLive(id, channel, fromGuide);
    }

    private void showFullscreenLive(String id, String channel, boolean fromGuide) {
        releasePlayer();
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        android.widget.FrameLayout fullscreen = new android.widget.FrameLayout(this);
        fullscreen.setBackgroundColor(Color.BLACK);
        setContentView(fullscreen);
        PlayerView fullscreenView = new PlayerView(this);
        fullscreenView.setUseController(false);
        fullscreenView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        fullscreen.addView(fullscreenView, new android.widget.FrameLayout.LayoutParams(-1, -1));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(14), dp(8), dp(14), dp(8));
        overlay.setBackgroundColor(Color.argb(210, 12, 18, 30));
        android.widget.FrameLayout.LayoutParams overlayParams = new android.widget.FrameLayout.LayoutParams(
            -1, -2, Gravity.BOTTOM);
        fullscreen.addView(overlay, overlayParams);
        TextView title = text(channel);
        title.setTextSize(17);
        title.setTextColor(Color.WHITE);
        overlay.addView(title);
        TextView now = text("NOW  •  Loading programme information...");
        now.setTextSize(14);
        now.setMaxLines(2);
        now.setEllipsize(android.text.TextUtils.TruncateAt.END);
        overlay.addView(now);
        TextView next = text("NEXT  •  Loading...");
        next.setTextSize(14);
        next.setMaxLines(2);
        next.setEllipsize(android.text.TextUtils.TruncateAt.END);
        overlay.addView(next);
        // Dream IPTV player controls: TV remote focusable and touch friendly.
        LinearLayout playerControls = new LinearLayout(this);
        playerControls.setOrientation(LinearLayout.HORIZONTAL);
        Button pauseButton = new Button(this);
        pauseButton.setText("PAUSE");
        pauseButton.setAllCaps(false);
        pauseButton.setFocusable(true);
        pauseButton.setOnClickListener(v -> {
            if (player == null) return;
            if (player.isPlaying()) {
                player.pause();
                pauseButton.setText("PLAY");
            } else {
                player.play();
                pauseButton.setText("PAUSE");
            }
        });
        playerControls.addView(pauseButton, new LinearLayout.LayoutParams(0, dp(40), 1f));
        Button fitButton = new Button(this);
        fitButton.setText("ASPECT: FIT");
        fitButton.setAllCaps(false);
        fitButton.setFocusable(true);
        fitButton.setOnClickListener(v -> {
            boolean zoom = fullscreenView.getResizeMode()
                == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT;
            fullscreenView.setResizeMode(zoom
                ? androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                : androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            fitButton.setText(zoom ? "ASPECT: ZOOM" : "ASPECT: FIT");
        });
        playerControls.addView(fitButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        Button guideButton = new Button(this);
        guideButton.setText("TV GUIDE");
        guideButton.setFocusable(true);
        guideButton.setOnClickListener(v -> {
            releasePlayer();
            getWindow().getDecorView().setSystemUiVisibility(0);
            showGuide();
        });
        playerControls.addView(guideButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        overlay.addView(playerControls);
        TextView playerStatus = text("Connecting to live stream...");
        playerStatus.setTextSize(14);
        overlay.addView(playerStatus);
        LinearLayout schedule = new LinearLayout(this); // EPG data only; full listings live in TV GUIDE.
        overlay.setVisibility(View.GONE);

        android.view.View tapArea = new android.view.View(this);
        tapArea.setClickable(true);
        fullscreen.addView(tapArea, new android.widget.FrameLayout.LayoutParams(-1, -1));
        tapArea.setOnClickListener(v -> overlay.setVisibility(
            overlay.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        overlay.setOnClickListener(v -> overlay.setVisibility(View.GONE));
        overlay.setClickable(true);
        Button back = new Button(this);
        back.setText("← BACK");
        android.widget.FrameLayout.LayoutParams backParams = new android.widget.FrameLayout.LayoutParams(
            dp(104), dp(40), Gravity.TOP | Gravity.START);
        backParams.setMargins(dp(12), dp(12), 0, 0);
        fullscreen.addView(back, backParams);
        back.setOnClickListener(v -> {
            releasePlayer();
            getWindow().getDecorView().setSystemUiVisibility(0);
            if (fromGuide) showGuide(); else showCategories("live");
        });

        int request = ++epgRequest;
        epgRefresh = new Runnable() {
            @Override public void run() {
                if (request != epgRequest) return;
                loadChannelEpg(id, request, now, next, schedule, true);
                epgHandler.postDelayed(this, 60000);
            }
        };
        epgHandler.post(epgRefresh);
        startStream("live", id, "ts", fullscreenView);
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean playing) {
                pauseButton.setText(playing ? "PAUSE" : "PLAY");
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) playerStatus.setText("");
                else if (state == Player.STATE_BUFFERING) playerStatus.setText("Buffering...");
            }
            @Override public void onPlayerError(PlaybackException error) {
                playerStatus.setText("Stream error: " + error.getErrorCodeName());
            }
        });
        pauseButton.requestFocus();
    }

    private void showEpgInline(String id, String channel, boolean fromGuide) {
        releasePlayer();
        screen(channel);
        button("← " + (fromGuide ? "TV Guide" : "Channels"),
            () -> { releasePlayer(); if (fromGuide) showGuide(); else showCategories("live"); });
        PlayerView view = new PlayerView(this);
        root.addView(view, new LinearLayout.LayoutParams(-1, dp(240)));
        addFullscreenToggle(view);
        startStream("live", id, "ts", view);
        TextView playbackStatus = text("Connecting to live stream...");
        root.addView(playbackStatus);
        attachPlaybackStatus(playbackStatus, id);
        root.addView(text("NOW PLAYING"));
        TextView now = text("Loading programme information...");
        root.addView(now);
        root.addView(text("UP NEXT"));
        TextView next = text("Loading...");
        root.addView(next);
        root.addView(text("TV PROGRAMME GUIDE"));
        LinearLayout schedule = new LinearLayout(this);
        schedule.setOrientation(LinearLayout.VERTICAL);
        root.addView(schedule);
        int request = ++epgRequest;
        epgRefresh = new Runnable() {
            @Override public void run() {
                if (request != epgRequest) return;
                loadChannelEpg(id, request, now, next, schedule);
                epgHandler.postDelayed(this, 60000);
            }
        };
        epgHandler.post(epgRefresh);
    }

    private void addFullscreenToggle(PlayerView view) {
        Button fullscreen = button("⛶ FULL SCREEN", () -> {});
        final boolean[] expanded = {false};
        final int normalHeight = ((LinearLayout.LayoutParams) view.getLayoutParams()).height;
        fullscreen.setOnClickListener(v -> {
            expanded[0] = !expanded[0];
            boolean full = expanded[0];
            for (int i = 0; i < root.getChildCount(); i++) {
                android.view.View child = root.getChildAt(i);
                if (child != view && child != fullscreen) child.setVisibility(full ? android.view.View.GONE : android.view.View.VISIBLE);
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) view.getLayoutParams();
            lp.height = full ? getResources().getDisplayMetrics().heightPixels : normalHeight;
            view.setLayoutParams(lp);
            getWindow().getDecorView().setSystemUiVisibility(full
                ? (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                   android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                   android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
                : 0);
            fullscreen.setText(full ? "EXIT FULL SCREEN" : "⛶ FULL SCREEN");
        });
    }

    private void loadChannelEpg(String id, int request, TextView now, TextView next, LinearLayout schedule) {
        loadChannelEpg(id, request, now, next, schedule, false);
    }

    private void loadChannelEpg(String id, int request, TextView now, TextView next, LinearLayout schedule, boolean compact) {
        new Thread(() -> {
            try {
                JSONObject data = new JSONObject(fetch(api(username, password, "get_short_epg")
                    + "&stream_id=" + enc(id) + "&limit=20"));
                JSONArray entries = data.optJSONArray("epg_listings");
                runOnUiThread(() -> {
                    if (request != epgRequest) return;
                    schedule.removeAllViews();
                    long current = System.currentTimeMillis();
                    JSONObject playing = null, upcoming = null;
                    if (entries != null) {
                        for (int i = 0; i < entries.length(); i++) {
                            JSONObject item = entries.optJSONObject(i);
                            if (item == null) continue;
                            long start = epgTime(item.optString("start_timestamp"), item.optString("start"));
                            long end = epgTime(item.optString("stop_timestamp"), item.optString("end"));
                            if (start <= current && end > current) playing = item;
                            if (start > current && (upcoming == null ||
                                start < epgTime(upcoming.optString("start_timestamp"), upcoming.optString("start"))))
                                upcoming = item;
                            if (!compact) {
                                TextView programme = text(epgLabel(item));
                                programme.setTextSize(16);
                                programme.setBackgroundColor(i % 2 == 0 ? Color.rgb(24, 37, 62) : Color.rgb(18, 28, 49));
                                schedule.addView(programme);
                            }
                        }
                    }
                    now.setText(playing == null ? "NOW  •  Programme unavailable" :
                        (compact ? "NOW  •  " + shortEpgLabel(playing) : epgLabel(playing)));
                    next.setText(upcoming == null ? "NEXT  •  Programme unavailable" :
                        (compact ? "NEXT  •  " + shortEpgLabel(upcoming) : epgLabel(upcoming)));
                });
            } catch (Exception ex) {
                runOnUiThread(() -> { if (request == epgRequest) {
                    now.setText("EPG unavailable: " + ex.getMessage());
                    next.setText("No upcoming programme information available");
                }});
            }
        }).start();
    }

    private String shortEpgLabel(JSONObject programme) {
        String label = epgLabel(programme);
        String[] lines = label.split("\\n");
        return lines.length > 1 ? lines[1].trim() : label.trim();
    }

    private long epgTime(String timestamp, String date) {
        try {
            if (!timestamp.isEmpty()) {
                long n = Long.parseLong(timestamp);
                return n < 100000000000L ? n * 1000L : n;
            }
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
            java.util.Date parsed = format.parse(date);
            return parsed == null ? -1 : parsed.getTime();
        } catch (Exception ignored) { return -1; }
    }

    private String epgLabel(JSONObject item) {
        String start = item.optString("start", "");
        String end = item.optString("end", "");
        String times = start.length() >= 16 && end.length() >= 16
            ? start.substring(11, 16) + " - " + end.substring(11, 16) : start + " - " + end;
        String title = decodeEpg(item.optString("title", ""));
        String description = decodeEpg(item.optString("description", ""));
        return times + "\n" + title + (description.isEmpty() ? "" : "\n" + description);
    }

    private String decodeEpg(String raw) {
        if (raw.isEmpty()) return raw;
        try {
            byte[] bytes = android.util.Base64.decode(raw, android.util.Base64.DEFAULT);
            String decoded = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            return decoded.indexOf((char) 0xFFFD) >= 0 ? raw.replace("\\\\n", "\n") : decoded.replace("\\\\n", "\n");
        } catch (Exception ignored) { return raw.replace("\\\\n", "\n"); }
    }

    private String api(String u, String p, String action) throws Exception {
        return "player_api.php?username=" + enc(u) + "&password=" + enc(p)
            + (action.isEmpty() ? "" : "&action=" + action);
    }

    private LinearLayout categorySidebar;
    private GridLayout contentGrid;
    private TextView browserHeading;
    private int browseRequest = 0;
    private static final int PAGE_SIZE = 45;
    private JSONArray currentItems;
    private int nextItemIndex = 0;
    private String currentBrowseType = "live";

    private void browserShell(String type) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(11, 16, 35));
        setContentView(page);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(68)));
        Button back = tile("‹ CATEGORIES", Color.rgb(34, 47, 76), () -> showCategories(type));
        back.setTextSize(14);
        header.addView(back, new LinearLayout.LayoutParams(dp(155), dp(48)));
        browserHeading = text(type.equals("live") ? "LIVE TV" : type.equals("vod") ? "MOVIES" : "SERIES");
        browserHeading.setTextSize(20);
        header.addView(browserHeading, new LinearLayout.LayoutParams(0, -2, 1));
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        contentGrid = new GridLayout(this);
        contentGrid.setColumnCount(type.equals("live") ? 2 : 4);
        contentGrid.setPadding(dp(12), dp(10), dp(12), dp(10));
        scroll.addView(contentGrid);
        categorySidebar = new LinearLayout(this);
    }

    private void sidebarItem(String title, boolean selected, Runnable action) {
        Button item = tile(title, selected ? Color.rgb(23, 110, 185) : Color.rgb(29, 39, 65), action);
        item.setTextSize(15);
        item.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(60));
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        categorySidebar.addView(item, p);
    }

    private final java.util.Map<String, JSONArray> categoryCache = new java.util.HashMap<>();
    private final java.util.Map<String, JSONArray> itemCache = new java.util.LinkedHashMap<>();

    private void showCategories(String type) {
        currentType = type;
        ++browseRequest;
        screen(type.equals("live") ? "LIVE TV  •  CATEGORIES" :
            type.equals("vod") ? "MOVIES  •  CATEGORIES" : "SERIES  •  CATEGORIES");
        Button home = button("‹  HOME", this::showHome);
        home.setTextSize(14);
        TextView hint = text("Choose a category");
        hint.setTextSize(15);
        root.addView(hint);
        GridLayout categoriesGrid = new GridLayout(this);
        categoriesGrid.setColumnCount(2);
        categoriesGrid.setUseDefaultMargins(false);
        root.addView(categoriesGrid, new LinearLayout.LayoutParams(-1, -2));
        JSONArray cached = categoryCache.get(type);
        if (cached != null) {
            renderCategoryTiles(type, cached, categoriesGrid);
            return;
        }
        TextView loading = text("Loading categories...");
        root.addView(loading);
        new Thread(() -> {
            try {
                String action = type.equals("live") ? "get_live_categories" :
                    type.equals("vod") ? "get_vod_categories" : "get_series_categories";
                JSONArray categories = new JSONArray(fetch(api(username, password, action)));
                categoryCache.put(type, categories);
                runOnUiThread(() -> {
                    if (!type.equals(currentType) || categoriesGrid.getParent() == null) return;
                    root.removeView(loading);
                    renderCategoryTiles(type, categories, categoriesGrid);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (categoriesGrid.getParent() == null) return;
                    loading.setText("Categories unavailable. Tap to retry.");
                    loading.setOnClickListener(v -> showCategories(type));
                });
            }
        }).start();
    }

    private void renderCategoryTiles(String type, JSONArray categories, GridLayout grid) {
        grid.removeAllViews();
        addCategoryTile(grid, "▣  ALL " + (type.equals("live") ? "CHANNELS" :
            type.equals("vod") ? "MOVIES" : "SERIES"), () -> openCategory(type, ""));
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.optJSONObject(i);
            if (category == null) continue;
            String id = category.optString("category_id");
            String name = category.optString("category_name", "Other");
            addCategoryTile(grid, "▣  " + name, () -> openCategory(type, id));
        }
    }

    private void addCategoryTile(GridLayout grid, String name, Runnable action) {
        Button item = tile(name, Color.rgb(27, 43, 74), action);
        item.setTextSize(15);
        item.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        item.setPadding(dp(16), dp(5), dp(8), dp(5));
        item.setSingleLine(true);
        item.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.setOnFocusChangeListener((v, focused) -> {
            item.setBackgroundColor(focused ? Color.rgb(39, 111, 190) : Color.rgb(27, 43, 74));
            item.setTextColor(Color.WHITE);
        });
        int width = getResources().getDisplayMetrics().widthPixels - dp(48);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = Math.max(dp(130), width / 2 - dp(8));
        params.height = dp(58);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        grid.addView(item, params);
    }

    private void openCategory(String type, String categoryId) {
        browserShell(type);
        String categoryName = "ALL";
        JSONArray categories = categoryCache.get(type);
        if (categories != null) for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.optJSONObject(i);
            if (category != null && categoryId.equals(category.optString("category_id"))) {
                categoryName = category.optString("category_name", "Category");
                break;
            }
        }
        final String selectedCategory = categoryName;
        browserHeading.setText(selectedCategory);
        showItems(type, categoryId);
        browserHeading.setText(selectedCategory);
    }

    private void showItems(String type, String categoryId) {
        currentType = type;
        currentCategory = categoryId;
        int requestId = ++browseRequest;
        currentItems = null;
        loadMoreButton = null;
        if (contentGrid == null) { openCategory(type, categoryId); return; }
        contentGrid.removeAllViews();
        contentGrid.addView(text("Loading channels and posters..."));
        String cacheKey = type + ":" + categoryId;
        JSONArray cachedItems = itemCache.get(cacheKey);
        if (cachedItems != null) {
            contentGrid.removeAllViews();
            currentItems = cachedItems;
            currentBrowseType = type;
            nextItemIndex = 0;
            appendNextPage();
            return;
        }
        new Thread(() -> {
            try {
                String action = type.equals("live") ? "get_live_streams" :
                    type.equals("vod") ? "get_vod_streams" : "get_series";
                String request = api(username, password, action);
                if (!categoryId.isEmpty()) request += "&category_id=" + enc(categoryId);
                JSONArray items = new JSONArray(fetch(request));
                synchronized (itemCache) {
                    if (itemCache.size() >= 8) itemCache.remove(itemCache.keySet().iterator().next());
                    itemCache.put(cacheKey, items);
                }
                runOnUiThread(() -> {
                    if (requestId != browseRequest || contentGrid == null) return;
                    contentGrid.removeAllViews();
                    if (items.length() == 0) contentGrid.addView(text("No content in this category"));
                    currentItems = items;
                    currentBrowseType = type;
                    nextItemIndex = 0;
                    appendNextPage();

                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (requestId != browseRequest || contentGrid == null) return;
                    contentGrid.removeAllViews();
                    contentGrid.addView(text("Unable to load: " + ex.getMessage()));
                });
            }
        }).start();
    }

    private void appendNextPage() {
        if (currentItems == null || contentGrid == null) return;
        int end = Math.min(currentItems.length(), nextItemIndex + PAGE_SIZE);
        for (int i = nextItemIndex; i < end; i++) {
            JSONObject item = currentItems.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString(currentBrowseType.equals("series") ? "series_id" : "stream_id");
            if (id.isEmpty()) continue;
            String name = item.optString("name", "Untitled");
            String ext = item.optString("container_extension", "mp4");
            String logo = currentBrowseType.equals("live") ? item.optString("stream_icon") :
                currentBrowseType.equals("vod") ? item.optString("stream_icon", item.optString("cover")) :
                item.optString("cover", item.optString("stream_icon"));
            channelCard(name, logo, currentBrowseType, id, ext, item);
        }
        nextItemIndex = end;
        if (nextItemIndex < currentItems.length()) {
            Button more = tile("LOAD MORE  (" + nextItemIndex + " / " + currentItems.length() + ")",
                Color.rgb(27, 94, 154), () -> {
                    if (loadMoreButton != null) contentGrid.removeView(loadMoreButton);
                    loadMoreButton = null;
                    appendNextPage();
                });
            loadMoreButton = more;
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = currentBrowseType.equals("live") ? dp(260) : dp(155);
            gp.height = dp(70);
            gp.setMargins(dp(6), dp(6), dp(6), dp(6));
            contentGrid.addView(more, gp);
        }
    }

    private Button loadMoreButton;

    private void channelCard(String name, String logo, String type, String id, String ext, JSONObject metadata) {
        if (type.equals("live")) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(5), dp(12), dp(5));
            row.setBackgroundColor(Color.rgb(27, 37, 57));
            row.setFocusable(true);
            row.setClickable(true);
            row.setOnClickListener(v -> showChannel(id, name));
            row.setOnFocusChangeListener((v, focused) -> {
                row.setBackgroundColor(focused ? Color.rgb(35, 111, 191) : Color.rgb(27, 37, 57));
                row.setScaleX(focused ? 1.015f : 1f);
                row.setScaleY(focused ? 1.015f : 1f);
            });
            ImageView logoView = new ImageView(this);
            logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logoView.setImageResource(android.R.drawable.ic_media_play);
            row.addView(logoView, new LinearLayout.LayoutParams(dp(58), dp(52)));
            loadArtwork(logo, logoView);
            TextView titleView = text(name);
            titleView.setTextSize(17);
            titleView.setMaxLines(2);
            titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            titleView.setGravity(Gravity.CENTER_VERTICAL);
            titleView.setPadding(dp(12), 0, 0, 0);
            row.addView(titleView, new LinearLayout.LayoutParams(0, -1, 1f));
            GridLayout.LayoutParams rowParams = new GridLayout.LayoutParams();
            int available = getResources().getDisplayMetrics().widthPixels - dp(52);
            rowParams.width = Math.max(dp(200), available / 2 - dp(12));
            rowParams.height = dp(68);
            rowParams.setMargins(dp(5), dp(5), dp(5), dp(5));
            contentGrid.addView(row, rowParams);
            if (contentGrid.getChildCount() == 1) row.requestFocus();
            return;
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.rgb(29, 36, 52));
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            if (type.equals("live")) showChannel(id, name);
            else if (type.equals("series")) showEpisodes(id, name);
            else play(type, id, ext);
        });
        card.setOnFocusChangeListener((v, focused) -> card.setBackgroundColor(
            focused ? Color.rgb(33, 107, 187) : Color.rgb(29, 36, 52)));
        ImageView image = new ImageView(this);
        image.setImageResource(android.R.drawable.ic_menu_gallery);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(105)));
        loadArtwork(logo, image);
        TextView label = text(name);
        label.setTextSize(14);
        label.setMaxLines(2);
        label.setGravity(Gravity.CENTER);
        card.addView(label, new LinearLayout.LayoutParams(-1, dp(45)));
        if (!type.equals("live")) {
            String rating = metadata.optString("rating_5based", metadata.optString("rating", ""));
            String year = metadata.optString("releaseDate", metadata.optString("release_date", ""));
            String imdb = metadata.optString("imdb_id", "");
            String details = (year.length() >= 4 ? year.substring(0, 4) : "") +
                (!rating.isEmpty() ? "  ★ " + rating : "") +
                (!imdb.isEmpty() ? "  IMDb" : "");
            if (!details.trim().isEmpty()) {
                TextView info = text(details.trim());
                info.setTextSize(11);
                info.setTextColor(Color.LTGRAY);
                info.setGravity(Gravity.CENTER);
                info.setSingleLine(true);
                card.addView(info, new LinearLayout.LayoutParams(-1, dp(18)));
            }
        }
        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
        gp.width = dp(155); gp.height = dp(170);
        gp.setMargins(dp(6), dp(6), dp(6), dp(6));
        contentGrid.addView(card, gp);

    }

    private final java.util.concurrent.ExecutorService artworkExecutor =
        java.util.concurrent.Executors.newFixedThreadPool(8);
    private final android.util.LruCache<String, Bitmap> artworkCache =
        new android.util.LruCache<String, Bitmap>(48) {
            @Override protected int sizeOf(String key, Bitmap bitmap) {
                return Math.max(1, bitmap.getByteCount() / 1024 / 1024);
            }
        };

    private void loadArtwork(String url, ImageView target) {
        if (url == null || url.trim().isEmpty() || url.equalsIgnoreCase("null")) return;
        url = url.trim().replace("&amp;", "&");
        if (url.startsWith("//")) url = "https:" + url;
        else if (url.startsWith("/")) url = server + url;
        else if (!url.startsWith("https://") && !url.startsWith("http://")) {
            if (url.contains("://")) return;
            url = server + "/" + url;
        }
        final String imageUrl = url;
        target.setTag(imageUrl);
        Bitmap cached = artworkCache.get(imageUrl);
        if (cached != null) { target.setImageBitmap(cached); return; }
        artworkExecutor.execute(() -> {
            try {
                java.io.File directory = new java.io.File(getCacheDir(), "channel-art");
                if (!directory.exists()) directory.mkdirs();
                String key = bytesToHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(imageUrl.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                java.io.File file = new java.io.File(directory, key + ".img");
                Bitmap bitmap = null;
                if (file.isFile() && file.length() > 0) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 2;
                    bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                }
                if (bitmap == null) {
                    HttpURLConnection connection = (HttpURLConnection) new java.net.URL(imageUrl).openConnection();
                    connection.setConnectTimeout(2500);
                    connection.setReadTimeout(3500);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Dream IPTV)");
                    connection.setRequestProperty("Accept", "image/*");
                    try (InputStream input = connection.getInputStream();
                         java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
                    } finally { connection.disconnect(); }
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 2;
                    bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                    if (bitmap == null) file.delete();
                }
                if (bitmap == null) return;
                artworkCache.put(imageUrl, bitmap);
                Bitmap ready = bitmap;
                runOnUiThread(() -> {
                    if (imageUrl.equals(target.getTag())) target.setImageBitmap(ready);
                });
            } catch (Exception ignored) { }
        });
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }

    private void showEpisodes(String seriesId, String name) {
        screen("Loading episodes...");
        new Thread(() -> {
            try {
                JSONObject result = new JSONObject(fetch(api(username, password, "get_series_info") + "&series_id=" + enc(seriesId)));
                JSONObject seasons = result.optJSONObject("episodes");
                runOnUiThread(() -> {
                    screen(name);
                    button("← Series", () -> showCategories("series"));
                    if (seasons == null) { root.addView(text("No episodes found")); return; }
                    java.util.Iterator<String> keys = seasons.keys();
                    while (keys.hasNext()) {
                        String season = keys.next();
                        root.addView(text("Season " + season));
                        JSONArray episodes = seasons.optJSONArray(season);
                        if (episodes == null) continue;
                        for (int i = 0; i < episodes.length(); i++) {
                            JSONObject episode = episodes.optJSONObject(i);
                            if (episode == null) continue;
                            String id = episode.optString("id");
                            String ext = episode.optString("container_extension", "mp4");
                            button(episode.optString("title", "Episode " + (i + 1)), () -> play("series", id, ext));
                        }
                    }
                });
            } catch (Exception e) { runOnUiThread(() -> { screen("Episodes unavailable"); root.addView(text(e.getMessage())); button("← Home", this::showHome); }); }
        }).start();
    }

    private void play(String type, String id, String ext) {
        releasePlayer();
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        android.widget.FrameLayout movieScreen = new android.widget.FrameLayout(this);
        movieScreen.setBackgroundColor(Color.BLACK);
        setContentView(movieScreen);
        PlayerView view = new PlayerView(this);
        view.setUseController(true);
        view.setControllerShowTimeoutMs(4000);
        view.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        movieScreen.addView(view, new android.widget.FrameLayout.LayoutParams(-1, -1));
        player = new ExoPlayer.Builder(this).build();
        view.setPlayer(player);
        TextView playbackStatus = text("Connecting to stream...");
        playbackStatus.setTextSize(14);
        playbackStatus.setBackgroundColor(Color.argb(170, 10, 16, 28));
        android.widget.FrameLayout.LayoutParams statusParams = new android.widget.FrameLayout.LayoutParams(
            -2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        movieScreen.addView(playbackStatus, statusParams);
        String folder = type.equals("vod") ? "movie" : type;
        String safeExt = ext.matches("[a-zA-Z0-9]{1,5}") ? ext : "mp4";
        String stream = server + "/" + folder + "/" + path(username) + "/" + path(password) + "/" + path(id) + "." + safeExt;
        final String initialStream = stream;
        player.addListener(new Player.Listener() {
            private boolean retriedHls = false;
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) playbackStatus.setText("");
                else if (state == Player.STATE_BUFFERING) playbackStatus.setText("Buffering...");
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (type.equals("live") && !retriedHls) {
                    retriedHls = true;
                    String hls = server + "/live/" + path(username) + "/" + path(password) + "/" + path(id) + ".m3u8";
                    playbackStatus.setText("Trying HLS stream...");
                    player.setMediaItem(MediaItem.fromUri(hls));
                    player.prepare(); player.play();
                } else {
                    playbackStatus.setText("Playback failed: " + error.getErrorCodeName() + " - " + error.getMessage() + "\\nCheck that the account allows streaming and the provider supports this format.");
                }
            }
        });
        player.setMediaItem(MediaItem.fromUri(initialStream));
        player.prepare(); player.play();
    }

    private void startStream(String type, String id, String ext, PlayerView view) {
        player = new ExoPlayer.Builder(this).build();
        view.setPlayer(player);
        String folder = type.equals("vod") ? "movie" : type;
        String safeExt = ext.matches("[a-zA-Z0-9]{1,5}") ? ext : "mp4";
        String stream = server + "/" + folder + "/" + path(username) + "/" + path(password) + "/" + path(id) + "." + safeExt;
        player.setMediaItem(MediaItem.fromUri(stream));
        player.prepare();
        player.play();
    }

    private void attachPlaybackStatus(TextView status, String id) {
        player.addListener(new Player.Listener() {
            private boolean retriedHls = false;
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) status.setText("");
                else if (state == Player.STATE_BUFFERING) status.setText("Buffering...");
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (!retriedHls) {
                    retriedHls = true;
                    status.setText("Trying HLS stream...");
                    String hls = server + "/live/" + path(username) + "/" + path(password) + "/" + path(id) + ".m3u8";
                    player.setMediaItem(MediaItem.fromUri(hls));
                    player.prepare();
                    player.play();
                } else status.setText("Playback failed: " + error.getMessage());
            }
        });
    }

    private void checkUpdates() {
        TextView status = text("Checking for updates...");
        root.addView(status);
        new Thread(() -> {
            try {
                JSONObject release = new JSONObject(fetchAbsolute(RELEASES_API));
                String tag = release.optString("tag_name", "").replaceFirst("^[vV]", "");
                JSONArray assets = release.optJSONArray("assets");
                String apkUrl = "";
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset != null && asset.optString("name").endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url"); break;
                    }
                }
                final String url = apkUrl;
                runOnUiThread(() -> {
                    if (tag.isEmpty()) { status.setText("No published release found."); return; }
                    if (compareVersions(tag, BuildConfig.VERSION_NAME) <= 0) {
                        status.setText("Up to date (" + BuildConfig.VERSION_NAME + ")"); return;
                    }
                    if (url.isEmpty()) {
                        status.setText("Version " + tag + " is available, but no APK is attached to its GitHub Release.");
                        return;
                    }
                    status.setText("New version " + tag + " available (installed: " + BuildConfig.VERSION_NAME + ")");
                    button("DOWNLOAD UPDATE", () -> downloadUpdate(url, status));
                });
            } catch (Exception e) { runOnUiThread(() -> status.setText("Update check failed: " + e.getMessage())); }
        }).start();
    }

    private int compareVersions(String a, String b) {
        String[] x = a.split("[.-]"), y = b.split("[.-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int n = i < x.length ? versionPart(x[i]) : 0;
            int m = i < y.length ? versionPart(y[i]) : 0;
            if (n != m) return Integer.compare(n, m);
        }
        return 0;
    }

    private int versionPart(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { return 0; }
    }

    private void downloadUpdate(String url, TextView status) {
        try {
            if (!url.startsWith("https://github.com/")) throw new IOException("Unexpected update URL");
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("Dream IPTV update");
            request.setDescription("Downloading new version");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            final String downloadPath = "dream-iptv-update-" + System.currentTimeMillis() + ".apk";
            request.setDestinationInExternalFilesDir(this, android.os.Environment.DIRECTORY_DOWNLOADS, downloadPath);
            updateDownloadId = manager.enqueue(request);
            status.setText("Downloading update...");
            if (updateReceiver != null) unregisterReceiver(updateReceiver);
            updateReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != updateDownloadId) return;
                    DownloadManager.Query query = new DownloadManager.Query().setFilterById(updateDownloadId);
                    try (Cursor cursor = manager.query(query)) {
                        if (cursor == null || !cursor.moveToFirst()) { status.setText("Update download missing"); return; }
                        int state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        if (state != DownloadManager.STATUS_SUCCESSFUL) { status.setText("Update download failed"); return; }
                    }
                    java.io.File downloadedApk = new java.io.File(
                        getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), downloadPath);
                    if (!downloadedApk.isFile() || downloadedApk.length() < 10240) {
                        status.setText("Update failed: downloaded file is missing or too small.");
                        return;
                    }
                    android.content.pm.PackageInfo archive = getPackageManager()
                        .getPackageArchiveInfo(downloadedApk.getAbsolutePath(), 0);
                    if (archive == null) {
                        status.setText("Update failed: downloaded file is not a valid Android APK.");
                        return;
                    }
                    if (!getPackageName().equals(archive.packageName)) {
                        status.setText("Update rejected: APK belongs to a different app (" + archive.packageName + ").");
                        return;
                    }
                    long newCode = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
                    long installedCode = BuildConfig.VERSION_CODE;
                    if (newCode <= installedCode) {
                        status.setText("Update rejected: APK version " + newCode +
                            " is not newer than installed version " + installedCode + ".");
                        return;
                    }
                    status.setText("APK verified. Launching Android installer...");
                    updateInstallStatus = status;
                    Uri apk = androidx.core.content.FileProvider.getUriForFile(
                        MainActivity.this, getPackageName() + ".fileprovider",
                        new java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), downloadPath));
                    pendingUpdateApk = apk;
                    try {
                        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                            status.setText("Enable Allow from this source, then return to Dream IPTV.");
                            Intent settings = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName()));
                            startActivity(settings);
                            button("INSTALL DOWNLOADED UPDATE", () -> installPendingUpdate(status));
                            return;
                        }
                        installPendingUpdate(status);
                    } catch (Exception e) {
                        status.setText("Installer launch failed: " + e.getMessage());
                        button("RETRY INSTALL", () -> installPendingUpdate(status));
                    }
                }
            };
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(updateReceiver, filter, Context.RECEIVER_EXPORTED);
            else registerReceiver(updateReceiver, filter);
        } catch (Exception e) { status.setText("Download failed: " + e.getMessage()); }
    }

    private void installPendingUpdate(TextView status) {
        if (pendingUpdateApk == null) {
            status.setText("No downloaded APK available. Download the update again.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            status.setText("Allow Dream IPTV to install unknown apps in Android settings first.");
            return;
        }
        Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        install.setData(pendingUpdateApk);
        install.setClipData(android.content.ClipData.newRawUri("Dream IPTV update", pendingUpdateApk));
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        install.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        try {
            for (android.content.pm.ResolveInfo handler : getPackageManager().queryIntentActivities(
                    install, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)) {
                if (handler.activityInfo != null)
                    grantUriPermission(handler.activityInfo.packageName, pendingUpdateApk,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            status.setText("Waiting for Android to install the update...");
            updateInstallStatus = status;
            startActivityForResult(install, UPDATE_INSTALL_REQUEST);
        } catch (Exception e) {
            status.setText("Android installer error: " + e.getMessage());
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != UPDATE_INSTALL_REQUEST || updateInstallStatus == null) return;
        if (resultCode == RESULT_OK) {
            updateInstallStatus.setText("Android reports installation succeeded.");
        } else {
            int installResult = data == null ? 0 :
                data.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, 0);
            String detail = data == null ? "" :
                data.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE);
            updateInstallStatus.setText("Android did not confirm installation (result " + resultCode +
                ", status " + installResult + ")." +
                (detail == null || detail.isEmpty() ? " Check the installer message shown on screen." : " " + detail));
        }
    }

    private static String enc(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }
    private static String path(String value) { return Uri.encode(value); }
    private String fetch(String relative) throws Exception { return fetchAbsolute(server + "/" + relative); }
    private String fetchAbsolute(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "DreamIPTV-Android");
        try {
            if (c.getResponseCode() >= 400) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
                return out.toString("UTF-8");
            }
        } finally { c.disconnect(); }
    }

    private void releasePlayer() { ++epgRequest; if (epgRefresh != null) { epgHandler.removeCallbacks(epgRefresh); epgRefresh = null; } if (player != null) { player.release(); player = null; } }
    @Override protected void onDestroy() { if (updateReceiver != null) { unregisterReceiver(updateReceiver); updateReceiver = null; } super.onDestroy(); }
    @Override protected void onStop() { super.onStop(); releasePlayer(); }
    @Override public void onBackPressed() {
        if (player != null) {
            releasePlayer();
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            getWindow().getDecorView().setSystemUiVisibility(0);
            if (currentType.equals("live")) showItems("live", currentCategory); else showCategories(currentType);
        }
        else if (!username.isEmpty()) showHome(); else super.onBackPressed();
    }
}
