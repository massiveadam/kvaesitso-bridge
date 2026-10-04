package com.adamdelisi.bridgefixture;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Test-only source of real Android notifications and an ordinary Android widget host. */
public class FixtureActivity extends Activity {
    private AppWidgetHost host;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        handleIntent();
    }
    private void handleIntent() {
        String action = getIntent().getStringExtra("action");
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("messages", "Test messages", NotificationManager.IMPORTANCE_DEFAULT));
        notifications.createNotificationChannel(new NotificationChannel("silent", "Test status", NotificationManager.IMPORTANCE_LOW));
        if ("post".equals(action)) post(notifications);
        if ("cancel".equals(action)) notifications.cancel(getIntent().getIntExtra("id", 1));
        if ("clear".equals(action)) notifications.cancelAll();
        if ("opened".equals(action)) {
            TextView text = new TextView(this);
            text.setText("Notification opened: " + getIntent().getIntExtra("id", 1));
            text.setTextSize(22); text.setPadding(30, 80, 30, 30); setContentView(text);
            return;
        }
        showWidget();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent();
    }
    private void post(NotificationManager manager) {
        Intent input = getIntent();
        int id = input.getIntExtra("id", 1);
        Intent open = new Intent(this, FixtureActivity.class).putExtra("action", "opened").putExtra("id", id);
        Notification.Builder builder = new Notification.Builder(this, input.getBooleanExtra("silent", false) ? "silent" : "messages")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(input.getStringExtra("title"))
                .setContentText(input.getStringExtra("text"))
                .setAutoCancel(true)
                .setOngoing(input.getBooleanExtra("ongoing", false));
        if (!input.getBooleanExtra("noIntent", false)) builder.setContentIntent(PendingIntent.getActivity(this, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        if (input.hasExtra("category")) builder.setCategory(input.getStringExtra("category"));
        if (input.hasExtra("group")) builder.setGroup(input.getStringExtra("group")).setGroupSummary(input.getBooleanExtra("summary", false));
        if (input.getBooleanExtra("foreground", false)) {
            Notification notification = builder.build();
            notification.flags |= Notification.FLAG_FOREGROUND_SERVICE;
            manager.notify(id, notification);
        } else manager.notify(id, builder.build());
    }
    private void showWidget() {
        if (host != null) host.stopListening();
        host = new AppWidgetHost(this, 471);
        AppWidgetManager manager = getSystemService(AppWidgetManager.class);
        int id = getPreferences(MODE_PRIVATE).getInt("widgetId", -1);
        ComponentName provider = new ComponentName("com.adamdelisi.kvaesitsobridge", "com.adamdelisi.kvaesitsobridge.widget.NotificationWidgetReceiver");
        if (id == -1 || manager.getAppWidgetInfo(id) == null) {
            id = host.allocateAppWidgetId();
            if (!manager.bindAppWidgetIdIfAllowed(id, provider)) {
                host.deleteAppWidgetId(id);
                TextView error = new TextView(this); error.setText("Grant widget bind access to the test fixture first"); setContentView(error); return;
            }
            getPreferences(MODE_PRIVATE).edit().putInt("widgetId", id).apply();
        }
        host.startListening();
        AppWidgetHostView view = host.createView(this, id, manager.getAppWidgetInfo(id));
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 340);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 340);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 360);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 360);
        manager.updateAppWidgetOptions(id, options);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(60), dp(20), dp(20));
        boolean dark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        root.setBackgroundColor(dark ? 0xff18231d : 0xfff1f2ed);
        TextView heading = new TextView(this); heading.setText("Android widget host · test fixture"); heading.setTextSize(13); heading.setTextColor(dark ? 0xffd5ddd7 : 0xff606760);
        root.addView(heading);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(360)); params.topMargin = dp(24);
        root.addView(view, params); setContentView(root);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onDestroy() { if (host != null) host.stopListening(); super.onDestroy(); }
}
