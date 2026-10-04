# Architecture

One Android application module, with framework-free notification and settings models. Minimum Android 12, target Android 17. No launcher SDK, database, DI framework, foreground service, history, analytics, or app network permission.

## Ownership

`BridgeApplication` owns a small `BridgeGraph`, coroutine scope, settings store, and notification repository. All components use the same process. The system binds `BridgeNotificationListener`; the settings activity does not own or keep it alive.

The listener maps Android notifications through `AndroidNotificationMapper` and replaces the repository's active set on each system event. This is slightly more work than patching individual keys but makes removal, ranking changes, reconnects and stale entries use one path. State is keyed by Android's notification key. Two identical messages with different keys remain separate because their actions may differ. Group summaries are suppressed only when eligible children exist in the same app/group.

`NotificationFilter` turns normalized snapshots and preferences into `FeedState`. It owns filtering, content privacy, ordering, grouping and limits. Compose and Glance consume the same Flow. The widget never parses framework notifications. Low-importance message/conversation notifications remain visible; media, foreground services, housekeeping categories and non-message silent status are suppressed. An app's background override bypasses noise rules. The global ongoing gate applies even to overridden apps.

`SettingsRepository` persists preferences and observed package names/app labels using DataStore. Notification text, notification keys and PendingIntents never enter disk storage or logs. Source uninstall broadcasts remove preferences and active entries. Settings only list observed apps that PackageManager can still resolve.

## Widget lifecycle and actions

`NotificationWidgetUpdater` coalesces events for 150ms and requests Glance updates. Glance schedules its own rendering work. `updatePeriodMillis=0` disables polling. The standard Glance receiver handles host updates, resizing, deletion and restoration. Reboot and app replacement request rebind and redraw. A new process starts empty and requests a listener reconnect; Android's active notifications rebuild the state. Disconnect clears the in-memory set immediately. Android's notification listener access check runs on listener events, activity resume and widget rendering. Screen state events refresh lock privacy.

Row taps use an explicit activity action, never a broadcast that tries to open an activity. `NotificationOpenActivity` resolves the current notification by key after reconnecting if necessary. The boundary executor sends the content PendingIntent with sender-side activity options, honors auto-cancel, and falls back to PackageManager launch. Dismissal is a Glance ActionCallback, rechecks Android clearability, and calls `cancelNotification`. Android's removal event supplies the resulting state.

The icon is visually 14dp inside a 48dp dismiss target. The 64dp minimum resize height reserves this touch target plus widget padding. Row count depends on widget height, configured line count and Android font scaling. App-only rows use less height. Automatic text color follows system day/night, with a manual light/dark override for wallpaper contrast.

## Decisions

We compared keeping notification history or a disk widget snapshot with rebuilding from Android. The latter wins: fewer lifecycle rules, no sensitive content at rest, and no stale history after reboot. Until the system reconnects, a widget may show a subtle connecting state. Android still retains the last RemoteViews in the launcher, which Bridge cannot synchronously erase across process death or lock transitions.

We compared plain RemoteViews with Glance. Glance keeps responsive layout readable and delegates supported activity/callback actions to Android. It adds its own session machinery, but no second app-specific rendering or persistence system is needed.

WorkManager is pinned to 2.12.0, the [current stable version](https://developer.android.com/jetpack/androidx/releases/work), rather than Glance's old transitive 2.7.1. The latter brought Room 2.2.5 with incomplete reflection rules for current R8 full mode, causing a release-only startup crash. This dependency supports Glance rendering; Bridge adds no periodic jobs or notification database.

`BridgeAction` has only notification open/dismiss cases. Future command types and an executor can be added without putting command policy in the listener. No speculative command registry or shortcuts ship in 0.1.

## Current API review

Checked 2026-10-04. [Android listener contract](https://developer.android.com/reference/android/service/notification/NotificationListenerService), [Glance actions](https://developer.android.com/develop/ui/compose/glance/user-interaction), [background activity launches](https://developer.android.com/guide/components/activities/background-starts), [AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes).

Inspected [Kvaesitso's plugin documentation](https://kvaesitso.mm20.de/docs/developer-guide/plugins/get-started.html) and [SDK tree at main commit 9f6c22e](https://github.com/MM2-0/Kvaesitso/tree/9f6c22e9dbfb06ece4f48e8fb00fb32751217b73/plugins/sdk). Current providers cover weather, files, contacts, locations and calendars. No documented secondary-text notification API was found. No integration is included.
