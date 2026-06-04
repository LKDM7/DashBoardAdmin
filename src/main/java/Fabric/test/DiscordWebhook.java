package Fabric.test;

import com.google.gson.*;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;

public class DiscordWebhook {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private static final String BOUNDARY = "----DiscordBoundary7x3k";

    // ── Report ────────────────────────────────────────────────────────────────

    public static void sendReport(String url, String playerName, String message, byte[] imageBytes) {
        if (url == null || url.isBlank()) return;
        boolean hasImage = imageBytes != null && imageBytes.length > 0;

        JsonObject embed = buildEmbed("⚑ Nouveau Report", 0xFF4444,
            new String[][]{
                {"Joueur",   playerName, "true"},
                {"Message",  message,    "false"}
            },
            hasImage ? "attachment://screenshot.png" : null
        );

        if (hasImage) {
            JsonObject payload = wrapEmbed(embed);
            JsonArray attachments = new JsonArray();
            JsonObject att = new JsonObject();
            att.addProperty("id", 0);
            att.addProperty("filename", "screenshot.png");
            attachments.add(att);
            payload.add("attachments", attachments);
            sendMultipart(url, payload.toString(), imageBytes);
        } else {
            sendJson(url, wrapEmbed(embed).toString());
        }
    }

    // ── Sanction ──────────────────────────────────────────────────────────────

    public static void sendSanction(String url, String adminName, String playerName,
                                     String type, String extra) {
        if (url == null || url.isBlank()) return;

        int color;
        String title;
        switch (type.toUpperCase()) {
            case "BAN"  -> { color = 0xCC0000; title = "🔨 Joueur Banni";  }
            case "KICK" -> { color = 0xFF8800; title = "🚹 Joueur Kické";  }
            case "MUTE" -> { color = 0xFFAA00; title = "🔇 Joueur Muté";   }
            default     -> { color = 0x888888; title = "⚡ Action Admin";  }
        }

        java.util.List<String[]> fields = new java.util.ArrayList<>();
        fields.add(new String[]{"Joueur", playerName, "true"});
        fields.add(new String[]{"Admin",  adminName,  "true"});
        if (extra != null && !extra.isBlank())
            fields.add(new String[]{"Raison", extra, "false"});

        JsonObject embed = buildEmbed(title, color, fields.toArray(new String[0][]), null);
        sendJson(url, wrapEmbed(embed).toString());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JsonObject buildEmbed(String title, int color,
                                          String[][] fields, String imageUrl) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "DashboardAdmin • Serveur Minecraft");
        embed.add("footer", footer);

        if (fields != null && fields.length > 0) {
            JsonArray arr = new JsonArray();
            for (String[] f : fields) {
                JsonObject field = new JsonObject();
                field.addProperty("name",   f.length > 0 ? f[0] : "");
                field.addProperty("value",  f.length > 1 ? f[1] : "");
                field.addProperty("inline", f.length > 2 && Boolean.parseBoolean(f[2]));
                arr.add(field);
            }
            embed.add("fields", arr);
        }

        if (imageUrl != null) {
            JsonObject image = new JsonObject();
            image.addProperty("url", imageUrl);
            embed.add("image", image);
        }

        return embed;
    }

    private static JsonObject wrapEmbed(JsonObject embed) {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", "DashboardAdmin");
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        return payload;
    }

    private static void sendJson(String url, String json) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> null);
        } catch (Exception ignored) {}
    }

    private static void sendMultipart(String url, String json, byte[] imageBytes) {
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            byte[] CRLF = "\r\n".getBytes();

            body.write(("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"payload_json\"\r\n"
                + "Content-Type: application/json\r\n\r\n").getBytes());
            body.write(json.getBytes());
            body.write(CRLF);

            body.write(("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"files[0]\"; filename=\"screenshot.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes());
            body.write(imageBytes);
            body.write(CRLF);

            body.write(("--" + BOUNDARY + "--\r\n").getBytes());

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> null);
        } catch (Exception ignored) {}
    }
}
