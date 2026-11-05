package pt.luis.oledmusic;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.List;

/** Small software sample of the displayed center crop. Called on a worker thread. */
final class AlbumArtworkColors {
    static final int SAMPLE_SIZE = 64;

    private AlbumArtworkColors() { }

    static int choose(Bitmap source, int radius, int stroke, int dim, int fallbackColor) {
        Bitmap software = null;
        Bitmap sample = null;
        try {
            if (source == null || source.isRecycled()) return fallbackColor;
            Bitmap input = source;
            if (source.getConfig() == Bitmap.Config.HARDWARE) {
                software = source.copy(Bitmap.Config.ARGB_8888, false);
                if (software == null) return fallbackColor;
                input = software;
            }
            sample = Bitmap.createBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(sample);
            canvas.drawColor(Color.BLACK);
            float scale = SAMPLE_SIZE / (float) Math.min(input.getWidth(), input.getHeight());
            canvas.translate((SAMPLE_SIZE - input.getWidth() * scale) / 2f,
                    (SAMPLE_SIZE - input.getHeight() * scale) / 2f);
            canvas.scale(scale, scale);
            canvas.drawBitmap(input, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
            int[] pixels = new int[SAMPLE_SIZE * SAMPLE_SIZE];
            sample.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE);

            // Sample where the image meets the inner edge of the actual rounded stroke.
            float inset = Math.min((SAMPLE_SIZE - 1) / 2f, Math.max(1, stroke));
            float min = inset;
            float max = SAMPLE_SIZE - 1 - inset;
            float edgeRadius = Math.max(0, Math.min(radius - inset, (max - min) / 2f));
            List<Integer> perimeter = new ArrayList<>();
            float straightLength = max - min - 2 * edgeRadius;
            int straightSteps = (int) Math.ceil(straightLength);
            for (int i = 0; i < straightSteps; i++) {
                float along = min + edgeRadius + straightLength * i / straightSteps;
                add(perimeter, pixels, along, min, dim);
                add(perimeter, pixels, along, max, dim);
                add(perimeter, pixels, min, along, dim);
                add(perimeter, pixels, max, along, dim);
            }
            for (int corner = 0; corner < 4; corner++) {
                float centerX = (corner == 0 || corner == 3) ? max - edgeRadius : min + edgeRadius;
                float centerY = corner < 2 ? max - edgeRadius : min + edgeRadius;
                int arcSteps = (int) Math.ceil(Math.PI * edgeRadius / 2);
                for (int i = 0; i < arcSteps; i++) {
                    double angle = Math.toRadians(corner * 90 + i * 90f / arcSteps);
                    add(perimeter, pixels, centerX + edgeRadius * (float) Math.cos(angle),
                            centerY + edgeRadius * (float) Math.sin(angle), dim);
                }
            }
            if (perimeter.isEmpty()) add(perimeter, pixels, min, min, dim);
            // Hidden corners do not contribute to the frequency denominator.
            for (int y = 0; y < SAMPLE_SIZE; y++) for (int x = 0; x < SAMPLE_SIZE; x++) {
                float dx = Math.max(0, Math.max(radius - (x + .5f), x + .5f - (SAMPLE_SIZE - radius)));
                float dy = Math.max(0, Math.max(radius - (y + .5f), y + .5f - (SAMPLE_SIZE - radius)));
                if (dx * dx + dy * dy > radius * radius) pixels[y * SAMPLE_SIZE + x] = 0;
            }
            int[] edges = new int[perimeter.size()];
            for (int i = 0; i < edges.length; i++) edges[i] = perimeter.get(i);
            return AlbumBorderPalette.choose(pixels, SAMPLE_SIZE, SAMPLE_SIZE, edges, fallbackColor);
        } catch (RuntimeException | OutOfMemoryError ignored) {
            return fallbackColor;
        } finally {
            if (sample != null) sample.recycle();
            if (software != null) software.recycle();
        }
    }

    private static void add(List<Integer> output, int[] pixels, float x, float y, int dim) {
        int ix = Math.max(0, Math.min(SAMPLE_SIZE - 1, Math.round(x)));
        int iy = Math.max(0, Math.min(SAMPLE_SIZE - 1, Math.round(y)));
        int color = pixels[iy * SAMPLE_SIZE + ix];
        // A colour found at the original edge is excluded even when dimming would
        // make it look different. Check the displayed edge as well for visibility.
        output.add(color);
        if (dim == 0) return;
        float factor = 1 - dim / 100f;
        output.add(Color.rgb(Math.round(Color.red(color) * factor),
                Math.round(Color.green(color) * factor), Math.round(Color.blue(color) * factor)));
    }
}
