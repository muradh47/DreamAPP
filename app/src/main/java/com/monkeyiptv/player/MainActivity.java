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
        showSplash();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            this::checkForUpdatesOnLaunch, 3000);
    }

    // Page 1: branded splash screen. No network requests or unnecessary loading delay.
    private void showSplash() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        android.widget.FrameLayout splash = new android.widget.FrameLayout(this);
        splash.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(1, 14, 37), Color.rgb(3, 37, 89), Color.rgb(1, 10, 29)}));
        setContentView(splash);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(dp(24), dp(20), dp(24), dp(20));
        android.widget.FrameLayout.LayoutParams centered = new android.widget.FrameLayout.LayoutParams(
            -1, -2, Gravity.CENTER);
        splash.addView(center, centered);

        TextView mark = text("▶");
        mark.setTextColor(Color.rgb(0, 160, 255));
        mark.setTextSize(78);
        mark.setGravity(Gravity.CENTER);
        mark.setPadding(0, 0, 0, dp(2));
        center.addView(mark);

        TextView name = text("DREAM IPTV");
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextSize(39);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, 0, 0, dp(4));
        center.addView(name);

        TextView tagline = text("L I V E   ·   M O V I E S   ·   S E R I E S");
        tagline.setTextSize(12);
        tagline.setTextColor(Color.rgb(195, 218, 244));
        tagline.setGravity(Gravity.CENTER);
        center.addView(tagline);

        splash.setFocusableInTouchMode(true);
        splash.requestFocus();
        new android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed(this::showLoadingScreen, 1000);
    }

    // Page 2: centred loading state for foldables, phones and television screens.
    private void showLoadingScreen() {
        if (isFinishing() || isDestroyed()) return;
        android.widget.FrameLayout loading = new android.widget.FrameLayout(this);
        loading.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(1, 14, 37), Color.rgb(3, 37, 89), Color.rgb(1, 10, 29)}));
        setContentView(loading);
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(dp(24), dp(20), dp(24), dp(20));
        loading.addView(center, new android.widget.FrameLayout.LayoutParams(
            -1, -2, Gravity.CENTER));

        TextView mark = text("▶");
        mark.setTextColor(Color.rgb(0, 160, 255));
        mark.setTextSize(78);
        mark.setGravity(Gravity.CENTER);
        center.addView(mark);

        TextView name = text("DREAM IPTV");
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextSize(39);
        name.setGravity(Gravity.CENTER);
        center.addView(name);

        TextView tagline = text("L I V E   ·   M O V I E S   ·   S E R I E S");
        tagline.setTextSize(12);
        tagline.setTextColor(Color.rgb(195, 218, 244));
        tagline.setGravity(Gravity.CENTER);
        center.addView(tagline);

        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(
            Color.rgb(0, 170, 255)));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        spinnerParams.gravity = Gravity.CENTER_HORIZONTAL;
        spinnerParams.topMargin = dp(26);
        center.addView(spinner, spinnerParams);

        loading.setFocusableInTouchMode(true);
        loading.requestFocus();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            getWindow().getDecorView().setSystemUiVisibility(0);
            if (!username.isEmpty() && !password.isEmpty()) showHome();
            else showLogin();
        }, 750);
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
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        getWindow().getDecorView().setSystemUiVisibility(0);
        screen("");
        root.removeAllViews();
        root.setGravity(Gravity.CENTER);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(2, 18, 47), Color.rgb(5, 37, 84), Color.rgb(2, 12, 32)}));
        LinearLayout page = root;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(22), dp(18), dp(22), dp(20));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.rgb(9, 28, 59));
        panelBg.setCornerRadius(dp(20));
        panelBg.setStroke(dp(1), Color.rgb(36, 84, 141));
        panel.setBackground(panelBg);
        int widthPx = getResources().getDisplayMetrics().widthPixels;
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
            Math.min(dp(520), Math.max(dp(260), widthPx - dp(48))), -2);
        panelParams.gravity = Gravity.CENTER;
        page.addView(panel, panelParams);
        root = panel;

        TextView logo = text("▶  DREAM IPTV");
        logo.setTextSize(29);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo);
        TextView welcome = text("Welcome Back");
        welcome.setTextSize(23);
        welcome.setGravity(Gravity.CENTER);
        root.addView(welcome);
        TextView subtitle = text("Sign in to start watching");
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setTextColor(Color.LTGRAY);
        root.addView(subtitle);

        EditText user = loginField("Username", false);
        android.content.SharedPreferences prefs = getPreferences(0);
        boolean rememberEnabled = prefs.getBoolean("remember_login", false);
        if (rememberEnabled) {
            user.setText(prefs.getString("saved_username", ""));
        }
        EditText pass = loginField("Password", true);
        if (rememberEnabled) pass.setText(prefs.getString("saved_password", ""));
        CheckBox remember = new CheckBox(this);
        remember.setText("Remember username and password");
        remember.setTextColor(Color.rgb(205, 225, 246));
        remember.setTextSize(14);
        remember.setChecked(rememberEnabled);
        root.addView(remember);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(dp(12), dp(18), dp(12), dp(12));
        root.addView(login, lp);
        TextView note = text("Secure sign in  •  Server configured automatically");
        note.setGravity(Gravity.CENTER);
        note.setTextSize(12);
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
        boolean compact = getResources().getDisplayMetrics().widthPixels < dp(700);
        root.setPadding(dp(compact ? 12 : 22), dp(10), dp(compact ? 12 : 22), dp(18));
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(4, 34, 79), Color.rgb(5, 19, 46), Color.rgb(2, 11, 30)}));
        TextView oldHeading = (TextView) root.getChildAt(0);
        root.removeView(oldHeading);

        LinearLayout topBar = row();
        TextView brand = text("▶ DREAM IPTV");
        brand.setTextSize(24);
        brand.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        brand.setTextColor(Color.WHITE);
        topBar.addView(brand, new LinearLayout.LayoutParams(0, dp(56), 1));
        Button search = homeTile(compact ? "⌕" : "⌕  SEARCH", Color.rgb(12, 37, 72), this::showSearch);
        search.setTextSize(14);
        topBar.addView(search, new LinearLayout.LayoutParams(dp(compact ? 52 : 125), dp(48)));
        Button settings = homeTile(compact ? "⚙" : "⚙  SETTINGS", Color.rgb(12, 37, 72), this::showHomeSettings);
        settings.setTextSize(14);
        topBar.addView(settings, new LinearLayout.LayoutParams(dp(compact ? 52 : 140), dp(48)));

        LinearLayout primary = row();
        if (compact) primary.setOrientation(LinearLayout.VERTICAL);
        Button live = homeTile("📺\nLIVE TV",
            Color.rgb(0, 88, 213), () -> showLiveDashboard());
        addHomeTile(primary, live, compact ? 126 : 196);
        addHomeTile(primary, homeTile("🎬\nMOVIES",
            Color.rgb(219, 32, 45), this::showMovieDashboard), compact ? 126 : 196);
        addHomeTile(primary, homeTile("🎭\nSERIES",
            Color.rgb(98, 14, 180), this::showSeriesDashboard), compact ? 126 : 196);

        LinearLayout shortcuts = row();
        if (compact) shortcuts.setOrientation(LinearLayout.VERTICAL);
        addHomeTile(shortcuts, homeTile("◷  CATCH UP", Color.rgb(10, 46, 84),
            () -> showCategories("live")), compact ? 64 : 88);
        addHomeTile(shortcuts, homeTile("▦  TV GUIDE", Color.rgb(10, 46, 84),
            this::showGuide), 88);
        addHomeTile(shortcuts, homeTile("♥  FAVOURITES", Color.rgb(10, 46, 84),
            () -> showCategories("live")), 88);
        addHomeTile(shortcuts, homeTile("⚙  SETTINGS", Color.rgb(10, 46, 84),
            this::showHomeSettings), 88);
        live.requestFocus();
    }

    private void showSearch() {
        screen("SEARCH");
        button("‹  HOME", this::showHome);
        EditText query = loginField("Search channels, movies and series", false);
        query.setSingleLine(true);
        button("SEARCH LIVE TV", () -> showCategories("live"));
        button("BROWSE MOVIES", () -> showCategories("vod"));
        button("BROWSE SERIES", () -> showCategories("series"));
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
        boolean vertical = parent.getOrientation() == LinearLayout.VERTICAL;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            vertical ? -1 : 0, dp(height), vertical ? 0f : 1f);
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
        overlay.setBackgroundColor(Color.argb(232, 9, 25, 53));
        android.widget.FrameLayout.LayoutParams overlayParams = new android.widget.FrameLayout.LayoutParams(
            -1, -2, Gravity.BOTTOM);
        fullscreen.addView(overlay, overlayParams);
        TextView title = text(channel);
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
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
        // Keep the video tap target behind the controls so buttons remain clickable.
        fullscreen.removeView(overlay);

        android.view.View tapArea = new android.view.View(this);
        tapArea.setClickable(true);
        fullscreen.addView(tapArea, new android.widget.FrameLayout.LayoutParams(-1, -1));
        tapArea.setFocusable(true);
        tapArea.setOnClickListener(v -> {
            boolean showing = overlay.getVisibility() != View.VISIBLE;
            overlay.setVisibility(showing ? View.VISIBLE : View.GONE);
            if (showing) pauseButton.requestFocus();
            else tapArea.requestFocus();
        });
        tapArea.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                overlay.setVisibility(View.VISIBLE);
                pauseButton.requestFocus();
                return true;
            }
            return false;
        });
        fullscreen.addView(overlay, new android.widget.FrameLayout.LayoutParams(
            -1, -2, Gravity.BOTTOM));
        Button back = new Button(this);
        back.setText("← BACK");
        back.setTextSize(13);
        back.setFocusable(true);
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
                playerStatus.setText("Switching to VLC software decoder...");
                String url = server + "/live/" + path(username) + "/" + path(password) + "/" + path(id) + ".m3u8";
                playWithVlc("live", id, "m3u8", url);
            }
        });
        tapArea.requestFocus();
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
        page.setFitsSystemWindows(true);
        setContentView(page);
        getWindow().getDecorView().setSystemUiVisibility(0);
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
        categorySidebar = new LinearLayout(this);
        categorySidebar.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        contentGrid = new GridLayout(this);
        contentGrid.setColumnCount(type.equals("live") ? 2 : 4);
        contentGrid.setPadding(dp(8), dp(10), dp(8), dp(10));
        scroll.addView(contentGrid);
    }

    private String selectedBrowseCategoryId = "";
    private final java.util.Map<String, Button> browseCategoryButtons = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Button> liveCategoryButtons = new java.util.LinkedHashMap<>();

    private void highlightBrowseCategory(String id) {
        selectedBrowseCategoryId = id;
        for (java.util.Map.Entry<String, Button> entry : browseCategoryButtons.entrySet()) {
            boolean active = entry.getKey().equals(id);
            Button button = entry.getValue();
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                active ? Color.rgb(24, 113, 197) : Color.rgb(29, 39, 65)));
            button.setTextColor(Color.WHITE);
            button.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
            button.setAlpha(active ? 1f : 0.88f);
        }
    }

    private void browseSidebarItem(String id, String title, Runnable action) {
        sidebarItem(title, id.equals(selectedBrowseCategoryId), () -> {
            highlightBrowseCategory(id);
            action.run();
        });
        Button button = (Button) categorySidebar.getChildAt(categorySidebar.getChildCount() - 1);
        browseCategoryButtons.put(id, button);
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

    // Live TV dashboard: categories, video preview, EPG and channel cards in one view.
    private LinearLayout liveDashboardCategories;
    private GridLayout liveDashboardChannels;
    private PlayerView liveDashboardPreview;
    private TextView liveDashboardNow, liveDashboardNext, liveDashboardTitle;
    private String liveDashboardSelectedId = "", liveDashboardSelectedName = "";
    private final java.util.Map<String, LinearLayout> liveDashboardCards = new java.util.HashMap<>();
    private int liveDashboardRequest = 0;
    private String liveDashboardCategoryId = "";
    private final java.util.Map<String, String> liveDashboardCardNames = new java.util.HashMap<>();
    private TextView liveDashboardPreviewBadge;

    private void showLiveDashboard() {
        releasePlayer();
        currentType = "live";
        ++browseRequest;
        ++liveDashboardRequest;
        liveDashboardCards.clear();
        liveDashboardCardNames.clear();
        liveDashboardSelectedId = "";
        liveDashboardSelectedName = "";
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setFitsSystemWindows(true);
        page.setBackgroundColor(Color.rgb(9, 17, 37));
        setContentView(page);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(5), dp(12), dp(5));
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));
        Button home = tile("‹ HOME", Color.rgb(25, 42, 75), this::showHome);
        header.addView(home, new LinearLayout.LayoutParams(dp(86), dp(42)));
        TextView brand = text("  DREAM IPTV  /  LIVE");
        brand.setTextSize(18);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button searchLive = tile("⌕", Color.rgb(25, 42, 75), () -> {
            final android.widget.EditText query = new android.widget.EditText(this);
            query.setSingleLine(true);
            query.setHint("Search channels");
            new android.app.AlertDialog.Builder(this).setTitle("Find a channel")
                .setView(query).setPositiveButton("SEARCH", (dialog, which) -> {
                    String needle = query.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                    for (java.util.Map.Entry<String, LinearLayout> entry : liveDashboardCards.entrySet()) {
                        String name = liveDashboardCardNames.get(entry.getKey());
                        entry.getValue().setVisibility(name != null && name.toLowerCase(java.util.Locale.ROOT).contains(needle)
                            ? View.VISIBLE : View.GONE);
                    }
                }).setNegativeButton("CANCEL", null).show();
        });
        header.addView(searchLive, new LinearLayout.LayoutParams(dp(45), dp(42)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int sidebarWidth = Math.min(dp(235), Math.max(dp(145), screenWidth / 4));
        ScrollView categoryScroll = new ScrollView(this);
        categoryScroll.setBackgroundColor(Color.rgb(14, 28, 57));
        body.addView(categoryScroll, new LinearLayout.LayoutParams(sidebarWidth, -1));
        liveDashboardCategories = new LinearLayout(this);
        liveDashboardCategories.setOrientation(LinearLayout.VERTICAL);
        liveDashboardCategories.setPadding(dp(5), dp(7), dp(5), dp(7));
        categoryScroll.addView(liveDashboardCategories);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(8), dp(4), dp(8), dp(4));
        body.addView(main, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout previewRow = new LinearLayout(this);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        main.addView(previewRow, new LinearLayout.LayoutParams(-1, dp(220)));
        android.widget.FrameLayout previewFrame = new android.widget.FrameLayout(this);
        previewRow.addView(previewFrame, new LinearLayout.LayoutParams(0, -1, 3));
        liveDashboardPreview = new PlayerView(this);
        liveDashboardPreview.setUseController(false);
        liveDashboardPreview.setBackgroundColor(Color.BLACK);
        previewFrame.addView(liveDashboardPreview, new android.widget.FrameLayout.LayoutParams(-1, -1));
        liveDashboardPreviewBadge = text("● LIVE  •  Select a channel");
        liveDashboardPreviewBadge.setTextSize(12);
        liveDashboardPreviewBadge.setTypeface(Typeface.DEFAULT_BOLD);
        liveDashboardPreviewBadge.setBackgroundColor(Color.argb(190, 9, 24, 48));
        liveDashboardPreviewBadge.setPadding(dp(9), dp(5), dp(9), dp(5));
        android.widget.FrameLayout.LayoutParams badgeParams = new android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        previewFrame.addView(liveDashboardPreviewBadge, badgeParams);
        LinearLayout epg = new LinearLayout(this);
        epg.setOrientation(LinearLayout.VERTICAL);
        epg.setPadding(dp(12), dp(8), dp(5), dp(8));
        epg.setBackgroundColor(Color.rgb(14, 29, 57));
        previewRow.addView(epg, new LinearLayout.LayoutParams(0, -1, 2));
        liveDashboardTitle = text("Select a channel");
        liveDashboardTitle.setTextSize(17);
        liveDashboardTitle.setTypeface(Typeface.DEFAULT_BOLD);
        liveDashboardTitle.setMaxLines(2);
        epg.addView(liveDashboardTitle);
        liveDashboardNow = text("NOW PLAYING\nSelect a channel to preview");
        liveDashboardNow.setTextSize(13);
        liveDashboardNow.setPadding(0, dp(12), 0, dp(6));
        epg.addView(liveDashboardNow);
        liveDashboardNext = text("UP NEXT\nProgramme guide");
        liveDashboardNext.setTextSize(13);
        epg.addView(liveDashboardNext);
        TextView heading = text("CHANNELS");
        heading.setTextSize(16);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(dp(6), dp(9), 0, dp(5));
        main.addView(heading);
        ScrollView channelScroll = new ScrollView(this);
        main.addView(channelScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        liveDashboardChannels = new GridLayout(this);
        liveDashboardChannels.setColumnCount(3);
        liveDashboardChannels.setPadding(dp(2), dp(2), dp(2), dp(8));
        channelScroll.addView(liveDashboardChannels);
        populateLiveDashboardCategories(categoryCache.get("live"));
        selectLiveDashboardCategory("", "ALL CHANNELS");
        if (categoryCache.get("live") == null) new Thread(() -> {
            try {
                JSONArray cats = new JSONArray(fetch(api(username, password, "get_live_categories")));
                categoryCache.put("live", cats);
                runOnUiThread(() -> {
                    if (liveDashboardCategories != null && liveDashboardCategories.getParent() != null
                        && "live".equals(currentType)) populateLiveDashboardCategories(cats);
                });
            } catch (Exception ignored) { }
        }).start();
    }

    private void populateLiveDashboardCategories(JSONArray categories) {
        liveDashboardCategories.removeAllViews();
        liveCategoryButtons.clear();
        addLiveDashboardCategory("ALL CHANNELS", "", "ALL CHANNELS");
        if (categories == null) return;
        for (int i = 0; i < categories.length(); i++) {
            JSONObject cat = categories.optJSONObject(i);
            if (cat == null) continue;
            String id = cat.optString("category_id");
            String name = cat.optString("category_name", "Other");
            addLiveDashboardCategory(name, id, name);
        }
    }

    private void addLiveDashboardCategory(String label, String id, String name) {
        Button button = tile(label, Color.rgb(25, 42, 75),
            () -> selectLiveDashboardCategory(id, name));
        button.setTextSize(13);
        button.setSingleLine(true);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(43));
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        liveDashboardCategories.addView(button, params);
        liveCategoryButtons.put(id, button);
    }

    private void selectLiveDashboardCategory(String categoryId, String label) {
        liveDashboardCategoryId = categoryId;
        for (java.util.Map.Entry<String, Button> entry : liveCategoryButtons.entrySet()) {
            boolean active = entry.getKey().equals(categoryId);
            Button item = entry.getValue();
            item.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                active ? Color.rgb(24, 113, 197) : Color.rgb(25, 42, 75)));
            item.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
            item.setAlpha(active ? 1f : 0.86f);
        }
        int request = ++liveDashboardRequest;
        liveDashboardChannels.removeAllViews();
        liveDashboardCards.clear();
        liveDashboardCardNames.clear();
        liveDashboardChannels.addView(text("Loading " + label + "..."));
        new Thread(() -> {
            try {
                String url = api(username, password, "get_live_streams");
                if (!categoryId.isEmpty()) url += "&category_id=" + enc(categoryId);
                JSONArray channels = new JSONArray(fetch(url));
                runOnUiThread(() -> {
                    if (request != liveDashboardRequest || liveDashboardChannels.getParent() == null) return;
                    liveDashboardChannels.removeAllViews();
                    int count = Math.min(channels.length(), 120);
                    for (int i = 0; i < count; i++) {
                        JSONObject item = channels.optJSONObject(i);
                        if (item == null) continue;
                        String id = item.optString("stream_id");
                        String name = item.optString("name", "Channel");
                        if (id.isEmpty()) continue;
                        LinearLayout card = new LinearLayout(this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setGravity(Gravity.CENTER);
                        card.setPadding(dp(8), dp(7), dp(8), dp(7));
                        card.setBackgroundColor(Color.rgb(25, 38, 65));
                        card.setFocusable(true);
                        card.setClickable(true);
                        card.setOnClickListener(v -> previewLiveDashboardChannel(id, name));
                        android.view.GestureDetector taps = new android.view.GestureDetector(this,
                            new android.view.GestureDetector.SimpleOnGestureListener() {
                                @Override public boolean onDown(android.view.MotionEvent event) { return true; }
                                @Override public boolean onSingleTapConfirmed(android.view.MotionEvent event) {
                                    previewLiveDashboardChannel(id, name);
                                    return true;
                                }
                                @Override public boolean onDoubleTap(android.view.MotionEvent event) {
                                    if (!id.equals(liveDashboardSelectedId))
                                        previewLiveDashboardChannel(id, name);
                                    showFullscreenLive(id, name, false);
                                    return true;
                                }
                            });
                        card.setOnTouchListener((v, event) -> {
                            taps.onTouchEvent(event);
                            return true;
                        });
                        card.setOnFocusChangeListener((v, focused) ->
                            card.setBackgroundColor(id.equals(liveDashboardSelectedId)
                                ? Color.rgb(17, 122, 194)
                                : focused ? Color.rgb(48, 91, 192) : Color.rgb(25, 38, 65)));
                        liveDashboardCards.put(id, card);
                        liveDashboardCardNames.put(id, name);
                        if (id.equals(liveDashboardSelectedId)) card.setBackgroundColor(Color.rgb(17, 122, 194));
                        ImageView logo = new ImageView(this);
                        logo.setImageResource(android.R.drawable.ic_media_play);
                        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        card.addView(logo, new LinearLayout.LayoutParams(-1, dp(34)));
                        loadArtwork(item.optString("stream_icon"), logo);
                        TextView title = text(name);
                        title.setTextSize(12);
                        title.setGravity(Gravity.CENTER);
                        title.setMaxLines(2);
                        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        card.addView(title);
                        TextView programme = text("Loading guide...");
                        programme.setTextSize(11);
                        programme.setTextColor(Color.rgb(166, 200, 242));
                        programme.setMaxLines(2);
                        programme.setGravity(Gravity.CENTER);
                        card.addView(programme);
                        loadLiveCardEpg(id, programme);
                        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                        int mainWidth = getResources().getDisplayMetrics().widthPixels -
                            Math.min(dp(235), Math.max(dp(145), getResources().getDisplayMetrics().widthPixels / 4)) - dp(30);
                        params.width = Math.max(dp(110), mainWidth / 3 - dp(9));
                        params.height = dp(126);
                        params.setMargins(dp(3), dp(3), dp(3), dp(3));
                        liveDashboardChannels.addView(card, params);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (request == liveDashboardRequest && liveDashboardChannels.getParent() != null) {
                        liveDashboardChannels.removeAllViews();
                        liveDashboardChannels.addView(text("Channels unavailable. Select category to retry."));
                    }
                });
            }
        }).start();
    }

    private void previewLiveDashboardChannel(String id, String name) {
        releasePlayer();
        liveDashboardSelectedId = id;
        liveDashboardSelectedName = name;
        for (java.util.Map.Entry<String, LinearLayout> entry : liveDashboardCards.entrySet()) {
            boolean playing = id.equals(entry.getKey());
            entry.getValue().setBackgroundColor(playing ? Color.rgb(17, 122, 194) : Color.rgb(25, 38, 65));
            entry.getValue().setContentDescription((playing ? "NOW PLAYING: " : "") + entry.getKey());
        }
        liveDashboardTitle.setText(name);
        liveDashboardPreviewBadge.setText("● LIVE  •  " + name);
        liveDashboardNow.setText("NOW PLAYING\nLoading...");
        liveDashboardNext.setText("UP NEXT\nLoading...");
        startStream("live", id, "ts", liveDashboardPreview);
        TextView status = liveDashboardNow;
        attachPlaybackStatus(status, id);
        LinearLayout schedule = new LinearLayout(this);
        int request = ++epgRequest;
        loadChannelEpg(id, request, liveDashboardNow, liveDashboardNext, schedule, true);
    }

    // Movies: Smarters-inspired sidebar and poster wall, using the IPTV provider's real VOD catalogue.
    private void showMovieDashboard() {
        releasePlayer();
        currentType = "vod";
        ++browseRequest;
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setFitsSystemWindows(true);
        page.setBackgroundColor(Color.rgb(9, 17, 37));
        setContentView(page);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(5), dp(10), dp(5));
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));
        Button home = tile("‹ HOME", Color.rgb(25, 42, 75), this::showHome);
        home.setTextSize(14);
        header.addView(home, new LinearLayout.LayoutParams(dp(90), dp(44)));
        TextView title = text("  DREAM IPTV  /  MOVIES");
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(19);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button search = tile("⌕", Color.rgb(25, 42, 75), () -> {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setHint("Search movie titles");
            new android.app.AlertDialog.Builder(this).setTitle("Search movies").setView(input)
                .setPositiveButton("SEARCH", (dialog, which) -> {
                    String q = input.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                    if (currentItems == null) return;
                    JSONArray matches = new JSONArray();
                    for (int i = 0; i < currentItems.length(); i++) {
                        JSONObject movie = currentItems.optJSONObject(i);
                        if (movie != null && movie.optString("name", "").toLowerCase(java.util.Locale.ROOT).contains(q))
                            matches.put(movie);
                    }
                    currentItems = matches;
                    nextItemIndex = 0;
                    contentGrid.removeAllViews();
                    browserHeading.setText("SEARCH RESULTS");
                    appendNextPage();
                }).setNegativeButton("CANCEL", null).show();
        });
        header.addView(search, new LinearLayout.LayoutParams(dp(52), dp(44)));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        int width = getResources().getDisplayMetrics().widthPixels;
        int sideWidth = Math.min(dp(255), Math.max(dp(150), width / 4));
        ScrollView sidebarScroll = new ScrollView(this);
        sidebarScroll.setBackgroundColor(Color.rgb(15, 29, 58));
        body.addView(sidebarScroll, new LinearLayout.LayoutParams(sideWidth, -1));
        categorySidebar = new LinearLayout(this);
        categorySidebar.setOrientation(LinearLayout.VERTICAL);
        categorySidebar.setPadding(dp(5), dp(6), dp(5), dp(6));
        sidebarScroll.addView(categorySidebar);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(6), dp(4), dp(6), 0);
        body.addView(main, new LinearLayout.LayoutParams(0, -1, 1));
        browserHeading = text("ALL MOVIES");
        browserHeading.setTypeface(Typeface.DEFAULT_BOLD);
        browserHeading.setTextSize(19);
        browserHeading.setPadding(dp(6), dp(8), 0, dp(8));
        main.addView(browserHeading);
        ScrollView posterScroll = new ScrollView(this);
        main.addView(posterScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        contentGrid = new GridLayout(this);
        contentGrid.setColumnCount(width >= dp(950) ? 5 : width >= dp(620) ? 4 : 3);
        contentGrid.setPadding(dp(2), dp(2), dp(2), dp(8));
        posterScroll.addView(contentGrid);
        browseCategoryButtons.clear();
        selectedBrowseCategoryId = "";
        browseSidebarItem("", "ALL MOVIES", () -> { browserHeading.setText("ALL MOVIES"); showItems("vod", ""); });
        JSONArray cached = categoryCache.get("vod");
        if (cached != null) renderMovieSidebar(cached);
        else new Thread(() -> {
            try {
                JSONArray categories = new JSONArray(fetch(api(username, password, "get_vod_categories")));
                categoryCache.put("vod", categories);
                runOnUiThread(() -> {
                    if (categorySidebar.getParent() != null) renderMovieSidebar(categories);
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> sidebarItem("RETRY CATEGORIES", false, this::showMovieDashboard));
            }
        }).start();
        showItems("vod", "");
    }

    private void renderMovieSidebar(JSONArray categories) {
        categorySidebar.removeAllViews();
        browseCategoryButtons.clear();
        selectedBrowseCategoryId = "";
        browseSidebarItem("", "ALL MOVIES", () -> { browserHeading.setText("ALL MOVIES"); showItems("vod", ""); });
        for (int i = 0; i < categories.length(); i++) {
            JSONObject c = categories.optJSONObject(i);
            if (c == null) continue;
            String id = c.optString("category_id");
            String name = c.optString("category_name", "Movies");
            browseSidebarItem(id, name, () -> {
                browserHeading.setText(name.toUpperCase(java.util.Locale.ROOT));
                showItems("vod", id);
            });
        }
    }

    private void showSeriesDashboard() {
        releasePlayer();
        currentType = "series";
        ++browseRequest;
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setFitsSystemWindows(true);
        page.setBackgroundColor(Color.rgb(9, 17, 37));
        setContentView(page);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(5), dp(10), dp(5));
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));
        Button home = tile("‹ HOME", Color.rgb(25, 42, 75), this::showHome);
        home.setTextSize(14);
        header.addView(home, new LinearLayout.LayoutParams(dp(90), dp(44)));
        TextView title = text("  DREAM IPTV  /  SERIES");
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(19);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button search = tile("⌕", Color.rgb(25, 42, 75), () -> {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setHint("Search series titles");
            new android.app.AlertDialog.Builder(this).setTitle("Search series").setView(input)
                .setPositiveButton("SEARCH", (dialog, which) -> {
                    String q = input.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                    if (currentItems == null) return;
                    JSONArray matches = new JSONArray();
                    for (int i = 0; i < currentItems.length(); i++) {
                        JSONObject series = currentItems.optJSONObject(i);
                        if (series != null && series.optString("name", "").toLowerCase(java.util.Locale.ROOT).contains(q))
                            matches.put(series);
                    }
                    currentItems = matches;
                    nextItemIndex = 0;
                    contentGrid.removeAllViews();
                    browserHeading.setText("SEARCH RESULTS");
                    appendNextPage();
                }).setNegativeButton("CANCEL", null).show();
        });
        header.addView(search, new LinearLayout.LayoutParams(dp(52), dp(44)));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        int width = getResources().getDisplayMetrics().widthPixels;
        int sideWidth = Math.min(dp(190), Math.max(dp(128), width / 6));
        ScrollView sidebarScroll = new ScrollView(this);
        sidebarScroll.setBackgroundColor(Color.rgb(15, 29, 58));
        body.addView(sidebarScroll, new LinearLayout.LayoutParams(sideWidth, -1));
        categorySidebar = new LinearLayout(this);
        categorySidebar.setOrientation(LinearLayout.VERTICAL);
        categorySidebar.setPadding(dp(5), dp(6), dp(5), dp(6));
        sidebarScroll.addView(categorySidebar);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(6), dp(4), dp(6), 0);
        body.addView(main, new LinearLayout.LayoutParams(0, -1, 1));
        browserHeading = text("DISCOVER SERIES");
        browserHeading.setTypeface(Typeface.DEFAULT_BOLD);
        browserHeading.setTextSize(19);
        browserHeading.setPadding(dp(6), dp(8), 0, dp(8));
        main.addView(browserHeading);
        ScrollView posterScroll = new ScrollView(this);
        main.addView(posterScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        contentGrid = new GridLayout(this);
        contentGrid.setColumnCount(width >= dp(900) ? 4 : 3);
        contentGrid.setPadding(dp(2), dp(2), dp(2), dp(8));
        posterScroll.addView(contentGrid);
        browseCategoryButtons.clear();
        selectedBrowseCategoryId = "";
        browseSidebarItem("", "ALL SERIES", () -> { browserHeading.setText("ALL SERIES"); showItems("series", ""); });
        JSONArray cached = categoryCache.get("series");
        if (cached != null) renderSeriesSidebar(cached);
        else new Thread(() -> {
            try {
                JSONArray categories = new JSONArray(fetch(api(username, password, "get_series_categories")));
                categoryCache.put("series", categories);
                runOnUiThread(() -> {
                    if (categorySidebar.getParent() != null) renderSeriesSidebar(categories);
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> sidebarItem("RETRY CATEGORIES", false, this::showSeriesDashboard));
            }
        }).start();
        showItems("series", "");
    }

    private void renderSeriesSidebar(JSONArray categories) {
        categorySidebar.removeAllViews();
        browseCategoryButtons.clear();
        selectedBrowseCategoryId = "";
        browseSidebarItem("", "ALL SERIES", () -> { browserHeading.setText("ALL SERIES"); showItems("series", ""); });
        for (int i = 0; i < categories.length(); i++) {
            JSONObject c = categories.optJSONObject(i);
            if (c == null) continue;
            String id = c.optString("category_id");
            String name = c.optString("category_name", "Series");
            sidebarItem(name, false, () -> {
                browserHeading.setText(name.toUpperCase(java.util.Locale.ROOT));
                showItems("series", id);
            });
        }
    }

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

    private final java.util.concurrent.ExecutorService cardEpgPool =
        java.util.concurrent.Executors.newFixedThreadPool(3);
    private final java.util.Map<String, String> cardEpgCache =
        new java.util.concurrent.ConcurrentHashMap<>();

    private void loadLiveCardEpg(String streamId, TextView target) {
        String cached = cardEpgCache.get(streamId);
        if (cached != null) {
            target.setText(cached);
            return;
        }
        cardEpgPool.execute(() -> {
            String label = "NOW  •  Programme unavailable";
            try {
                JSONObject response = new JSONObject(fetch(api(username, password, "get_short_epg")
                    + "&stream_id=" + enc(streamId) + "&limit=5"));
                JSONArray listings = response.optJSONArray("epg_listings");
                long now = System.currentTimeMillis();
                if (listings != null) for (int i = 0; i < listings.length(); i++) {
                    JSONObject entry = listings.optJSONObject(i);
                    if (entry == null) continue;
                    long start = epgTime(entry.optString("start_timestamp"), entry.optString("start"));
                    long end = epgTime(entry.optString("stop_timestamp"), entry.optString("end"));
                    if (start <= now && end > now) {
                        String programme = decodeEpg(entry.optString("title", "")).trim();
                        if (!programme.isEmpty()) {
                            java.text.SimpleDateFormat timeFormat =
                                new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                            label = "NOW  •  " + programme + "  (" +
                                timeFormat.format(new java.util.Date(start)) + "–" +
                                timeFormat.format(new java.util.Date(end)) + ")";
                        }
                        break;
                    }
                }
            } catch (Exception ignored) { }
            final String result = label;
            cardEpgCache.put(streamId, result);
            runOnUiThread(() -> {
                if (target.getParent() != null) target.setText(result);
            });
        });
    }

    private int moviePosterHeight(String type) { return type.equals("series") && categorySidebar != null && categorySidebar.getParent() != null ? dp(265) : type.equals("vod") && categorySidebar != null && categorySidebar.getParent() != null ? dp(146) : dp(105); }

    private void showMovieDetails(String id, String name, String ext, String poster, JSONObject listing) {
        releasePlayer();
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(18), dp(20), dp(12));
        page.setBackgroundColor(Color.rgb(9, 17, 37));
        android.widget.FrameLayout scene = new android.widget.FrameLayout(this);
        scene.setBackgroundColor(Color.rgb(9, 17, 37));
        scene.setFitsSystemWindows(true);
        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.23f);
        scene.addView(backdrop, new android.widget.FrameLayout.LayoutParams(-1, -1));
        page.setBackgroundColor(Color.TRANSPARENT);
        scene.addView(page, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(scene);
        Button back = tile("‹ BACK TO MOVIES", Color.rgb(25, 42, 75), this::showMovieDashboard);
        back.setTextSize(12);
        page.addView(back, new LinearLayout.LayoutParams(dp(200), dp(48)));
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(15), 0, 0);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row);
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(cover, new LinearLayout.LayoutParams(dp(230), dp(345)));
        loadArtwork(poster, cover);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(24), dp(3), dp(5), dp(5));
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        TextView heading = text(name);
        heading.setTextSize(27);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(heading);
        TextView facts = text("Loading movie information...");
        facts.setTextSize(15);
        facts.setTextColor(Color.rgb(160, 195, 235));
        facts.setPadding(0, dp(12), 0, dp(12));
        info.addView(facts);
        LinearLayout actions = new LinearLayout(this);
        info.addView(actions);
        Button playButton = tile("▶ PLAY MOVIE", Color.rgb(23, 112, 197), () -> play("vod", id, ext));
        playButton.setTextSize(17);
        actions.addView(playButton, new LinearLayout.LayoutParams(dp(185), dp(58)));
        String[] trailerUrl = new String[]{""};
        Button trailerButton = tile("▶ TRAILER", Color.rgb(28, 47, 80), () -> {
            String link = trailerUrl[0];
            if (link.isEmpty()) {
                new android.app.AlertDialog.Builder(this).setMessage("No trailer is available for this movie.")
                    .setPositiveButton("OK", null).show();
                return;
            }
            try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(link))); }
            catch (Exception e) {
                new android.app.AlertDialog.Builder(this).setMessage("No app available to open the trailer.")
                    .setPositiveButton("OK", null).show();
            }
        });
        trailerButton.setTextSize(15);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(155), dp(58));
        tp.setMargins(dp(12), 0, 0, 0);
        actions.addView(trailerButton, tp);
        TextView description = text("Loading description...");
        description.setTextSize(17);
        description.setPadding(0, dp(22), 0, 0);
        info.addView(description);
        TextView credits = text("");
        credits.setTextSize(14);
        credits.setTextColor(Color.rgb(181, 199, 223));
        credits.setPadding(0, dp(16), 0, 0);
        info.addView(credits);
        JSONObject original = listing == null ? new JSONObject() : listing;
        populateMovieDetails(original, facts, description, trailerUrl);
        populateMovieCredits(original, credits);
        String initialBackdrop = movieBackdrop(original);
        if (!initialBackdrop.isEmpty()) loadArtwork(initialBackdrop, backdrop);
        new Thread(() -> {
            try {
                JSONObject response = new JSONObject(fetch(api(username, password, "get_vod_info") + "&vod_id=" + enc(id)));
                JSONObject detail = response.optJSONObject("info");
                if (detail == null) detail = response;
                JSONObject ready = detail;
                runOnUiThread(() -> {
                    if (scroll.getParent() != page) return;
                    populateMovieDetails(ready, facts, description, trailerUrl);
                    populateMovieCredits(ready, credits);
                    String backdropUrl = movieBackdrop(ready);
                    if (backdropUrl.isEmpty()) backdropUrl = movieBackdrop(response);
                    if (!backdropUrl.isEmpty()) loadArtwork(backdropUrl, backdrop);
                    String updatedPoster = ready.optString("movie_image", ready.optString("cover_big", ""));
                    if (!updatedPoster.isEmpty()) loadArtwork(updatedPoster, cover);
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> {
                    if (description.getText().toString().equals("No description available from your provider."))
                        description.setText("Movie description is not available from your provider.");
                });
            }
        }).start();
        playButton.requestFocus();
    }

    private String movieBackdrop(JSONObject data) {
        JSONArray backdrops = data.optJSONArray("backdrop_path");
        if (backdrops == null) backdrops = data.optJSONArray("backdrop");
        if (backdrops != null && backdrops.length() > 0) {
            String url = backdrops.optString(0, "");
            if (url.startsWith("http")) return url;
        }
        for (String key : new String[]{"backdrop_path", "backdrop", "backdrop_url", "background", "cover_big"}) {
            String url = data.optString(key, "");
            if (url.startsWith("http://") || url.startsWith("https://")) return url;
        }
        return "";
    }

    private void populateMovieCredits(JSONObject data, TextView credits) {
        String director = data.optString("director", "");
        String cast = data.optString("cast", data.optString("actors", ""));
        String country = data.optString("country", "");
        StringBuilder result = new StringBuilder();
        if (!director.isEmpty()) result.append("Director: ").append(director);
        if (!cast.isEmpty()) {
            if (result.length() > 0) result.append("\\n\\n");
            result.append("Cast: ").append(cast);
        }
        if (!country.isEmpty()) {
            if (result.length() > 0) result.append("\\n\\n");
            result.append("Country: ").append(country);
        }
        credits.setText(result.toString());
    }

    private void populateMovieDetails(JSONObject movie, TextView facts, TextView description, String[] trailerUrl) {
        String year = movie.optString("releasedate", movie.optString("releaseDate", movie.optString("year", "")));
        String genre = movie.optString("genre", "");
        String rating = movie.optString("rating", movie.optString("rating_5based", ""));
        String duration = movie.optString("duration", "");
        String meta = (year.length() >= 4 ? year.substring(0, 4) : "") +
            (!genre.isEmpty() ? "    •    " + genre : "") +
            (!duration.isEmpty() ? "    •    " + duration : "") +
            (!rating.isEmpty() ? "    •    ★ " + rating + " (provider)" : "");
        facts.setText(meta.trim().isEmpty() ? "Movie" : meta.trim());
        String plot = movie.optString("plot", movie.optString("description", movie.optString("overview", "")));
        description.setText(plot.isEmpty() ? "No description available from your provider." : plot);
        String trailer = movie.optString("youtube_trailer", movie.optString("trailer", ""));
        if (!trailer.isEmpty()) {
            if (trailer.matches("[A-Za-z0-9_-]{11}")) trailer = "https://www.youtube.com/watch?v=" + trailer;
            if (trailer.startsWith("https://") || trailer.startsWith("http://")) trailerUrl[0] = trailer;
        }
    }

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
            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setGravity(Gravity.CENTER_VERTICAL);
            details.setPadding(dp(10), dp(4), dp(4), dp(4));
            TextView titleView = text(name);
            titleView.setTextSize(16);
            titleView.setMaxLines(1);
            titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            titleView.setPadding(0, 0, 0, 0);
            details.addView(titleView);
            TextView nowView = text("NOW  •  Loading guide...");
            nowView.setTextSize(12);
            nowView.setTextColor(Color.rgb(159, 208, 245));
            nowView.setMaxLines(2);
            nowView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nowView.setPadding(0, dp(5), 0, 0);
            details.addView(nowView);
            row.addView(details, new LinearLayout.LayoutParams(0, -1, 1f));
            loadLiveCardEpg(id, nowView);
            GridLayout.LayoutParams rowParams = new GridLayout.LayoutParams();
            int available = getResources().getDisplayMetrics().widthPixels - dp(36);
            rowParams.width = Math.max(dp(140), available / 2 - dp(10));
            rowParams.height = dp(96);
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
            else if (type.equals("series")) showSeriesDetails(id, name, logo, metadata);
            else showMovieDetails(id, name, ext, logo, metadata);
        });
        card.setOnFocusChangeListener((v, focused) -> card.setBackgroundColor(
            focused ? Color.rgb(33, 107, 187) : Color.rgb(29, 36, 52)));
        ImageView image = new ImageView(this);
        image.setImageResource(android.R.drawable.ic_menu_gallery);
        image.setScaleType(type.equals("series") ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        image.setPadding(type.equals("series") ? 0 : dp(8), type.equals("series") ? 0 : dp(8),
            type.equals("series") ? 0 : dp(8), type.equals("series") ? 0 : dp(8));
        card.addView(image, new LinearLayout.LayoutParams(-1, moviePosterHeight(type)));
        loadArtwork(logo, image);
        TextView label = text(name);
        label.setTextSize(type.equals("series") ? 16 : 14);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
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
        boolean seriesWall = type.equals("series") && categorySidebar != null && categorySidebar.getParent() != null;
        boolean movieWall = type.equals("vod") && categorySidebar != null && categorySidebar.getParent() != null;
        int availableWidth = getResources().getDisplayMetrics().widthPixels
            - (seriesWall ? Math.min(dp(190), Math.max(dp(128), getResources().getDisplayMetrics().widthPixels / 6)) : movieWall ? Math.min(dp(255), Math.max(dp(150), getResources().getDisplayMetrics().widthPixels / 4)) : dp(16))
            - dp(26);
        int cols = contentGrid.getColumnCount();
        gp.width = seriesWall ? Math.max(dp(140), availableWidth / cols - dp(12)) :
            movieWall ? Math.max(dp(95), availableWidth / cols - dp(12)) : dp(155);
        gp.height = seriesWall ? dp(330) : movieWall ? dp(214) : dp(170);
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

    private void showSeriesDetails(String seriesId, String name, String poster, JSONObject listing) {
        releasePlayer();
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setFitsSystemWindows(true);
        page.setPadding(dp(16), dp(8), dp(16), dp(8));
        page.setBackgroundColor(Color.rgb(9, 17, 37));
        android.widget.FrameLayout scene = new android.widget.FrameLayout(this);
        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.18f);
        scene.addView(backdrop, new android.widget.FrameLayout.LayoutParams(-1, -1));
        page.setBackgroundColor(Color.TRANSPARENT);
        scene.addView(page, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(scene);
        if (listing != null) {
            String background = movieBackdrop(listing);
            if (!background.isEmpty()) loadArtwork(background, backdrop);
        }
        Button back = tile("‹ BACK TO SERIES", Color.rgb(25, 42, 75), this::showSeriesDashboard);
        page.addView(back, new LinearLayout.LayoutParams(dp(190), dp(45)));
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        LinearLayout hero = new LinearLayout(this);
        hero.setPadding(0, dp(14), 0, dp(14));
        content.addView(hero);
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        hero.addView(cover, new LinearLayout.LayoutParams(dp(215), dp(310)));
        loadArtwork(poster, cover);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(20), 0, 0, 0);
        hero.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        TextView heading = text(name);
        heading.setTextSize(26);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(heading);
        TextView facts = text("Loading series details...");
        facts.setTextSize(15);
        facts.setTextColor(Color.rgb(160, 195, 235));
        info.addView(facts);
        TextView plot = text(listing == null ? "" : listing.optString("plot", ""));
        plot.setTextSize(16);
        plot.setPadding(0, dp(14), 0, dp(10));
        info.addView(plot);
        TextView seasonLabel = text("SEASONS & EPISODES");
        seasonLabel.setTextSize(21);
        seasonLabel.setTypeface(Typeface.DEFAULT_BOLD);
        seasonLabel.setPadding(0, dp(6), 0, dp(14));
        content.addView(seasonLabel);
        LinearLayout episodesList = new LinearLayout(this);
        episodesList.setOrientation(LinearLayout.VERTICAL);
        content.addView(episodesList);
        episodesList.addView(text("Loading episodes..."));
        new Thread(() -> {
            try {
                JSONObject response = new JSONObject(fetch(api(username, password, "get_series_info") + "&series_id=" + enc(seriesId)));
                JSONObject metadata = response.optJSONObject("info");
                JSONObject episodes = response.optJSONObject("episodes");
                runOnUiThread(() -> {
                    if (scroll.getParent() != page) return;
                    if (metadata != null) {
                        String year = metadata.optString("releaseDate", metadata.optString("year", ""));
                        String genre = metadata.optString("genre", "");
                        String rating = metadata.optString("rating", "");
                        facts.setText(year + (!genre.isEmpty() ? "   •   " + genre : "") +
                            (!rating.isEmpty() ? "   •   ★ " + rating + " (provider)" : ""));
                        String description = metadata.optString("plot", metadata.optString("description", ""));
                        if (!description.isEmpty()) plot.setText(description);
                        String image = metadata.optString("cover", "");
                        if (!image.isEmpty()) loadArtwork(image, cover);
                        String background = movieBackdrop(metadata);
                        if (background.isEmpty()) background = movieBackdrop(response);
                        if (!background.isEmpty()) loadArtwork(background, backdrop);
                    }
                    episodesList.removeAllViews();
                    if (episodes == null || episodes.length() == 0) {
                        episodesList.addView(text("No episodes available from your provider."));
                        return;
                    }
                    java.util.ArrayList<String> keys = new java.util.ArrayList<>();
                    java.util.Iterator<String> iterator = episodes.keys();
                    while (iterator.hasNext()) keys.add(iterator.next());
                    keys.sort((x, y) -> {
                        try { return Integer.compare(Integer.parseInt(x), Integer.parseInt(y)); }
                        catch (NumberFormatException e) { return x.compareTo(y); }
                    });
                    for (String season : keys) {
                        JSONArray entries = episodes.optJSONArray(season);
                        if (entries == null) continue;
                        Button seasonButton = tile("SEASON " + season + "  (" + entries.length() + " episodes)  ▾",
                            Color.rgb(25, 42, 75), () -> {});
                        episodesList.addView(seasonButton, new LinearLayout.LayoutParams(-1, dp(48)));
                        LinearLayout seasonItems = new LinearLayout(this);
                        seasonItems.setOrientation(LinearLayout.VERTICAL);
                        episodesList.addView(seasonItems);
                        seasonButton.setOnClickListener(v -> seasonItems.setVisibility(
                            seasonItems.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
                        for (int i = 0; i < entries.length(); i++) {
                            JSONObject episode = entries.optJSONObject(i);
                            if (episode == null) continue;
                            String episodeId = episode.optString("id");
                            String extension = episode.optString("container_extension", "mp4");
                            String title = episode.optString("title", "Episode " + (i + 1));
                            JSONObject episodeInfo = episode.optJSONObject("info");
                            String duration = episodeInfo == null ? "" : episodeInfo.optString("duration", "");
                            Button episodeButton = tile("▶  " + title + (duration.isEmpty() ? "" : "  •  " + duration),
                                Color.rgb(26, 39, 65), () -> play("series", episodeId, extension));
                            episodeButton.setTextSize(14);
                            episodeButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
                            LinearLayout.LayoutParams episodeParams = new LinearLayout.LayoutParams(-1, dp(54));
                            episodeParams.setMargins(dp(10), dp(3), dp(10), dp(3));
                            seasonItems.addView(episodeButton, episodeParams);
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (scroll.getParent() != page) return;
                    episodesList.removeAllViews();
                    episodesList.addView(text("Unable to load series episodes. Please try again."));
                });
            }
        }).start();
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

    private org.videolan.libvlc.LibVLC vlcEngine;
    private org.videolan.libvlc.MediaPlayer vlcPlayer;
    private org.videolan.libvlc.util.VLCVideoLayout vlcVideo;

    private void releaseVlc() {
        if (vlcPlayer != null) {
            vlcPlayer.stop();
            vlcPlayer.detachViews();
            vlcPlayer.release();
            vlcPlayer = null;
        }
        if (vlcEngine != null) { vlcEngine.release(); vlcEngine = null; }
        vlcVideo = null;
    }

    // Native VLC fallback supplies its own software codecs when Android MediaCodec cannot play a stream.
    private boolean draggingSeek(SeekBar seek) { return seek.isPressed(); }
    private String formatVlcTime(long ms) {
        long seconds = Math.max(0, ms / 1000);
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }

    private void playWithVlc(String type, String id, String ext, String originalUrl) {
        releasePlayer();
        releaseVlc();
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        setContentView(frame);
        vlcVideo = new org.videolan.libvlc.util.VLCVideoLayout(this);
        frame.addView(vlcVideo, new android.widget.FrameLayout.LayoutParams(-1, -1));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(12), dp(22), dp(14));
        panel.setBackgroundColor(Color.argb(190, 5, 12, 27));
        android.widget.FrameLayout.LayoutParams panelParams = new android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        frame.addView(panel, panelParams);
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        if (!type.equals("live")) {
            LinearLayout timeline = new LinearLayout(this);
            timeline.setGravity(Gravity.CENTER_VERTICAL);
            TextView elapsed = text("00:00");
            elapsed.setTextSize(13);
            timeline.addView(elapsed, new LinearLayout.LayoutParams(dp(82), -2));
            SeekBar seek = new SeekBar(this);
            seek.setMax(1000);
            seek.setFocusable(true);
            timeline.addView(seek, new LinearLayout.LayoutParams(0, dp(42), 1));
            TextView duration = text("00:00");
            duration.setTextSize(13);
            timeline.addView(duration, new LinearLayout.LayoutParams(dp(82), -2));
            panel.addView(timeline, new LinearLayout.LayoutParams(-1, dp(45)));
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                private boolean dragging = false;
                @Override public void onStartTrackingTouch(SeekBar bar) { dragging = true; }
                @Override public void onStopTrackingTouch(SeekBar bar) {
                    dragging = false;
                    if (vlcPlayer != null && vlcPlayer.isSeekable() && vlcPlayer.getLength() > 0)
                        vlcPlayer.setTime(vlcPlayer.getLength() * bar.getProgress() / 1000);
                }
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser && vlcPlayer != null && vlcPlayer.isSeekable() && vlcPlayer.getLength() > 0)
                        elapsed.setText(formatVlcTime(vlcPlayer.getLength() * progress / 1000));
                }
            });
            seek.setOnKeyListener((v, key, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN || vlcPlayer == null || !vlcPlayer.isSeekable()) return false;
                if (key == android.view.KeyEvent.KEYCODE_DPAD_LEFT || key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    long len = vlcPlayer.getLength();
                    if (len > 0) vlcPlayer.setTime(Math.max(0, Math.min(len, vlcPlayer.getTime() +
                        (key == android.view.KeyEvent.KEYCODE_DPAD_LEFT ? -10000 : 10000))));
                    return true;
                }
                return false;
            });
            android.os.Handler progressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            Runnable progressUpdate = new Runnable() {
                @Override public void run() {
                    if (vlcPlayer == null || panel.getParent() == null) return;
                    long len = vlcPlayer.getLength();
                    long pos = Math.max(0, vlcPlayer.getTime());
                    if (len > 0) {
                        duration.setText(formatVlcTime(len));
                        if (!draggingSeek(seek)) seek.setProgress((int) Math.min(1000, pos * 1000 / len));
                    }
                    elapsed.setText(formatVlcTime(pos));
                    progressHandler.postDelayed(this, 800);
                }
            };
            progressHandler.post(progressUpdate);
        }
        panel.addView(controls, new LinearLayout.LayoutParams(-1, dp(62)));
        Button back = tile("‹ BACK", Color.rgb(25, 42, 75), () -> {
            releaseVlc();
            if (type.equals("live")) showLiveDashboard(); else showMovieDashboard();
        });
        back.setTextSize(13);
        controls.addView(back, new LinearLayout.LayoutParams(dp(88), dp(50)));
        Button toggle = tile("PAUSE", Color.rgb(25, 42, 75), () -> {
            if (vlcPlayer == null) return;
            if (vlcPlayer.isPlaying()) { vlcPlayer.pause(); }
            else vlcPlayer.play();
        });
        toggle.setTextSize(14);
        controls.addView(toggle, new LinearLayout.LayoutParams(dp(108), dp(54)));
        if (!type.equals("live")) {
            Button rewind = tile("« 10s", Color.rgb(25, 42, 75), () -> {
                if (vlcPlayer != null && vlcPlayer.isSeekable())
                    vlcPlayer.setTime(Math.max(0, vlcPlayer.getTime() - 10000));
            });
            rewind.setTextSize(13);
            controls.addView(rewind, new LinearLayout.LayoutParams(dp(84), dp(48)));
            Button forward = tile("10s »", Color.rgb(25, 42, 75), () -> {
                if (vlcPlayer != null && vlcPlayer.isSeekable())
                    vlcPlayer.setTime(Math.min(vlcPlayer.getLength(), vlcPlayer.getTime() + 10000));
            });
            forward.setTextSize(13);
            controls.addView(forward, new LinearLayout.LayoutParams(dp(84), dp(48)));
        }
        Button size = tile("SIZE", Color.rgb(25, 42, 75), () -> {
            if (vlcPlayer == null) return;
            String[] modes = {"Fit screen", "Fill screen (crop)", "Stretch to screen", "16:9", "4:3"};
            new android.app.AlertDialog.Builder(this).setTitle("Video size")
                .setSingleChoiceItems(modes, -1, (dialog, choice) -> {
                    if (vlcPlayer == null) return;
                    if (choice == 0) { vlcPlayer.setAspectRatio(null); vlcPlayer.setScale(0); }
                    else if (choice == 1) { vlcPlayer.setAspectRatio(null); vlcPlayer.setScale(1.25f); }
                    else if (choice == 2) { vlcPlayer.setAspectRatio("16:9"); vlcPlayer.setScale(0); }
                    else if (choice == 3) { vlcPlayer.setAspectRatio("16:9"); vlcPlayer.setScale(0); }
                    else { vlcPlayer.setAspectRatio("4:3"); vlcPlayer.setScale(0); }
                    dialog.dismiss();
                }).show();
        });
        size.setTextSize(12);
        controls.addView(size, new LinearLayout.LayoutParams(dp(75), dp(48)));
        Button audio = tile("AUDIO", Color.rgb(25, 42, 75), () -> {
            if (vlcPlayer == null) return;
            org.videolan.libvlc.MediaPlayer.TrackDescription[] tracks = vlcPlayer.getAudioTracks();
            if (tracks == null || tracks.length == 0) return;
            String[] labels = new String[tracks.length];
            for (int i = 0; i < tracks.length; i++) labels[i] = tracks[i].name;
            new android.app.AlertDialog.Builder(this).setTitle("Audio track")
                .setItems(labels, (dialog, index) -> vlcPlayer.setAudioTrack(tracks[index].id)).show();
        });
        audio.setTextSize(12);
        controls.addView(audio, new LinearLayout.LayoutParams(dp(80), dp(48)));
        Button subs = tile("SUBS", Color.rgb(25, 42, 75), () -> {
            if (vlcPlayer == null) return;
            org.videolan.libvlc.MediaPlayer.TrackDescription[] tracks = vlcPlayer.getSpuTracks();
            if (tracks == null || tracks.length == 0) return;
            String[] labels = new String[tracks.length];
            for (int i = 0; i < tracks.length; i++) labels[i] = tracks[i].name;
            new android.app.AlertDialog.Builder(this).setTitle("Subtitles")
                .setItems(labels, (dialog, index) -> vlcPlayer.setSpuTrack(tracks[index].id)).show();
        });
        subs.setTextSize(12);
        controls.addView(subs, new LinearLayout.LayoutParams(dp(76), dp(48)));
        TextView status = text("VLC software decoder • Loading...");
        status.setTextSize(13);
        status.setVisibility(View.GONE);
        panel.addView(status, new LinearLayout.LayoutParams(-1, -2));
        // Movie-player style: unobstructed video with a compact bottom transport overlay.
        panel.setVisibility(View.VISIBLE);
        android.os.Handler hideHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable hideControls = () -> { if (panel.getParent() != null) panel.setVisibility(View.GONE); };
        Runnable revealControls = () -> {
            panel.setVisibility(View.VISIBLE);
            hideHandler.removeCallbacks(hideControls);
            hideHandler.postDelayed(hideControls, 5500);
        };
        revealControls.run();
        vlcVideo.setOnClickListener(v -> {
            if (panel.getVisibility() == View.VISIBLE) { panel.setVisibility(View.GONE); hideHandler.removeCallbacks(hideControls); }
            else { revealControls.run(); toggle.requestFocus(); }
        });
        vlcVideo.setFocusable(true);
        vlcVideo.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (key == android.view.KeyEvent.KEYCODE_DPAD_CENTER || key == android.view.KeyEvent.KEYCODE_ENTER) {
                revealControls.run();
                toggle.requestFocus();
                return true;
            }
            return false;
        });
        // Touch and remote navigation refreshes the visibility timeout.
        for (int i = 0; i < controls.getChildCount(); i++) {
            View control = controls.getChildAt(i);
            control.setOnFocusChangeListener((v, focused) -> {
                if (focused) { hideHandler.removeCallbacks(hideControls); panel.setVisibility(View.VISIBLE); }
                else hideHandler.postDelayed(hideControls, 5500);
            });
        }
        try {
            java.util.ArrayList<String> options = new java.util.ArrayList<>();
            options.add("--network-caching=1200");
            options.add("--avcodec-hw=none");
            vlcEngine = new org.videolan.libvlc.LibVLC(this, options);
            vlcPlayer = new org.videolan.libvlc.MediaPlayer(vlcEngine);
            vlcPlayer.attachViews(vlcVideo, null, false, false);
            org.videolan.libvlc.Media media = new org.videolan.libvlc.Media(vlcEngine, Uri.parse(originalUrl));
            media.addOption(":network-caching=1200");
            vlcPlayer.setMedia(media);
            media.release();
            vlcPlayer.setEventListener(event -> runOnUiThread(() -> {
                if (vlcPlayer == null) return;
                if (event.type == org.videolan.libvlc.MediaPlayer.Event.Playing) status.setText("VLC • Playing");
                else if (event.type == org.videolan.libvlc.MediaPlayer.Event.EncounteredError)
                    status.setText("VLC cannot play this stream. Check the provider format.");
                toggle.setText(vlcPlayer.isPlaying() ? "PAUSE" : "PLAY");
            }));
            vlcPlayer.play();
        } catch (Exception error) { status.setText("VLC failed: " + error.getMessage()); }
    }

    private void play(String type, String id, String ext) {
        if (type.equals("vod") || type.equals("series")) {
            String folder = type.equals("vod") ? "movie" : "series";
            String safeExt = ext != null && ext.matches("[a-zA-Z0-9]{1,5}") ? ext : "mp4";
            String url = server + "/" + folder + "/" + path(username) + "/" + path(password)
                + "/" + path(id) + "." + safeExt;
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            playWithVlc(type, id, ext, url);
            return;
        }

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
        player = new ExoPlayer.Builder(this, new androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)).build();
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
                    playbackStatus.setText("Switching to VLC software decoder...");
                    playWithVlc(type, id, ext, initialStream);
                }
            }
        });
        player.setMediaItem(MediaItem.fromUri(initialStream));
        player.prepare(); player.play();
    }

    private void startStream(String type, String id, String ext, PlayerView view) {
        player = new ExoPlayer.Builder(this, new androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)).build();
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

    // Check the published GitHub Release once after launch, without blocking the UI.
    private void checkForUpdatesOnLaunch() {
        new Thread(() -> {
            try {
                JSONObject release = new JSONObject(fetchAbsolute(RELEASES_API));
                String tag = release.optString("tag_name", "").replaceFirst("^[vV]", "");
                if (tag.isEmpty() || compareVersions(tag, BuildConfig.VERSION_NAME) <= 0) return;
                JSONArray assets = release.optJSONArray("assets");
                String apkUrl = "";
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset != null && asset.optString("name", "").endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "");
                        break;
                    }
                }
                if (!apkUrl.startsWith("https://github.com/")) return;
                final String url = apkUrl;
                runOnUiThread(() -> {
                    if (isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                    new android.app.AlertDialog.Builder(this)
                        .setTitle("Dream IPTV update available")
                        .setMessage("Version " + tag + " is ready.\\nInstalled: " +
                            BuildConfig.VERSION_NAME + "\\n\\nDownload and install the update?")
                        .setPositiveButton("DOWNLOAD UPDATE", (dialog, which) -> {
                            TextView downloadStatus = new TextView(this);
                            downloadUpdate(url, downloadStatus);
                        })
                        .setNegativeButton("LATER", null)
                        .show();
                });
            } catch (Exception ignored) {
                // No network connection: keep the app usable; manual check remains in Settings.
            }
        }).start();
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
            status.setText("Downloading update... 0%");
            ProgressBar downloadBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            downloadBar.setMax(100);
            downloadBar.setProgress(0);
            LinearLayout progressLayout = new LinearLayout(this);
            progressLayout.setOrientation(LinearLayout.VERTICAL);
            progressLayout.setPadding(dp(24), dp(16), dp(24), dp(12));
            TextView progressLabel = text("Downloading Dream IPTV update • 0%");
            progressLabel.setTextSize(16);
            progressLayout.addView(progressLabel);
            progressLayout.addView(downloadBar, new LinearLayout.LayoutParams(-1, dp(18)));
            android.app.AlertDialog progressDialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Updating Dream IPTV")
                .setView(progressLayout)
                .setNegativeButton("HIDE", (dialog, which) -> {})
                .create();
            progressDialog.show();
            android.os.Handler downloadHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            Runnable pollProgress = new Runnable() {
                @Override public void run() {
                    if (updateDownloadId < 0) return;
                    DownloadManager.Query progressQuery = new DownloadManager.Query().setFilterById(updateDownloadId);
                    try (Cursor progressCursor = manager.query(progressQuery)) {
                        if (progressCursor == null || !progressCursor.moveToFirst()) return;
                        int state = progressCursor.getInt(progressCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        long total = progressCursor.getLong(progressCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        long done = progressCursor.getLong(progressCursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        if (total > 0) {
                            int percent = (int) Math.min(100, done * 100 / total);
                            downloadBar.setProgress(percent);
                            String progressText = "Downloading Dream IPTV update • " + percent + "%";
                            progressLabel.setText(progressText);
                            status.setText(progressText);
                        } else {
                            progressLabel.setText("Downloading Dream IPTV update • " + (done / 1048576) + " MB");
                            status.setText("Downloading update • " + (done / 1048576) + " MB");
                        }
                        if (state == DownloadManager.STATUS_SUCCESSFUL) {
                            downloadBar.setProgress(100);
                            progressLabel.setText("Download complete • 100%");
                            progressDialog.dismiss();
                            return;
                        }
                        if (state == DownloadManager.STATUS_FAILED) {
                            progressLabel.setText("Download failed");
                            progressDialog.dismiss();
                            return;
                        }
                    } catch (Exception ignored) { return; }
                    downloadHandler.postDelayed(this, 700);
                }
            };
            downloadHandler.post(pollProgress);
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

    private void releasePlayer() { releaseVlc(); ++epgRequest; if (epgRefresh != null) { epgHandler.removeCallbacks(epgRefresh); epgRefresh = null; } if (player != null) { player.release(); player = null; } }
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
