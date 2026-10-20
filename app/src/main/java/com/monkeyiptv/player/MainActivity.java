package com.monkeyiptv.player;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.*;

public class MainActivity extends Activity {
    private LinearLayout root;
    private ExoPlayer player;
    private String username, password;
    private final String server = BuildConfig.SERVER_URL.replaceAll("/$", "");

    @Override public void onCreate(Bundle state) { super.onCreate(state); showLogin(); }

    private TextView text(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(18);
        view.setPadding(14, 14, 14, 14);
        return view;
    }

    private void screen(String title) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);
        root.setBackgroundColor(Color.rgb(13, 18, 30));
        scroll.addView(root);
        setContentView(scroll);
        root.addView(text(title));
    }

    private void showLogin() {
        screen("MONKEY IPTV • Sign in");
        EditText user = new EditText(this);
        user.setHint("Username");
        user.setSingleLine(true);
        user.setTextColor(Color.WHITE);
        user.setHintTextColor(Color.LTGRAY);
        root.addView(user);
        EditText pass = new EditText(this);
        pass.setHint("Password");
        pass.setSingleLine(true);
        pass.setInputType(129);
        pass.setTextColor(Color.WHITE);
        pass.setHintTextColor(Color.LTGRAY);
        root.addView(pass);
        Button login = new Button(this);
        login.setText("Log in");
        root.addView(login);
        TextView status = text("");
        root.addView(status);
        login.setOnClickListener(v -> {
            String u = user.getText().toString().trim();
            String p = pass.getText().toString();
            if (u.isEmpty() || p.isEmpty()) { status.setText("Enter username and password"); return; }
            login.setEnabled(false);
            status.setText("Connecting...");
            new Thread(() -> {
                try {
                    JSONObject response = new JSONObject(fetch("player_api.php?username=" + enc(u) + "&password=" + enc(p)));
                    JSONObject info = response.optJSONObject("user_info");
                    if (info == null || !"Active".equalsIgnoreCase(info.optString("status")))
                        throw new IOException("Login failed or account inactive");
                    username = u;
                    password = p;
                    runOnUiThread(this::showChannels);
                } catch (Exception ex) {
                    runOnUiThread(() -> { login.setEnabled(true); status.setText("Login failed: " + ex.getMessage()); });
                }
            }).start();
        });
    }

    private void showChannels() {
        screen("Loading live channels...");
        new Thread(() -> {
            try {
                JSONArray channels = new JSONArray(fetch("player_api.php?username=" + enc(username) + "&password=" + enc(password) + "&action=get_live_streams"));
                runOnUiThread(() -> {
                    screen("Live TV (" + channels.length() + ")");
                    for (int i = 0; i < channels.length(); i++) {
                        JSONObject channel = channels.optJSONObject(i);
                        if (channel == null) continue;
                        String id = channel.optString("stream_id");
                        if (id.isEmpty()) continue;
                        Button item = new Button(this);
                        item.setText(channel.optString("name", "Channel"));
                        item.setOnClickListener(v -> play(id));
                        root.addView(item);
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> { screen("Unable to load channels"); root.addView(text(ex.getMessage())); });
            }
        }).start();
    }

    private void play(String id) {
        releasePlayer();
        screen("Live TV");
        Button back = new Button(this);
        back.setText("Back to channels");
        back.setOnClickListener(v -> { releasePlayer(); showChannels(); });
        root.addView(back);
        PlayerView view = new PlayerView(this);
        root.addView(view, new LinearLayout.LayoutParams(-1, 600));
        player = new ExoPlayer.Builder(this).build();
        view.setPlayer(player);
        String stream = server + "/live/" + path(username) + "/" + path(password) + "/" + path(id) + ".ts";
        player.setMediaItem(MediaItem.fromUri(stream));
        player.prepare();
        player.play();
    }

    private static String enc(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }
    private static String path(String value) { return android.net.Uri.encode(value); }

    private String fetch(String relative) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(server + "/" + relative).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(20000);
        try {
            int status = connection.getResponseCode();
            if (status >= 400) throw new IOException("Server returned HTTP " + status);
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192];
                int n;
                while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
                return out.toString("UTF-8");
            }
        } finally { connection.disconnect(); }
    }

    private void releasePlayer() { if (player != null) { player.release(); player = null; } }
    @Override protected void onStop() { super.onStop(); releasePlayer(); }
    @Override public void onBackPressed() {
        if (player != null) { releasePlayer(); showChannels(); }
        else super.onBackPressed();
    }
}
