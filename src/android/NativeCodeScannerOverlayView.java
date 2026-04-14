package com.acme.cordova.nativecodescanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;

final class NativeCodeScannerOverlayView extends FrameLayout {
    private final Paint scrimPaint;
    private final Paint framePaint;
    private final RectF finderRect = new RectF();

    private final TextView promptView;
    private final AppCompatButton cancelButton;
    private final AppCompatButton torchButton;
    private final AppCompatButton flipButton;

    NativeCodeScannerOverlayView(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);

        scrimPaint = new Paint();
        scrimPaint.setColor(0x99000000);

        framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        framePaint.setColor(0xFFFFFFFF);
        framePaint.setStrokeWidth(dp(3));
        framePaint.setStyle(Paint.Style.STROKE);

        LinearLayout topContainer = new LinearLayout(context);
        topContainer.setOrientation(LinearLayout.VERTICAL);
        topContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        LayoutParams topParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP);
        topParams.topMargin = dp(32);
        topParams.leftMargin = dp(20);
        topParams.rightMargin = dp(20);
        addView(topContainer, topParams);

        promptView = new TextView(context);
        promptView.setTextColor(0xFFFFFFFF);
        promptView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        promptView.setTypeface(Typeface.DEFAULT_BOLD);
        promptView.setGravity(Gravity.CENTER);
        promptView.setPadding(dp(18), dp(12), dp(18), dp(12));
        promptView.setBackground(createPanelBackground(0xB30F172A));
        topContainer.addView(promptView, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        LinearLayout bottomContainer = new LinearLayout(context);
        bottomContainer.setOrientation(LinearLayout.HORIZONTAL);
        bottomContainer.setGravity(Gravity.CENTER);
        bottomContainer.setPadding(dp(20), dp(12), dp(20), dp(28));
        bottomContainer.setWeightSum(3f);
        LayoutParams bottomParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        addView(bottomContainer, bottomParams);

        cancelButton = createButton(context, "Close", 0xFFE2E8F0, 0xFF0F172A);
        torchButton = createButton(context, "Light", 0xFF0F4C81, 0xFFFFFFFF);
        flipButton = createButton(context, "Camera", 0xFFF59E0B, 0xFF111827);

        bottomContainer.addView(cancelButton, createButtonLayoutParams());
        bottomContainer.addView(torchButton, createButtonLayoutParams());
        bottomContainer.addView(flipButton, createButtonLayoutParams());
    }

    void configure(String prompt, boolean showTorchButton, boolean showFlipButton) {
        boolean hasPrompt = prompt != null && prompt.trim().length() > 0;
        promptView.setVisibility(hasPrompt ? View.VISIBLE : View.GONE);
        promptView.setText(hasPrompt ? prompt : "");
        torchButton.setVisibility(showTorchButton ? View.VISIBLE : View.GONE);
        flipButton.setVisibility(showFlipButton ? View.VISIBLE : View.GONE);
    }

    void setOnCancelClickListener(OnClickListener listener) {
        cancelButton.setOnClickListener(listener);
    }

    void setOnTorchClickListener(OnClickListener listener) {
        torchButton.setOnClickListener(listener);
    }

    void setOnFlipClickListener(OnClickListener listener) {
        flipButton.setOnClickListener(listener);
    }

    void setTorchAvailable(boolean available) {
        torchButton.setEnabled(available);
        torchButton.setAlpha(available ? 1f : 0.45f);
    }

    void setTorchEnabled(boolean enabled) {
        torchButton.setText(enabled ? "Light on" : "Light");
    }

    void setFlipAvailable(boolean available) {
        flipButton.setVisibility(available ? View.VISIBLE : View.GONE);
    }

    RectF getFinderRect() {
        return new RectF(finderRect);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float horizontalInset = width * 0.12f;
        float rectWidth = width - (horizontalInset * 2f);
        float rectHeight = Math.min(rectWidth * 0.62f, height * 0.35f);
        float left = horizontalInset;
        float top = (height - rectHeight) / 2f;
        finderRect.set(left, top, left + rectWidth, top + rectHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = dp(24);

        canvas.drawRect(0, 0, getWidth(), finderRect.top, scrimPaint);
        canvas.drawRect(0, finderRect.top, finderRect.left, finderRect.bottom, scrimPaint);
        canvas.drawRect(finderRect.right, finderRect.top, getWidth(), finderRect.bottom, scrimPaint);
        canvas.drawRect(0, finderRect.bottom, getWidth(), getHeight(), scrimPaint);
        canvas.drawRoundRect(finderRect, radius, radius, framePaint);
    }

    private LinearLayout.LayoutParams createButtonLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = dp(6);
        params.rightMargin = dp(6);
        return params;
    }

    private AppCompatButton createButton(Context context, String label, int backgroundColor, int textColor) {
        AppCompatButton button = new AppCompatButton(context);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(textColor);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setPadding(dp(14), dp(12), dp(14), dp(12));
        button.setBackground(createPanelBackground(backgroundColor));
        return button;
    }

    private GradientDrawable createPanelBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(999));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        ));
    }
}
