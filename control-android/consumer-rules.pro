# Reguły R8 dla aplikacji używającej :control-android.
# Moduł nie używa refleksji. Fabryka i fasady są wołane z Javy (Game.java), więc zachowujemy
# publiczne API, żeby uprościć debugowanie stack trace'ów w buildach release.
-keep class app.thumbcast.android.ThumbcastOverlayFactory { public *; }
-keep class app.thumbcast.android.ThumbcastProfiles { public *; }
-keep interface app.thumbcast.android.host.** { *; }
