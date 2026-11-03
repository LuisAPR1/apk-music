package pt.luis.oledmusic;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

public final class ColorPickerDialog extends DialogFragment {
    private static final int[] COLORS = {-1, -16777216, -6381922, -769226, -26624, -16121, -5317, -11751600, -16738680, -16728876, -14575885, -12627531, -6543440, -1499549};
    private static final String[] NAMES = {"Branco", "Preto", "Cinzento", "Vermelho", "Laranja", "Âmbar", "Amarelo", "Verde", "Turquesa", "Ciano", "Azul", "Índigo", "Roxo", "Rosa"};
    private EditText hex;
    private boolean hexExpanded;
    private Button hexToggle;
    private SeekBar hue;
    private boolean paletteStyled;
    private final List<Button> presets = new ArrayList();
    private ColorPreviewView preview;
    private int selectedColor;
    private SaturationValueView spectrum;
    private boolean updating;

    interface Listener {
        void onColorChosen(int i, int i2);
    }

    static ColorPickerDialog create(int i, String str, boolean z, int i2) {
        ColorPickerDialog colorPickerDialog = new ColorPickerDialog();
        Bundle bundle = new Bundle();
        bundle.putInt("target", i);
        bundle.putString("caption", str);
        bundle.putBoolean("controls", z);
        bundle.putInt("color", i2);
        colorPickerDialog.setArguments(bundle);
        return colorPickerDialog;
    }

    ColorPickerDialog textSample(String str) {
        getArguments().putString("sample", str);
        return this;
    }

    @Override
    public Dialog onCreateDialog(Bundle bundle) {
        this.selectedColor = bundle == null ? getArguments().getInt("color") : bundle.getInt("color");
        this.hexExpanded = bundle != null && bundle.getBoolean("hex_expanded");
        this.presets.clear();
        this.paletteStyled = false;
        LinearLayout linearLayout = new LinearLayout(getActivity());
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(16), dp(8), dp(16), dp(8));
        linearLayout.setBackgroundColor(-16777216);
        ScrollView scrollView = new ScrollView(getActivity());
        scrollView.setFillViewport(false);
        scrollView.addView(linearLayout);
        ColorPreviewView colorPreviewView = new ColorPreviewView(getActivity(), getArguments().getBoolean("controls"));
        this.preview = colorPreviewView;
        colorPreviewView.setId(R.id.color_preview);
        if (getArguments().containsKey("sample")) {
            this.preview.setTextSample(getArguments().getString("sample"));
        }
        linearLayout.addView(this.preview, new LinearLayout.LayoutParams(-1, dp(56)));
        linearLayout.addView(text("Escolhe uma cor pronta ou arrasta no quadrado e na faixa de tons."));
        SaturationValueView saturationValueView = new SaturationValueView(getActivity());
        this.spectrum = saturationValueView;
        saturationValueView.setId(R.id.color_spectrum);
        this.spectrum.setColor(this.selectedColor);
        if (bundle != null && bundle.getFloatArray("hsv") != null) {
            this.spectrum.restoreHsv(bundle.getFloatArray("hsv"));
        }
        linearLayout.addView(this.spectrum, new LinearLayout.LayoutParams(-1, dp(180)));
        FrameLayout frameLayout = new FrameLayout(getActivity());
        View view = new View(getActivity());
        GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{-65536, -256, -16711936, -16711681, -16776961, -65281, -65536});
        gradientDrawable.setCornerRadius(dp(6));
        view.setBackground(gradientDrawable);
        view.setImportantForAccessibility(2);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(-1, dp(12), 16);
        layoutParams.setMargins(dp(8), 0, dp(8), 0);
        frameLayout.addView(view, layoutParams);
        SeekBar seekBar = new SeekBar(getActivity());
        this.hue = seekBar;
        seekBar.setId(R.id.color_hue);
        this.hue.setMax(359);
        this.hue.setContentDescription("Tom da cor");
        this.hue.setPadding(dp(8), 0, dp(8), 0);
        this.hue.setSplitTrack(false);
        this.hue.setProgressDrawable(new ColorDrawable(0));
        this.hue.setProgressTintList(ColorStateList.valueOf(0));
        GradientDrawable gradientDrawable2 = new GradientDrawable();
        gradientDrawable2.setShape(1);
        gradientDrawable2.setColor(-1);
        gradientDrawable2.setStroke(dp(2), -13619152);
        gradientDrawable2.setSize(dp(18), dp(18));
        this.hue.setThumb(gradientDrawable2);
        this.hue.setThumbTintList(null);
        this.hue.setThumbOffset(dp(9));
        frameLayout.addView(this.hue, new FrameLayout.LayoutParams(-1, -1));
        linearLayout.addView(frameLayout, new LinearLayout.LayoutParams(-1, dp(48)));
        GridLayout gridLayout = new GridLayout(getActivity());
        int iMax = Math.max(3, Math.min(7, (Math.min(getResources().getDisplayMetrics().widthPixels, dp(440)) - dp(56)) / dp(48)));
        gridLayout.setColumnCount(iMax);
        int i = 0;
        while (true) {
            int[] iArr = COLORS;
            if (i >= iArr.length) {
                break;
            }
            final int i2 = iArr[i];
            Button button = new Button(getActivity());
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setPadding(0, 0, 0, 0);
            button.setTextSize(16.0f);
            button.setTag(Integer.valueOf(i2));
            button.setContentDescription(NAMES[i]);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public final void onClick(View view2) {
                    ColorPickerDialog.this.choosePreset(i2, view2);
                }
            });
            GridLayout.LayoutParams layoutParams2 = new GridLayout.LayoutParams();
            layoutParams2.width = 0;
            layoutParams2.height = dp(48);
            layoutParams2.columnSpec = GridLayout.spec(i % iMax, 1.0f);
            layoutParams2.rowSpec = GridLayout.spec(i / iMax);
            gridLayout.addView(button, layoutParams2);
            this.presets.add(button);
            i++;
        }
        linearLayout.addView(gridLayout, new LinearLayout.LayoutParams(-1, -2));
        Button button2 = new Button(getActivity());
        this.hexToggle = button2;
        button2.setId(R.id.picker_hex_toggle);
        this.hexToggle.setAllCaps(false);
        this.hexToggle.setTextColor(-4473925);
        this.hexToggle.setBackgroundColor(-16777216);
        this.hexToggle.setMinHeight(dp(48));
        linearLayout.addView(this.hexToggle, new LinearLayout.LayoutParams(-1, -2));
        EditText editText = new EditText(getActivity());
        this.hex = editText;
        editText.setId(R.id.picker_hex_input);
        this.hex.setSingleLine(true);
        this.hex.setTextColor(-1);
        this.hex.setTextSize(18.0f);
        this.hex.setTypeface(Typeface.MONOSPACE);
        this.hex.setInputType(528385);
        this.hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7), new InputFilter.AllCaps()});
        this.hex.setHint("#RRGGBB");
        this.hex.setHintTextColor(-7829368);
        this.hex.setContentDescription("Código hexadecimal opcional");
        linearLayout.addView(this.hex, new LinearLayout.LayoutParams(-1, dp(56)));
        this.spectrum.setListener(new IntConsumer() {
            @Override
            public final void accept(int i3) {
                ColorPickerDialog.this.onSpectrumColorChanged(i3);
            }
        });
        this.hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar seekBar2) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar2) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar2, int i3, boolean z) {
                if (ColorPickerDialog.this.updating) {
                    return;
                }
                ColorPickerDialog.this.spectrum.setHue(i3);
            }
        });
        this.hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i3, int i4, int i5) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i3, int i4, int i5) {
                if (ColorPickerDialog.this.updating) {
                    return;
                }
                ColorPickerDialog.this.hex.setError(null);
                Integer hex = ColorPickerDialog.this.parseHex(charSequence.toString());
                if (hex != null) {
                    ColorPickerDialog.this.selectedColor = hex.intValue();
                    ColorPickerDialog.this.spectrum.setColor(hex.intValue());
                    ColorPickerDialog.this.refresh(false);
                }
            }
        });
        this.hexToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view2) {
                ColorPickerDialog.this.toggleHexInput(view2);
            }
        });
        setHexVisibility();
        refresh(true);
        if (bundle != null && bundle.containsKey("hex_text")) {
            this.hex.setText(bundle.getString("hex_text"));
        }
        return new AlertDialog.Builder(getActivity()).setTitle(getArguments().getString("caption")).setView(scrollView).setNegativeButton("Cancelar", (DialogInterface.OnClickListener) null).setPositiveButton("Aplicar cor", (DialogInterface.OnClickListener) null).create();
    }

    void choosePreset(int i, View view) {
        choose(i);
    }

    void onSpectrumColorChanged(int i) {
        this.selectedColor = i;
        refresh(true);
    }

    void toggleHexInput(View view) {
        this.hexExpanded = !this.hexExpanded;
        setHexVisibility();
        if (this.hexExpanded) {
            return;
        }
        InputMethodManager inputMethodManager = (InputMethodManager) getActivity().getSystemService(InputMethodManager.class);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(this.hex.getWindowToken(), 0);
        }
        this.hex.clearFocus();
        refresh(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog alertDialog = (AlertDialog) getDialog();
        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(-16777216));
            alertDialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(440)), -2);
            alertDialog.getWindow().setSoftInputMode(16);
        }
        alertDialog.getButton(-1).setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                ColorPickerDialog.this.applySelectedColor(view);
            }
        });
    }

    void applySelectedColor(View view) {
        if (this.hexExpanded && parseHex(this.hex.getText().toString()) == null) {
            this.hex.setError("Usa 6 dígitos, por exemplo #FFFFFF, ou escolhe uma cor na paleta.");
            this.hex.requestFocus();
        } else {
            if (getActivity() instanceof Listener) {
                ((Listener) getActivity()).onColorChosen(getArguments().getInt("target"), this.selectedColor);
            }
            dismiss();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putInt("color", this.selectedColor);
        bundle.putBoolean("hex_expanded", this.hexExpanded);
        SaturationValueView saturationValueView = this.spectrum;
        if (saturationValueView != null) {
            bundle.putFloatArray("hsv", saturationValueView.getHsv());
        }
        EditText editText = this.hex;
        if (editText != null) {
            bundle.putString("hex_text", editText.getText().toString());
        }
    }

    private void choose(int i) {
        this.selectedColor = i;
        this.spectrum.setColor(i);
        refresh(true);
    }

    public void refresh(boolean z) {
        this.updating = true;
        this.preview.setColor(this.selectedColor);
        this.hue.setProgress(Math.round(this.spectrum.getHue()));
        if (z) {
            this.hex.setText(String.format(Locale.ROOT, "#%06X", Integer.valueOf(this.selectedColor & 16777215)));
            this.hex.setError(null);
        }
        for (int i = 0; i < this.presets.size(); i++) {
            Button button = this.presets.get(i);
            int iIntValue = ((Integer) button.getTag()).intValue();
            boolean z2 = this.selectedColor == iIntValue;
            if (!this.paletteStyled || button.isSelected() != z2) {
                GradientDrawable gradientDrawable = new GradientDrawable();
                gradientDrawable.setShape(1);
                gradientDrawable.setColor(iIntValue);
                gradientDrawable.setStroke(dp(z2 ? 2 : 1), z2 ? -1 : -8947849);
                button.setBackground(new InsetDrawable((Drawable) gradientDrawable, dp(7)));
                button.setSelected(z2);
                button.setText(z2 ? "✓" : "");
                button.setTextColor(Color.luminance(iIntValue) > 0.179f ? -16777216 : -1);
                button.setContentDescription(NAMES[i] + (z2 ? ", selecionada" : ""));
            }
        }
        this.paletteStyled = true;
        this.updating = false;
    }

    private void setHexVisibility() {
        this.hex.setVisibility(this.hexExpanded ? 0 : 8);
        this.hexToggle.setText(this.hexExpanded ? "Ocultar código hex" : "Usar código hex (opcional)");
    }

    public Integer parseHex(String str) {
        String strTrim = str.trim();
        if (!strTrim.matches("#?[0-9a-fA-F]{6}")) {
            return null;
        }
        if (!strTrim.startsWith("#")) {
            strTrim = "#" + strTrim;
        }
        return Integer.valueOf(Color.parseColor(strTrim));
    }

    private TextView text(String str) {
        TextView textView = new TextView(getActivity());
        textView.setText(str);
        textView.setTextColor(-4473925);
        textView.setTextSize(14.0f);
        return textView;
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }
}
