package pt.luis.oledmusic;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public final class SettingsActivity extends Activity implements ColorPickerDialog.Listener {
    private Switch artist;
    private View artistOptions;
    private View borderOptions;
    private AlbumArtworkView borderPreview;
    private TextView artistPreview;
    private Switch brightness;
    private View brightnessOptions;
    private Switch controls;
    private Button durationButton;
    private Switch gestures;
    private AppPreferences preferences;
    private View textOptions;
    private Button timerCancel;
    private Button timerPrecision;
    private TextView timerStatus;
    private Switch title;
    private View titleOptions;
    private TextView titlePreview;
    private Switch volume;
    private View volumeOptions;
    private final List<Section> sections = new ArrayList();
    private int expanded = -1;
    private boolean building = true;
    private int timerMinutes = 30;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            SettingsActivity.this.updateTimer();
            SettingsActivity.this.handler.postDelayed(this, 1000L);
        }
    };

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Serializable serializable = bundle == null ? null : bundle.getSerializable("draft");
        this.preferences = serializable instanceof AppPreferences ? (AppPreferences) serializable : AppPreferences.load(this);
        if (bundle != null) {
            this.timerMinutes = bundle.getInt("minutes", 30);
        }
        getWindow().setStatusBarColor(-16777216);
        getWindow().setNavigationBarColor(-16777216);
        getWindow().setBackgroundDrawable(new ColorDrawable(-16777216));
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        final LinearLayout linearLayoutColumn = column();
        linearLayoutColumn.setBackgroundColor(-16777216);
        linearLayoutColumn.setPadding(dp(20), 0, dp(20), 0);
        linearLayoutColumn.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public final WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                return SettingsActivity.this.applySettingsInsets(linearLayoutColumn, view, windowInsets);
            }
        });
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setGravity(16);
        Button button = button("‹", false);
        button.setContentDescription("Voltar sem guardar");
        linearLayout.addView(button, new LinearLayout.LayoutParams(dp(48), dp(56)));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                SettingsActivity.this.closeSettings(view);
            }
        });
        TextView textViewText = text("Definições", 24, -1);
        textViewText.setTypeface(Typeface.DEFAULT, 1);
        linearLayout.addView(textViewText, new LinearLayout.LayoutParams(0, -2, 1.0f));
        Button button2 = button("Guardar", true);
        button2.setTextSize(14.0f);
        linearLayout.addView(button2, new LinearLayout.LayoutParams(dp(96), dp(48)));
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                SettingsActivity.this.saveAndClose(view);
            }
        });
        linearLayoutColumn.addView(linearLayout, new LinearLayout.LayoutParams(-1, dp(76)));
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        LinearLayout linearLayoutColumn2 = column();
        linearLayoutColumn2.setPadding(0, 0, 0, dp(24));
        scrollView.addView(linearLayoutColumn2);
        linearLayoutColumn.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        setContentView(linearLayoutColumn);
        Section section = section(linearLayoutColumn2, "Capa", R.id.cover_section_header, R.id.cover_section_content, new Supplier() {
            @Override
            public final Object get() {
                return SettingsActivity.this.coverSummary();
            }
        });
        slider(section.body, "Tamanho", R.id.album_size_slider, 50, 100, this.preferences.albumPercent, "%", new IntConsumer() {
            @Override
            public final void accept(int i) {
                SettingsActivity.this.setAlbumPercent(i);
            }
        });
        slider(section.body, "Escurecer só a capa", R.id.cover_dim_slider, 0, 90, this.preferences.coverDim, "%", new IntConsumer() {
            @Override
            public final void accept(int i) {
                SettingsActivity.this.setCoverDim(i);
            }
        });
        this.gestures = toggle(section.body, "Gestos na capa", R.id.cover_gestures_switch, this.preferences.coverGestures, new Consumer() {
            @Override
            public final void accept(Object obj) {
                SettingsActivity.this.setCoverGestures((Boolean) obj);
            }
        });
        help(section.body, "Dois toques: reproduzir/pausar. Deslizar para a esquerda: seguinte. Para a direita: anterior. Três dedos continuam a abrir as definições.");
        help(section.body, "A imagem preenche sempre o quadrado com recorte central, sem deformar as proporções.");
        borderPreview = new AlbumArtworkView(this);
        borderPreview.setContentDescription("Pré-visualização da borda e dos cantos da capa");
        GradientDrawable sample = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF354E68, 0xFF895C86, 0xFFE0A77A});
        Bitmap sampleBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Canvas sampleCanvas = new Canvas(sampleBitmap);
        sample.setBounds(0, 0, 128, 128);
        sample.draw(sampleCanvas);
        borderPreview.setArtwork(sampleBitmap);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(160), dp(160));
        previewParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        previewParams.topMargin = dp(16);
        section.body.addView(borderPreview, previewParams);
        toggle(section.body, "Mostrar borda na capa", R.id.album_border_switch,
                preferences.showAlbumBorder, value -> preferences.showAlbumBorder = value);
        LinearLayout borderColumn = column();
        borderOptions = borderColumn;
        add(section.body, borderColumn, 4);
        toggle(borderColumn, "Cor automática pela capa", R.id.album_border_auto_color_switch,
                preferences.autoAlbumBorderColor, value -> preferences.autoAlbumBorderColor = value);
        help(borderColumn, "Escolhe a cor mais frequente que ocupe até 40% da capa e se distinga das suas margens. Se não houver uma cor adequada, usa uma cor de contraste.");
        color(borderColumn, "Cor da borda", R.id.album_border_color_choice, false,
                preferences.albumBorderColor, false);
        slider(borderColumn, "Espessura da borda", R.id.album_border_width_slider, 1, 24,
                preferences.albumBorderWidth, " dp", value -> preferences.albumBorderWidth = value);
        slider(section.body, "Arredondamento dos cantos", R.id.album_corner_radius_slider, 0, 100,
                preferences.albumCornerRadius, " dp", value -> preferences.albumCornerRadius = value);
        Section section2 = section(linearLayoutColumn2, "Controlos", R.id.controls_section_header, R.id.controls_section_content, new Supplier() {
            @Override
            public final Object get() {
                return SettingsActivity.this.controlsSummary();
            }
        });
        this.controls = toggle(section2.body, "Mostrar botões", R.id.show_controls_switch, this.preferences.showControls, new Consumer() {
            @Override
            public final void accept(Object obj) {
                SettingsActivity.this.setShowControls((Boolean) obj);
            }
        });
        help(section2.body, "Para ocultar os botões, ativa primeiro os gestos na capa. A cor e o escurecimento não alteram o brilho do telemóvel.");
        color(section2.body, "Cor dos controlos", R.id.controls_color_choice, true, this.preferences.controlsColor, false);
        slider(section2.body, "Escurecer controlos e barras", R.id.controls_dim_slider, 0, 85, this.preferences.controlsDim, "%", new IntConsumer() {
            @Override
            public final void accept(int i) {
                SettingsActivity.this.setControlsDim(i);
            }
        });
        Section section3 = section(linearLayoutColumn2, "Faixa", R.id.track_section_header, R.id.track_section_content, new Supplier() {
            @Override
            public final Object get() {
                return SettingsActivity.this.trackSummary();
            }
        });
        this.title = toggle(section3.body, "Mostrar título", R.id.show_title_switch, this.preferences.showTitle, new Consumer() {
            @Override
            public final void accept(Object obj) {
                SettingsActivity.this.setShowTitle((Boolean) obj);
            }
        });
        this.artist = toggle(section3.body, "Mostrar artista", R.id.show_artist_switch, this.preferences.showArtist, new Consumer() {
            @Override
            public final void accept(Object obj) {
                SettingsActivity.this.setShowArtist((Boolean) obj);
            }
        });
        LinearLayout linearLayoutColumn3 = column();
        this.textOptions = linearLayoutColumn3;
        add(section3.body, linearLayoutColumn3, 8);
        choice(linearLayoutColumn3, "Posição", R.id.text_position_spinner, new String[]{"Acima da capa", "Abaixo da capa", "Rodapé"}, this.preferences.textPosition, new IntConsumer() {
            @Override
            public final void accept(int i) {
                SettingsActivity.this.setTextPosition(i);
            }
        });
        choice(linearLayoutColumn3, "Tipo de letra", R.id.font_family_spinner, new String[]{"Sans", "Serif", "Monoespaçada", "Condensada"}, this.preferences.fontFamily, new IntConsumer() {
            @Override
            public final void accept(int i) {
                SettingsActivity.this.setFontFamily(i);
            }
        });
        choice(linearLayoutColumn3, "Estilo", R.id.font_style_spinner, new String[]{"Normal", "Negrito", "Itálico", "Negrito e itálico"}, this.preferences.fontStyle, new IntConsumer() {
            @Override
            public final void accept(int i) {
                SettingsActivity.this.setFontStyle(i);
            }
        });
        LinearLayout linearLayoutColumn4 = column();
        this.titleOptions = linearLayoutColumn4;
        add(linearLayoutColumn3, linearLayoutColumn4, 12);
        slider(linearLayoutColumn4, "Tamanho do título", R.id.title_size_slider, 12, 36, this.preferences.titleSize, " sp", new IntConsumer() {
            @Override
            public final void accept(int i) {
                SettingsActivity.this.setTitleSize(i);
            }
        });
        color(linearLayoutColumn4, "Cor do título", R.id.title_color_choice, false, this.preferences.titleColor, true);
        LinearLayout linearLayoutColumn5 = column();
        this.artistOptions = linearLayoutColumn5;
        add(linearLayoutColumn3, linearLayoutColumn5, 12);
        slider(linearLayoutColumn5, "Tamanho do artista", R.id.artist_size_slider, 12, 32, this.preferences.artistSize, " sp", new IntConsumer() {
            @Override
            public final void accept(int i) {
                SettingsActivity.this.setArtistSize(i);
            }
        });
        color(linearLayoutColumn5, "Cor do artista", R.id.artist_color_choice, false, this.preferences.artistColor, true);
        this.titlePreview = text("Nome da música", 20, this.preferences.titleColor);
        this.artistPreview = text("Nome do artista", 16, this.preferences.artistColor);
        this.titlePreview.setGravity(17);
        this.artistPreview.setGravity(17);
        add(linearLayoutColumn3, this.titlePreview, 16);
        add(linearLayoutColumn3, this.artistPreview, 4);
        help(section3.body, "O artista depende dos metadados publicados pelo player em uso.");
        Section section4 = section(linearLayoutColumn2, "Barras", R.id.bars_section_header, R.id.bars_section_content, new Supplier() {
            @Override
            public final Object get() {
                return SettingsActivity.this.barsSummary();
            }
        });
        this.brightness = toggle(section4.body, "Mostrar brilho", R.id.show_brightness_switch, this.preferences.showBrightness, new Consumer() {
            @Override
            public final void accept(Object obj) {
                SettingsActivity.this.setShowBrightness((Boolean) obj);
            }
        });
        LinearLayout linearLayoutColumn6 = column();
        this.brightnessOptions = linearLayoutColumn6;
        add(section4.body, linearLayoutColumn6, 4);
        color(linearLayoutColumn6, "Cor do brilho", R.id.brightness_color_choice, false, this.preferences.brightnessColor, false);
        help(linearLayoutColumn6, "Altera o brilho de todo o telemóvel e seleciona brilho manual. O Android pede autorização na primeira utilização.");
        this.volume = toggle(section4.body, "Mostrar volume", R.id.show_volume_switch, this.preferences.showVolume, new Consumer() {
            @Override
            public final void accept(Object obj) {
                SettingsActivity.this.setShowVolume((Boolean) obj);
            }
        });
        LinearLayout linearLayoutColumn7 = column();
        this.volumeOptions = linearLayoutColumn7;
        add(section4.body, linearLayoutColumn7, 4);
        color(linearLayoutColumn7, "Cor do volume", R.id.volume_color_choice, false, this.preferences.volumeColor, false);
        help(linearLayoutColumn7, "Volume multimédia, incluindo o dispositivo de áudio ligado. Não altera o volume das chamadas nem dos alarmes.");
        Section section5 = section(linearLayoutColumn2, "Temporizador", R.id.timer_section_header, R.id.timer_section_content, new Supplier() {
            @Override
            public final Object get() {
                return SettingsActivity.this.timerSummary();
            }
        });
        this.timerStatus = text("", 18, -1);
        add(section5.body, this.timerStatus, 0);
        LinearLayout linearLayout2 = new LinearLayout(this);
        int[] iArr = {15, 30, 60};
        for (int i = 0; i < 3; i++) {
            final int i2 = iArr[i];
            Button button3 = button(i2 + " min", false);
            linearLayout2.addView(button3, new LinearLayout.LayoutParams(0, dp(48), 1.0f));
            button3.setOnClickListener(new View.OnClickListener() {
                @Override
                public final void onClick(View view) {
                    SettingsActivity.this.chooseTimerPreset(i2, view);
                }
            });
        }
        add(section5.body, linearLayout2, 12);
        Button button4 = button("", false);
        this.durationButton = button4;
        button4.setId(R.id.timer_duration_button);
        add(section5.body, this.durationButton, 0);
        this.durationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                SettingsActivity.this.openTimerDurationPicker(view);
            }
        });
        Button button5 = button("Iniciar temporizador", true);
        button5.setId(R.id.timer_start_button);
        add(section5.body, button5, 12);
        button5.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                SettingsActivity.this.startTimer(view);
            }
        });
        Button button6 = button("Cancelar temporizador", false);
        this.timerCancel = button6;
        button6.setId(R.id.timer_cancel_button);
        add(section5.body, this.timerCancel, 4);
        this.timerCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                SettingsActivity.this.cancelTimer(view);
            }
        });
        this.timerPrecision = button("Permitir alarmes precisos", false);
        add(section5.body, this.timerPrecision, 4);
        this.timerPrecision.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                SettingsActivity.this.requestExactAlarms(view);
            }
        });
        help(section5.body, "Pausa o player em uso no fim, mesmo com o ecrã apagado. Sem autorização para alarmes precisos, o Android pode atrasar o disparo. Iniciar e cancelar têm efeito imediato. Reiniciar o telemóvel cancela o temporizador.");
        Section section6 = section(linearLayoutColumn2, "Bloqueio", R.id.lock_section_header, R.id.lock_section_content, new Supplier() {
            @Override
            public final Object get() {
                return SettingsActivity.this.lockScreenSummary();
            }
        });
        toggle(section6.body, "Mostrar leitor no ecrã bloqueado", R.id.show_on_lock_screen_switch, this.preferences.showOnLockScreen, new Consumer() {
            @Override
            public final void accept(Object obj) {
                SettingsActivity.this.setShowOnLockScreen((Boolean) obj);
            }
        });
        help(section6.body, "Com a app aberta e uma sessão ligada, podes usar o leitor sem desbloquear. As definições continuam a pedir autenticação.");
        this.building = false;
        refresh();
        updateDuration();
        updateTimer();
        expand(bundle != null ? bundle.getInt("section", -1) : -1);
    }

    WindowInsets applySettingsInsets(LinearLayout linearLayout, View view, WindowInsets windowInsets) {
        int systemWindowInsetLeft;
        int systemWindowInsetTop;
        int systemWindowInsetRight;
        int systemWindowInsetBottom;
        if (Build.VERSION.SDK_INT >= 30) {
            Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            systemWindowInsetLeft = insets.left;
            systemWindowInsetTop = insets.top;
            systemWindowInsetRight = insets.right;
            systemWindowInsetBottom = insets.bottom;
        } else {
            systemWindowInsetLeft = windowInsets.getSystemWindowInsetLeft();
            systemWindowInsetTop = windowInsets.getSystemWindowInsetTop();
            systemWindowInsetRight = windowInsets.getSystemWindowInsetRight();
            systemWindowInsetBottom = windowInsets.getSystemWindowInsetBottom();
        }
        linearLayout.setPadding(dp(20) + systemWindowInsetLeft, systemWindowInsetTop, dp(20) + systemWindowInsetRight, systemWindowInsetBottom);
        return windowInsets;
    }

    void closeSettings(View view) {
        finish();
    }

    void saveAndClose(View view) {
        this.preferences.save(this);
        finish();
    }

    String coverSummary() {
        return this.preferences.albumPercent + "% · escurecimento " + this.preferences.coverDim + "%";
    }

    void setAlbumPercent(int i) {
        this.preferences.albumPercent = i;
    }

    void setCoverDim(int i) {
        this.preferences.coverDim = i;
    }

    void setCoverGestures(Boolean bool) {
        this.preferences.coverGestures = bool.booleanValue();
        if (bool.booleanValue()) {
            return;
        }
        this.preferences.showControls = true;
        Switch r3 = this.controls;
        if (r3 != null) {
            r3.setChecked(true);
        }
    }

    String controlsSummary() {
        return (this.preferences.showControls ? "Botões visíveis" : "Só gestos") + " · escurecimento " + this.preferences.controlsDim + "%";
    }

    void setShowControls(Boolean bool) {
        this.preferences.showControls = bool.booleanValue() || !this.preferences.coverGestures;
    }

    void setControlsDim(int i) {
        this.preferences.controlsDim = i;
    }

    String trackSummary() {
        if (this.preferences.showTitle && this.preferences.showArtist) {
            return "Título e artista";
        }
        if (this.preferences.showTitle) {
            return "Só título";
        }
        return this.preferences.showArtist ? "Só artista" : "Informação oculta";
    }

    void setShowTitle(Boolean bool) {
        this.preferences.showTitle = bool.booleanValue();
    }

    void setShowArtist(Boolean bool) {
        this.preferences.showArtist = bool.booleanValue();
    }

    void setTextPosition(int i) {
        this.preferences.textPosition = i;
    }

    void setFontFamily(int i) {
        this.preferences.fontFamily = i;
    }

    void setFontStyle(int i) {
        this.preferences.fontStyle = i;
    }

    void setTitleSize(int i) {
        this.preferences.titleSize = i;
    }

    void setArtistSize(int i) {
        this.preferences.artistSize = i;
    }

    String barsSummary() {
        if (this.preferences.showBrightness && this.preferences.showVolume) {
            return "Brilho e volume";
        }
        if (this.preferences.showBrightness) {
            return "Só brilho";
        }
        return this.preferences.showVolume ? "Só volume" : "Ocultas";
    }

    void setShowBrightness(Boolean bool) {
        this.preferences.showBrightness = bool.booleanValue();
    }

    void setShowVolume(Boolean bool) {
        this.preferences.showVolume = bool.booleanValue();
    }

    void chooseTimerPreset(int i, View view) {
        this.timerMinutes = i;
        updateDuration();
    }

    void openTimerDurationPicker(View view) {
        final NumberPicker numberPicker = new NumberPicker(this);
        numberPicker.setMinValue(1);
        numberPicker.setMaxValue(180);
        numberPicker.setValue(this.timerMinutes);
        new AlertDialog.Builder(this).setTitle("Minutos").setView(numberPicker).setNegativeButton("Cancelar", (DialogInterface.OnClickListener) null).setPositiveButton("Escolher", new DialogInterface.OnClickListener() {
            @Override
            public final void onClick(DialogInterface dialogInterface, int i) {
                SettingsActivity.this.acceptTimerDuration(numberPicker, dialogInterface, i);
            }
        }).show();
    }

    void acceptTimerDuration(NumberPicker numberPicker, DialogInterface dialogInterface, int i) {
        this.timerMinutes = numberPicker.getValue();
        updateDuration();
    }

    void startTimer(View view) {
        if (!MediaBridge.hasAccess(this)) {
            new AlertDialog.Builder(this).setMessage("Para pausar a música no fim, permite primeiro o acesso às notificações.").setNegativeButton("Cancelar", (DialogInterface.OnClickListener) null).setPositiveButton("Permitir", new DialogInterface.OnClickListener() {
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    SettingsActivity.this.openNotificationAccessSettings(dialogInterface, i);
                }
            }).show();
            return;
        }
        if (!SleepTimer.start(this, this.timerMinutes)) {
            Toast.makeText(this, "Não foi possível iniciar o temporizador.", 1).show();
        }
        updateTimer();
    }

    void openNotificationAccessSettings(DialogInterface dialogInterface, int i) {
        try {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        } catch (ActivityNotFoundException unused) {
            Toast.makeText(this, "Abre o acesso às notificações nas definições do Android.", 1).show();
        }
    }

    void cancelTimer(View view) {
        SleepTimer.cancel(this);
        updateTimer();
    }

    void requestExactAlarms(View view) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                startActivity(new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM", Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException unused) {
                Toast.makeText(this, "Procura Alarmes e lembretes nas definições do Android.", 1).show();
            }
        }
    }

    String lockScreenSummary() {
        return this.preferences.showOnLockScreen ? "Leitor sobre o bloqueio" : "Bloqueio normal do Android";
    }

    void setShowOnLockScreen(Boolean bool) {
        this.preferences.showOnLockScreen = bool.booleanValue();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SleepTimer.upgradePrecision(this);
        this.handler.removeCallbacks(this.tick);
        this.tick.run();
    }

    @Override
    protected void onPause() {
        this.handler.removeCallbacks(this.tick);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        bundle.putSerializable("draft", this.preferences);
        bundle.putInt("section", this.expanded);
        bundle.putInt("minutes", this.timerMinutes);
        super.onSaveInstanceState(bundle);
    }

    @Override
    protected void onRestoreInstanceState(Bundle bundle) {
        super.onRestoreInstanceState(bundle);
        refresh();
    }

    private Section section(LinearLayout linearLayout, String str, int i, int i2, Supplier<String> supplier) {
        Section section = new Section();
        section.caption = str;
        section.summary = supplier;
        LinearLayout linearLayoutColumn = column();
        linearLayoutColumn.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-15921907);
        gradientDrawable.setCornerRadius(dp(18));
        gradientDrawable.setStroke(dp(1), -14342875);
        linearLayoutColumn.setBackground(gradientDrawable);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setId(i);
        linearLayout2.setGravity(16);
        linearLayout2.setMinimumHeight(dp(76));
        linearLayout2.setFocusable(true);
        linearLayout2.setClickable(true);
        linearLayout2.setBackground(new RippleDrawable(ColorStateList.valueOf(587202559), null, null));
        LinearLayout linearLayoutColumn2 = column();
        TextView textViewText = text(str, 18, -1);
        textViewText.setTypeface(Typeface.DEFAULT, 1);
        linearLayoutColumn2.addView(textViewText);
        section.description = text("", 13, -6710887);
        linearLayoutColumn2.addView(section.description);
        linearLayout2.addView(linearLayoutColumn2, new LinearLayout.LayoutParams(0, -2, 1.0f));
        section.arrow = text("⌄", 24, -6710887);
        linearLayout2.addView(section.arrow);
        linearLayoutColumn.addView(linearLayout2);
        section.header = linearLayout2;
        section.body = column();
        section.body.setId(i2);
        section.body.setPadding(0, dp(4), 0, dp(20));
        linearLayoutColumn.addView(section.body);
        section.body.setVisibility(8);
        final int size = this.sections.size();
        this.sections.add(section);
        linearLayout2.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                SettingsActivity.this.toggleSection(size, view);
            }
        });
        add(linearLayout, linearLayoutColumn, 10);
        return section;
    }

    void toggleSection(int i, View view) {
        if (this.expanded == i) {
            i = -1;
        }
        expand(i);
    }

    private void expand(int i) {
        this.expanded = i;
        int i2 = 0;
        while (i2 < this.sections.size()) {
            Section section = this.sections.get(i2);
            section.body.setVisibility(i2 == this.expanded ? 0 : 8);
            section.arrow.setText(i2 == this.expanded ? "⌃" : "⌄");
            section.header.setContentDescription(section.caption + ". " + section.summary.get() + (i2 == this.expanded ? ". Recolher" : ". Expandir"));
            i2++;
        }
    }

    private Switch toggle(LinearLayout linearLayout, String str, int i, boolean z, final Consumer<Boolean> consumer) {
        Switch r0 = new Switch(this);
        r0.setId(i);
        r0.setText(str);
        r0.setTextColor(-1);
        r0.setTextSize(16.0f);
        r0.setMinHeight(dp(56));
        r0.setChecked(z);
        r0.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[0]}, new int[]{-1, -8947849}));
        add(linearLayout, r0, 4);
        r0.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z2) {
                SettingsActivity.this.onToggleChanged(consumer, compoundButton, z2);
            }
        });
        return r0;
    }

    void onToggleChanged(Consumer consumer, CompoundButton compoundButton, boolean z) {
        consumer.accept(Boolean.valueOf(z));
        refresh();
    }

    private SeekBar slider(LinearLayout linearLayout, final String str, int i, int i2, int i3, int i4, final String str2, final IntConsumer intConsumer) {
        final TextView textViewText = text(str + " · " + i4 + str2, 15, -3355444);
        add(linearLayout, textViewText, 12);
        SeekBar seekBarCreate = MinimalSlider.create(this, -1);
        seekBarCreate.setId(i);
        seekBarCreate.setMin(i2);
        seekBarCreate.setMax(i3);
        seekBarCreate.setProgress(i4);
        seekBarCreate.setContentDescription(str);
        add(linearLayout, seekBarCreate, 0);
        seekBarCreate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int i5, boolean z) {
                textViewText.setText(SettingsActivity.this.getString(R.string.setting_value, new Object[]{str, Integer.valueOf(i5), str2}));
                intConsumer.accept(i5);
                SettingsActivity.this.refresh();
            }
        });
        return seekBarCreate;
    }

    private void choice(LinearLayout linearLayout, String str, int i, String[] strArr, int i2, final IntConsumer intConsumer) {
        add(linearLayout, text(str, 15, -4473925), 12);
        Spinner spinner = new Spinner(this);
        spinner.setId(i);
        spinner.setAdapter((SpinnerAdapter) new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, strArr));
        spinner.setSelection(i2);
        add(linearLayout, spinner, 0);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }

            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i3, long j) {
                intConsumer.accept(i3);
                SettingsActivity.this.refresh();
            }
        });
    }

    private void color(LinearLayout linearLayout, final String str, final int i, final boolean z, int i2, final boolean z2) {
        final ColorChoiceView colorChoiceView = new ColorChoiceView(this, str, z, i2);
        colorChoiceView.setId(i);
        if (z2) {
            colorChoiceView.setTextSample("Aa");
        }
        add(linearLayout, colorChoiceView, 12);
        colorChoiceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                SettingsActivity.this.openColorPicker(i, str, z, colorChoiceView, z2, view);
            }
        });
    }

    void openColorPicker(int i, String str, boolean z, ColorChoiceView colorChoiceView, boolean z2, View view) {
        if (getFragmentManager().isStateSaved() || getFragmentManager().findFragmentByTag("color_picker") != null) {
            return;
        }
        ColorPickerDialog colorPickerDialogCreate = ColorPickerDialog.create(i, str, z, colorChoiceView.getColor());
        if (z2) {
            colorPickerDialogCreate.textSample("Aa");
        }
        colorPickerDialogCreate.show(getFragmentManager(), "color_picker");
    }

    @Override
    public void onColorChosen(int i, int i2) {
        ColorChoiceView colorChoiceView = (ColorChoiceView) findViewById(i);
        if (colorChoiceView != null) {
            colorChoiceView.setColor(i2);
        }
        if (i == R.id.controls_color_choice) {
            this.preferences.controlsColor = i2;
        } else if (i == R.id.brightness_color_choice) {
            this.preferences.brightnessColor = i2;
        } else if (i == R.id.volume_color_choice) {
            this.preferences.volumeColor = i2;
        } else if (i == R.id.title_color_choice) {
            this.preferences.titleColor = i2;
        } else if (i == R.id.artist_color_choice) {
            this.preferences.artistColor = i2;
        } else if (i == R.id.album_border_color_choice) {
            this.preferences.albumBorderColor = i2;
        }
        refresh();
    }

    public void refresh() {
        if (this.building) {
            return;
        }
        this.borderOptions.setVisibility(this.preferences.showAlbumBorder ? View.VISIBLE : View.GONE);
        this.borderPreview.applyAppearance(this.preferences);
        this.borderPreview.setImageAlpha(Math.round((1 - this.preferences.coverDim / 100f) * 255));
        findViewById(R.id.album_border_color_choice).setVisibility(
                this.preferences.autoAlbumBorderColor ? View.GONE : View.VISIBLE);
        this.controls.setEnabled(this.preferences.coverGestures);
        this.controls.setAlpha(this.preferences.coverGestures ? 1.0f : 0.45f);
        if (!this.preferences.coverGestures && !this.preferences.showControls) {
            this.preferences.showControls = true;
        }
        if (this.controls.isChecked() != this.preferences.showControls) {
            this.controls.setChecked(this.preferences.showControls);
        }
        this.titleOptions.setVisibility(this.preferences.showTitle ? 0 : 8);
        this.artistOptions.setVisibility(this.preferences.showArtist ? 0 : 8);
        this.textOptions.setVisibility((this.preferences.showTitle || this.preferences.showArtist) ? 0 : 8);
        this.brightnessOptions.setVisibility(this.preferences.showBrightness ? 0 : 8);
        this.volumeOptions.setVisibility(this.preferences.showVolume ? 0 : 8);
        this.titlePreview.setVisibility(this.preferences.showTitle ? 0 : 8);
        this.artistPreview.setVisibility(this.preferences.showArtist ? 0 : 8);
        Typeface typefaceFont = font(this.preferences);
        this.titlePreview.setTypeface(typefaceFont);
        this.artistPreview.setTypeface(typefaceFont);
        this.titlePreview.setTextSize(this.preferences.titleSize);
        this.artistPreview.setTextSize(this.preferences.artistSize);
        this.titlePreview.setTextColor(this.preferences.titleColor);
        this.artistPreview.setTextColor(this.preferences.artistColor);
        for (Section section : this.sections) {
            section.description.setText(section.summary.get());
        }
    }

    static Typeface font(AppPreferences appPreferences) {
        return Typeface.create(new String[]{"sans-serif", "serif", "monospace", "sans-serif-condensed"}[appPreferences.fontFamily], appPreferences.fontStyle);
    }

    public String timerSummary() {
        long jRemaining = SleepTimer.remaining(this);
        if (jRemaining > 0) {
            return "Ativo · " + ((jRemaining + 59999) / 60000) + " min";
        }
        return SleepTimer.deadline(this) > 0 ? "À espera do Android" : SleepTimer.status(this);
    }

    public void updateTimer() {
        String timerText;
        if (this.timerStatus == null) {
            return;
        }
        long jRemaining = SleepTimer.remaining(this);
        TextView textView = this.timerStatus;
        if (jRemaining > 0) {
            timerText = String.format(Locale.ROOT, "%02d:%02d%s", Long.valueOf(jRemaining / 60000), Long.valueOf((jRemaining / 1000) % 60), SleepTimer.isExact(this) ? "" : " · aproximado");
        } else {
            timerText = timerSummary();
        }
        textView.setText(timerText);
        this.timerCancel.setVisibility(SleepTimer.deadline(this) > 0 ? 0 : 8);
        this.timerPrecision.setVisibility(SleepTimer.canBeExact(this) ? 8 : 0);
        if (this.building) {
            return;
        }
        for (Section section : this.sections) {
            section.description.setText(section.summary.get());
        }
    }

    private void updateDuration() {
        this.durationButton.setText(getString(R.string.timer_duration, new Object[]{Integer.valueOf(this.timerMinutes)}));
    }

    private LinearLayout column() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        return linearLayout;
    }

    private TextView text(String str, int i, int i2) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(i);
        textView.setTextColor(i2);
        return textView;
    }

    private void help(LinearLayout linearLayout, String str) {
        add(linearLayout, text(str, 13, -6710887), 8);
    }

    private void add(LinearLayout linearLayout, View view, int i) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = dp(i);
        linearLayout.addView(view, layoutParams);
    }

    private Button button(String str, boolean z) {
        Button button = new Button(this);
        button.setText(str);
        button.setAllCaps(false);
        button.setTextColor(z ? -16777216 : -1);
        button.setMinHeight(dp(48));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(z ? -1 : 0);
        gradientDrawable.setCornerRadius(dp(24));
        button.setBackground(gradientDrawable);
        return button;
    }

    private static final class Section {
        TextView arrow;
        LinearLayout body;
        String caption;
        TextView description;
        LinearLayout header;
        Supplier<String> summary;

        private Section() {
        }
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }
}
