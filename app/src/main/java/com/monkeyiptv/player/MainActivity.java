package com.monkeyiptv.player;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.widget.*;
import android.view.View;
import androidx.media3.common.MediaItem;
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
    private String server = BuildConfig.SERVER_URL.replaceAll("/$", "");
    private static final String RELEASES_API = "https://api.github.com/repos/muradh47/MonkeyIPTV/releases/latest";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
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
        root.setPadding(20, 20, 20, 20);
        root.setBackgroundColor(Color.rgb(13, 18, 30));
        scroll.addView(root); setContentView(scroll);
        TextView heading = text(title);
        heading.setTextSize(25); root.addView(heading);
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

    private void showLogin() {
        screen("MONKEY IPTV  •  Sign in");
        EditText user = new EditText(this);
        user.setHint("Username"); user.setSingleLine(true);
        user.setTextColor(Color.WHITE); user.setHintTextColor(Color.LTGRAY); root.addView(user);
        EditText pass = new EditText(this);
        pass.setHint("Password"); pass.setSingleLine(true);
        pass.setInputType(129);
        pass.setTextColor(Color.WHITE); pass.setHintTextColor(Color.LTGRAY); root.addView(pass);
        TextView status = text(""); root.addView(status);
        Button login = button("Log in", () -> {
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
    }

    private void showHome() {
        releasePlayer();
        screen("MONKEY IPTV");
        root.addView(text("Welcome back  •  Choose what to watch"));
        button("📺   LIVE TV", () -> showCategories("live"));
        button("🎬   MOVIES", () -> showCategories("vod"));
        button("📺   SERIES", () -> showCategories("series"));
        button("⬇   Check for app updates", this::checkUpdates);
        button("Log out", () -> {
            getPreferences(0).edit().clear().apply();
            username = ""; password = ""; showLogin();
        });
    }

    private String api(String u, String p, String action) throws Exception {
        return "player_api.php?username=" + enc(u) + "&password=" + enc(p)
            + (action.isEmpty() ? "" : "&action=" + action);
    }

    private void showCategories(String type) {
        currentType = type;
        screen("Loading categories...");
        new Thread(() -> {
            try {
                String action = type.equals("live") ? "get_live_categories" :
                    type.equals("vod") ? "get_vod_categories" : "get_series_categories";
                JSONArray categories = new JSONArray(fetch(api(username, password, action)));
                runOnUiThread(() -> {
                    screen(type.equals("live") ? "Live TV" : type.equals("vod") ? "Movies" : "Series");
                    button("← Home", this::showHome);
                    button("All", () -> showItems(type, ""));
                    for (int i = 0; i < categories.length(); i++) {
                        JSONObject category = categories.optJSONObject(i);
                        if (category == null) continue;
                        String id = category.optString("category_id");
                        String name = category.optString("category_name", "Other");
                        button(name, () -> showItems(type, id));
                    }
                    if (categories.length() == 0) root.addView(text("No categories returned by provider. Try All."));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    screen("Categories unavailable");
                    button("← Home", this::showHome);
                    root.addView(text(e.getMessage()));
                    button("Show all items", () -> showItems(type, ""));
                });
            }
        }).start();
    }

    private void showItems(String type, String categoryId) {
        currentType = type;
        screen("Loading...");
        new Thread(() -> {
            try {
                String action = type.equals("live") ? "get_live_streams" :
                    type.equals("vod") ? "get_vod_streams" : "get_series";
                String request = api(username, password, action);
                if (!categoryId.isEmpty()) request += "&category_id=" + enc(categoryId);
                JSONArray items = new JSONArray(fetch(request));
                runOnUiThread(() -> {
                    screen(type.equals("live") ? "Live channels" : type.equals("vod") ? "Movies" : "Series");
                    button("← Categories", () -> showCategories(type));
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;
                        String id = item.optString(type.equals("series") ? "series_id" : "stream_id");
                        if (id.isEmpty()) continue;
                        String name = item.optString("name", "Untitled");
                        button(name, () -> {
                            if (type.equals("series")) showEpisodes(id, name);
                            else play(type, id, type.equals("vod") ? item.optString("container_extension", "mp4") : "ts");
                        });
                    }
                    if (items.length() == 0) root.addView(text("No items in this category."));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    screen("Unable to load content");
                    button("← Categories", () -> showCategories(type));
                    root.addView(text(e.getMessage()));
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

    private void play(String type, String id, String ext) {
        releasePlayer();
        screen("Now playing");
        button("← Back", () -> { releasePlayer(); showCategories(type); });
        PlayerView view = new PlayerView(this);
        root.addView(view, new LinearLayout.LayoutParams(-1, 600));
        player = new ExoPlayer.Builder(this).build();
        view.setPlayer(player);
        String folder = type.equals("vod") ? "movie" : type;
        String safeExt = ext.matches("[a-zA-Z0-9]{1,5}") ? ext : "mp4";
        String stream = server + "/" + folder + "/" + path(username) + "/" + path(password) + "/" + path(id) + "." + safeExt;
        player.setMediaItem(MediaItem.fromUri(stream));
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
        c.setRequestProperty("User-Agent", "MonkeyIPTV-Android");
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
        if (player != null) { releasePlayer(); showCategories(currentType); }
        else if (!username.isEmpty()) showHome(); else super.onBackPressed();
    }
}
