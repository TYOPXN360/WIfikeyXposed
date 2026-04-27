package com.ty.wifikeyxposed;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    
    private boolean isDarkMode;
    private SharedPreferences prefs;
    
    private int colorBackground;
    private int colorSurface;
    private int colorPrimary;
    private int colorOnPrimary;
    private int colorTextPrimary;
    private int colorAccent;
    private int colorControlNormal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs \u003d getSharedPreferences("settings", Context.MODE_PRIVATE);
        String themeMode \u003d prefs.getString("theme_mode", "auto");
        if (themeMode.equals("dark")) {
            isDarkMode \u003d true;
        } else if (themeMode.equals("light")) {
            isDarkMode \u003d false;
        } else {
            isDarkMode \u003d (getResources().getConfiguration().uiMode \u0026 android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
                    \u003d\u003d android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }

        applyThemeColors();

        LinearLayout root \u003d new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBackground);
        
        // --- MD3E Toolbar ---
        FrameLayout toolbar \u003d new FrameLayout(this);
        toolbar.setPadding(64, 140, 64, 48);
        
        TextView title \u003d new TextView(this);
        title.setText("Wifi万能钥匙增强");
        title.setTextSize(26);
        title.setTextColor(colorTextPrimary);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        FrameLayout.LayoutParams titleLp \u003d new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.gravity \u003d Gravity.START | Gravity.CENTER_VERTICAL;
        toolbar.addView(title, titleLp);
        
        ImageView themeToggle \u003d new ImageView(this);
        themeToggle.setImageDrawable(new VectorIconDrawable(isDarkMode ? "moon" : "sun", colorPrimary));
        themeToggle.setPadding(24, 24, 24, 24);
        themeToggle.setClickable(true);
        themeToggle.setFocusable(true);
        themeToggle.setBackground(createCircularRipple());
        
        FrameLayout.LayoutParams iconLp \u003d new FrameLayout.LayoutParams(120, 120);
        iconLp.gravity \u003d Gravity.END | Gravity.CENTER_VERTICAL;
        toolbar.addView(themeToggle, iconLp);
        
        themeToggle.setOnClickListener(v -\u003e {
            prefs.edit().putString("theme_mode", isDarkMode ? "light" : "dark").apply();
            recreate();
        });
        
        root.addView(toolbar);

        ScrollView scrollView \u003d new ScrollView(this);
        LinearLayout content \u003d new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(64, 32, 64, 100);
        
        // --- 功能卡片 ---
        content.addView(createSectionHeader("ENHANCEMENT"));
        
        LinearLayout card \u003d new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(64, 64, 64, 64);
        GradientDrawable cardShape \u003d new GradientDrawable();
        cardShape.setCornerRadius(80); 
        cardShape.setColor(colorSurface);
        card.setBackground(cardShape);
        
        // 1. 推送拦截
        card.addView(createMD3ESwitchItem("拦截广告推送", "block_news", "极致过滤通知栏新闻内容", prefs));
        
        // 增加分割线
        View div \u003d new View(this);
        div.setBackgroundColor(colorControlNormal);
        div.setAlpha(0.1f);
        LinearLayout.LayoutParams divLp \u003d new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2);
        divLp.setMargins(0, 20, 0, 20);
        card.addView(div, divLp);
        
        // 2. 解锁会员 (新功能)
        card.addView(createMD3ESwitchItem("解锁本地会员", "unlock_vip", "开启极速连接等会员特权", prefs));
        
        // 增加分割线
        View div2 = new View(this);
        div2.setBackgroundColor(colorControlNormal);
        div2.setAlpha(0.1f);
        LinearLayout.LayoutParams divLp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2);
        divLp2.setMargins(0, 20, 0, 20);
        card.addView(div2, divLp2);

        // 3. 去广告
        card.addView(createMD3ESwitchItem("去除内置广告", "remove_ads", "拦截开屏、列表及视频广告", prefs));

        content.addView(card);
        
        scrollView.addView(content);
        root.addView(scrollView);
        setContentView(root);
        
        setupStatusBar();
    }
    
    private void applyThemeColors() {
        if (isDarkMode) {
            colorBackground \u003d Color.parseColor("#121316");
            colorSurface \u003d Color.parseColor("#1E2024");
            colorPrimary \u003d Color.parseColor("#A8C7FF");
            colorOnPrimary \u003d Color.parseColor("#003062");
            colorTextPrimary \u003d Color.parseColor("#E2E2E6");
            colorAccent \u003d Color.parseColor("#7CACFF");
            colorControlNormal \u003d Color.parseColor("#8E9199");
        } else {
            colorBackground \u003d Color.parseColor("#FDFBFF");
            colorSurface \u003d Color.parseColor("#EFF1F8");
            colorPrimary \u003d Color.parseColor("#005AC1");
            colorOnPrimary \u003d Color.WHITE;
            colorTextPrimary \u003d Color.parseColor("#1B1B1F");
            colorAccent \u003d Color.parseColor("#0061A4");
            colorControlNormal \u003d Color.parseColor("#74777F");
        }
    }

    private View createSectionHeader(String text) {
        TextView tv \u003d new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setLetterSpacing(0.15f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setTextColor(colorAccent);
        tv.setPadding(24, 64, 0, 24);
        return tv;
    }

    private View createMD3ESwitchItem(String title, String key, String sub, SharedPreferences p) {
        LinearLayout container \u003d new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setPadding(0, 20, 0, 20);
        
        LinearLayout textGroup \u003d new LinearLayout(this);
        textGroup.setOrientation(LinearLayout.VERTICAL);
        textGroup.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        
        TextView titleTv \u003d new TextView(this);
        titleTv.setText(title);
        titleTv.setTextSize(18);
        titleTv.setTextColor(colorTextPrimary);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        
        TextView subTv \u003d new TextView(this);
        subTv.setText(sub);
        subTv.setTextSize(13);
        subTv.setTextColor(colorControlNormal);
        
        textGroup.addView(titleTv);
        textGroup.addView(subTv);
        
        Switch sw \u003d new Switch(this);
        sw.setChecked(p.getBoolean(key, false));
        sw.setOnCheckedChangeListener((b, isChecked) -\u003e p.edit().putBoolean(key, isChecked).apply());
        
        sw.setTrackDrawable(createMD3Track());
        sw.setThumbDrawable(createMD3Thumb());
        
        container.addView(textGroup);
        container.addView(sw);
        return container;
    }

    private Drawable createMD3Track() {
        GradientDrawable on \u003d new GradientDrawable();
        on.setShape(GradientDrawable.RECTANGLE);
        on.setCornerRadius(100);
        on.setColor(colorPrimary);
        on.setSize(104, 64); 

        GradientDrawable off \u003d new GradientDrawable();
        off.setShape(GradientDrawable.RECTANGLE);
        off.setCornerRadius(100);
        off.setColor(isDarkMode ? Color.parseColor("#44474E") : Color.parseColor("#E1E2EC"));
        off.setStroke(4, isDarkMode ? Color.parseColor("#8E9199") : Color.parseColor("#74777F"));
        off.setSize(104, 64);

        StateListDrawable sld \u003d new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_checked}, on);
        sld.addState(new int[]{}, off);
        return sld;
    }

    private Drawable createMD3Thumb() {
        GradientDrawable on \u003d new GradientDrawable();
        on.setShape(GradientDrawable.OVAL);
        on.setColor(colorOnPrimary);
        on.setSize(56, 56); 

        GradientDrawable off \u003d new GradientDrawable();
        off.setShape(GradientDrawable.OVAL);
        off.setColor(isDarkMode ? Color.parseColor("#C4C6D0") : Color.parseColor("#74777F"));
        off.setSize(48, 48); 

        StateListDrawable sld \u003d new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_checked}, on);
        sld.addState(new int[]{}, off);
        return sld;
    }

    private Drawable createCircularRipple() {
        GradientDrawable mask \u003d new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.BLACK);
        return new RippleDrawable(ColorStateList.valueOf(Color.parseColor(isDarkMode ? "#40FFFFFF" : "#40000000")), null, mask);
    }

    private void setupStatusBar() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        int flags \u003d View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (!isDarkMode) flags |\u003d View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private class VectorIconDrawable extends Drawable {
        private final Paint paint \u003d new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String type;
        
        public VectorIconDrawable(String type, int color) {
            this.type \u003d type;
            this.paint.setColor(color);
            this.paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            float w \u003d getBounds().width();
            float h \u003d getBounds().height();
            float cx \u003d w / 2, cy \u003d h / 2;
            
            if (type.equals("sun")) {
                canvas.drawCircle(cx, cy, w * 0.25f, paint);
                for (int i \u003d 0; i \u003c 8; i++) {
                    canvas.save();
                    canvas.rotate(i * 45, cx, cy);
                    canvas.drawRoundRect(cx - 2, cy - w * 0.45f, cx + 2, cy - w * 0.33f, 4, 4, paint);
                    canvas.restore();
                }
            } else {
                Path path \u003d new Path();
                path.addCircle(cx * 1.1f, cy * 0.9f, w * 0.35f, Path.Direction.CW);
                Path moon \u003d new Path();
                moon.addCircle(cx, cy, w * 0.35f, Path.Direction.CW);
                moon.op(path, Path.Op.DIFFERENCE);
                canvas.drawPath(moon, paint);
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
