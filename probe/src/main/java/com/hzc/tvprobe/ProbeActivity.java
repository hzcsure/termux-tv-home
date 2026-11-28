package com.hzc.tvprobe;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/**
 * 探针界面：只为证明「桌面上出现了这个包」。
 * 背景用高对比色，方便在截图里一眼认出。
 */
public class ProbeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("LEANBACK PROBE\n\n桌面能显示这个图标\n就说明 LEANBACK_LAUNCHER 查询生效");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(24);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(0xFF7B1FA2);
        setContentView(tv);
    }
}
