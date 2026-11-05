package pt.luis.oledmusic;

/** Pixel-only player geometry, shared by the Android view and ordinary JVM tests. */
public final class PlayerExtrasLayout {
    private static final int BRIGHTNESS = 0;
    private static final int VOLUME = 1;
    private static final int MODE = 2;
    private static final int METADATA = 3;
    private static final int ART = 4;
    private static final int LYRICS = 5;
    private static final int CONTROLS = 6;
    private static final int COUNT = 7;
    private static final Rect EMPTY = new Rect(0, 0, 0, 0);

    private PlayerExtrasLayout() { }

    public static final class Rect {
        public final int left;
        public final int top;
        public final int width;
        public final int height;

        private Rect(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        public int right() { return left + width; }
        public int bottom() { return top + height; }
        public boolean isEmpty() { return width == 0 || height == 0; }
    }

    public static final class Plan {
        public final Rect art;
        public final Rect mode;
        public final Rect metadata;
        public final Rect lyrics;
        public final Rect previous;
        public final Rect play;
        public final Rect next;
        public final Rect brightness;
        public final Rect volume;

        private Plan(Rect[] blocks, Rect previous, Rect play, Rect next) {
            art = blocks[ART];
            mode = blocks[MODE];
            metadata = blocks[METADATA];
            lyrics = blocks[LYRICS];
            brightness = blocks[BRIGHTNESS];
            volume = blocks[VOLUME];
            this.previous = previous;
            this.play = play;
            this.next = next;
        }
    }

    /** Width and height are the content area after applying system insets. */
    public static Plan create(int width, int height, float density, int albumPercent,
            boolean controls, boolean brightness, boolean volume, int metadataHeight,
            int textPosition, boolean videoSwitcher, boolean lyrics, int lyricsPercent) {
        width = Math.max(0, width);
        height = Math.max(0, height);
        if (!(density > 0) || Float.isInfinite(density)) density = 1f;
        int album = clamp(albumPercent, 50, 100);
        int lyricShare = clamp(lyricsPercent, 15, 50);
        int position = clamp(textPosition, 0, 2);
        int paddingX = Math.min(dp(16, density), width / 8);
        int paddingY = Math.min(dp(16, density), height / 12);
        int innerWidth = width - paddingX * 2;
        int innerHeight = height - paddingY * 2;
        Builder builder = new Builder(density);

        if (width > height) {
            int columnGap = Math.min(dp(24, density), innerWidth / 12);
            int columnsWidth = innerWidth - columnGap;
            int leftWidth = Math.round(columnsWidth * .47f);
            int rightWidth = columnsWidth - leftWidth;
            int rightLeft = paddingX + leftWidth + columnGap;
            int artSize = Math.min(leftWidth, percent(leftWidth, album));
            int[] leftHeights = new int[COUNT];
            leftHeights[BRIGHTNESS] = brightness ? dp(48, density) : 0;
            leftHeights[VOLUME] = volume ? dp(48, density) : 0;
            leftHeights[MODE] = videoSwitcher ? dp(56, density) : 0;
            leftHeights[ART] = artSize;
            int[] leftMinimum = minimums(leftHeights, density, dp(videoSwitcher ? 200 : 180, density));
            int leftGap = fit(leftHeights, leftMinimum, innerHeight, dp(8, density));
            builder.stack(leftHeights, new int[]{BRIGHTNESS, VOLUME, MODE, ART},
                    paddingX, paddingY, leftWidth, innerHeight, leftGap, false);

            int[] rightHeights = new int[COUNT];
            rightHeights[METADATA] = Math.max(0, metadataHeight);
            rightHeights[LYRICS] = lyrics ? Math.max(dp(112, density), percent(height, lyricShare)) : 0;
            rightHeights[CONTROLS] = controls ? controlsHeight(rightWidth, density) : 0;
            int rightGap = fit(rightHeights, minimums(rightHeights, density, 0),
                    innerHeight, dp(12, density));
            if (lyrics && !controls) fillLyrics(rightHeights, innerHeight, rightGap);
            // Both ordinary title positions lead the reading column; footer remains at its bottom.
            builder.stack(rightHeights,
                    position == 2 ? new int[]{LYRICS, CONTROLS, METADATA}
                            : new int[]{METADATA, LYRICS, CONTROLS},
                    rightLeft, paddingY, rightWidth, innerHeight, rightGap, position == 2);
        } else {
            int[] heights = new int[COUNT];
            heights[BRIGHTNESS] = brightness ? dp(48, density) : 0;
            heights[VOLUME] = volume ? dp(48, density) : 0;
            heights[MODE] = videoSwitcher ? dp(56, density) : 0;
            heights[METADATA] = Math.max(0, metadataHeight);
            heights[ART] = Math.min(innerWidth, percent(width, album));
            heights[LYRICS] = lyrics ? Math.max(dp(112, density), percent(height, lyricShare)) : 0;
            heights[CONTROLS] = controls ? controlsHeight(innerWidth, density) : 0;
            int gap = fit(heights, minimums(heights, density, dp(200, density)),
                    innerHeight, dp(8, density));
            if (lyrics && !controls) fillLyrics(heights, innerHeight, gap);
            int[] order = position == 0
                    ? new int[]{BRIGHTNESS, VOLUME, MODE, METADATA, ART, LYRICS, CONTROLS}
                    : position == 1
                    ? new int[]{BRIGHTNESS, VOLUME, MODE, ART, METADATA, LYRICS, CONTROLS}
                    : new int[]{BRIGHTNESS, VOLUME, MODE, ART, LYRICS, CONTROLS, METADATA};
            builder.stack(heights, order, paddingX, paddingY, innerWidth, innerHeight, gap,
                    position == 2);
        }
        return new Plan(builder.blocks, builder.previous, builder.play, builder.next);
    }

    private static int[] minimums(int[] desired, float density, int artMinimum) {
        int[] result = desired.clone();
        result[BRIGHTNESS] = Math.min(result[BRIGHTNESS], dp(32, density));
        result[VOLUME] = Math.min(result[VOLUME], dp(32, density));
        result[MODE] = Math.min(result[MODE], dp(44, density));
        result[METADATA] = Math.min(result[METADATA], dp(40, density));
        result[ART] = Math.min(result[ART], artMinimum);
        result[LYRICS] = Math.min(result[LYRICS], dp(100, density));
        result[CONTROLS] = Math.min(result[CONTROLS], dp(64, density));
        return result;
    }

    /** Prefer a useful cover and readable lyrics before reducing touch targets. */
    private static int fit(int[] heights, int[] minimums, int capacity, int gap) {
        int count = activeCount(heights);
        gap = Math.min(gap, capacity / Math.max(1, count * 4));
        long excess = stackHeight(heights, gap) - capacity;
        int[] reductionOrder = {ART, LYRICS, BRIGHTNESS, VOLUME, MODE, METADATA, CONTROLS};
        for (int id : reductionOrder) {
            if (excess <= 0) break;
            int reduction = (int) Math.min(excess, heights[id] - minimums[id]);
            heights[id] -= reduction;
            excess -= reduction;
        }
        if (excess > 0) {
            // Split-screen and very small windows can be smaller than all useful minimums.
            int budget = Math.max(0, capacity - Math.max(0, count - 1) * gap);
            long total = 0;
            for (int value : heights) total += value;
            long cumulative = 0;
            int allocated = 0;
            for (int id = 0; id < COUNT; id++) {
                cumulative += heights[id];
                int edge = total == 0 ? 0 : (int) (cumulative * budget / total);
                heights[id] = edge - allocated;
                allocated = edge;
            }
        }
        return gap;
    }

    private static void fillLyrics(int[] heights, int capacity, int gap) {
        if (heights[LYRICS] > 0) {
            heights[LYRICS] += (int) Math.max(0, capacity - stackHeight(heights, gap));
        }
    }

    private static int controlsHeight(int width, float density) {
        int desired = dp(width < dp(280, density) ? 64 : 88, density);
        // The three squares must fit horizontally even in a narrow floating window.
        return Math.min(desired, (int) Math.min(Integer.MAX_VALUE, (long) width * 2 / 5));
    }

    private static int activeCount(int[] heights) {
        int count = 0;
        for (int value : heights) if (value > 0) count++;
        return count;
    }

    private static long stackHeight(int[] heights, int gap) {
        long total = 0;
        for (int value : heights) total += value;
        return total + (long) Math.max(0, activeCount(heights) - 1) * gap;
    }

    private static int percent(int value, int percent) {
        return (int) ((long) value * percent / 100);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int dp(int value, float density) { return Math.round(value * density); }

    private static final class Builder {
        final Rect[] blocks = {EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY};
        final float density;
        Rect previous = EMPTY;
        Rect play = EMPTY;
        Rect next = EMPTY;

        Builder(float density) { this.density = density; }

        void stack(int[] heights, int[] order, int left, int top, int width, int height,
                int gap, boolean footer) {
            int total = (int) stackHeight(heights, gap);
            int footerHeight = footer ? heights[METADATA] : 0;
            int free = Math.max(0, height - total);
            int y = top + free / 2;
            int artWidth = heights[ART];
            boolean first = true;
            for (int id : order) {
                int rowHeight = heights[id];
                if (rowHeight == 0) continue;
                if (!first) y += gap;
                first = false;
                if (id == METADATA && footerHeight > 0) y = top + height - footerHeight;
                if (id == CONTROLS) {
                    controls(left, y, width, rowHeight);
                } else {
                    int rowWidth = id == ART ? rowHeight
                            : id == MODE ? Math.min(width, dp(224, density))
                            : (id == BRIGHTNESS || id == VOLUME) && artWidth > 0 ? artWidth : width;
                    blocks[id] = rowWidth == 0 ? EMPTY
                            : new Rect(left + (width - rowWidth) / 2, y, rowWidth, rowHeight);
                }
                y += rowHeight;
            }
        }

        void controls(int left, int top, int width, int rowHeight) {
            int playSize = rowHeight;
            int sideSize = Math.min(dp(64, density), rowHeight * 3 / 4);
            int occupied = playSize + sideSize * 2;
            int gap = Math.min(dp(20, density), Math.max(0, width - occupied) / 2);
            int x = left + (width - occupied - gap * 2) / 2;
            previous = sideSize == 0 ? EMPTY
                    : new Rect(x, top + (rowHeight - sideSize) / 2, sideSize, sideSize);
            x += sideSize + gap;
            play = playSize == 0 ? EMPTY : new Rect(x, top, playSize, playSize);
            x += playSize + gap;
            next = sideSize == 0 ? EMPTY
                    : new Rect(x, top + (rowHeight - sideSize) / 2, sideSize, sideSize);
        }
    }
}
