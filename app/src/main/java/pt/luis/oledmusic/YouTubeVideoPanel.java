package pt.luis.oledmusic;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Official YouTube embedded player. It is separate from the native music app's session. */
final class YouTubeVideoPanel extends FrameLayout {
    interface Listener {
        void onProgress(long positionMs, long durationMs, boolean playing);
        void onError(String message);
    }

    private static final String ORIGIN = "https://pt.luis.oledmusic";
    private static final String SIZE_MESSAGE = "Aumenta o tamanho da capa ou a janela para ver o vídeo";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextView status;
    private WebView webView;
    private Listener listener;
    private String videoId;
    private int generation;
    private long positionMs, durationMs, positionUpdatedAt;
    private long pendingSeekMs = -1;
    private boolean playerReady;
    private boolean requested, loaded, playing, started, destroyed;

    YouTubeVideoPanel(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        status = new TextView(context);
        status.setTextColor(0xffb9b9c1);
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        int padding = Math.round(18 * getResources().getDisplayMetrics().density);
        status.setPadding(padding, padding, padding, padding);
        status.setText("Seleciona um vídeo para começar");
        addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    void setListener(Listener listener) { this.listener = listener; }

    /** Called after an explicit user choice; never searches for or substitutes another video. */
    void play(String id, long startMs) {
        if (destroyed) return;
        if (!YouTubeVideoId.isValid(id)) {
            fail("Este tema não tem um vídeo YouTube identificado.");
            return;
        }
        unload();
        videoId = id;
        positionMs = Math.max(0, startMs);
        durationMs = 0;
        positionUpdatedAt = SystemClock.elapsedRealtime();
        started = false;
        requested = true;
        maybeLoad();
    }

    void pause() {
        if (destroyed) return;
        positionMs = getPositionMs();
        requested = false;
        unload();
        showStatus("Vídeo em pausa");
    }

    void stop() {
        if (destroyed) return;
        requested = false;
        unload();
        videoId = null;
        positionMs = durationMs = 0;
        started = false;
        showStatus("Seleciona um vídeo para começar");
    }

    void destroy() {
        if (destroyed) return;
        stop();
        destroyed = true;
        listener = null;
        disposeWebView();
    }

    long getPositionMs() {
        long position = positionMs;
        if (playing) position += Math.max(0, SystemClock.elapsedRealtime() - positionUpdatedAt);
        return durationMs > 0 ? Math.min(position, durationMs) : position;
    }

    boolean isPlaying() { return playing; }
    boolean hasStarted() { return started; }

    /** Seek does not change playback state, including when the IFrame is paused. */
    void seekTo(long targetMs) {
        if (destroyed) return;
        positionMs = Math.max(0, durationMs > 0 ? Math.min(targetMs, durationMs) : targetMs);
        positionUpdatedAt = SystemClock.elapsedRealtime();
        if (loaded && webView != null) {
            pendingSeekMs = playerReady ? -1 : positionMs;
            evaluateSeek(positionMs);
        }
    }

    private void evaluateSeek(long targetMs) {
        // A long converted to seconds is always a finite number, never executable input.
        webView.evaluateJavascript("try{if(window.player&&typeof player.seekTo==='function'){player.seekTo("
                + (targetMs / 1000.0) + ",true);}}catch(e){}", null);
    }

    private boolean enoughRoom() {
        int minimum = (int) Math.ceil(200 * getResources().getDisplayMetrics().density);
        return getWidth() >= minimum && getHeight() >= minimum;
    }

    private void maybeLoad() {
        if (destroyed || !requested || loaded || videoId == null) return;
        if (!enoughRoom()) {
            showStatus(SIZE_MESSAGE);
            return;
        }
        if (!isShown() || getWindowVisibility() != VISIBLE) return;
        if (!ensureWebView()) return;
        final int token = ++generation;
        loaded = true;
        webView.setVisibility(VISIBLE);
        status.setVisibility(GONE);
        webView.onResume();
        // The HTTPS base URL supplies the Referer required by YouTube's embedded player.
        webView.loadDataWithBaseURL(ORIGIN, html(videoId, positionMs, token), "text/html", "UTF-8", null);
    }

    private boolean ensureWebView() {
        if (webView != null) return true;
        try {
            webView = new WebView(getContext());
            webView.setBackgroundColor(Color.BLACK);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            settings.setBuiltInZoomControls(false);
            webView.setWebChromeClient(new WebChromeClient());
            webView.addJavascriptInterface(new PlayerBridge(), "OledPlayer");
            webView.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    Uri uri = request.getUrl();
                    String url = uri.toString();
                    if ("about:blank".equals(url) || ORIGIN.equals(url) || (ORIGIN + "/").equals(url)) return false;
                    // Subframe navigation to the official player is needed by the IFrame API.
                    String host = uri.getHost();
                    boolean official = "www.youtube.com".equalsIgnoreCase(host)
                            || "www.youtube-nocookie.com".equalsIgnoreCase(host);
                    if (!request.isForMainFrame() && official && "https".equalsIgnoreCase(uri.getScheme())
                            && uri.getPath() != null && uri.getPath().startsWith("/embed/")) return false;
                    if (request.hasGesture()) openExternal(uri);
                    return true;
                }

                @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (loaded && request.isForMainFrame()) fail("Não foi possível ligar ao YouTube. Verifica a ligação à Internet.");
                }

                @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                    handler.cancel();
                    if (loaded) fail("Não foi possível estabelecer uma ligação segura ao YouTube.");
                }

                @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    loaded = false;
                    disposeWebView();
                    fail("O leitor de vídeo foi interrompido. Volta a selecionar Vídeo para tentar novamente.");
                    return true;
                }
            });
            addView(webView, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            return true;
        } catch (RuntimeException | LinkageError unavailable) {
            disposeWebView();
            fail("O Android System WebView não está disponível. Ativa ou atualiza-o para ver vídeos.");
            return false;
        }
    }

    private void openExternal(Uri uri) {
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || uri.getHost() == null || uri.getUserInfo() != null) return;
        try {
            getContext().startActivity(new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            if (listener != null) listener.onError("Não existe uma aplicação disponível para abrir esta ligação.");
        }
    }

    private void showStatus(String message) {
        status.setText(message);
        status.setVisibility(VISIBLE);
        if (webView != null) webView.setVisibility(GONE);
    }

    private void unload() {
        ++generation;
        loaded = false;
        playerReady = false;
        pendingSeekMs = -1;
        playing = false;
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.onPause();
        }
    }

    private void disposeWebView() {
        if (webView == null) return;
        WebView old = webView;
        webView = null;
        removeView(old);
        try {
            old.removeJavascriptInterface("OledPlayer");
            old.stopLoading();
            old.destroy();
        } catch (RuntimeException ignored) {
            // Some missing/terminated WebView providers also reject cleanup calls.
        }
    }

    private void fail(String message) {
        requested = false;
        positionMs = getPositionMs();
        unload();
        showStatus(message);
        if (listener != null) listener.onError(message);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (status == null || destroyed) return;
        if (loaded && !enoughRoom()) {
            positionMs = getPositionMs();
            unload();
            showStatus(SIZE_MESSAGE);
        } else maybeLoad();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        maybeLoad();
    }

    @Override protected void onDetachedFromWindow() {
        pause();
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (status == null || destroyed) return;
        if (!isShown() && loaded) pause();
        else maybeLoad();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (status == null || destroyed) return;
        if (visibility != VISIBLE && loaded) pause();
        else if (visibility == VISIBLE) maybeLoad();
    }

    private final class PlayerBridge {
        @JavascriptInterface public void ready(final int token) {
            main.post(() -> {
                if (destroyed || !requested || !loaded || token != generation) return;
                playerReady = true;
                if (pendingSeekMs >= 0 && webView != null) {
                    evaluateSeek(pendingSeekMs);
                    pendingSeekMs = -1;
                }
            });
        }

        @JavascriptInterface public void progress(final int token, final double seconds,
                                                   final double duration, final int state) {
            main.post(() -> {
                if (destroyed || !requested || !loaded || token != generation) return;
                if (Double.isNaN(seconds) || Double.isInfinite(seconds)
                        || Double.isNaN(duration) || Double.isInfinite(duration)) return;
                positionMs = Math.max(0, (long) (seconds * 1000));
                durationMs = Math.max(0, (long) (duration * 1000));
                positionUpdatedAt = SystemClock.elapsedRealtime();
                playing = state == 1;
                if (playing) started = true;
                if (listener != null) listener.onProgress(positionMs, durationMs, playing);
            });
        }

        @JavascriptInterface public void error(final int token, final int code) {
            main.post(() -> {
                if (destroyed || !requested || !loaded || token != generation) return;
                String message;
                if (code == 101 || code == 150) message = "Este vídeo não permite reprodução dentro da app.";
                else if (code == 153) message = "O YouTube não conseguiu validar este leitor de vídeo (erro 153).";
                else if (code == 100) message = "Este vídeo está indisponível ou é privado.";
                else if (code == -1 || code == -2) message = "Não foi possível ligar ao leitor YouTube. Verifica a ligação à Internet.";
                else message = "Não foi possível reproduzir este vídeo (erro " + code + ").";
                fail(message);
            });
        }
    }

    private static String html(String id, long startMs, int token) {
        // Only an already-validated ID and numeric values enter this document.
        double startSeconds = startMs / 1000.0;
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body,#player{margin:0;width:100%;height:100%;background:#000;overflow:hidden}</style>"
                + "</head><body><div id='player'></div><script>var player,timer,token=" + token + ";"
                + "var watchdog=setTimeout(function(){OledPlayer.error(token,-2);},25000);"
                + "function report(){try{OledPlayer.progress(token,player.getCurrentTime()||0,player.getDuration()||0,player.getPlayerState());}catch(e){}}"
                + "function onYouTubeIframeAPIReady(){player=new YT.Player('player',{width:'100%',height:'100%',videoId:'" + id + "',"
                + "playerVars:{controls:1,playsinline:1,rel:0,fs:0,enablejsapi:1,origin:'" + ORIGIN + "'},"
                + "events:{onReady:function(){clearTimeout(watchdog);player.seekTo(" + startSeconds + ",true);player.playVideo();OledPlayer.ready(token);report();timer=setInterval(report,500);},"
                + "onStateChange:function(){report();},onError:function(e){OledPlayer.error(token,e.data);}}});}"
                + "window.addEventListener('pagehide',function(){clearInterval(timer);clearTimeout(watchdog);try{player.pauseVideo();}catch(e){}});"
                + "</script><script src='https://www.youtube.com/iframe_api' onerror='OledPlayer.error(token,-1)'></script></body></html>";
    }
}
