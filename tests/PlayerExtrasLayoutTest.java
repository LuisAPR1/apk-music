package pt.luis.oledmusic;

/** Real handset, rotation and split-screen geometry without Android dependencies. */
public final class PlayerExtrasLayoutTest {
    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static PlayerExtrasLayout.Plan plan(int width, int height, float density,
            boolean controls, boolean brightness, boolean volume, int metadata,
            int position, boolean mode, boolean lyrics, int lyricShare) {
        PlayerExtrasLayout.Plan plan = PlayerExtrasLayout.create(width, height, density, 85,
                controls, brightness, volume, metadata, position, mode, lyrics, lyricShare);
        valid(plan, Math.max(0, width), Math.max(0, height));
        return plan;
    }

    private static void valid(PlayerExtrasLayout.Plan plan, int width, int height) {
        PlayerExtrasLayout.Rect[] rects = {plan.art, plan.mode, plan.metadata, plan.lyrics,
                plan.previous, plan.play, plan.next, plan.brightness, plan.volume};
        String[] names = {"art", "mode", "metadata", "lyrics", "previous", "play", "next", "brightness", "volume"};
        for (int i = 0; i < rects.length; i++) {
            PlayerExtrasLayout.Rect a = rects[i];
            String context = names[i] + " in " + width + "x" + height;
            check(a.left >= 0 && a.top >= 0 && a.width >= 0 && a.height >= 0, context + " has negative geometry");
            check(a.right() <= width && a.bottom() <= height, context + " escapes bounds");
            if (a.isEmpty()) continue;
            for (int j = 0; j < i; j++) {
                PlayerExtrasLayout.Rect b = rects[j];
                check(b.isEmpty() || a.left >= b.right() || b.left >= a.right()
                        || a.top >= b.bottom() || b.top >= a.bottom(), context + " overlaps " + names[j]);
            }
        }
        check(plan.art.width == plan.art.height, "Artwork stays square");
        check(plan.play.width == plan.play.height, "Play stays square");
        check(plan.previous.width == plan.previous.height && plan.next.width == plan.next.height,
                "Skip buttons stay square");
    }

    public static void main(String[] args) {
        float pixelDensity = 2.75f;
        PlayerExtrasLayout.Plan pixel = plan(1080, 2268, pixelDensity, true, true, true,
                176, 1, true, true, 25);
        check(pixel.art.width >= Math.round(200 * pixelDensity), "Pixel cover remains at least 200 dp with all extras");
        check(pixel.lyrics.height >= Math.round(100 * pixelDensity), "Pixel lyrics remain readable");
        check(pixel.mode.bottom() <= pixel.art.top, "Song/video switcher precedes artwork");
        check(pixel.mode.height >= Math.round(48 * pixelDensity), "Mode switch has a comfortable normal touch target");
        check(pixel.brightness.bottom() <= pixel.art.top && pixel.volume.bottom() <= pixel.art.top,
                "Sliders precede artwork");
        check(pixel.metadata.top >= pixel.art.bottom(), "Below-cover title position is preserved");
        check(pixel.lyrics.top >= pixel.art.bottom(), "Lyrics follow the artwork");
        check(Math.abs(pixel.art.left * 2 + pixel.art.width - 1080) <= 1, "Portrait cover is centered");
        check(pixel.play.width == Math.round(88 * pixelDensity), "Normal play target is 88 dp");
        check(pixel.previous.width == Math.round(64 * pixelDensity), "Normal skip target is 64 dp");

        PlayerExtrasLayout.Plan noControls = plan(1080, 2268, pixelDensity, false, true, true,
                176, 1, true, true, 25);
        check(noControls.play.isEmpty() && noControls.previous.isEmpty() && noControls.next.isEmpty(),
                "Hidden controls have no reserved rectangles");
        check(noControls.art.height + noControls.lyrics.height > pixel.art.height + pixel.lyrics.height,
                "Hidden controls free space for artwork and lyrics");
        check(noControls.art.height >= pixel.art.height && noControls.lyrics.height >= pixel.lyrics.height,
                "Hiding controls does not shrink artwork or lyrics");

        for (int position = 0; position < 3; position++) {
            PlayerExtrasLayout.Plan portrait = plan(400, 800, 1, true, true, true,
                    60, position, true, true, 50);
            check(portrait.art.width >= 200, "Cover has priority over excessive requested lyrics height");
            if (position == 0) check(portrait.metadata.bottom() <= portrait.art.top, "Above-cover title is above cover");
            if (position == 1) check(portrait.metadata.top >= portrait.art.bottom(), "Below-cover title is below cover");
            if (position == 2) check(portrait.metadata.bottom() == 784, "Footer title stays at the bottom inset");
        }

        PlayerExtrasLayout.Plan landscape = plan(840, 400, 1, true, true, true,
                56, 1, true, true, 30);
        check(landscape.art.right() < landscape.lyrics.left, "Landscape places lyrics beside artwork");
        check(landscape.art.width >= 200, "Landscape keeps the YouTube minimum viewport when it fits");
        check(landscape.art.right() < landscape.play.left, "Landscape places controls beside artwork");
        check(landscape.mode.bottom() <= landscape.art.top, "Landscape switcher precedes artwork");
        PlayerExtrasLayout.Plan landscapeHidden = plan(840, 400, 1, false, true, true,
                56, 1, true, true, 30);
        check(landscapeHidden.lyrics.height > landscape.lyrics.height,
                "Landscape lyrics use space released by hidden controls");

        PlayerExtrasLayout.Plan narrow = plan(240, 600, 1, true, false, false, 48, 1, true, true, 25);
        check(narrow.play.width == 64 && narrow.previous.width == 48, "Narrow windows use compact touch targets");
        PlayerExtrasLayout.Plan plain = plan(400, 800, 1, false, false, false, 0, 1, false, false, 25);
        check(plain.mode.isEmpty() && plain.lyrics.isEmpty() && plain.metadata.isEmpty()
                && plain.brightness.isEmpty() && plain.volume.isEmpty(), "Disabled extras occupy no space");

        PlayerExtrasLayout.Plan smallAlbum = PlayerExtrasLayout.create(400, 800, 1, -10,
                false, false, false, 0, 1, false, false, 25);
        PlayerExtrasLayout.Plan largeAlbum = PlayerExtrasLayout.create(400, 800, 1, 1000,
                false, false, false, 0, 1, false, false, 25);
        check(smallAlbum.art.width == 200, "Album percentage is clamped to 50 percent");
        check(largeAlbum.art.width == 368, "Album percentage is capped by 100 percent and screen margins");

        // All combinations of small windows, rotation, optional rows and title positions.
        int[] widths = {0, 1, 8, 48, 120, 240, 400, 840};
        int[] heights = {0, 1, 7, 40, 96, 240, 400, 800};
        for (int width : widths) {
            for (int height : heights) {
                for (int flags = 0; flags < 32; flags++) {
                    for (int position = 0; position < 3; position++) {
                        plan(width, height, 1, (flags & 1) != 0, (flags & 2) != 0,
                                (flags & 4) != 0, 128, position, (flags & 8) != 0,
                                (flags & 16) != 0, 50);
                    }
                }
            }
        }
        plan(-4, -1, Float.NaN, true, true, true, -50, -1, true, true, 100);
        plan(1, 1, Float.POSITIVE_INFINITY, true, true, true, 100, 3, true, true, -5);
        System.out.println("PASS: " + assertions + " responsive player layout assertions");
    }
}
