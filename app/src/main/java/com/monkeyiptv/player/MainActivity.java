package com.monkeyiptv.player;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
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
    private String username, password;
    private String currentType = "live";
    private String currentCategory = "";
    private String server = BuildConfig.SERVER_URL.replaceAll("/$", "");
    private static final String RELEASES_API = "https://api.github.com/repos/muradh47/MonkeyIPTV/releases/latest";

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
        EditText pass = loginField("Password", true);
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
                    getPreferences(0).edit().putString("username", u).putString("password", p).apply();
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
        screen("DREAM IPTV");
        root.addView(text("Your entertainment, your way"));
        LinearLayout main = row();
        addTile(main, "▣\\nLIVE TV", Color.rgb(0, 148, 181), 242, () -> showCategories("live"));
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        main.addView(right, new LinearLayout.LayoutParams(0, -2, 2f));
        LinearLayout top = new LinearLayout(this);
        right.addView(top);
        addTile(top, "▶\\nMOVIES", Color.rgb(221, 78, 39), 145, () -> showCategories("vod"));
        addTile(top, "▤\\nSERIES", Color.rgb(112, 77, 187), 145, () -> showCategories("series"));
        LinearLayout lower = new LinearLayout(this);
        right.addView(lower);
        addTile(lower, "TV GUIDE", Color.rgb(37, 134, 109), 87, this::showGuide);
        addTile(lower, "CATCH UP", Color.rgb(36, 120, 97), 87, () -> showCategories("live"));
        LinearLayout tools = row();
        addTile(tools, "CHECK UPDATES", Color.rgb(38, 57, 89), 88, this::checkUpdates);
        addTile(tools, "LOG OUT", Color.rgb(38, 57, 89), 88, () -> {
            getPreferences(0).edit().clear().apply();
            username = ""; password = ""; showLogin();
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
        screen(channel);
        button("← " + (fromGuide ? "TV Guide" : "Channels"),
            () -> { if (fromGuide) showGuide(); else showItems("live", currentCategory); });
        button("▶ WATCH LIVE", () -> play("live", id, "ts"));
        root.addView(text("TV PROGRAMME GUIDE"));
        TextView status = text("Loading programme information...");
        root.addView(status);
        new Thread(() -> {
            try {
                JSONObject data = new JSONObject(fetch(api(username, password, "get_short_epg") + "&stream_id=" + enc(id) + "&limit=20"));
                JSONArray entries = data.optJSONArray("epg_listings");
                runOnUiThread(() -> {
                    status.setText(entries == null || entries.length() == 0
                        ? "No EPG available from the provider for this channel."
                        : "Programme schedule");
                    if (entries == null) return;
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject item = entries.optJSONObject(i);
                        if (item == null) continue;
                        String title = decodeEpg(item.optString("title", ""));
                        String desc = decodeEpg(item.optString("description", ""));
                        String start = item.optString("start", "");
                        String end = item.optString("end", "");
                        TextView programme = text(start + " - " + end + "\\n" + title
                            + (desc.isEmpty() ? "" : "\\n" + desc));
                        programme.setTextSize(16);
                        programme.setBackgroundColor(i % 2 == 0 ? Color.rgb(24, 37, 62) : Color.rgb(18, 28, 49));
                        root.addView(programme);
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> status.setText("EPG unavailable: " + ex.getMessage()));
            }
        }).start();
    }

    private String decodeEpg(String raw) {
        if (raw.isEmpty()) return raw;
        try {
            byte[] bytes = android.util.Base64.decode(raw, android.util.Base64.DEFAULT);
            String decoded = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            return decoded.indexOf((char) 0xFFFD) >= 0 ? raw : decoded;
        } catch (Exception ignored) { return raw; }
    }

    private String api(String u, String p, String action) throws Exception {
        return "player_api.php?username=" + enc(u) + "&password=" + enc(p)
            + (action.isEmpty() ? "" : "&action=" + action);
    }

    private LinearLayout categorySidebar;
    private GridLayout contentGrid;
    private TextView browserHeading;
    private int browseRequest = 0;

    private void browserShell(String type) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(11, 16, 35));
        setContentView(page);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(68)));
        Button back = tile("‹ HOME", Color.rgb(34, 47, 76), this::showHome);
        header.addView(back, new LinearLayout.LayoutParams(dp(115), dp(50)));
        browserHeading = text(type.equals("live") ? "LIVE TV" : type.equals("vod") ? "MOVIES" : "SERIES");
        browserHeading.setTextSize(23);
        header.addView(browserHeading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView clock = text(new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date()));
        header.addView(clock);

        LinearLayout body = new LinearLayout(this);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView left = new ScrollView(this);
        left.setBackgroundColor(Color.rgb(22, 31, 56));
        body.addView(left, new LinearLayout.LayoutParams(dp(210), -1));
        categorySidebar = new LinearLayout(this);
        categorySidebar.setOrientation(LinearLayout.VERTICAL);
        left.addView(categorySidebar);

        ScrollView right = new ScrollView(this);
        body.addView(right, new LinearLayout.LayoutParams(0, -1, 1));
        contentGrid = new GridLayout(this);
        contentGrid.setColumnCount(3);
        contentGrid.setPadding(dp(8), dp(8), dp(8), dp(8));
        right.addView(contentGrid);
    }

    private void sidebarItem(String title, boolean selected, Runnable action) {
        Button item = tile(title, selected ? Color.rgb(23, 110, 185) : Color.rgb(29, 39, 65), action);
        item.setTextSize(15);
        item.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(60));
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        categorySidebar.addView(item, p);
    }

    private void showCategories(String type) {
        currentType = type;
        browserShell(type);
        sidebarItem("Loading categories...", true, () -> {});
        new Thread(() -> {
            try {
                String action = type.equals("live") ? "get_live_categories" :
                    type.equals("vod") ? "get_vod_categories" : "get_series_categories";
                JSONArray categories = new JSONArray(fetch(api(username, password, action)));
                runOnUiThread(() -> {
                    categorySidebar.removeAllViews();
                    sidebarItem("ALL", true, () -> showItems(type, ""));
                    for (int i = 0; i < categories.length(); i++) {
                        JSONObject c = categories.optJSONObject(i);
                        if (c == null) continue;
                        String id = c.optString("category_id");
                        String name = c.optString("category_name", "Other");
                        sidebarItem(name, false, () -> showItems(type, id));
                    }
                    showItems(type, "");
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    categorySidebar.removeAllViews();
                    sidebarItem("ALL", true, () -> showItems(type, ""));
                    contentGrid.removeAllViews();
                    contentGrid.addView(text("Category loading failed: " + ex.getMessage()));
                    showItems(type, "");
                });
            }
        }).start();
    }

    private void showItems(String type, String categoryId) {
        currentType = type;
        currentCategory = categoryId;
        int requestId = ++browseRequest;
        if (contentGrid == null || categorySidebar == null) { showCategories(type); return; }
        browserHeading.setText(type.equals("live") ? "LIVE TV" : type.equals("vod") ? "MOVIES" : "SERIES");
        contentGrid.removeAllViews();
        contentGrid.addView(text("Loading channels and posters..."));
        for (int i = 0; i < categorySidebar.getChildCount(); i++) {
            View v = categorySidebar.getChildAt(i);
            if (v instanceof Button) v.setAlpha(0.85f);
        }
        new Thread(() -> {
            try {
                String action = type.equals("live") ? "get_live_streams" :
                    type.equals("vod") ? "get_vod_streams" : "get_series";
                String request = api(username, password, action);
                if (!categoryId.isEmpty()) request += "&category_id=" + enc(categoryId);
                JSONArray items = new JSONArray(fetch(request));
                runOnUiThread(() -> {
                    if (requestId != browseRequest || contentGrid == null) return;
                    contentGrid.removeAllViews();
                    if (items.length() == 0) contentGrid.addView(text("No content in this category"));
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;
                        String id = item.optString(type.equals("series") ? "series_id" : "stream_id");
                        if (id.isEmpty()) continue;
                        String name = item.optString("name", "Untitled");
                        String logo = item.optString(type.equals("live") ? "stream_icon" : "cover", "");
                        String ext = item.optString("container_extension", "mp4");
                        channelCard(name, logo, type, id, ext);
                    }
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

    private void channelCard(String name, String logo, String type, String id, String ext) {
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
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(105)));
        TextView label = text(name);
        label.setTextSize(14);
        label.setMaxLines(2);
        label.setGravity(Gravity.CENTER);
        card.addView(label, new LinearLayout.LayoutParams(-1, dp(56)));
        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
        gp.width = dp(155); gp.height = dp(170);
        gp.setMargins(dp(6), dp(6), dp(6), dp(6));
        contentGrid.addView(card, gp);
        if (logo.startsWith("https://") || logo.startsWith("http://")) {
            new Thread(() -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(logo).openConnection();
                    conn.setConnectTimeout(4500); conn.setReadTimeout(4500);
                    Bitmap bmp;
                    try (InputStream in = conn.getInputStream()) { bmp = BitmapFactory.decodeStream(in); }
                    finally { conn.disconnect(); }
                    if (bmp != null) runOnUiThread(() -> image.setImageBitmap(bmp));
                } catch (Exception ignored) {}
            }).start();
        }
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
        screen("Now playing");
        button("← Back", () -> { releasePlayer(); if (type.equals("live")) showItems("live", currentCategory); else showCategories(type); });
        PlayerView view = new PlayerView(this);
        root.addView(view, new LinearLayout.LayoutParams(-1, getResources().getDisplayMetrics().heightPixels * 2 / 3));
        player = new ExoPlayer.Builder(this).build();
        view.setPlayer(player);
        String folder = type.equals("vod") ? "movie" : type;
        String safeExt = ext.matches("[a-zA-Z0-9]{1,5}") ? ext : "mp4";
        String stream = server + "/" + folder + "/" + path(username) + "/" + path(password) + "/" + path(id) + "." + safeExt;
        TextView playbackStatus = text("Connecting to stream...");
        root.addView(playbackStatus);
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

    private void checkUpdates() {
        root.addView(text("Checking GitHub Releases..."));
        new Thread(() -> {
            try {
                JSONObject release = new JSONObject(fetchAbsolute(RELEASES_API));
                String tag = release.optString("tag_name", "");
                String link = release.optString("html_url", "");
                runOnUiThread(() -> {
                    if (tag.isEmpty() || link.isEmpty()) { root.addView(text("No published release found.")); return; }
                    root.addView(text("Latest release: " + tag + " • Installed: " + BuildConfig.VERSION_NAME));
                    button("Open release download page", () -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link))));
                });
            } catch (Exception e) { runOnUiThread(() -> root.addView(text("Could not check releases: " + e.getMessage()))); }
        }).start();
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

    private void releasePlayer() { if (player != null) { player.release(); player = null; } }
    @Override protected void onStop() { super.onStop(); releasePlayer(); }
    @Override public void onBackPressed() {
        if (player != null) { releasePlayer(); if (currentType.equals("live")) showItems("live", currentCategory); else showCategories(currentType); }
        else if (!username.isEmpty()) showHome(); else super.onBackPressed();
    }
}
