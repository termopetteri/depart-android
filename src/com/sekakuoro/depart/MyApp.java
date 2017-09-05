package com.sekakuoro.depart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.sekakuoro.depart.bulletins.BulletinsFeed;
import com.sekakuoro.depart.helpers.Utils;
import com.sekakuoro.depart.mapui.MyLocationOverlay;
import com.sekakuoro.depart.stops.HslStops;
import com.sekakuoro.depart.stops.JyStops;
import com.sekakuoro.depart.stops.TreStops;
import com.sekakuoro.depart.stops.TurkuStops;
import com.sekakuoro.depart.stops.VrStops;
import com.sekakuoro.depart.tracker.Hsl;
import com.sekakuoro.depart.tracker.Tre;
import com.sekakuoro.depart.tracker.Turku;
import com.sekakuoro.depart.tracker.Vr;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MyApp extends Application {
  public static final String TAG = "MyApp";

  public static final boolean DEBUG = false;

  public static final int UPDATER_TIMINGS_FREQ = 600;
  public static final int ABSTRACT_DEPARTURE_ACTIVITY_TIMINGS_FREQ = 15;
  public static final int BULLETINS_TIMINGS_FREQ = 5;

  public static MyApp instance;

  private static GoogleAnalytics sAnalytics = null;
  private static Tracker sTracker = null;
  public static MyLocationOverlay myLocationOverlay;

  public static UpdaterCollection uc = new UpdaterCollection();

  public static int updateSpeedSelection = 1;
  public static float[] updateSpeedMultipliers = { 0.6666f, 1.0f, 1.6666f };

  public static ArrayList<BulletinsFeed> bulletinFeeds = new ArrayList<BulletinsFeed>();

  public static boolean useGPS = true;
  public static long filterId = -1;

  private static String rssLang = "default";
  public static boolean rssLangSystemDefault = true;

  public static final String STATE_KEY = "Depart";

  private static boolean useAnalytics = true;

  // These store the location of a stop when user wishes to zoom to the selected
  // stop
  public static int zoomToLatE6 = 0;
  public static int zoomToLngE6 = 0;

  private static ThreadFactory threadFactory = new ThreadFactory() {
    public Thread newThread(Runnable r) {
      Thread thread = new Thread(r);
      thread.setPriority(Thread.MIN_PRIORITY);
      return thread;
    }
  };

  private static ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(getDefaultCorePoolSize(),
      threadFactory);

  public static MyApp getApp() {
    return instance;
  }

  @Override
  public void onCreate() {
    instance = this;
    super.onCreate();
    sAnalytics = GoogleAnalytics.getInstance(this);
    Utils.init();

    FavoritesCollection.setContextAndLoad(this.getBaseContext());

    getPrefs();

    final Drawable myLocDrawable = this.getResources().getDrawable(R.drawable.ic_maps_indicator_current_position);
    myLocationOverlay = new MyLocationOverlay(myLocDrawable, this.getBaseContext());

    uc.add(new TreStops());
    uc.add(new HslStops());
    uc.add(new TurkuStops());
    uc.add(new JyStops());
    uc.add(new VrStops());

    uc.add(new Tre());
    uc.add(new Hsl());
    uc.add(new Vr());
    uc.add(new Turku());
  }

  public static void initBulletinFeeds() {
    if (bulletinFeeds.isEmpty()) {
      bulletinFeeds.add(new BulletinsFeed(R.string.bulletinsVr, "VR", getRssLang(),
          "http://ext-service.vr.fi/juha/internet/rss/tiedotteetrss.action?lang="));
      bulletinFeeds.add(new BulletinsFeed(R.string.bulletinsTre, "Tre", "",
          "http://joukkoliikenne-tampere.sivuviidakko.fi/etusivu.rss"));
      bulletinFeeds.add(new BulletinsFeed(R.string.bulletinsLiik, "Liik", "",
          "http://www.epressi.com/feeds/liikennevirasto.rss"));
    }
  }

  public static Resources getResourcesWrapper() {
    return MyApp.getApp().getResources();
  }

  private static void getPrefs() {

    try {

      SharedPreferences settings = getApp().getSharedPreferences(MyApp.STATE_KEY, MODE_PRIVATE);
      final int appVersionFromPrefs = settings.getInt("appVersion", 0);
      final int versionCodeInt = getApp().getPackageManager().getPackageInfo(getApp().getPackageName(), 0).versionCode;
      if (appVersionFromPrefs == 0) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("appVersion", versionCodeInt);
        editor.apply();
      }

      if (appVersionFromPrefs > 0 && appVersionFromPrefs < versionCodeInt) {
        // Here can be shown an "app updated" dialog etc.
      }

      final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApp().getBaseContext());

      final String def = getApp().getResources().getString(R.string.updateSpeedDefaultValue);
      updateSpeedSelection = Integer.parseInt(prefs.getString("updateSpeedPref", def));

      useGPS = prefs.getBoolean("useGPSPref", true);

      rssLang = prefs.getString("rssLangPref", getApp().getResources().getStringArray(R.array.rssLangValues)[0]);
      if (MyApp.rssLang.equals(getApp().getResources().getStringArray(R.array.rssLangValues)[0]))
        MyApp.rssLangSystemDefault = true;
      else
        MyApp.rssLangSystemDefault = false;

    } catch (Exception e) {
    }

  }

  public static float getUpdateSpeedMultiplier() {
    return updateSpeedMultipliers[updateSpeedSelection];
  }

  public static String getRssLang() {
    String lang = rssLang;

    if (rssLangSystemDefault) {
      lang = Locale.getDefault().getLanguage();
      if (!Arrays.asList(getApp().getResources().getStringArray(R.array.rssLangValues)).contains(lang)) {
        lang = "en";
      }
    }

    return lang;
  }

  public static void setRssLang(String string) {
    rssLang = string;
    if (rssLang.equals(getApp().getResources().getStringArray(R.array.rssLangValues)[0])) {
      rssLangSystemDefault = true;
    } else {
      rssLangSystemDefault = false;
    }
  }

  public static void setFilter(long id) {
    filterId = id;
  }

  synchronized private static Tracker getTracker() {
    if (sTracker == null) {
      sTracker = sAnalytics.newTracker("UA-00000000-0");
    }

    return sTracker;
  }

  public static void trackView(final String analyticsPagePath) {
    if (useAnalytics) {
      Tracker t = getTracker();
      t.setScreenName(analyticsPagePath);
      t.send(new HitBuilders.ScreenViewBuilder().build());
    }
  }

  public static void trackEvent(final String category, final String action, final String label, final long value) {
    if (useAnalytics) {
      getTracker().send(new HitBuilders.EventBuilder()
              .setCategory(category)
              .setAction(action)
              .setLabel(label)
              .setValue(value)
              .build());
    }
  }

  public static void trackTiming(final String category, final long interval, final String name, final String label) {
    if (interval <= 1000 * 60 && useAnalytics) {
      getTracker().send(new HitBuilders.TimingBuilder()
              .setCategory(category)
              .setValue(interval)
              .setVariable(name)
              .setLabel(label)
              .build());
    }
  }

  public static String GetHttpFile(final String url, final LocationItem item) {
    long start = 0;
    if (item != null && ((int) (Math.random() * MyApp.UPDATER_TIMINGS_FREQ)) == 1)
      start = System.currentTimeMillis();

    final String payload = GetHttpFile(url);

    if (start > 0) {
      final long end = System.currentTimeMillis();
      MyApp.trackTiming("MyApp", end - start, "GetHttpFile item", item.getAnalyticsPagePath());
    }

    return payload;
  }

  public static String GetHttpFile(final String url, final LocationItemCollection itemcoll) {
    long start = 0;
    if (itemcoll != null && ((int) (Math.random() * MyApp.UPDATER_TIMINGS_FREQ)) == 1)
      start = System.currentTimeMillis();

    final String payload = GetHttpFile(url);

    if (start > 0) {
      final long end = System.currentTimeMillis();
      MyApp.trackTiming("MyApp", end - start, "GetHttpFile itemcoll", itemcoll.getAnalyticsAreaPath());
    }

    return payload;
  }

  public static byte[] GetHttpFileAsBytes(final String url, final LocationItemCollection itemcoll) {
    long start = 0;
    if (itemcoll != null && ((int) (Math.random() * MyApp.UPDATER_TIMINGS_FREQ)) == 1)
      start = System.currentTimeMillis();

    final byte[] payload = GetHttpFileAsBytes(url);

    if (start > 0) {
      final long end = System.currentTimeMillis();
      MyApp.trackTiming("MyApp", end - start, "GetHttpFile itemcoll", itemcoll.getAnalyticsAreaPath());
    }

    return payload;
  }

  private static ResponseBody GetHttpFileResponse(final String url) {
    try {
      OkHttpClient client = new OkHttpClient.Builder()
              .connectTimeout(30, TimeUnit.SECONDS)
              .writeTimeout(30, TimeUnit.SECONDS)
              .readTimeout(30, TimeUnit.SECONDS)
              .build();

      Request request = new Request.Builder().url(url).build();

      Response response = client.newCall(request).execute();
      return response.body();
    } catch (Exception e) {
    }

    return null;
  }

  private static String GetHttpFile(final String url) {
    try {
      ResponseBody body = GetHttpFileResponse(url);
      if (body != null) {
          return body.string();
      }
    } catch (Exception e) {
    }

    return "";
  }

  public static byte[] GetHttpFileAsBytes(final String url) {
    try {
      ResponseBody body = GetHttpFileResponse(url);
      if (body != null) {
        return body.bytes();
      }
    } catch (Exception e) {
    }

    return new byte[] { 0 };
  }

  public static void executeRunnable(Runnable runnable) {
    executor.execute(runnable);
  }

  public static ScheduledFuture<?> executeRunnableAfterDelay(Runnable runnable, long delay) {
    return executor.schedule(runnable, delay, TimeUnit.MILLISECONDS);
  }

  private static int getDefaultCorePoolSize() {
    return Math.min(Math.max(Utils.N_OF_CORES, 2), 5);
  }

  public static void onResume() {
    executor.setCorePoolSize(getDefaultCorePoolSize());
  }

  public static void onPause() {
    executor.setCorePoolSize(1);
  }

  public static void logErrorToAnalytics(final String error) {
    MyApp.trackEvent("Error", "Error", error, 1);
  }

}
