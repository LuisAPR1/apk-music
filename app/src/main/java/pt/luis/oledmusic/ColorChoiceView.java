package pt.luis.oledmusic;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class ColorChoiceView extends LinearLayout {
    private final String caption;
    private int color;
    private final ColorPreviewView preview;
    private final TextView swatch;

    public ColorChoiceView(Context context) {
        this(context, "Cor", true, -1);
    }

    ColorChoiceView(Context context, String str, boolean z, int i) {
        super(context);
        this.caption = str;
        setOrientation(1);
        setPadding(dp(16), dp(12), dp(16), dp(12));
        setClickable(true);
        setFocusable(true);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-15724528);
        gradientDrawable.setCornerRadius(dp(16));
        gradientDrawable.setStroke(dp(1), -13619152);
        setBackground(new RippleDrawable(ColorStateList.valueOf(587202559), gradientDrawable, null));
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setGravity(16);
        TextView textView = new TextView(context);
        this.swatch = textView;
        linearLayout.addView(textView, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView textView2 = new TextView(context);
        textView2.setText(str);
        textView2.setTextSize(17.0f);
        textView2.setTextColor(-1);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.setMarginStart(dp(16));
        linearLayout.addView(textView2, layoutParams);
        TextView textView3 = new TextView(context);
        textView3.setText("›");
        textView3.setTextSize(28.0f);
        textView3.setTextColor(-5592406);
        linearLayout.addView(textView3);
        addView(linearLayout);
        ColorPreviewView colorPreviewView = new ColorPreviewView(context, z);
        this.preview = colorPreviewView;
        addView(colorPreviewView, new LinearLayout.LayoutParams(-1, dp(56)));
        setColor(i);
    }

    int getColor() {
        return this.color;
    }

    void setTextSample(String str) {
        this.preview.setTextSample(str);
    }

    void setColor(int i) {
        this.color = i | (-16777216);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(1);
        gradientDrawable.setColor(this.color);
        gradientDrawable.setStroke(dp(1), -8947849);
        this.swatch.setBackground(gradientDrawable);
        this.preview.setColor(this.color);
        setContentDescription(this.caption + ". Toca para escolher uma cor.");
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState savedState = new SavedState(super.onSaveInstanceState());
        savedState.color = this.color;
        return savedState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable parcelable) {
        if (!(parcelable instanceof SavedState)) {
            super.onRestoreInstanceState(parcelable);
            return;
        }
        SavedState savedState = (SavedState) parcelable;
        super.onRestoreInstanceState(savedState.getSuperState());
        setColor(savedState.color);
    }

    public static final class SavedState extends View.BaseSavedState {
        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel parcel) {
                return new SavedState(parcel);
            }

            @Override
            public SavedState[] newArray(int i) {
                return new SavedState[i];
            }
        };
        int color;

        SavedState(Parcelable parcelable) {
            super(parcelable);
        }

        SavedState(Parcel parcel) {
            super(parcel);
            this.color = parcel.readInt();
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            super.writeToParcel(parcel, i);
            parcel.writeInt(this.color);
        }
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }
}
