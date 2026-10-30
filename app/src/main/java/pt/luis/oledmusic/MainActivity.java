package pt.luis.oledmusic;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** A deliberately quiet, local remote for the YouTube Music media session. */
public final class MainActivity extends Activity implements MediaBridge.Listener {
    private MediaBridge bridge;
    private FrameLayout root;
    private PlayerLayout player;
    private LinearLayout setup;
    private ScrollView setupScroll;
    private TextView setupTitle;
    private TextView setupBody;
    private Button setupAction;
    private Button openMusic;
    private boolean accessGranted;
    private boolean displayedPlaying;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        player = new PlayerLayout();
        root.addView(player, new FrameLayout.LayoutParams(-1, -1));
        buildSetup();
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left = 0, top = 0, right = 0, bottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout());
                left = safe.left; top = safe.top; right = safe.right; bottom = safe.bottom;
            } else if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                left = insets.getDisplayCutout().getSafeInsetLeft();
                top = insets.getDisplayCutout().getSafeInsetTop();
                right = insets.getDisplayCutout().getSafeInsetRight();
                bottom = insets.getDisplayCutout().getSafeInsetBottom();
            }
            root.setPadding(left, top, right, bottom);
            return insets;
        });
        bridge = new MediaBridge(this, this);
        showSetup(MediaBridge.hasAccess(this));
        hideSystemBars();
    }

    @Override protected void onStart() {
        super.onStart();
        bridge.start();
    }

    @Override protected void onResume() {
        super.onResume();
        bridge.start();
        hideSystemBars();
    }

    @Override protected void onStop() {
        bridge.stop();
        super.onStop();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override public void onState(MediaBridge.State state) {
        if (!state.hasAccess || !state.connected) {
            showSetup(state.hasAccess);
            return;
        }
        setupScroll.setVisibility(View.GONE);
        player.setVisibility(View.VISIBLE);
        if (state.artwork != null) {
            player.artwork.setImageBitmap(state.artwork);
            player.artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
        } else {
            player.artwork.setImageDrawable(new SymbolDrawable(SymbolDrawable.NOTE, 0xFF555555));
            player.artwork.setScaleType(ImageView.ScaleType.CENTER);
        }
        player.artwork.setContentDescription(state.title == null || state.title.isEmpty()
            ? "Capa do álbum" : "Capa: " + state.title);
        if (displayedPlaying != state.playing || player.play.getDrawable() == null) {
            displayedPlaying = state.playing;
            player.play.setImageDrawable(new SymbolDrawable(state.playing ? SymbolDrawable.PAUSE : SymbolDrawable.PLAY, Color.BLACK));
        }
        player.play.setContentDescription(state.playing ? "Pausar" : "Reproduzir");
        setAvailable(player.play, state.canToggle);
        setAvailable(player.previous, state.canPrevious);
        setAvailable(player.next, state.canNext);
    }

    private void setAvailable(View button, boolean available) {
        button.setEnabled(available);
        button.setAlpha(available ? 1f : 0.32f);
    }

    private void buildSetup() {
        setupScroll = new ScrollView(this);
        setupScroll.setFillViewport(true);
        setupScroll.setBackgroundColor(Color.BLACK);
        root.addView(setupScroll, new FrameLayout.LayoutParams(-1, -1));
        setup = new LinearLayout(this);
        setup.setOrientation(LinearLayout.VERTICAL);
        setup.setGravity(Gravity.CENTER);
        setup.setPadding(dp(32), dp(24), dp(32), dp(24));
        setup.setBackgroundColor(Color.BLACK);
        setupScroll.addView(setup, new ScrollView.LayoutParams(-1, -2));
        setupTitle = text(28, Color.WHITE);
        setupTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        setup.addView(setupTitle, new LinearLayout.LayoutParams(-1, -2));
        setupBody = text(16, 0xFFB5B5B5);
        setupBody.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(20);
        setup.addView(setupBody, bodyParams);
        setupAction = new Button(this);
        setupAction.setAllCaps(false);
        setupAction.setTextSize(16);
        setupAction.setTextColor(Color.BLACK);
        setupAction.setBackground(roundedBackground(Color.WHITE, dp(28)));
        setupAction.setPadding(dp(24), dp(8), dp(24), dp(8));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-2, -2);
        buttonParams.topMargin = dp(32);
        setupAction.setMinHeight(dp(56));
        setup.addView(setupAction, buttonParams);
        setupAction.setOnClickListener(v -> { if (accessGranted) launchMusic(); else requestMediaAccess(); });
        openMusic = new Button(this);
        openMusic.setText("Abrir YouTube Music");
        openMusic.setAllCaps(false);
        openMusic.setTextColor(0xFFB5B5B5);
        openMusic.setBackground(roundedBackground(Color.BLACK, dp(24)));
        openMusic.setMinHeight(dp(48));
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(-2, -2);
        secondaryParams.topMargin = dp(12);
        setup.addView(openMusic, secondaryParams);
        openMusic.setOnClickListener(v -> launchMusic());
    }

    private TextView text(int size, int color) {
        TextView text = new TextView(this);
        text.setTextColor(color);
        text.setTextSize(size);
        text.setGravity(Gravity.CENTER);
        return text;
    }

    private void showSetup(boolean hasAccess) {
        accessGranted = hasAccess;
        player.setVisibility(View.GONE);
        setupScroll.setVisibility(View.VISIBLE);
        setupTitle.setText(hasAccess ? "A tua música.\nSó o essencial." : "Liga a tua música.");
        setupBody.setText(hasAccess
            ? "Começa uma música no YouTube Music e volta aqui. A capa e os controlos aparecem automaticamente."
            : "Para mostrar a capa e controlar o YouTube Music, permite o acesso às notificações nas definições do Android.\n\nA app usa apenas a sessão multimédia e a notificação do YouTube Music, no teu telemóvel.");
        setupAction.setText(hasAccess ? "Abrir YouTube Music" : "Permitir acesso");
        openMusic.setVisibility(hasAccess ? View.GONE : View.VISIBLE);
    }

    private void requestMediaAccess() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
                detail.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    new ComponentName(this, MediaAccessService.class).flattenToString());
                startActivity(detail);
                return;
            }
        } catch (ActivityNotFoundException ignored) { }
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
        catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, "Abre as definições do Android e permite o acesso às notificações para OLED Music.", Toast.LENGTH_LONG).show();
        }
    }

    private void launchMusic() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(MediaBridge.preferredPackage(this));
        if (intent != null) {
            try { startActivity(intent); return; }
            catch (ActivityNotFoundException ignored) { }
        }
        Toast.makeText(this, "Instala ou ativa o YouTube Music para continuar.", Toast.LENGTH_LONG).show();
    }

    private Drawable roundedBackground(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return new RippleDrawable(ColorStateList.valueOf(color == Color.WHITE ? 0x33000000 : 0x33FFFFFF), shape, null);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class PlayerLayout extends ViewGroup {
        final ImageView artwork;
        final ImageButton previous;
        final ImageButton play;
        final ImageButton next;
        private boolean landscape;
        private int artSize;
        private int sideSize;
        private int playSize;

        PlayerLayout() {
            super(MainActivity.this);
            setBackgroundColor(Color.BLACK);
            artwork = new ImageView(MainActivity.this);
            artwork.setBackgroundColor(Color.BLACK);
            artwork.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
                }
            });
            artwork.setClipToOutline(true);
            addView(artwork);
            previous = control(SymbolDrawable.PREVIOUS, "Faixa anterior", false);
            play = control(SymbolDrawable.PLAY, "Reproduzir", true);
            next = control(SymbolDrawable.NEXT, "Faixa seguinte", false);
            previous.setOnClickListener(v -> bridge.previous());
            play.setOnClickListener(v -> bridge.playPause());
            next.setOnClickListener(v -> bridge.next());
        }

        private ImageButton control(int symbol, String description, boolean primary) {
            ImageButton button = new ImageButton(MainActivity.this);
            button.setContentDescription(description);
            button.setImageDrawable(new SymbolDrawable(symbol, primary ? Color.BLACK : Color.WHITE));
            button.setScaleType(ImageView.ScaleType.FIT_CENTER);
            button.setPadding(dp(primary ? 25 : 15), dp(primary ? 25 : 15), dp(primary ? 25 : 15), dp(primary ? 25 : 15));
            button.setBackground(roundedBackground(primary ? Color.WHITE : Color.BLACK, dp(100)));
            addView(button);
            return button;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            landscape = width > height;
            sideSize = dp(64);
            playSize = dp(88);
            if (landscape) {
                artSize = Math.max(dp(48), Math.min(height - dp(48), (int) (width * .49f) - dp(40)));
                // Keep all three targets available even in compact split-screen windows.
                int controlWidth = width - artSize - dp(72);
                if (controlWidth < dp(240)) {
                    sideSize = dp(48);
                    playSize = dp(72);
                }
            } else {
                artSize = Math.max(dp(48), Math.min((int) (width * .846f), height - dp(176)));
            }
            artwork.measure(exact(artSize), exact(artSize));
            previous.measure(exact(sideSize), exact(sideSize));
            play.measure(exact(playSize), exact(playSize));
            next.measure(exact(sideSize), exact(sideSize));
        }

        private int exact(int size) { return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY); }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l, height = b - t;
            if (landscape) {
                int artLeft = dp(28);
                int artTop = (height - artSize) / 2;
                artwork.layout(artLeft, artTop, artLeft + artSize, artTop + artSize);
                int remainingLeft = artLeft + artSize + dp(20);
                int centerX = remainingLeft + (width - dp(20) - remainingLeft) / 2;
                int offset = Math.min(dp(94), Math.max(dp(68), (width - remainingLeft - dp(28) - sideSize) / 2));
                layoutCentered(previous, centerX - offset, height / 2, sideSize);
                layoutCentered(play, centerX, height / 2, playSize);
                layoutCentered(next, centerX + offset, height / 2, sideSize);
            } else {
                int artTop = Math.max(dp(22), Math.round(height * .045f));
                int artLeft = (width - artSize) / 2;
                artwork.layout(artLeft, artTop, artLeft + artSize, artTop + artSize);
                int controlsY = Math.min(height - dp(40) - playSize / 2,
                    Math.max(artTop + artSize + dp(80), Math.round(height * .81f)));
                int offset = Math.min(dp(100), (width - sideSize - dp(32)) / 2);
                layoutCentered(previous, width / 2 - offset, controlsY, sideSize);
                layoutCentered(play, width / 2, controlsY, playSize);
                layoutCentered(next, width / 2 + offset, controlsY, sideSize);
            }
        }

        private void layoutCentered(View view, int x, int y, int size) {
            view.layout(x - size / 2, y - size / 2, x - size / 2 + size, y - size / 2 + size);
        }
    }

    /** Small native paths keep controls crisp at every display density. */
    private final class SymbolDrawable extends Drawable {
        static final int PLAY = 0, PAUSE = 1, PREVIOUS = 2, NEXT = 3, NOTE = 4;
        private final int symbol;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SymbolDrawable(int symbol, int color) { this.symbol = symbol; paint.setColor(color); }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
            Path path = new Path();
            if (symbol == PLAY) {
                path.moveTo(6, 3); path.lineTo(21, 12); path.lineTo(6, 21); path.close();
                canvas.drawPath(path, paint);
            } else if (symbol == PAUSE) {
                canvas.drawRoundRect(5, 3, 10, 21, .8f, .8f, paint);
                canvas.drawRoundRect(14, 3, 19, 21, .8f, .8f, paint);
            } else if (symbol == PREVIOUS || symbol == NEXT) {
                if (symbol == PREVIOUS) { canvas.translate(24, 0); canvas.scale(-1, 1); }
                path.moveTo(3, 3); path.lineTo(18, 12); path.lineTo(3, 21); path.close();
                canvas.drawPath(path, paint);
                canvas.drawRoundRect(19, 3, 21, 21, .6f, .6f, paint);
            } else {
                canvas.drawCircle(8, 17.5f, 4, paint);
                canvas.drawRect(10, 4, 13, 18, paint);
                path.moveTo(10, 4); path.lineTo(21, 2); path.lineTo(21, 7); path.lineTo(10, 9); path.close();
                canvas.drawPath(path, paint);
            }
            canvas.restore();
        }
        @Override public int getIntrinsicWidth() { return dp(symbol == NOTE ? 48 : 32); }
        @Override public int getIntrinsicHeight() { return dp(symbol == NOTE ? 48 : 32); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
