package pt.luis.oledmusic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Small, deterministic album palette. No Android classes or external dependencies. */
final class AlbumBorderPalette {
    private static final int BIN_COUNT = 4096; // 4 bits per RGB channel.
    private static final double MERGE_DISTANCE_SQUARED = 100; // Delta E 76 <= 10.
    private static final double EDGE_DISTANCE_SQUARED = 324; // Delta E 76 < 18.

    private AlbumBorderPalette() { }

    /** Uses a narrow band around the unrounded source square for edge comparison. */
    static int choose(int[] pixels, int width, int height, int fallbackColor) {
        if (!valid(pixels, width, height)) return opaque(fallbackColor);
        int band = Math.max(1, Math.min(width, height) / 32);
        int[] edges = new int[width * height];
        int count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x < band || y < band || x >= width - band || y >= height - band) {
                    edges[count++] = pixels[y * width + x];
                }
            }
        }
        return choose(pixels, width, height, Arrays.copyOf(edges, count), fallbackColor);
    }

    /**
     * Palette pixels are the original visible crop; transparent clipped corners are ignored.
     * Explicit perimeter pixels may represent rounded corners and the displayed cover dim.
     * Candidate colours come from the palette, while contrast is tested against that perimeter.
     */
    static int choose(int[] pixels, int width, int height, int[] perimeterPixels, int fallbackColor) {
        if (!valid(pixels, width, height)) return opaque(fallbackColor);
        Bin[] bins = new Bin[BIN_COUNT];
        int total = 0;
        for (int i = 0; i < width * height; i++) {
            int color = pixels[i];
            if ((color >>> 24) < 128) continue;
            int key = key(color);
            if (bins[key] == null) bins[key] = new Bin(key);
            bins[key].add(color);
            total++;
        }
        if (total == 0) return opaque(fallbackColor);

        List<Bin> occupied = new ArrayList<>();
        for (Bin bin : bins) if (bin != null) occupied.add(bin);
        occupied.sort(Comparator.comparingInt((Bin b) -> -b.count).thenComparingInt(b -> b.key));
        List<Group> groups = new ArrayList<>();
        for (Bin bin : occupied) {
            double[] lab = lab(bin.mean());
            Group nearest = null;
            double distance = MERGE_DISTANCE_SQUARED;
            for (Group group : groups) {
                double candidate = distance(lab, group.lab);
                if (candidate <= distance) {
                    distance = candidate;
                    nearest = group;
                }
            }
            if (nearest == null) {
                nearest = new Group(bin.key);
                groups.add(nearest);
            }
            nearest.add(bin);
            bin.group = nearest;
        }
        // Pick an actual sampled pixel closest to each group's mean, not a new synthetic colour.
        for (int i = 0; i < width * height; i++) {
            int color = pixels[i];
            if ((color >>> 24) < 128) continue;
            Group group = bins[key(color)].group;
            double distance = distance(lab(color), group.lab);
            if (distance < group.representativeDistance
                    || (distance == group.representativeDistance && (color & 0xFFFFFF) < (group.color & 0xFFFFFF))) {
                group.representativeDistance = distance;
                group.color = opaque(color);
            }
        }
        groups.sort(Comparator.comparingInt((Group g) -> -g.count).thenComparingInt(g -> g.key));
        List<Edge> edges = edges(perimeterPixels);
        for (Group group : groups) {
            if (group.count * 100L > total * 40L) continue;
            if (visible(group.color, edges)) return group.color;
        }
        int fallback = opaque(fallbackColor);
        if (visible(fallback, edges)) return fallback;
        // A monochrome cover has no qualifying album colour. Prefer a contrasting neutral.
        // Mixed black/white edges can need an intermediate grey; no uniform border can
        // guarantee contrast against an edge that already contains every possible shade.
        int best = 0xFF000000;
        double bestMinimum = -1;
        double bestAverage = -1;
        for (int value = 0; value <= 255; value++) {
            int color = 0xFF000000 | value << 16 | value << 8 | value;
            double minimum = Double.POSITIVE_INFINITY;
            double average = 0;
            int edgeCount = 0;
            double luminance = luminance(color);
            for (Edge edge : edges) {
                double contrast = contrast(luminance, edge.luminance);
                minimum = Math.min(minimum, contrast);
                average += contrast * edge.count;
                edgeCount += edge.count;
            }
            average /= Math.max(1, edgeCount);
            if (minimum > bestMinimum || (minimum == bestMinimum && average > bestAverage)) {
                best = color;
                bestMinimum = minimum;
                bestAverage = average;
            }
        }
        return best;
    }

    private static boolean valid(int[] pixels, int width, int height) {
        return pixels != null && width > 0 && height > 0 && (long) width * height <= pixels.length;
    }

    private static int opaque(int color) { return color | 0xFF000000; }
    private static int key(int color) {
        return ((color >> 12) & 0xF00) | ((color >> 8) & 0xF0) | ((color >> 4) & 0xF);
    }

    private static List<Edge> edges(int[] pixels) {
        Bin[] bins = new Bin[BIN_COUNT];
        if (pixels != null) for (int color : pixels) {
            if ((color >>> 24) < 128) continue;
            int key = key(color);
            if (bins[key] == null) bins[key] = new Bin(key);
            bins[key].add(color);
        }
        List<Edge> result = new ArrayList<>();
        for (Bin bin : bins) if (bin != null) result.add(new Edge(bin));
        return result;
    }

    private static boolean visible(int color, List<Edge> edges) {
        double[] lab = lab(color);
        double luminance = luminance(color);
        for (Edge edge : edges) {
            double distance = distance(lab, edge.lab);
            // Reject the same/nearby colour, and very low-contrast pale variants as well.
            if (distance < EDGE_DISTANCE_SQUARED
                    || (distance < 1225 && contrast(luminance, edge.luminance) < 1.5)) return false;
        }
        return true;
    }

    private static double distance(double[] a, double[] b) {
        double l = a[0] - b[0], x = a[1] - b[1], y = a[2] - b[2];
        return l * l + x * x + y * y;
    }

    private static double linear(int component) {
        double value = component / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    private static double luminance(int color) {
        return .2126 * linear((color >> 16) & 255) + .7152 * linear((color >> 8) & 255)
                + .0722 * linear(color & 255);
    }

    private static double contrast(double a, double b) {
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }

    private static double[] lab(int color) {
        double r = linear((color >> 16) & 255), g = linear((color >> 8) & 255), b = linear(color & 255);
        double x = labAxis((.4124564 * r + .3575761 * g + .1804375 * b) / .95047);
        double y = labAxis(.2126729 * r + .7151522 * g + .0721750 * b);
        double z = labAxis((.0193339 * r + .1191920 * g + .9503041 * b) / 1.08883);
        return new double[]{116 * y - 16, 500 * (x - y), 200 * (y - z)};
    }

    private static double labAxis(double value) {
        return value > .008856451679 ? Math.cbrt(value) : 7.787037037 * value + 16.0 / 116;
    }

    private static class Bin {
        final int key;
        int count;
        long red, green, blue;
        Group group;
        Bin(int key) { this.key = key; }
        void add(int color) {
            count++;
            red += (color >> 16) & 255;
            green += (color >> 8) & 255;
            blue += color & 255;
        }
        int mean() {
            return 0xFF000000 | (int) (red / count) << 16 | (int) (green / count) << 8 | (int) (blue / count);
        }
    }

    private static final class Group extends Bin {
        double[] lab;
        int color;
        double representativeDistance = Double.POSITIVE_INFINITY;
        Group(int key) { super(key); }
        void add(Bin bin) {
            count += bin.count;
            red += bin.red;
            green += bin.green;
            blue += bin.blue;
            lab = AlbumBorderPalette.lab(mean());
        }
    }

    private static final class Edge {
        final int count;
        final double[] lab;
        final double luminance;
        Edge(Bin bin) {
            count = bin.count;
            int color = bin.mean();
            lab = AlbumBorderPalette.lab(color);
            luminance = AlbumBorderPalette.luminance(color);
        }
    }
}
