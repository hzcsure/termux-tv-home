package com.hzc.tvdecoy;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 开机 / 解锁 / 亮屏 时把诱饵 Activity 重新拉到前台；
 * 宿主（TVHome = com.termux）被覆盖安装或卸载时，诱饵自我了断，让桌面回来。
 *
 * 为什么需要后半段（2026-11-28 在 MiBOX3 / Android 5.1.1 上实测）：
 *   `pm install -r com.termux` 会杀掉 com.termux 进程，其 HomeLauncher 也随之
 *   从任务栈移除；但诱饵是独立包、独立进程，仍停在栈顶 resumed。
 *   诱饵是透明窗口，此时屏幕上只剩一层"透明的墙"——黑底加日期标签，
 *   遥控器所有按键都被它吃掉。系统不会自己去重启 home，必须手动按一次 HOME。
 *
 *   修法：宿主被 REPLACED / REMOVED 时
 *     1) 广播让在跑的诱饵 finish() 掉；
 *     2) 用【显式 component】把 HOME 拉起来（冷启动 TVHome）。
 *   不加显式 component 的话，多桌面环境下 startActivity(CATEGORY_HOME) 会弹
 *   "选择主屏幕应用"——那比黑屏更糟。
 *
 *   诱饵退出后，TVHome 的 LauncherActivity.onResume 会照常 startDecoy()，
 *   于是诱饵重新回到栈顶，保护链路自愈。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    /** 宿主包名：TVHome 就是改造后的 termux-app。 */
    private static final String HOST_PKG = "com.termux";

    /**
     * 宿主桌面组件（activity-alias → .app.LauncherActivity，声明 CATEGORY_HOME）。
     * 必须用别名而不是 .app.LauncherActivity：后者启动即 finish，只负责建 shell。
     */
    private static final String HOST_HOME_CLASS = "com.termux.HomeLauncher";

    /**
     * 装完到进程落定之间有个空档，立刻抢前台容易和 PMS 的收尾流程打架。
     * 1.2s 是实测下来既够快又不冲突的值。
     */
    private static final long HOST_CHANGED_DELAY_MS = 1200L;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_PRESENT.equals(action)
                || Intent.ACTION_SCREEN_ON.equals(action)) {
            Intent i = new Intent(context, DecoyActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return;
        }

        if (Intent.ACTION_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_PACKAGE_REMOVED.equals(action)
                || Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action)) {
            String pkg = intent.getData() != null
                    ? intent.getData().getSchemeSpecificPart() : null;
            if (!HOST_PKG.equals(pkg)) return;

            // 升级分两段广播：先 REMOVED(replacing=true)，再 REPLACED。
            // 前半段宿主还在，不必动作，否则会在安装中途把屏幕搅乱。
            boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
            if (Intent.ACTION_PACKAGE_REMOVED.equals(action) && replacing) return;

            Log.i(TAG, "host " + action + " -> restore HOME");
            restoreHome(context.getApplicationContext());
        }
    }

    private void restoreHome(final Context app) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                // 0) 只在诱饵刚占着栈顶时才动手。
                //    诱饵在前台 = 用户正看着桌面，宿主被杀后这层透明窗口会变成
                //    “黑底 + 日期”的天花板，必须救；
                //    诱饵不在前台 = 用户在看视频/在别的 app 里升级宿主，不该被抢前台。
                if (!DecoyActivity.wasRecentlyForeground()) {
                    Log.i(TAG, "decoy not foreground recently, skip restore");
                    return;
                }

                // 1) 让在跑的诱饵自己退出。诱饵没跑时动态接收器不存在，什么都不会发生。
                try {
                    app.sendBroadcast(new Intent(DecoyActivity.ACTION_HOST_GONE)
                            .setPackage(app.getPackageName()));
                } catch (Exception e) {
                    Log.w(TAG, "notify decoy failed: " + e.getMessage());
                }

                // 2) 显式拉起宿主自己的桌面组件。
                //
                //    这里刻意【不再】用 resolveActivity(CATEGORY_HOME) 去问“默认桌面是谁”：
                //    2026-11-28 在 MiBOX3 上实测，这台盒子的默认 HOME 是系统预装的
                //    com.ayst.linearlauncher（resolve 返回它），于是旧逻辑判定
                //    “default HOME 不是宿主” 直接放弃，升级 termux 后桌面照样回不来。
                //    decoy 本来就是 termux-home 的专属配件（HOST_PKG 已写死），
                //    直接拉宿主自己的别名更可靠：升级完就是要回到这个桌面。
                ComponentName hostHome = new ComponentName(HOST_PKG, HOST_HOME_CLASS);
                Intent home = new Intent(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                home.setComponent(hostHome);
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    // 组件不存在 / 已被禁用（宿主被卸载）时 resoloveActivity 返回 null
                    if (app.getPackageManager().resolveActivity(home, 0) == null) {
                        Log.w(TAG, "host home component unavailable, leave it to system");
                        return;
                    }
                    app.startActivity(home);
                    Log.i(TAG, "HOME relaunched: " + hostHome.flattenToShortString());
                } catch (Exception e) {
                    Log.w(TAG, "relaunch HOME failed: " + e.getMessage());
                }
            }
        }, HOST_CHANGED_DELAY_MS);
    }
}
