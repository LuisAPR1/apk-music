package pt.luis.oledmusic;

import android.content.Context;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** User-selected video links and local lyric files, attached to one track only. */
final class TrackAttachments {
    private TrackAttachments() { }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String identity(String packageName, String mediaId, String title, String artist, String album, long duration) {
        if (mediaId != null && !mediaId.trim().isEmpty()) return packageName + "\nid:" + mediaId;
        return packageName + "\n" + title + "\n" + artist + "\n" + album;
    }

    static String video(Context context, String trackKey) {
        return context.getSharedPreferences("track_videos", 0).getString(hash(trackKey), "");
    }

    static void video(Context context, String trackKey, String videoId) {
        android.content.SharedPreferences.Editor editor = context.getSharedPreferences("track_videos", 0).edit();
        if (videoId.isEmpty()) editor.remove(hash(trackKey)); else editor.putString(hash(trackKey), videoId);
        editor.apply();
    }

    static void importLyrics(Context context, Uri uri, String trackKey) throws Exception {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new java.io.IOException("Não foi possível abrir o ficheiro.");
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (content.size() + count > 1024 * 1024) throw new java.io.IOException("O ficheiro é demasiado grande.");
                content.write(buffer, 0, count);
            }
        }
        String text = new String(content.toByteArray(), StandardCharsets.UTF_8);
        if (LyricTimeline.parse(text).lines().isEmpty()) throw new java.io.IOException("O ficheiro não contém uma letra legível.");
        File dir = new File(context.getFilesDir(), "lyrics-local");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Não foi possível guardar a letra.");
        File temp = File.createTempFile("import-", ".tmp", dir);
        try {
            try (FileOutputStream output = new FileOutputStream(temp)) { output.write(content.toByteArray()); }
            if (!temp.renameTo(new File(dir, hash(trackKey) + ".lrc"))) throw new java.io.IOException("Não foi possível guardar a letra.");
        } finally { temp.delete(); }
    }
}
