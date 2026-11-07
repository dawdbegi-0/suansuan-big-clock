package com.suansuan.bigclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private ClockView clockView;
    private final Handler keyHandler = new Handler(Looper.getMainLooper());
    private boolean volumeDownLongHandled = false;

    private final Runnable volumeDownReset = new Runnable() {
        @Override
        public void run() {
            volumeDownLongHandled = true;
            clockView.resetStopwatch();
            Toast.makeText(MainActivity.this, "秒表已清零", Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();

        clockView = new ClockView(this);
        setContentView(clockView);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP
                && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.dispatchKeyEvent(event);
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() > 0) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                boolean showClock = clockView.toggleDisplay();
                Toast.makeText(MainActivity.this,
                        showClock ? "当前时间" : "秒表",
                        Toast.LENGTH_SHORT).show();
            } else {
                volumeDownLongHandled = false;
                keyHandler.postDelayed(volumeDownReset, 650L);
            }
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                keyHandler.removeCallbacks(volumeDownReset);
                if (!volumeDownLongHandled) {
                    boolean running = clockView.toggleStopwatch();
                    Toast.makeText(MainActivity.this,
                            running ? "秒表开始" : "秒表暂停",
                            Toast.LENGTH_SHORT).show();
                }
                volumeDownLongHandled = false;
            }
            return true;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        keyHandler.removeCallbacks(volumeDownReset);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private final class ClockView extends View {
        private static final String PREFS = "big_clock_prefs";
        private static final String KEY_BG = "bg_color";
        private static final String KEY_FG = "fg_color";

        private final SharedPreferences prefs;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final GestureDetector gestureDetector;

        private int bgColor;
        private int textColor;

        private boolean showClock = true;
        private boolean stopwatchRunning = false;
        private long stopwatchAccumMs = 0L;
        private long stopwatchStartedAt = 0L;
        private String lastText = "";

        private final Runnable ticker = new Runnable() {
            @Override
            public void run() {
                String now = currentText();
                if (!now.equals(lastText)) {
                    lastText = now;
                    invalidate();
                }
                handler.postDelayed(this, 200L);
            }
        };

        ClockView(Context context) {
            super(context);
            prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
            bgColor = prefs.getInt(KEY_BG, Color.BLACK);
            textColor = prefs.getInt(KEY_FG, Color.WHITE);

            paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(textColor);

            setBackgroundColor(bgColor);
            setFocusable(true);
            setClickable(true);

            gestureDetector = new GestureDetector(context,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(MotionEvent e) {
                            return true;
                        }

                        @Override
                        public boolean onSingleTapUp(MotionEvent e) {
                            tapAction();
                            return true;
                        }

                        @Override
                        public void onLongPress(MotionEvent e) {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            showColorChoiceDialog();
                        }
                    });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }

        @Override
        protected void onDetachedFromWindow() {
            handler.removeCallbacks(ticker);
            super.onDetachedFromWindow();
        }

        private void tapAction() {
            boolean running;
            if (showClock) {
                showClock = false;
                startStopwatch();
                running = true;
            } else {
                running = toggleStopwatch();
            }
            Toast.makeText(MainActivity.this,
                    running ? "秒表开始" : "秒表暂停",
                    Toast.LENGTH_SHORT).show();
        }

        boolean toggleDisplay() {
            showClock = !showClock;
            lastText = currentText();
            invalidate();
            return showClock;
        }

        boolean toggleStopwatch() {
            if (showClock) {
                showClock = false;
            }
            if (stopwatchRunning) {
                stopwatchAccumMs += SystemClock.elapsedRealtime() - stopwatchStartedAt;
                stopwatchRunning = false;
            } else {
                stopwatchStartedAt = SystemClock.elapsedRealtime();
                stopwatchRunning = true;
            }
            lastText = currentText();
            invalidate();
            return stopwatchRunning;
        }

        void startStopwatch() {
            if (!stopwatchRunning) {
                stopwatchStartedAt = SystemClock.elapsedRealtime();
                stopwatchRunning = true;
            }
            lastText = currentText();
            invalidate();
        }

        void resetStopwatch() {
            stopwatchRunning = false;
            stopwatchAccumMs = 0L;
            stopwatchStartedAt = 0L;
            lastText = currentText();
            invalidate();
        }

        private long stopwatchElapsedMs() {
            if (stopwatchRunning) {
                return stopwatchAccumMs
                        + (SystemClock.elapsedRealtime() - stopwatchStartedAt);
            }
            return stopwatchAccumMs;
        }

        private String currentText() {
            if (!showClock) {
                return formatHms(stopwatchElapsedMs() / 1000L);
            }
            java.util.Calendar c = java.util.Calendar.getInstance();
            return String.format(Locale.CHINA, "%02d:%02d:%02d",
                    c.get(java.util.Calendar.HOUR_OF_DAY),
                    c.get(java.util.Calendar.MINUTE),
                    c.get(java.util.Calendar.SECOND));
        }

        private String formatHms(long totalSeconds) {
            long hours = totalSeconds / 3600L;
            long minutes = (totalSeconds % 3600L) / 60L;
            long seconds = totalSeconds % 60L;
            return String.format(Locale.CHINA, "%02d:%02d:%02d",
                    hours, minutes, seconds);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }

            paint.setColor(textColor);
            paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);

            String text = currentText();
            lastText = text;

            float base = 1000f;
            paint.setTextSize(base);
            float textWidth = paint.measureText("00:00:00");
            Paint.FontMetrics baseFm = paint.getFontMetrics();
            float textHeight = baseFm.descent - baseFm.ascent;

            float scaleW = (w * 0.90f) / textWidth;
            float scaleH = (h * 0.66f) / textHeight;
            float scale = Math.min(scaleW, scaleH);
            float size = Math.max(20f, base * scale);

            paint.setTextSize(size);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(text, w / 2f, baseline, paint);
        }

        private void showColorChoiceDialog() {
            final String[] items = new String[]{"修改数字颜色", "修改背景颜色"};
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("显示设置")
                    .setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) {
                                showRgbDialog(false);
                            } else {
                                showRgbDialog(true);
                            }
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }

        private int dp(float value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }

        private void showRgbDialog(final boolean background) {
            final int original = background ? bgColor : textColor;
            int r = Color.red(original);
            int g = Color.green(original);
            int b = Color.blue(original);

            LinearLayout root = new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(20f);
            root.setPadding(pad, dp(8f), pad, 0);

            final TextView preview = new TextView(MainActivity.this);
            preview.setText("00:00:00");
            preview.setGravity(Gravity.CENTER);
            preview.setTextSize(30f);
            preview.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            preview.setPadding(0, dp(10f), 0, dp(10f));
            root.addView(preview, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            final SeekBar[] bars = new SeekBar[3];
            final TextView[] values = new TextView[3];
            final String[] names = new String[]{"红 R", "绿 G", "蓝 B"};
            final int[] initial = new int[]{r, g, b};

            for (int i = 0; i < 3; i++) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(6f), 0, 0);

                TextView label = new TextView(MainActivity.this);
                label.setText(names[i]);
                label.setTextSize(15f);
                row.addView(label, new LinearLayout.LayoutParams(
                        dp(58f), LinearLayout.LayoutParams.WRAP_CONTENT));

                bars[i] = new SeekBar(MainActivity.this);
                bars[i].setMax(255);
                bars[i].setProgress(initial[i]);
                row.addView(bars[i], new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                values[i] = new TextView(MainActivity.this);
                values[i].setText(String.valueOf(initial[i]));
                values[i].setTextSize(15f);
                values[i].setGravity(Gravity.END);
                row.addView(values[i], new LinearLayout.LayoutParams(
                        dp(42f), LinearLayout.LayoutParams.WRAP_CONTENT));

                final int idx = i;
                bars[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        values[idx].setText(String.valueOf(progress));
                        int color = Color.rgb(bars[0].getProgress(),
                                bars[1].getProgress(), bars[2].getProgress());
                        if (background) {
                            preview.setBackgroundColor(color);
                            preview.setTextColor(textColor);
                        } else {
                            preview.setBackgroundColor(bgColor);
                            preview.setTextColor(color);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                root.addView(row);
            }

            if (background) {
                preview.setBackgroundColor(original);
                preview.setTextColor(textColor);
            } else {
                preview.setBackgroundColor(bgColor);
                preview.setTextColor(original);
            }

            final AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle(background ? "背景颜色" : "数字颜色")
                    .setView(root)
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            int color = Color.rgb(bars[0].getProgress(),
                                    bars[1].getProgress(), bars[2].getProgress());
                            if (background) {
                                bgColor = color;
                                setBackgroundColor(bgColor);
                            } else {
                                textColor = color;
                            }
                            prefs.edit()
                                    .putInt(KEY_BG, bgColor)
                                    .putInt(KEY_FG, textColor)
                                    .apply();
                            invalidate();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .create();

            dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface d) {
                    if (background) {
                        bgColor = original;
                        setBackgroundColor(bgColor);
                    } else {
                        textColor = original;
                    }
                    invalidate();
                }
            });
            dialog.show();
        }
    }
}
