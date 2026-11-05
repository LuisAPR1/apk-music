package pt.luis.oledmusic;

import java.util.Arrays;

/** Synthetic covers make the population limit and visible-edge decisions unambiguous. */
public final class AlbumBorderPaletteTest {
    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static int assertions;

    private static void equal(int expected, int actual, String message) {
        check(expected == actual, message + ": expected=" + Integer.toHexString(expected)
                + ", actual=" + Integer.toHexString(actual));
    }

    private static void check(boolean result, String message) {
        assertions++;
        if (!result) throw new AssertionError(message);
    }

    private static int[] colors(int... pairs) {
        int length = 0;
        for (int i = 1; i < pairs.length; i += 2) length += pairs[i];
        int[] result = new int[length];
        int at = 0;
        for (int i = 0; i < pairs.length; i += 2) {
            Arrays.fill(result, at, at + pairs[i + 1], pairs[i]);
            at += pairs[i + 1];
        }
        return result;
    }

    private static int choose(int[] pixels, int... edges) {
        return AlbumBorderPalette.choose(pixels, 10, 10, edges, WHITE);
    }

    public static void main(String[] args) {
        equal(RED, choose(colors(RED, 40, GREEN, 35, BLUE, 25), WHITE),
                "Exactly 40 percent remains eligible");
        equal(GREEN, choose(colors(RED, 41, GREEN, 35, BLUE, 24), WHITE),
                "More than 40 percent rejects the first and chooses the second colour");
        equal(BLUE, choose(colors(RED, 55, GREEN, 41, BLUE, 4), WHITE),
                "The limit also applies to subsequent candidates");
        equal(GREEN, choose(colors(RED, 40, GREEN, 35, BLUE, 25), RED),
                "A colour already at the visible edge is rejected");
        equal(GREEN, choose(colors(RED, 40, GREEN, 35, BLUE, 25), 0xFFFF1010),
                "A near-identical red edge is also rejected");
        equal(BLUE, choose(colors(0xFFCF0000, 30, 0xFFD00000, 20, BLUE, 35, GREEN, 15), WHITE),
                "Similar JPEG shades across quantisation boundaries count as one dominant colour");
        equal(BLUE, choose(colors(0x00FFFFFF, 40, RED, 25, BLUE, 20, GREEN, 15), WHITE),
                "Transparent corners are excluded from the population denominator");
        equal(BLACK, AlbumBorderPalette.choose(colors(WHITE, 100), 10, 10, WHITE),
                "A white monochrome cover gets a visible dark fallback");
        equal(WHITE, AlbumBorderPalette.choose(colors(BLACK, 100), 10, 10, BLACK),
                "A black monochrome cover gets a visible light fallback");
        equal(0xFFFF00FF, AlbumBorderPalette.choose(colors(BLACK, 100), 10, 10,
                new int[]{BLACK}, 0xFFFF00FF), "A contrasting saved manual colour is respected as fallback");
        int neutral = AlbumBorderPalette.choose(colors(RED, 100), 10, 10,
                new int[]{BLACK, WHITE}, WHITE);
        int level = neutral & 255;
        check(level > 90 && level < 150 && ((neutral >> 8) & 255) == level
                && ((neutral >> 16) & 255) == level,
                "Mixed black and white edges use a contrasting intermediate neutral");

        int[] framed = new int[400];
        Arrays.fill(framed, RED);
        int[] interior = colors(GREEN, 140, BLUE, 100, RED, 84);
        int position = 0;
        for (int y = 1; y < 19; y++) for (int x = 1; x < 19; x++) {
            framed[y * 20 + x] = interior[position++];
        }
        equal(GREEN, AlbumBorderPalette.choose(framed, 20, 20, WHITE),
                "Default perimeter extraction reads the visible outside edge");
        equal(RED, AlbumBorderPalette.choose(framed, 20, 20, new int[]{BLACK}, WHITE),
                "Explicit rendered perimeter overrides the undimmed source edge");

        int[] clustered = colors(0xFFC60000, 20, 0xFFD00000, 20, GREEN, 35, BLUE, 25);
        int actual = choose(clustered, BLACK);
        check(actual == 0xFFC60000 || actual == 0xFFD00000,
                "A selected group is represented by a real album pixel, not an invented average");
        int[] original = Arrays.copyOf(clustered, clustered.length);
        choose(clustered, BLACK);
        check(Arrays.equals(original, clustered), "Analysis does not mutate source pixels");

        int[] tied = colors(RED, 40, GREEN, 40, BLUE, 20);
        int first = choose(tied, WHITE);
        for (int i = 0; i < tied.length / 2; i++) {
            int value = tied[i];
            tied[i] = tied[tied.length - 1 - i];
            tied[tied.length - 1 - i] = value;
        }
        equal(first, choose(tied, WHITE), "Equal populations are deterministic regardless of pixel order");
        equal(0xFF123456, AlbumBorderPalette.choose(null, 10, 10, 0x00123456),
                "Missing image uses the manual fallback with opaque alpha");
        equal(WHITE, AlbumBorderPalette.choose(new int[0], 0, 0, WHITE), "Empty input is safe");
        equal(RED, AlbumBorderPalette.choose(new int[1], Integer.MAX_VALUE, 2, RED),
                "Overflowing dimensions are rejected safely");
        equal(BLUE, AlbumBorderPalette.choose(new int[100], 10, 10, BLUE),
                "An entirely transparent image uses the saved fallback");
        equal(WHITE, AlbumBorderPalette.choose(new int[]{BLACK}, 1, 1, BLACK),
                "A one-pixel cover is supported");
        System.out.println("PASS: " + assertions + " album border palette assertions");
    }
}
