package pt.luis.oledmusic;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity implements MediaBridge.Listener {
    private boolean accessGranted;
    private LyricsRepository lyricsRepository;
    private boolean extrasActive;
    private String lyricRequestKey = "";
    private boolean videoMode;
    private VideoResume rotationVideo;
    private VideoResume pausedVideo;
    private static final class VideoResume {
        final String id, trackKey;
        final long position;
        final boolean playing;
        VideoResume(String id, String trackKey, long position, boolean playing) {
            this.id = id; this.trackKey = trackKey; this.position = position; this.playing = playing;
        }
    }
    private boolean videoWasNativePlaying;
    private String videoTrackKey = "";
    private String currentVideoId = "";
    private final Handler lyricHandler = new Handler(Looper.getMainLooper());
    private final Runnable lyricTick = new Runnable() {
        @Override public void run() {
            if (!extrasActive || !appearance.showLyrics) return;
            updateLyricPosition();
            lyricHandler.postDelayed(this, 200);
        }
    };
    private AppPreferences appearance;
    private MediaBridge bridge;
    private BrightnessControl brightnessControl;
    private boolean displayedPlaying;
    private LockScreenController lockScreen;
    private MediaBridge.State mediaState = new MediaBridge.State();
    private Button openMusic;
    private PlayerLayout player;
    private FrameLayout root;
    private ThreeFingerTapGesture settingsGesture;
    private LinearLayout setup;
    private Button setupAction;
    private TextView setupBody;
    private ScrollView setupScroll;
    private TextView setupTitle;
    private boolean started;
    private VolumeControl volumeControl;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.appearance = AppPreferences.load(this);
        this.lyricsRepository = new LyricsRepository(this);
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof VideoResume) rotationVideo = (VideoResume) retained;
        this.lockScreen = new LockScreenController(this);
        getWindow().setStatusBarColor(-16777216);
        getWindow().setNavigationBarColor(-16777216);
        getWindow().setBackgroundDrawable(new ColorDrawable(-16777216));
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode = 1;
            getWindow().setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        FrameLayout frameLayout = new FrameLayout(this);
        this.root = frameLayout;
        frameLayout.setBackgroundColor(-16777216);
        PlayerLayout playerLayout = new PlayerLayout();
        this.player = playerLayout;
        this.root.addView(playerLayout, new FrameLayout.LayoutParams(-1, -1));
        buildSetup();
        this.settingsGesture = new ThreeFingerTapGesture(this, new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.openSettings();
            }
        });
        this.root.setContentDescription("OLED Music");
        this.root.setImportantForAccessibility(1);
        this.root.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View view, AccessibilityNodeInfo accessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(view, accessibilityNodeInfo);
                accessibilityNodeInfo.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.open_settings_action, MainActivity.this.getString(R.string.open_settings)));
            }

            @Override
            public boolean performAccessibilityAction(View view, int i, Bundle bundle2) {
                if (i == R.id.open_settings_action) {
                    MainActivity.this.openSettings();
                    return true;
                }
                return super.performAccessibilityAction(view, i, bundle2);
            }
        });
        this.brightnessControl = new BrightnessControl(this, this.player.brightness);
        this.volumeControl = new VolumeControl(this, this.player.volume);
        setContentView(this.root);
        this.root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public final WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                return MainActivity.this.applySafeInsets(view, windowInsets);
            }
        });
        this.bridge = new MediaBridge(this, this);
        showSetup(MediaBridge.hasAccess(this));
        hideSystemBars();
    }

    WindowInsets applySafeInsets(View view, WindowInsets windowInsets) {
        int safeInsetLeft;
        int safeInsetBottom;
        int safeInsetTop;
        int safeInsetRight;
        if (Build.VERSION.SDK_INT >= 30) {
            Insets insetsIgnoringVisibility = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout());
            safeInsetLeft = insetsIgnoringVisibility.left;
            safeInsetTop = insetsIgnoringVisibility.top;
            safeInsetRight = insetsIgnoringVisibility.right;
            safeInsetBottom = insetsIgnoringVisibility.bottom;
        } else if (Build.VERSION.SDK_INT < 28 || windowInsets.getDisplayCutout() == null) {
            safeInsetLeft = 0;
            safeInsetBottom = 0;
            safeInsetTop = 0;
            safeInsetRight = 0;
        } else {
            safeInsetLeft = windowInsets.getDisplayCutout().getSafeInsetLeft();
            safeInsetTop = windowInsets.getDisplayCutout().getSafeInsetTop();
            safeInsetRight = windowInsets.getDisplayCutout().getSafeInsetRight();
            safeInsetBottom = windowInsets.getDisplayCutout().getSafeInsetBottom();
        }
        this.root.setPadding(safeInsetLeft, safeInsetTop, safeInsetRight, safeInsetBottom);
        return windowInsets;
    }

    @Override
    protected void onStart() {
        super.onStart();
        this.started = true;
        AppPreferences appPreferencesLoad = AppPreferences.load(this);
        this.appearance = appPreferencesLoad;
        this.lockScreen.onForeground(appPreferencesLoad.showOnLockScreen);
        this.bridge.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppPreferences appPreferencesLoad = AppPreferences.load(this);
        this.appearance = appPreferencesLoad;
        this.lockScreen.setEnabled(appPreferencesLoad.showOnLockScreen);
        this.extrasActive = true;
        this.lyricRequestKey = "";
        this.player.applyAppearance();
        updateLyrics(mediaState);
        lyricHandler.removeCallbacks(lyricTick);
        lyricTick.run();
        this.brightnessControl.resume();
        this.volumeControl.resume();
        this.bridge.start();
        if (rotationVideo != null) {
            VideoResume restore = rotationVideo;
            rotationVideo = null;
            if (appearance.showVideoSwitcher && mediaState.connected && restore.trackKey.equals(mediaState.trackKey)) restoreVideo(restore);
        }
        hideSystemBars();
    }

    @Override
    protected void onPause() {
        this.extrasActive = false;
        lyricHandler.removeCallbacks(lyricTick);
        lyricsRepository.cancel();
        lyricRequestKey = "";
        pausedVideo = videoMode && !currentVideoId.isEmpty()
                ? new VideoResume(currentVideoId, videoTrackKey, player.video.getPositionMs(),
                        player.video.isPlaying() || (!player.video.hasStarted() && videoWasNativePlaying)) : null;
        leaveVideo(false);
        this.settingsGesture.reset();
        this.brightnessControl.pause();
        this.volumeControl.pause();
        this.player.coverGestures.cancel();
        super.onPause();
    }

    @Override public Object onRetainNonConfigurationInstance() { return pausedVideo; }

    public void openSettings() {
        DeviceUnlock.run(this, new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.launchSettings();
            }
        });
    }

    void launchSettings() {
        startActivity(new Intent(this, (Class<?>) SettingsActivity.class)
                .putExtra("track_key", mediaState.connected ? mediaState.trackKey : "")
                .putExtra("track_title", mediaState.title));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        if (motionEvent.getActionMasked() == 3) {
            BrightnessControl brightnessControl = this.brightnessControl;
            if (brightnessControl != null) {
                brightnessControl.cancelTouch();
            }
            VolumeControl volumeControl = this.volumeControl;
            if (volumeControl != null) {
                volumeControl.cancelTouch();
            }
        }
        ThreeFingerTapGesture threeFingerTapGesture = this.settingsGesture;
        if (threeFingerTapGesture != null) {
            boolean zIsCapturing = threeFingerTapGesture.isCapturing();
            if (this.settingsGesture.onTouchEvent(motionEvent)) {
                if (zIsCapturing || !this.settingsGesture.isCapturing()) {
                    return true;
                }
                BrightnessControl brightnessControl2 = this.brightnessControl;
                if (brightnessControl2 != null) {
                    brightnessControl2.cancelTouch();
                }
                VolumeControl volumeControl2 = this.volumeControl;
                if (volumeControl2 != null) {
                    volumeControl2.cancelTouch();
                }
                MotionEvent motionEventObtain = MotionEvent.obtain(motionEvent);
                motionEventObtain.setAction(3);
                super.dispatchTouchEvent(motionEventObtain);
                motionEventObtain.recycle();
                return true;
            }
        }
        return super.dispatchTouchEvent(motionEvent);
    }

    @Override
    protected void onStop() {
        this.started = false;
        if (!DeviceUnlock.isPending(this)) {
            this.lockScreen.onStopped();
        }
        if (!this.lockScreen.keepMonitoringWhileScreenOff()) {
            this.bridge.stop();
        }
        super.onStop();
    }

    @Override
    protected void onUserLeaveHint() {
        this.lockScreen.onUserLeave();
        super.onUserLeaveHint();
    }

    @Override
    protected void onDestroy() {
        this.bridge.stop();
        lyricsRepository.close();
        lyricHandler.removeCallbacks(lyricTick);
        player.video.destroy();
        this.lockScreen.destroy();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
        if (z) {
            hideSystemBars();
        }
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController insetsController = getWindow().getInsetsController();
            if (insetsController != null) {
                insetsController.setSystemBarsBehavior(2);
                insetsController.hide(WindowInsets.Type.systemBars());
                return;
            }
            return;
        }
        getWindow().getDecorView().setSystemUiVisibility(5894);
    }

    @Override
    public void onState(MediaBridge.State state) {
        boolean nativeStarted = state.playing && state.playbackSpeed > 0
                && (!mediaState.playing || mediaState.playbackSpeed <= 0);
        if (videoMode && (!state.connected || !videoTrackKey.equals(state.trackKey) || nativeStarted)) leaveVideo(false, false);
        this.mediaState = state;
        updateLyrics(state);
        this.player.updateMetadata(state.title, state.artist);
        this.lockScreen.onMediaState(state.hasAccess && state.connected);
        if (!this.started && !this.lockScreen.keepMonitoringWhileScreenOff()) {
            this.lockScreen.onUserLeave();
            this.bridge.stop();
        }
        if (!state.hasAccess || !state.connected) {
            showSetup(state.hasAccess);
            return;
        }
        this.setupScroll.setVisibility(8);
        this.player.setVisibility(0);
        if (state.artwork != null) {
            this.player.artwork.setArtwork(state.artwork);
        } else {
            this.player.artwork.setPlaceholder(new SymbolDrawable(4, -11184811));
        }
        this.player.artwork.setContentDescription((state.title == null || state.title.isEmpty()) ? "Capa do álbum" : "Capa: " + state.title);
        updatePlayButton();
        setAvailable(this.player.previous, state.canPrevious);
        setAvailable(this.player.next, state.canNext);
    }

    private long nativePosition() {
        return PlaybackClock.position(mediaState.positionMs, mediaState.positionUpdatedAtMs,
                mediaState.playbackSpeed, mediaState.playing, mediaState.durationMs, SystemClock.elapsedRealtime());
    }

    private void updatePlayButton() {
        boolean embedded = videoMode && !currentVideoId.isEmpty();
        boolean playing = embedded ? player.video.isPlaying() : mediaState.playing;
        if (displayedPlaying != playing || player.play.getDrawable() == null) {
            displayedPlaying = playing;
            player.play.setImageDrawable(new SymbolDrawable(playing ? SymbolDrawable.PAUSE : SymbolDrawable.PLAY, controlIconColor()));
        }
        player.play.setContentDescription(playing ? "Pausar" : "Reproduzir");
        setAvailable(player.play, embedded || mediaState.canToggle);
    }

    private void updateLyrics(MediaBridge.State state) {
        if (!extrasActive || !appearance.showLyrics || !state.connected || state.title == null || state.title.trim().isEmpty()) {
            if (!lyricRequestKey.isEmpty()) lyricsRepository.cancel();
            lyricRequestKey = "";
            if (!state.connected) player.lyrics.setStatus("Começa uma música para ver a letra.");
            else if (state.title == null || state.title.trim().isEmpty()) player.lyrics.setStatus("A aguardar informações da música…");
            return;
        }
        String attachmentKey = state.trackKey;
        String request = attachmentKey + "|source=" + appearance.lyricsSource
                + "|title=" + state.title + "|artist=" + state.artist + "|album=" + state.album + "|duration=" + state.durationMs;
        String expectedKey = appearance.lyricsSource == 1 ? attachmentKey : request;
        if (request.equals(lyricRequestKey)) return;
        lyricRequestKey = request;
        player.lyrics.setStatus("A procurar letra…");
        LyricsRepository.Listener listener = (key, timeline, status) -> {
            if (!extrasActive || !appearance.showLyrics || !request.equals(lyricRequestKey)
                    || !key.equals(expectedKey) || !attachmentKey.equals(mediaState.trackKey)) return;
            if (timeline != null) player.lyrics.setTimeline(timeline);
            else player.lyrics.setStatus(status);
            updateLyricPosition();
        };
        if (appearance.lyricsSource == 1) lyricsRepository.loadLocal(state.trackKey, listener);
        else lyricsRepository.load(request, state.title, state.artist, state.album, state.durationMs, listener);
    }

    private void updateLyricPosition() {
        if (!appearance.showLyrics) return;
        boolean embedded = videoMode && !currentVideoId.isEmpty();
        long position = embedded ? player.video.getPositionMs() : nativePosition();
        player.lyrics.updatePosition(Math.max(0, position - appearance.lyricsOffsetMs), embedded || mediaState.canSeek);
    }

    private String videoId() {
        String saved = TrackAttachments.video(this, mediaState.trackKey);
        if (YouTubeVideoId.isValid(saved)) return saved;
        if (MediaBridge.YT_PACKAGE.equals(mediaState.packageName) || MediaBridge.REVANCED_PACKAGE.equals(mediaState.packageName)) {
            String id = YouTubeVideoId.from(mediaState.mediaId);
            if (id == null || id.isEmpty()) id = YouTubeVideoId.from(mediaState.mediaUri);
            return id == null ? "" : id;
        }
        return "";
    }

    private void chooseVideoMode(boolean video) {
        if (!appearance.showVideoSwitcher || !mediaState.connected) { player.mode.setVideoSelected(false); return; }
        if (!video) { leaveVideo(appearance.resumeAfterVideo); return; }
        if (videoMode) return;
        currentVideoId = videoId();
        videoTrackKey = mediaState.trackKey;
        videoWasNativePlaying = mediaState.playing;
        videoMode = true;
        player.mode.setVideoSelected(true);
        player.artwork.setVisibility(View.GONE);
        player.video.setVisibility(View.VISIBLE);
        if (!currentVideoId.isEmpty()) {
            long start = nativePosition();
            bridge.pause();
            player.video.play(currentVideoId, start);
        } else {
            player.video.play("", 0);
            Toast.makeText(this, "Associa um link YouTube em Definições → Vídeo para esta música.", Toast.LENGTH_LONG).show();
        }
        updatePlayButton();
    }

    private void restoreVideo(VideoResume restore) {
        if (!YouTubeVideoId.isValid(restore.id)) return;
        currentVideoId = restore.id;
        videoTrackKey = restore.trackKey;
        videoWasNativePlaying = restore.playing;
        videoMode = true;
        player.mode.setVideoSelected(true);
        player.artwork.setVisibility(View.GONE);
        player.video.setVisibility(View.VISIBLE);
        bridge.pause();
        if (restore.playing) player.video.play(restore.id, restore.position);
        else { player.video.seekTo(restore.position); player.video.pause(); }
        updatePlayButton();
    }

    private void leaveVideo(boolean resume) { leaveVideo(resume, true); }

    private void leaveVideo(boolean resume, boolean transferPosition) {
        if (!videoMode) return;
        boolean hadVideo = !currentVideoId.isEmpty();
        long position = player.video.getPositionMs();
        boolean hadPlayback = player.video.hasStarted();
        boolean playing = player.video.isPlaying() || (!hadPlayback && videoWasNativePlaying);
        videoMode = false;
        currentVideoId = "";
        player.mode.setVideoSelected(false);
        player.video.stop();
        player.video.setVisibility(View.GONE);
        player.artwork.setVisibility(View.VISIBLE);
        if (transferPosition && hadVideo && mediaState.connected && videoTrackKey.equals(mediaState.trackKey)) {
            if (hadPlayback && mediaState.canSeek) bridge.seekTo(position);
            if (resume && playing) bridge.play();
        }
        updatePlayButton();
    }

    private void setAvailable(View view, boolean z) {
        view.setEnabled(z);
        view.setAlpha((z ? 1.0f : 0.32f) * (1.0f - (this.appearance.controlsDim / 100.0f)));
    }

    private void buildSetup() {
        ScrollView scrollView = new ScrollView(this);
        this.setupScroll = scrollView;
        scrollView.setFillViewport(true);
        this.setupScroll.setBackgroundColor(-16777216);
        this.root.addView(this.setupScroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout linearLayout = new LinearLayout(this);
        this.setup = linearLayout;
        linearLayout.setOrientation(1);
        this.setup.setGravity(17);
        this.setup.setPadding(dp(32.0f), dp(24.0f), dp(32.0f), dp(24.0f));
        this.setup.setBackgroundColor(-16777216);
        this.setupScroll.addView(this.setup, new FrameLayout.LayoutParams(-1, -2));
        TextView textViewText = text(28, -1);
        this.setupTitle = textViewText;
        textViewText.setTypeface(Typeface.DEFAULT, 1);
        this.setup.addView(this.setupTitle, new LinearLayout.LayoutParams(-1, -2));
        TextView textViewText2 = text(16, -4868683);
        this.setupBody = textViewText2;
        textViewText2.setLineSpacing(dp(3.0f), 1.0f);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(20.0f);
        this.setup.addView(this.setupBody, layoutParams);
        Button button = new Button(this);
        this.setupAction = button;
        button.setAllCaps(false);
        this.setupAction.setTextSize(16.0f);
        this.setupAction.setTextColor(-16777216);
        this.setupAction.setBackground(roundedBackground(-1, dp(28.0f)));
        this.setupAction.setPadding(dp(24.0f), dp(8.0f), dp(24.0f), dp(8.0f));
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-2, -2);
        layoutParams2.topMargin = dp(32.0f);
        this.setupAction.setMinHeight(dp(56.0f));
        this.setup.addView(this.setupAction, layoutParams2);
        this.setupAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                MainActivity.this.onSetupAction(view);
            }
        });
        Button button2 = new Button(this);
        this.openMusic = button2;
        button2.setText("Abrir app de música");
        this.openMusic.setAllCaps(false);
        this.openMusic.setTextColor(-4868683);
        this.openMusic.setBackground(roundedBackground(-16777216, dp(24.0f)));
        this.openMusic.setMinHeight(dp(48.0f));
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(-2, -2);
        layoutParams3.topMargin = dp(12.0f);
        this.setup.addView(this.openMusic, layoutParams3);
        this.openMusic.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                MainActivity.this.onOpenMusic(view);
            }
        });
    }

    void onSetupAction(View view) {
        if (this.accessGranted) {
            launchMusic();
        } else {
            requestMediaAccess();
        }
    }

    void onOpenMusic(View view) {
        launchMusic();
    }

    private TextView text(int i, int i2) {
        TextView textView = new TextView(this);
        textView.setTextColor(i2);
        textView.setTextSize(i);
        textView.setGravity(17);
        return textView;
    }

    private void showSetup(boolean z) {
        String str;
        this.accessGranted = z;
        this.player.setVisibility(8);
        this.setupScroll.setVisibility(0);
        this.setupTitle.setText(z ? "A tua música.\nSó o essencial." : "Liga a tua música.");
        TextView textView = this.setupBody;
        if (z) {
            str = "Começa uma música no SoundCloud ou YouTube Music e volta aqui. A app segue o último player que começou a tocar, mesmo depois de o pausares.";
        } else {
            str = "Para mostrar a capa e controlar o SoundCloud ou YouTube Music, permite o acesso às notificações nas definições do Android.\n\nA app usa apenas as sessões multimédia e as notificações destes players, no teu telemóvel.";
        }
        textView.setText(str);
        this.setupAction.setText(z ? "Abrir app de música" : "Permitir acesso");
        this.openMusic.setVisibility(z ? 8 : 0);
    }

    private void requestMediaAccess() {
        DeviceUnlock.run(this, new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.requestMediaAccessAfterUnlock();
            }
        });
    }

    public void requestMediaAccessAfterUnlock() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Intent intent = new Intent("android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS");
                intent.putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME", new ComponentName(this, (Class<?>) MediaAccessService.class).flattenToString());
                startActivity(intent);
                return;
            }
        } catch (ActivityNotFoundException unused) {
        }
        try {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        } catch (ActivityNotFoundException unused2) {
            Toast.makeText(this, "Abre as definições do Android e permite o acesso às notificações para OLED Music.", 1).show();
        }
    }

    private void launchMusic() {
        DeviceUnlock.run(this, new Runnable() {
            @Override
            public final void run() {
                MainActivity.this.launchMusicAfterUnlock();
            }
        });
    }

    public void launchMusicAfterUnlock() {
        Intent launchIntentForPackage = getPackageManager().getLaunchIntentForPackage(MediaBridge.preferredPackage(this));
        if (launchIntentForPackage != null) {
            try {
                startActivity(launchIntentForPackage);
                return;
            } catch (ActivityNotFoundException unused) {
            }
        }
        Toast.makeText(this, "Instala ou ativa o SoundCloud ou YouTube Music para continuar.", 1).show();
    }

    public Drawable roundedBackground(int i, int i2) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(i);
        gradientDrawable.setCornerRadius(i2);
        return new RippleDrawable(ColorStateList.valueOf(i == -1 ? 855638016 : 872415231), gradientDrawable, null);
    }

    public int dp(float f) {
        return Math.round(f * getResources().getDisplayMetrics().density);
    }

    public int controlIconColor() {
        return Color.luminance(this.appearance.controlsColor) > 0.179f ? -16777216 : -1;
    }

    final class PlayerLayout extends ViewGroup {
        private int artLeft;
        private int artSize;
        private int artTop;
        final TextView artistText;
        final AlbumArtworkView artwork;
        final LyricsPanel lyrics;
        final YouTubeVideoPanel video;
        final MediaModeSwitch mode;
        private PlayerExtrasLayout.Plan extrasPlan;
        private int barHeight;
        final SeekBar brightness;
        final LinearLayout brightnessRow;
        final CoverGestures coverGestures;
        private int groupHeight;
        private int infoWidth;
        private boolean landscape;
        final LinearLayout metadata;
        private int metadataHeight;
        final ImageButton next;
        final ImageButton play;
        private int playSize;
        final ImageButton previous;
        private int sideSize;
        final TextView titleText;
        private final ImageButton[] transportButtons;
        final SeekBar volume;
        final LinearLayout volumeRow;

        PlayerLayout() {
            super(MainActivity.this);
            setBackgroundColor(-16777216);
            mode = new MediaModeSwitch(MainActivity.this);
            mode.setId(R.id.media_mode_switch);
            mode.setOnModeChanged(MainActivity.this::chooseVideoMode);
            addView(mode);
            video = new YouTubeVideoPanel(MainActivity.this);
            video.setId(R.id.player_video);
            video.setVisibility(View.GONE);
            video.setListener(new YouTubeVideoPanel.Listener() {
                @Override public void onProgress(long position, long duration, boolean playing) {
                    updatePlayButton();
                    updateLyricPosition();
                }
                @Override public void onError(String message) { updatePlayButton(); }
            });
            lyrics = new LyricsPanel(MainActivity.this);
            lyrics.setId(R.id.player_lyrics);
            lyrics.setOnSeek(time -> {
                long target = PlaybackClock.seekTarget(time, appearance.lyricsOffsetMs, mediaState.durationMs);
                if (videoMode && !currentVideoId.isEmpty()) video.seekTo(target);
                else if (mediaState.canSeek) bridge.seekTo(target);
            });
            addView(lyrics);
            addView(video);
            SeekBar seekBarCreate = MinimalSlider.create(MainActivity.this, MainActivity.this.appearance.brightnessColor);
            this.brightness = seekBarCreate;
            SeekBar seekBarCreate2 = MinimalSlider.create(MainActivity.this, MainActivity.this.appearance.volumeColor);
            this.volume = seekBarCreate2;
            LinearLayout linearLayoutBar = bar(5, seekBarCreate);
            this.brightnessRow = linearLayoutBar;
            LinearLayout linearLayoutBar2 = bar(6, seekBarCreate2);
            this.volumeRow = linearLayoutBar2;
            addView(linearLayoutBar);
            addView(linearLayoutBar2);
            AlbumArtworkView artworkView = new AlbumArtworkView(MainActivity.this);
            this.artwork = artworkView;
            artworkView.setBackgroundColor(-16777216);
            artworkView.setId(R.id.player_artwork);
            addView(artworkView);
            CoverGestures coverGestures = new CoverGestures(MainActivity.this, new Runnable() {
                @Override
                public final void run() {
                    PlayerLayout.this.toggleFromCover();
                }
            }, new Runnable() {
                @Override
                public final void run() {
                    PlayerLayout.this.previousFromCover();
                }
            }, new Runnable() {
                @Override
                public final void run() {
                    PlayerLayout.this.nextFromCover();
                }
            });
            this.coverGestures = coverGestures;
            artworkView.setOnTouchListener(coverGestures);
            artworkView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View view, AccessibilityNodeInfo accessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(view, accessibilityNodeInfo);
                    if (MainActivity.this.appearance.coverGestures) {
                        if (MainActivity.this.mediaState.canToggle) {
                            accessibilityNodeInfo.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.cover_toggle_action, "Reproduzir/pausar"));
                        }
                        if (MainActivity.this.mediaState.canPrevious) {
                            accessibilityNodeInfo.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.cover_previous_action, "Faixa anterior"));
                        }
                        if (MainActivity.this.mediaState.canNext) {
                            accessibilityNodeInfo.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.cover_next_action, "Faixa seguinte"));
                        }
                    }
                }

                @Override
                public boolean performAccessibilityAction(View view, int i, Bundle bundle) {
                    if (MainActivity.this.appearance.coverGestures) {
                        if (i == R.id.cover_toggle_action && MainActivity.this.mediaState.canToggle) {
                            MainActivity.this.bridge.playPause();
                            return true;
                        }
                        if (i == R.id.cover_previous_action && MainActivity.this.mediaState.canPrevious) {
                            MainActivity.this.bridge.previous();
                            return true;
                        }
                        if (i == R.id.cover_next_action && MainActivity.this.mediaState.canNext) {
                            MainActivity.this.bridge.next();
                            return true;
                        }
                    }
                    return super.performAccessibilityAction(view, i, bundle);
                }
            });
            ImageButton imageButtonControl = control(2, "Faixa anterior", false);
            this.previous = imageButtonControl;
            ImageButton imageButtonControl2 = control(0, "Reproduzir", true);
            this.play = imageButtonControl2;
            ImageButton imageButtonControl3 = control(3, "Faixa seguinte", false);
            this.next = imageButtonControl3;
            this.transportButtons = new ImageButton[]{imageButtonControl, imageButtonControl2, imageButtonControl3};
            imageButtonControl.setOnClickListener(new View.OnClickListener() {
                @Override
                public final void onClick(View view) {
                    PlayerLayout.this.onPreviousClick(view);
                }
            });
            imageButtonControl2.setOnClickListener(new View.OnClickListener() {
                @Override
                public final void onClick(View view) {
                    PlayerLayout.this.onPlayClick(view);
                }
            });
            imageButtonControl3.setOnClickListener(new View.OnClickListener() {
                @Override
                public final void onClick(View view) {
                    PlayerLayout.this.onNextClick(view);
                }
            });
            LinearLayout linearLayout = new LinearLayout(MainActivity.this);
            this.metadata = linearLayout;
            linearLayout.setOrientation(1);
            linearLayout.setGravity(17);
            TextView textView = new TextView(MainActivity.this);
            this.titleText = textView;
            TextView textView2 = new TextView(MainActivity.this);
            this.artistText = textView2;
            textView.setId(R.id.player_title);
            textView2.setId(R.id.player_artist);
            textView.setGravity(17);
            textView2.setGravity(17);
            textView.setMaxLines(2);
            textView2.setMaxLines(1);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView2.setEllipsize(TextUtils.TruncateAt.END);
            linearLayout.addView(textView, new LinearLayout.LayoutParams(-1, -2));
            linearLayout.addView(textView2, new LinearLayout.LayoutParams(-1, -2));
            addView(linearLayout);
            applyAppearance();
        }

        void toggleFromCover() { onPlayClick(null); }

        void previousFromCover() { if (mediaState.canPrevious) onPreviousClick(null); }

        void nextFromCover() { if (mediaState.canNext) onNextClick(null); }

        void onPreviousClick(View view) {
            boolean embedded = videoMode;
            leaveVideo(false);
            bridge.previous();
            if (embedded) bridge.play();
        }

        void onPlayClick(View view) {
            if (videoMode && !currentVideoId.isEmpty()) {
                if (video.isPlaying()) video.pause();
                else video.play(currentVideoId, video.getPositionMs());
                updatePlayButton();
            } else if (mediaState.canToggle) bridge.playPause();
        }

        void onNextClick(View view) {
            boolean embedded = videoMode;
            leaveVideo(false);
            bridge.next();
            if (embedded) bridge.play();
        }

        private LinearLayout bar(int i, SeekBar seekBar) {
            LinearLayout linearLayout = new LinearLayout(MainActivity.this);
            linearLayout.setGravity(16);
            ImageView imageView = new ImageView(MainActivity.this);
            imageView.setImageDrawable(MainActivity.this.new SymbolDrawable(i, -5592406));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setPadding(MainActivity.this.dp(4.0f), MainActivity.this.dp(4.0f), MainActivity.this.dp(4.0f), MainActivity.this.dp(4.0f));
            imageView.setImportantForAccessibility(2);
            linearLayout.addView(imageView, new LinearLayout.LayoutParams(MainActivity.this.dp(24.0f), MainActivity.this.dp(32.0f)));
            linearLayout.addView(seekBar, new LinearLayout.LayoutParams(0, -1, 1.0f));
            return linearLayout;
        }

        void updateMetadata(String str, String str2) {
            TextView textView = this.titleText;
            if (TextUtils.isEmpty(str)) {
                str = "Sem título";
            }
            textView.setText(str);
            this.artistText.setText(str2 == null ? "" : str2);
            this.artistText.setVisibility((!MainActivity.this.appearance.showArtist || TextUtils.isEmpty(str2)) ? 8 : 0);
            requestLayout();
        }

        void applyAppearance() {
            if (!appearance.showVideoSwitcher) leaveVideo(false);
            mode.setVisibility(appearance.showVideoSwitcher ? View.VISIBLE : View.GONE);
            lyrics.setVisibility(appearance.showLyrics ? View.VISIBLE : View.GONE);
            lyrics.setStyle(FontCatalog.get(MainActivity.this, appearance.lyricsFontFamily, appearance.lyricsFontStyle),
                    appearance.lyricsSize, appearance.lyricsColor, appearance.lyricsSpacing,
                    appearance.lyricsAlignment, appearance.lyricsTapToSeek);
            this.artwork.applyAppearance(MainActivity.this.appearance);
            MinimalSlider.tint(this.brightness, MainActivity.this.appearance.brightnessColor);
            MinimalSlider.tint(this.volume, MainActivity.this.appearance.volumeColor);
            this.brightnessRow.setVisibility(MainActivity.this.appearance.showBrightness ? 0 : 8);
            this.volumeRow.setVisibility(MainActivity.this.appearance.showVolume ? 0 : 8);
            this.brightness.setVisibility(MainActivity.this.appearance.showBrightness ? 0 : 8);
            this.volume.setVisibility(MainActivity.this.appearance.showVolume ? 0 : 8);
            float f = 1.0f - (MainActivity.this.appearance.controlsDim / 100.0f);
            this.brightnessRow.setAlpha(f);
            this.volumeRow.setAlpha(f);
            ImageButton imageButton = this.previous;
            MainActivity mainActivity = MainActivity.this;
            imageButton.setImageDrawable(mainActivity.new SymbolDrawable(2, mainActivity.appearance.controlsColor));
            ImageButton imageButton2 = this.next;
            MainActivity mainActivity2 = MainActivity.this;
            imageButton2.setImageDrawable(mainActivity2.new SymbolDrawable(3, mainActivity2.appearance.controlsColor));
            ImageButton imageButton3 = this.play;
            MainActivity mainActivity3 = MainActivity.this;
            imageButton3.setBackground(mainActivity3.roundedBackground(mainActivity3.appearance.controlsColor, MainActivity.this.dp(100.0f)));
            ImageButton imageButton4 = this.play;
            MainActivity mainActivity4 = MainActivity.this;
            imageButton4.setImageDrawable(mainActivity4.new SymbolDrawable(mainActivity4.displayedPlaying ? 1 : 0, MainActivity.this.controlIconColor()));
            boolean z = MainActivity.this.appearance.showControls || !MainActivity.this.appearance.coverGestures;
            for (ImageButton imageButton5 : this.transportButtons) {
                imageButton5.setVisibility(z ? 0 : 8);
                imageButton5.setAlpha((imageButton5.isEnabled() ? 1.0f : 0.32f) * f);
            }
            this.artwork.setImageAlpha(Math.round((1.0f - (MainActivity.this.appearance.coverDim / 100.0f)) * 255.0f));
            this.coverGestures.setEnabled(MainActivity.this.appearance.coverGestures);
            this.artwork.setImportantForAccessibility(MainActivity.this.appearance.coverGestures ? 1 : 0);
            this.titleText.setVisibility(MainActivity.this.appearance.showTitle ? 0 : 8);
            this.artistText.setVisibility((!MainActivity.this.appearance.showArtist || this.artistText.length() <= 0) ? 8 : 0);
            this.titleText.setTypeface(SettingsActivity.font(MainActivity.this, MainActivity.this.appearance));
            this.artistText.setTypeface(SettingsActivity.font(MainActivity.this, MainActivity.this.appearance));
            this.titleText.setTextSize(MainActivity.this.appearance.titleSize);
            this.artistText.setTextSize(MainActivity.this.appearance.artistSize);
            this.titleText.setTextColor(MainActivity.this.appearance.titleColor);
            this.artistText.setTextColor(MainActivity.this.appearance.artistColor);
            this.metadata.setVisibility((MainActivity.this.appearance.showTitle || MainActivity.this.appearance.showArtist) ? 0 : 8);
            requestLayout();
        }

        private ImageButton control(int i, String str, boolean z) {
            ImageButton imageButton = new ImageButton(MainActivity.this);
            imageButton.setContentDescription(str);
            imageButton.setImageDrawable(MainActivity.this.new SymbolDrawable(i, z ? -16777216 : -1));
            imageButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageButton.setPadding(MainActivity.this.dp(z ? 25.0f : 15.0f), MainActivity.this.dp(z ? 25.0f : 15.0f), MainActivity.this.dp(z ? 25.0f : 15.0f), MainActivity.this.dp(z ? 25.0f : 15.0f));
            MainActivity mainActivity = MainActivity.this;
            imageButton.setBackground(mainActivity.roundedBackground(z ? -1 : -16777216, mainActivity.dp(100.0f)));
            addView(imageButton);
            return imageButton;
        }

        @Override
        protected void onMeasure(int i, int i2) {
            int size = View.MeasureSpec.getSize(i);
            int size2 = View.MeasureSpec.getSize(i2);
            setMeasuredDimension(size, size2);
            if (appearance.showLyrics || appearance.showVideoSwitcher) {
                int infoWidth = Math.max(0, (size > size2 ? size / 2 : size) - dp(32));
                metadata.measure(exact(infoWidth), MeasureSpec.makeMeasureSpec(Math.max(0, size2 / 3), MeasureSpec.AT_MOST));
                extrasPlan = PlayerExtrasLayout.create(size, size2, getResources().getDisplayMetrics().density,
                        appearance.albumPercent, play.getVisibility() != View.GONE, appearance.showBrightness,
                        appearance.showVolume, metadata.getVisibility() == View.GONE ? 0 : metadata.getMeasuredHeight(),
                        appearance.textPosition, appearance.showVideoSwitcher, appearance.showLyrics, appearance.lyricsHeight);
                metadata.measure(exact(extrasPlan.metadata.width), MeasureSpec.makeMeasureSpec(Math.max(0, size2 / 3), MeasureSpec.AT_MOST));
                extrasPlan = PlayerExtrasLayout.create(size, size2, getResources().getDisplayMetrics().density,
                        appearance.albumPercent, play.getVisibility() != View.GONE, appearance.showBrightness,
                        appearance.showVolume, metadata.getVisibility() == View.GONE ? 0 : metadata.getMeasuredHeight(),
                        appearance.textPosition, appearance.showVideoSwitcher, appearance.showLyrics, appearance.lyricsHeight);
                measurePlan(artwork, extrasPlan.art);
                measurePlan(video, extrasPlan.art);
                measurePlan(mode, extrasPlan.mode);
                measurePlan(lyrics, extrasPlan.lyrics);
                measurePlan(metadata, extrasPlan.metadata);
                measurePlan(brightnessRow, extrasPlan.brightness);
                measurePlan(volumeRow, extrasPlan.volume);
                measurePlan(previous, extrasPlan.previous);
                measurePlan(play, extrasPlan.play);
                measurePlan(next, extrasPlan.next);
                return;
            }
            extrasPlan = null;
            this.landscape = size > size2 && this.play.getVisibility() != 8;
            this.sideSize = MainActivity.this.dp(64.0f);
            this.playSize = MainActivity.this.dp(88.0f);
            this.barHeight = (MainActivity.this.appearance.showBrightness ? MainActivity.this.dp(48.0f) : 0) + (MainActivity.this.appearance.showVolume ? MainActivity.this.dp(48.0f) : 0);
            int iMax = Math.max(MainActivity.this.dp(80.0f), this.landscape ? Math.min(size - MainActivity.this.dp(220.0f), Math.round(size * 0.55f)) : size);
            int iMax2 = Math.max(MainActivity.this.dp(48.0f), (MainActivity.this.appearance.textPosition == 2 ? size : iMax) - MainActivity.this.dp(32.0f));
            this.infoWidth = iMax2;
            this.metadata.measure(exact(iMax2), View.MeasureSpec.makeMeasureSpec(Math.max(0, size2 / 2), Integer.MIN_VALUE));
            this.metadataHeight = this.metadata.getVisibility() == 8 ? 0 : this.metadata.getMeasuredHeight();
            int i3 = MainActivity.this.appearance.textPosition == 2 ? 0 : this.metadataHeight;
            int iDp = MainActivity.this.appearance.textPosition == 2 ? this.metadataHeight + MainActivity.this.dp(16.0f) : 0;
            int iDp2 = this.play.getVisibility() == 8 ? 0 : MainActivity.this.dp(112.0f);
            if (this.landscape) {
                int iMax3 = Math.max(MainActivity.this.dp(36.0f), Math.min(Math.round((((size * 0.49f) - MainActivity.this.dp(40.0f)) * MainActivity.this.appearance.albumPercent) / 85.0f), Math.min(iMax - MainActivity.this.dp(40.0f), (((size2 - MainActivity.this.dp(40.0f)) - this.barHeight) - i3) - iDp)));
                this.artSize = iMax3;
                this.groupHeight = iMax3 + this.barHeight + i3;
                this.artTop = Math.max(MainActivity.this.dp(12.0f), ((size2 - iDp) - this.groupHeight) / 2) + this.barHeight + (MainActivity.this.appearance.textPosition == 0 ? this.metadataHeight : 0);
                this.artLeft = (iMax - this.artSize) / 2;
                if (size - iMax < MainActivity.this.dp(280.0f)) {
                    this.sideSize = MainActivity.this.dp(48.0f);
                    this.playSize = MainActivity.this.dp(72.0f);
                }
            } else {
                this.artTop = MainActivity.this.dp(20.0f) + this.barHeight + (MainActivity.this.appearance.textPosition == 0 ? this.metadataHeight : 0);
                int iMax4 = Math.max(MainActivity.this.dp(36.0f), Math.min(size - MainActivity.this.dp(32.0f), Math.min(Math.round((MainActivity.this.appearance.albumPercent * size) / 100.0f), ((((size2 - this.artTop) - iDp2) - iDp) - (MainActivity.this.appearance.textPosition == 1 ? this.metadataHeight : 0)) - MainActivity.this.dp(24.0f))));
                this.artSize = iMax4;
                this.artLeft = (size - iMax4) / 2;
            }
            setButtonPadding(play, playSize);
            setButtonPadding(previous, sideSize);
            setButtonPadding(next, sideSize);
            this.artwork.measure(exact(this.artSize), exact(this.artSize));
            ImageButton[] imageButtonArr = this.transportButtons;
            int length = imageButtonArr.length;
            for (int i4 = 0; i4 < length; i4++) {
                ImageButton imageButton = imageButtonArr[i4];
                imageButton.measure(exact(imageButton == this.play ? this.playSize : this.sideSize), exact(imageButton == this.play ? this.playSize : this.sideSize));
            }
            this.brightnessRow.measure(exact(this.artSize), exact(MainActivity.this.dp(48.0f)));
            this.volumeRow.measure(exact(this.artSize), exact(MainActivity.this.dp(48.0f)));
        }

        private void measurePlan(View view, PlayerExtrasLayout.Rect rect) {
            setButtonPadding(view, Math.min(rect.width, rect.height));
            view.measure(exact(rect.width), exact(rect.height));
        }

        private void setButtonPadding(View view, int size) {
            if (view != play && view != previous && view != next) return;
            int padding = Math.max(0, Math.round(size * (view == play ? 25f / 88f : 15f / 64f)));
            view.setPadding(padding, padding, padding, padding);
        }

        private void layoutPlan(View view, PlayerExtrasLayout.Rect rect) {
            view.layout(rect.left, rect.top, rect.right(), rect.bottom());
        }

        private int exact(int i) {
            return View.MeasureSpec.makeMeasureSpec(Math.max(0, i), 1073741824);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (extrasPlan != null) {
                layoutPlan(artwork, extrasPlan.art);
                layoutPlan(video, extrasPlan.art);
                layoutPlan(mode, extrasPlan.mode);
                layoutPlan(lyrics, extrasPlan.lyrics);
                layoutPlan(metadata, extrasPlan.metadata);
                layoutPlan(brightnessRow, extrasPlan.brightness);
                layoutPlan(volumeRow, extrasPlan.volume);
                layoutPlan(previous, extrasPlan.previous);
                layoutPlan(play, extrasPlan.play);
                layoutPlan(next, extrasPlan.next);
                return;
            }
            int width = right - left;
            int height = bottom - top;
            artwork.layout(artLeft, artTop, artLeft + artSize, artTop + artSize);
            int barTop = artTop - barHeight;
            if (appearance.showBrightness) {
                brightnessRow.layout(artLeft, barTop, artLeft + artSize, barTop + dp(48));
                barTop += dp(48);
            }
            if (appearance.showVolume) {
                volumeRow.layout(artLeft, barTop, artLeft + artSize, barTop + dp(48));
            }
            int infoLeft = !landscape || appearance.textPosition == 2
                    ? (width - infoWidth) / 2
                    : Math.max(0, artLeft + (artSize - infoWidth) / 2);
            int infoTop;
            if (appearance.textPosition == 0) {
                infoTop = artTop - barHeight - metadataHeight;
            } else if (appearance.textPosition == 1) {
                infoTop = artTop + artSize;
            } else {
                infoTop = height - dp(16) - metadataHeight;
            }
            metadata.layout(infoLeft, infoTop, infoLeft + infoWidth, infoTop + metadataHeight);
            if (play.getVisibility() == View.GONE) return;
            if (landscape) {
                int controlsLeft = Math.max(artLeft + artSize + dp(20), Math.round(width * .55f));
                int centerX = controlsLeft + (width - dp(16) - controlsLeft) / 2;
                int offset = Math.min(dp(94), Math.max(sideSize, (width - controlsLeft - sideSize) / 3));
                int centerY = (height - (appearance.textPosition == 2 ? metadataHeight : 0)) / 2;
                layoutCentered(previous, centerX - offset, centerY, sideSize);
                layoutCentered(play, centerX, centerY, playSize);
                layoutCentered(next, centerX + offset, centerY, sideSize);
            } else {
                int controlsTop = artTop + artSize
                        + (appearance.textPosition == 1 ? metadataHeight : 0) + dp(16);
                int controlsBottom = appearance.textPosition == 2 ? infoTop - dp(12) : height - dp(16);
                int centerY = Math.max(controlsTop + playSize / 2, (controlsTop + controlsBottom) / 2);
                int offset = Math.min(dp(100), (width - sideSize - dp(32)) / 2);
                layoutCentered(previous, width / 2 - offset, centerY, sideSize);
                layoutCentered(play, width / 2, centerY, playSize);
                layoutCentered(next, width / 2 + offset, centerY, sideSize);
            }
        }

        private void layoutCentered(View view, int i, int i2, int i3) {
            int i4 = i3 / 2;
            view.layout(i - i4, i2 - i4, i + i4, i2 + i4);
        }
    }

    private final class SymbolDrawable extends Drawable {
        static final int NEXT = 3;
        static final int NOTE = 4;
        static final int PAUSE = 1;
        static final int PLAY = 0;
        static final int PREVIOUS = 2;
        static final int SUN = 5;
        static final int VOLUME = 6;
        private final Paint paint;
        private final Path path;
        private final int symbol;

        @Override
        public int getOpacity() {
            return -3;
        }

        SymbolDrawable(int i, int i2) {
            Paint paint = new Paint(PAUSE);
            this.paint = paint;
            this.path = new Path();
            this.symbol = i;
            paint.setColor(i2);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / 24.0f, bounds.height() / 24.0f);
            this.path.reset();
            this.paint.setStyle(Paint.Style.FILL);
            int i = this.symbol;
            if (i == 0) {
                this.path.moveTo(6.0f, 3.0f);
                this.path.lineTo(21.0f, 12.0f);
                this.path.lineTo(6.0f, 21.0f);
                this.path.close();
                canvas.drawPath(this.path, this.paint);
            } else if (i == PAUSE) {
                canvas.drawRoundRect(5.0f, 3.0f, 10.0f, 21.0f, 0.8f, 0.8f, this.paint);
                canvas.drawRoundRect(14.0f, 3.0f, 19.0f, 21.0f, 0.8f, 0.8f, this.paint);
            } else if (i == PREVIOUS || i == NEXT) {
                if (i == PREVIOUS) {
                    canvas.translate(24.0f, 0.0f);
                    canvas.scale(-1.0f, 1.0f);
                }
                this.path.moveTo(3.0f, 3.0f);
                this.path.lineTo(18.0f, 12.0f);
                this.path.lineTo(3.0f, 21.0f);
                this.path.close();
                canvas.drawPath(this.path, this.paint);
                canvas.drawRoundRect(19.0f, 3.0f, 21.0f, 21.0f, 0.6f, 0.6f, this.paint);
            } else if (i == SUN) {
                this.paint.setStyle(Paint.Style.STROKE);
                this.paint.setStrokeWidth(2.0f);
                canvas.drawCircle(12.0f, 12.0f, 4.0f, this.paint);
                for (int i2 = PLAY; i2 < 8; i2 += PAUSE) {
                    canvas.drawLine(12.0f, 2.0f, 12.0f, 5.0f, this.paint);
                    canvas.rotate(45.0f, 12.0f, 12.0f);
                }
            } else if (i == VOLUME) {
                this.path.moveTo(3.0f, 9.0f);
                this.path.lineTo(7.0f, 9.0f);
                this.path.lineTo(12.0f, 4.0f);
                this.path.lineTo(12.0f, 20.0f);
                this.path.lineTo(7.0f, 15.0f);
                this.path.lineTo(3.0f, 15.0f);
                this.path.close();
                canvas.drawPath(this.path, this.paint);
                this.paint.setStyle(Paint.Style.STROKE);
                this.paint.setStrokeWidth(2.0f);
                canvas.drawArc(9.0f, 5.0f, 21.0f, 19.0f, -55.0f, 110.0f, false, this.paint);
            } else {
                canvas.drawCircle(8.0f, 17.5f, 4.0f, this.paint);
                canvas.drawRect(10.0f, 4.0f, 13.0f, 18.0f, this.paint);
                this.path.moveTo(10.0f, 4.0f);
                this.path.lineTo(21.0f, 2.0f);
                this.path.lineTo(21.0f, 7.0f);
                this.path.lineTo(10.0f, 9.0f);
                this.path.close();
                canvas.drawPath(this.path, this.paint);
            }
            canvas.restore();
        }

        @Override
        public int getIntrinsicWidth() {
            return MainActivity.this.dp(this.symbol == NOTE ? 48.0f : 32.0f);
        }

        @Override
        public int getIntrinsicHeight() {
            return MainActivity.this.dp(this.symbol == NOTE ? 48.0f : 32.0f);
        }

        @Override
        public void setAlpha(int i) {
            this.paint.setAlpha(i);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            this.paint.setColorFilter(colorFilter);
            invalidateSelf();
        }
    }
}
