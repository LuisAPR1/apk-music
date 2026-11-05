package pt.luis.oledmusic;

/** Accepted source links and inputs that must never become embedded HTML. */
public final class YouTubeVideoIdTest {
    private static int assertions;
    private static final String ID = "dQw4w9WgXcQ";

    private static void equal(String expected, String actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void accepted(String input) { equal(ID, YouTubeVideoId.from(input), input); }
    private static void rejected(String input) { equal(null, YouTubeVideoId.from(input), input); }

    public static void main(String[] args) {
        accepted(ID);
        accepted("  " + ID + "  ");
        accepted("https://www.youtube.com/watch?v=" + ID);
        accepted("https://music.youtube.com/watch?list=example&v=" + ID + "&feature=share");
        accepted("http://m.youtube.com/watch?v=" + ID + "#lyrics");
        accepted("https://youtube.com:443/watch?v=" + ID);
        accepted("https://youtu.be/" + ID + "?si=share");
        accepted("https://www.youtu.be/" + ID + "/");
        accepted("https://www.youtube.com/embed/" + ID);
        accepted("https://www.youtube-nocookie.com/embed/" + ID);
        accepted("https://youtube.com/shorts/" + ID + "?feature=share");
        accepted("https://YOUTUBE.COM/watch?v=" + ID);
        rejected(null);
        rejected("");
        rejected("dQw4w9WgXc");
        rejected("dQw4w9WgXcQx");
        rejected("<script>xxx");
        rejected("https://example.com/watch?v=" + ID);
        rejected("https://youtube.com.evil.example/watch?v=" + ID);
        rejected("https://youtube.com@evil.example/watch?v=" + ID);
        rejected("https://evil@youtube.com/watch?v=" + ID);
        rejected("https://youtu.be.evil.example/" + ID);
        rejected("https://youtube.com:8080/watch?v=" + ID);
        rejected("javascript:alert(1)");
        rejected("file:///watch?v=" + ID);
        rejected("data:text/html," + ID);
        rejected("//youtube.com/watch?v=" + ID);
        rejected("https://youtube.com/watch?list=" + ID);
        rejected("https://youtube.com/watch?v=" + ID + "&v=" + ID);
        rejected("https://youtube.com/watch?v=%3Cscript%3E123");
        rejected("https://youtube.com/watch?v=dQw4w9WgXc+");
        rejected("https://youtube.com/watch?v=");
        rejected("https://youtu.be/" + ID + "/arbitrary");
        rejected("https://youtube.com/embed/" + ID + "/arbitrary");
        rejected("https://youtube.com/embed/%64Qw4w9WgXcQ");
        rejected("https://youtube-nocookie.com/watch?v=" + ID);
        rejected("https://youtube.com/watch?v=" + ID + "\n<script>");
        equal("true", String.valueOf(YouTubeVideoId.isValid("aB09_-xYz12")), "ID alphabet");
        equal("false", String.valueOf(YouTubeVideoId.isValid("aB09_-xYz1!")), "Reject markup alphabet");
        System.out.println("PASS: " + assertions + " YouTube video ID assertions");
    }
}
