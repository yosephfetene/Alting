package org.alting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import io.github.cdimascio.dotenv.Dotenv;

public class SupabaseStorage {
    private static final Dotenv dotenv = Dotenv.load();
    private static final String key = dotenv.get("SUPABASE_SERVICE_KEY");
    private static final String base = dotenv.get("SUPABASE_URL");
    private static final String bucket = "spotlight-images";
    private static final HttpClient client = HttpClient.newHttpClient();

    /** Download a temporary Telegram image and re-upload it to Supabase Storage.
     *  Returns the permanent public URL, or null if anything fails. */
    public static String rehost(String telegramUrl, String path) {
        try {
            HttpResponse<byte[]> img = client.send(
                HttpRequest.newBuilder(URI.create(telegramUrl))
                    .header("User-Agent", "Mozilla/5.0").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
            if (img.statusCode() != 200) return null;

            String uploadUrl = base + "/storage/v1/object/" + bucket + "/" + path;
            HttpResponse<String> up = client.send(
                HttpRequest.newBuilder(URI.create(uploadUrl))
                    .header("Authorization", "Bearer " + key)
                    .header("apikey", key)
                    .header("Content-Type", "image/jpeg")
                    .header("x-upsert", "true")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(img.body()))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
            if (up.statusCode() != 200) {
                System.out.println("  upload failed (" + up.statusCode() + "): " + up.body());
                return null;
            }
            return base + "/storage/v1/object/public/" + bucket + "/" + path;
        } catch (Exception e) {
            System.out.println("  rehost error: " + e.getMessage());
            return null;
        }
    }

    /** Delete a stored image given its public URL.
     *  Skips URLs that aren't ours (e.g. leftover Telegram links) and ignores already-gone objects. */
    public static void deleteByUrl(String url) {
        if (url == null) return;
        String marker = "/public/" + bucket + "/";
        int idx = url.indexOf(marker);
        if (idx < 0) return;                    // not one of our storage URLs — nothing to delete
        String path = url.substring(idx + marker.length());
        try {
            client.send(
                HttpRequest.newBuilder(URI.create(base + "/storage/v1/object/" + bucket + "/" + path))
                    .header("Authorization", "Bearer " + key)
                    .header("apikey", key)
                    .DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            System.out.println("  storage delete error: " + e.getMessage());
        }
    }
}
