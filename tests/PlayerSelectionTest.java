package pt.luis.oledmusic;

import java.util.Arrays;
import java.util.Collections;

/** Real playback sequences: paused rivals, delayed callbacks and process/service restarts. */
public final class PlayerSelectionTest {
    private static final String YT = "app.revanced.android.apps.youtube.music";
    private static final String SC = "com.soundcloud.android";
    private static int assertions;

    private static void equal(Object expected, Object actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    public static void main(String[] args) {
        PlayerSelection<String> players = new PlayerSelection<>("");
        players.update("yt", YT, false, 100);
        players.update("sc", SC, true, 200);
        equal("sc", players.selected(), "Playing SoundCloud wins over paused YouTube");
        players.update("sc", SC, false, 300);
        equal("sc", players.selected(), "Pause retains SoundCloud");
        players.update("yt", YT, false, 400);
        equal("sc", players.selected(), "Opening/seeking a paused rival does not switch");
        players.update("yt", YT, true, 500);
        equal("yt", players.selected(), "Play in YouTube switches player");
        players.update("sc", SC, false, 600);
        equal("yt", players.selected(), "Delayed old pause does not switch back");
        players.update("sc", SC, true, 700);
        equal("sc", players.selected(), "Play in SoundCloud switches back");
        players.update("yt", YT, true, 800);
        equal("sc", players.selected(), "Repeated old PLAYING state does not steal selection");
        players.update("sc", SC, false, 900);
        equal("sc", players.selected(), "Old stale PLAYING state cannot steal on pause");
        players.update("yt", YT, false, 1000);
        players.update("yt", YT, true, 1100);
        equal("yt", players.selected(), "A new actual play transition can switch");
        players.update("yt", YT, false, 1200);
        players.retain(Collections.singleton("sc"));
        equal(null, players.selected(), "Closing selected player does not choose an unrelated paused one");
        equal(YT, players.lastPackage(), "Last package survives session destruction");
        players.update("yt-new-token", YT, false, 1300);
        equal("yt-new-token", players.selected(), "Recreated paused session of last player is retained");
        players.update("sc", SC, true, 1400);
        equal("sc", players.selected(), "Rival may take over after last session closes");
        equal(SC, players.lastPackage(), "Playing player is persisted");

        players.retain(Collections.emptyList());
        players.update("yt-2", YT, false, 1500);
        players.update("sc-2", SC, false, 1501);
        equal("sc-2", players.selected(), "Listener reconnect keeps last paused player");
        players.retain(Arrays.asList("sc-2", "yt-2"));
        equal("sc-2", players.selected(), "Reordering sessions does not switch");

        PlayerSelection<String> restarted = new PlayerSelection<>(SC);
        restarted.update("yt", YT, false, 9000);
        restarted.update("sc", SC, false, 2000);
        equal("sc", restarted.selected(), "Process restart prefers remembered player over recent paused metadata");
        restarted.update("yt", YT, true, 10000);
        equal("yt", restarted.selected(), "Currently playing rival takes over after restart");

        PlayerSelection<String> initial = new PlayerSelection<>("");
        equal(null, initial.selected(), "No sessions is supported");
        initial.update("sc", SC, true, 500);
        initial.update("yt", YT, true, 400);
        equal("sc", initial.selected(), "Initial discovery uses playback time, not discovery order");
        initial.update("yt", YT, false, 600);
        initial.update("yt", YT, true, 700);
        initial.update("sc", SC, false, 700);
        initial.update("sc", SC, true, 700);
        equal("sc", initial.selected(), "Same-millisecond starts follow the latest callback");

        PlayerSelection<String> remembered = new PlayerSelection<>(SC);
        remembered.update("yt", YT, false, 100);
        equal(null, remembered.selected(), "A paused rival alone does not replace remembered SoundCloud");
        remembered.update("yt", YT, true, 200);
        equal("yt", remembered.selected(), "Play activates the available rival");

        PlayerSelection<String> delayed = new PlayerSelection<>("");
        delayed.update("yt", YT, false, 600);
        delayed.update("sc", SC, true, 700);
        delayed.update("yt", YT, true, 500);
        equal("sc", delayed.selected(), "An older delayed play does not steal selection");
        delayed.update("yt", YT, true, 800);
        equal("yt", delayed.selected(), "Old callbacks do not mask the next real start");
        delayed.update("yt", YT, false, 750);
        delayed.update("sc", SC, false, 850);
        delayed.update("sc", SC, true, 900);
        delayed.update("yt", YT, true, 950);
        equal("sc", delayed.selected(), "An older delayed pause cannot fake a new play transition");
        System.out.println("PASS: " + assertions + " player switching assertions");
    }
}
