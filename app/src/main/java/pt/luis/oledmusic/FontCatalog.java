package pt.luis.oledmusic;

import android.content.Context;
import android.graphics.Typeface;

/** Stable font choices shared by the player, lyrics and settings previews. */
public final class FontCatalog {
    // The first four entries are persisted by older app versions. Never reorder them.
    private static final String[] NAMES = {
            "Sans", "Serif", "Monoespaçada", "Condensada",
            "Sans leve", "Sans fina", "Sans média", "Sans pesada",
            "Condensada leve", "Condensada média", "Serif monoespaçada",
            "Cursiva", "Casual", "Versaletes",
            "Quicksand · arredondada", "Oswald · estreita", "Playfair Display · clássica",
            "Dancing Script · manuscrita", "Bebas Neue · cartaz"
    };

    private static final String[] SYSTEM_FAMILIES = {
            "sans-serif", "serif", "monospace", "sans-serif-condensed",
            "sans-serif-light", "sans-serif-thin", "sans-serif-medium", "sans-serif-black",
            "sans-serif-condensed-light", "sans-serif-condensed-medium", "serif-monospace",
            "cursive", "casual", "sans-serif-smallcaps"
    };

    private static final String[] ASSETS = {
            "fonts/Quicksand.ttf", "fonts/Oswald.ttf", "fonts/PlayfairDisplay.ttf",
            "fonts/DancingScript.ttf", "fonts/BebasNeue.ttf"
    };

    private static final String[] ASSET_FALLBACKS = {
            "sans-serif", "sans-serif-condensed", "serif", "cursive", "sans-serif-condensed"
    };

    private static final Typeface[] ASSET_FONTS = new Typeface[ASSETS.length];

    private FontCatalog() {
    }

    public static String[] names() {
        return NAMES.clone();
    }

    public static int count() {
        return NAMES.length;
    }

    /** Styles use the Android NORMAL, BOLD, ITALIC and BOLD_ITALIC values (0–3). */
    public static Typeface get(Context context, int family, int style) {
        if (family < 0 || family >= NAMES.length) {
            family = 0;
        }
        if (style < Typeface.NORMAL || style > Typeface.BOLD_ITALIC) {
            style = Typeface.NORMAL;
        }
        if (family < SYSTEM_FAMILIES.length) {
            // Android resolves an unavailable family alias to its default typeface.
            return Typeface.create(SYSTEM_FAMILIES[family], style);
        }
        int assetIndex = family - SYSTEM_FAMILIES.length;
        Typeface base;
        synchronized (ASSET_FONTS) {
            base = ASSET_FONTS[assetIndex];
            if (base == null && context != null) {
                try {
                    base = Typeface.createFromAsset(context.getAssets(), ASSETS[assetIndex]);
                } catch (RuntimeException unavailableFont) {
                    // A missing or unsupported asset must never prevent music playback.
                    base = Typeface.create(ASSET_FALLBACKS[assetIndex], Typeface.NORMAL);
                }
                ASSET_FONTS[assetIndex] = base;
            }
        }
        if (base == null) {
            base = Typeface.create(ASSET_FALLBACKS[assetIndex], Typeface.NORMAL);
        }
        return Typeface.create(base, style);
    }
}
