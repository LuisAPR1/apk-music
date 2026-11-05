package pt.luis.oledmusic;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Locale;

/** Extracts an ID from supported YouTube links without accepting arbitrary web content. */
final class YouTubeVideoId {
    private YouTubeVideoId() {}

    static boolean isValid(String value) {
        if (value == null || value.length() != 11) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z')
                    && !(c >= '0' && c <= '9') && c != '_' && c != '-') return false;
        }
        return true;
    }

    static String from(String value) {
        if (value == null) return null;
        value = value.trim();
        if (isValid(value)) return value;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    || uri.getRawUserInfo() != null || uri.getHost() == null) return null;
            int port = uri.getPort();
            if (port != -1 && !("https".equalsIgnoreCase(scheme) && port == 443)
                    && !("http".equalsIgnoreCase(scheme) && port == 80)) return null;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getRawPath();
            if (path == null) return null;
            if ("youtu.be".equals(host) || "www.youtu.be".equals(host)) {
                return pathId(path, "/");
            }
            boolean youtube = "youtube.com".equals(host) || "www.youtube.com".equals(host)
                    || "m.youtube.com".equals(host) || "music.youtube.com".equals(host);
            boolean privacyEmbed = "youtube-nocookie.com".equals(host)
                    || "www.youtube-nocookie.com".equals(host);
            if (!youtube && !privacyEmbed) return null;
            if (path.startsWith("/embed/")) return pathId(path, "/embed/");
            if (!youtube) return null;
            if (path.startsWith("/shorts/")) return pathId(path, "/shorts/");
            if (!"/watch".equals(path)) return null;
            String query = uri.getRawQuery();
            if (query == null) return null;
            String found = null;
            for (String item : query.split("&")) {
                int separator = item.indexOf('=');
                String key = URLDecoder.decode(separator < 0 ? item : item.substring(0, separator), "UTF-8");
                if (!"v".equals(key)) continue;
                // Ambiguous repeated IDs are not accepted, even when their values match.
                if (found != null || separator < 0) return null;
                found = URLDecoder.decode(item.substring(separator + 1), "UTF-8");
                if (!isValid(found)) return null;
            }
            return found;
        } catch (Exception invalidUrl) {
            return null;
        }
    }

    private static String pathId(String path, String prefix) {
        if (!path.startsWith(prefix)) return null;
        String id = path.substring(prefix.length());
        if (id.endsWith("/")) id = id.substring(0, id.length() - 1);
        return isValid(id) ? id : null;
    }
}
