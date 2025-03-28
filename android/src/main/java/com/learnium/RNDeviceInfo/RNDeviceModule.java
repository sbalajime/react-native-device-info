package com.learnium.RNDeviceInfo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.FeatureInfo;
import android.content.res.Configuration;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.BatteryManager;
import android.provider.Settings;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.app.ActivityManager;
import android.util.DisplayMetrics;
import android.hardware.Camera;
import android.hardware.camera2.CameraManager;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.lang.Runtime;
import java.net.NetworkInterface;
import java.math.BigInteger;
import java.util.Map;

import javax.annotation.Nonnull;

import static android.os.BatteryManager.BATTERY_STATUS_CHARGING;
import static android.os.BatteryManager.BATTERY_STATUS_FULL;
import static android.provider.Settings.Secure.getString;

@ReactModule(name = RNDeviceModule.NAME)
public class RNDeviceModule extends ReactContextBaseJavaModule {
  public static final String NAME = "RNDeviceInfo";
  private BroadcastReceiver receiver;

  private double mLastBatteryLevel = -1;
  private String sLastBatteryState = "";

  private static String BATTERY_STATE = "batteryState";
  private static String BATTERY_LEVEL= "batteryLevel";
  private static String LOW_POWER_MODE = "lowPowerMode";

  public RNDeviceModule(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  public void initialize() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_BATTERY_CHANGED);

    receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        WritableMap powerState = getPowerStateFromIntent(intent);

        if(powerState == null) {
          return;
        }

        String batteryState = powerState.getString(BATTERY_STATE);
        Double batteryLevel = powerState.getDouble(BATTERY_LEVEL);

        if(!sLastBatteryState.equalsIgnoreCase(batteryState)) {
          sendEvent(getReactApplicationContext(), "RNDeviceInfo_powerStateDidChange", powerState);
          sLastBatteryState = batteryState;
        }

        if(mLastBatteryLevel != batteryLevel) {
            sendEvent(getReactApplicationContext(), "RNDeviceInfo_batteryLevelDidChange", batteryLevel);

          if(batteryLevel <= .15) {
            sendEvent(getReactApplicationContext(), "RNDeviceInfo_batteryLevelIsLow", batteryLevel);
          }

          mLastBatteryLevel = batteryLevel;
        }
      }
    };

    getReactApplicationContext().registerReceiver(receiver, filter);
  }


  @Override
  public void onCatalystInstanceDestroy() {
    getReactApplicationContext().unregisterReceiver(receiver);
  }


  @Override
  @Nonnull
  public String getName() {
    return NAME;
  }

  @SuppressLint("MissingPermission")
  private WifiInfo getWifiInfo() {
    WifiManager manager = (WifiManager) getReactApplicationContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    if (manager != null) {
      return manager.getConnectionInfo();
    }
    return null;
  }

  @Override
  public Map<String, Object> getConstants() {
    String appVersion, buildNumber, appName;

    try {
      appVersion = getPackageInfo().versionName;
      buildNumber = Integer.toString(getPackageInfo().versionCode);
      appName = getReactApplicationContext().getApplicationInfo().loadLabel(getReactApplicationContext().getPackageManager()).toString();
    } catch (Exception e) {
      appVersion = "unknown";
      buildNumber = "unknown";
      appName = "unknown";
    }

    final Map<String, Object> constants = new HashMap<>();

    constants.put("uniqueId", getUniqueIdSync());
    constants.put("deviceId", Build.BOARD);
    constants.put("bundleId", getReactApplicationContext().getPackageName());
    constants.put("systemName", "Android");
    constants.put("systemVersion", Build.VERSION.RELEASE);
    constants.put("appVersion", appVersion);
    constants.put("buildNumber", buildNumber);
    constants.put("isTablet", getDeviceType() == DeviceType.TABLET);
    constants.put("appName", appName);
    constants.put("brand", Build.BRAND);
    constants.put("model", Build.MODEL);

    return constants;
  }

  @ReactMethod
  public void isEmulator(Promise p) {
    p.resolve(isEmulatorSync());
  }

  @SuppressLint("HardwareIds")
  @ReactMethod(isBlockingSynchronousMethod = true)
  public boolean isEmulatorSync() {
    return Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.toLowerCase().contains("droid4x")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.HARDWARE.contains("goldfish")
            || Build.HARDWARE.contains("ranchu")
            || Build.HARDWARE.contains("vbox86")
            || Build.PRODUCT.contains("sdk")
            || Build.PRODUCT.contains("google_sdk")
            || Build.PRODUCT.contains("sdk_google")
            || Build.PRODUCT.contains("sdk_x86")
            || Build.PRODUCT.contains("vbox86p")
            || Build.PRODUCT.contains("emulator")
            || Build.PRODUCT.contains("simulator")
            || Build.BOARD.toLowerCase().contains("nox")
            || Build.BOOTLOADER.toLowerCase().contains("nox")
            || Build.HARDWARE.toLowerCase().contains("nox")
            || Build.PRODUCT.toLowerCase().contains("nox")
            || Build.SERIAL.toLowerCase().contains("nox")
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"));
  }

  private DeviceType getDeviceType() {
    // Detect TVs via ui mode (Android TVs) or system features (Fire TV).
    if (getReactApplicationContext().getPackageManager().hasSystemFeature("amazon.hardware.fire_tv")) {
      return DeviceType.TV;
    }

    UiModeManager uiManager = (UiModeManager) getReactApplicationContext().getSystemService(Context.UI_MODE_SERVICE);
    if (uiManager != null && uiManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
      return DeviceType.TV;
    }

    DeviceType deviceTypeFromConfig = getDeviceTypeFromResourceConfiguration();

    if (deviceTypeFromConfig != null && deviceTypeFromConfig != DeviceType.UNKNOWN) {
      return deviceTypeFromConfig;
    }

    return  getDeviceTypeFromPhysicalSize();
  }

  // Use `smallestScreenWidthDp` to determine the screen size
  // https://android-developers.googleblog.com/2011/07/new-tools-for-managing-screen-sizes.html
  private DeviceType getDeviceTypeFromResourceConfiguration() {
    int smallestScreenWidthDp = getReactApplicationContext().getResources().getConfiguration().smallestScreenWidthDp;

    if (smallestScreenWidthDp == Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED) {
      return DeviceType.UNKNOWN;
    }

    return smallestScreenWidthDp >= 600 ? DeviceType.TABLET : DeviceType.HANDSET;
  }

  private DeviceType getDeviceTypeFromPhysicalSize() {
    // Find the current window manager, if none is found we can't measure the device physical size.
    WindowManager windowManager = (WindowManager) getReactApplicationContext().getSystemService(Context.WINDOW_SERVICE);

    if (windowManager == null) {
      return DeviceType.UNKNOWN;
    }

    // Get display metrics to see if we can differentiate handsets and tablets.
    // NOTE: for API level 16 the metrics will exclude window decor.
    DisplayMetrics metrics = new DisplayMetrics();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      windowManager.getDefaultDisplay().getRealMetrics(metrics);
    } else {
      windowManager.getDefaultDisplay().getMetrics(metrics);
    }

    // Calculate physical size.
    double widthInches = metrics.widthPixels / (double) metrics.xdpi;
    double heightInches = metrics.heightPixels / (double) metrics.ydpi;
    double diagonalSizeInches = Math.sqrt(Math.pow(widthInches, 2) + Math.pow(heightInches, 2));

    if (diagonalSizeInches >= 3.0 && diagonalSizeInches <= 6.9) {
      // Devices in a sane range for phones are considered to be Handsets.
      return DeviceType.HANDSET;
    } else if (diagonalSizeInches > 6.9 && diagonalSizeInches <= 18.0) {
      // Devices larger than handset and in a sane range for tablets are tablets.
      return DeviceType.TABLET;
    } else {
      // Otherwise, we don't know what device type we're on/
      return DeviceType.UNKNOWN;
    }
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public float getFontScaleSync() { return getReactApplicationContext().getResources().getConfiguration().fontScale; }
  @ReactMethod
  public void getFontScale(Promise p) { p.resolve(getFontScaleSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public boolean isPinOrFingerprintSetSync() {
    KeyguardManager keyguardManager = (KeyguardManager) getReactApplicationContext().getSystemService(Context.KEYGUARD_SERVICE);
    if (keyguardManager != null) {
      return keyguardManager.isKeyguardSecure();
    }
    System.err.println("Unable to determine keyguard status. KeyguardManager was null");
    return false;
  }
  @ReactMethod
  public void isPinOrFingerprintSet(Promise p) { p.resolve(isPinOrFingerprintSetSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  @SuppressWarnings("ConstantConditions")
  public String getIpAddressSync() {
    try {
      return
              InetAddress.getByAddress(
                      ByteBuffer
                              .allocate(4)
                              .order(ByteOrder.LITTLE_ENDIAN)
                              .putInt(getWifiInfo().getIpAddress())
                              .array())
                      .getHostAddress();
    } catch (Exception e) {
      return "unknown";
    }
  }

  @ReactMethod
  public void getIpAddress(Promise p) { p.resolve(getIpAddressSync()); }

  @SuppressWarnings("deprecation")
  @ReactMethod(isBlockingSynchronousMethod = true)
  public boolean isCameraPresentSync() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      CameraManager manager=(CameraManager)getReactApplicationContext().getSystemService(Context.CAMERA_SERVICE);
      try {
        return manager.getCameraIdList().length > 0;
      } catch (Exception e) {
        return false;
      }
    } else {
      return Camera.getNumberOfCameras()> 0;
    }
  }
  @ReactMethod
  public void isCameraPresent(Promise p) { p.resolve(isCameraPresentSync()); }

  @SuppressLint("HardwareIds")
  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getMacAddressSync() {
    WifiInfo wifiInfo = getWifiInfo();
    String macAddress = "";
    if (wifiInfo != null) {
      macAddress = wifiInfo.getMacAddress();
    }

    String permission = "android.permission.INTERNET";
    int res = getReactApplicationContext().checkCallingOrSelfPermission(permission);

    if (res == PackageManager.PERMISSION_GRANTED) {
      try {
        List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
        for (NetworkInterface nif : all) {
          if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

          byte[] macBytes = nif.getHardwareAddress();
          if (macBytes == null) {
            macAddress = "";
          } else {

            StringBuilder res1 = new StringBuilder();
            for (byte b : macBytes) {
              res1.append(String.format("%02X:",b));
            }

            if (res1.length() > 0) {
              res1.deleteCharAt(res1.length() - 1);
            }

            macAddress = res1.toString();
          }
        }
      } catch (Exception ex) {
        // do nothing
      }
    }

    return macAddress;
  }

  @ReactMethod
  public void getMacAddress(Promise p) { p.resolve(getMacAddressSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getCarrierSync() {
    TelephonyManager telMgr = (TelephonyManager) getReactApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
    if (telMgr != null) {
      return telMgr.getNetworkOperatorName();
    } else {
      System.err.println("Unable to get network operator name. TelephonyManager was null");
      return "unknown";
    }
  }
  @ReactMethod
  public void getCarrier(Promise p) { p.resolve(getCarrierSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public double getTotalDiskCapacitySync() {
    try {
      StatFs root = new StatFs(Environment.getRootDirectory().getAbsolutePath());
      return BigInteger.valueOf(root.getBlockCount()).multiply(BigInteger.valueOf(root.getBlockSize())).doubleValue();
    } catch (Exception e) {
      return -1;
    }
  }
  @ReactMethod
  public void getTotalDiskCapacity(Promise p) { p.resolve(getTotalDiskCapacitySync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public double getFreeDiskStorageSync() {
    try {
      StatFs external = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
      long availableBlocks;
      long blockSize;

      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
        availableBlocks = external.getAvailableBlocks();
        blockSize = external.getBlockSize();
      } else {
        availableBlocks = external.getAvailableBlocksLong();
        blockSize = external.getBlockSizeLong();
      }

      return BigInteger.valueOf(availableBlocks).multiply(BigInteger.valueOf(blockSize)).doubleValue();
    } catch (Exception e) {
      return -1;
    }
  }
  @ReactMethod
  public void getFreeDiskStorage(Promise p) { p.resolve(getFreeDiskStorageSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public boolean isBatteryChargingSync(){
    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    Intent batteryStatus = getReactApplicationContext().registerReceiver(null, ifilter);
    int status = 0;
    if (batteryStatus != null) {
      status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    }
    return status == BATTERY_STATUS_CHARGING;
  }
  @ReactMethod
  public void isBatteryCharging(Promise p) { p.resolve(isBatteryChargingSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public int getUsedMemorySync() {
    Runtime rt = Runtime.getRuntime();
    long usedMemory = rt.totalMemory() - rt.freeMemory();
    return (int)usedMemory;
  }
  @ReactMethod
  public void getUsedMemory(Promise p) { p.resolve(getUsedMemorySync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap getPowerStateSync() {
    Intent intent = getReactApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    return getPowerStateFromIntent(intent);
  }

  @ReactMethod
  public void getPowerState(Promise p) { p.resolve(getPowerStateSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public double getBatteryLevelSync() {
    Intent intent = getReactApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    WritableMap powerState = getPowerStateFromIntent(intent);

    if(powerState == null) {
      return 0;
    }

    return powerState.getDouble(BATTERY_LEVEL);
  }

  @ReactMethod
  public void getBatteryLevel(Promise p) { p.resolve(getBatteryLevelSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public boolean isAirplaneModeSync() {
    boolean isAirplaneMode;
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
      isAirplaneMode = Settings.System.getInt(getReactApplicationContext().getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    } else {
      isAirplaneMode = Settings.Global.getInt(getReactApplicationContext().getContentResolver(),Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }
    return isAirplaneMode;
  }
  @ReactMethod
  public void isAirplaneMode(Promise p) { p.resolve(isAirplaneModeSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public boolean hasSystemFeatureSync(String feature) {
    if (feature == null || feature.equals("")) {
      return false;
    }

    return getReactApplicationContext().getPackageManager().hasSystemFeature(feature);
  }
  @ReactMethod
  public void hasSystemFeature(String feature, Promise p) { p.resolve(hasSystemFeatureSync(feature)); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableArray getSystemAvailableFeaturesSync() {
    final FeatureInfo[] featureList = getReactApplicationContext().getPackageManager().getSystemAvailableFeatures();

    WritableArray promiseArray = Arguments.createArray();
    for (FeatureInfo f : featureList) {
      if (f.name != null) {
        promiseArray.pushString(f.name);
      }
    }

    return promiseArray;
  }
  @ReactMethod
  public void getSystemAvailableFeatures(Promise p) { p.resolve(getSystemAvailableFeaturesSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public boolean isLocationEnabledSync() {
    boolean locationEnabled;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      LocationManager mLocationManager = (LocationManager) getReactApplicationContext().getSystemService(Context.LOCATION_SERVICE);
      try {
        locationEnabled = mLocationManager.isLocationEnabled();
      } catch (Exception e) {
        System.err.println("Unable to determine if location enabled. LocationManager was null");
        return false;
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      int locationMode = Settings.Secure.getInt(getReactApplicationContext().getContentResolver(), Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
      locationEnabled = locationMode != Settings.Secure.LOCATION_MODE_OFF;
    } else {
      String locationProviders = getString(getReactApplicationContext().getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
      locationEnabled = !TextUtils.isEmpty(locationProviders);
    }

    return locationEnabled;
  }
  @ReactMethod
  public void isLocationEnabled(Promise p) { p.resolve(isLocationEnabledSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableMap getAvailableLocationProvidersSync() {
    LocationManager mLocationManager = (LocationManager) getReactApplicationContext().getSystemService(Context.LOCATION_SERVICE);
    WritableMap providersAvailability = Arguments.createMap();
    try {
      List<String> providers = mLocationManager.getProviders(false);
      for (String provider : providers) {
        providersAvailability.putBoolean(provider, mLocationManager.isProviderEnabled(provider));
      }
    } catch (Exception e) {
      System.err.println("Unable to get location providers. LocationManager was null");
    }

    return providersAvailability;
  }
  @ReactMethod
  public void getAvailableLocationProviders(Promise p) { p.resolve(getAvailableLocationProvidersSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getInstallReferrerSync() {
    SharedPreferences sharedPref = getReactApplicationContext().getSharedPreferences("react-native-device-info", Context.MODE_PRIVATE);
    return sharedPref.getString("installReferrer", Build.UNKNOWN);
  }
  @ReactMethod
  public void getInstallReferrer(Promise p) { p.resolve(getInstallReferrerSync()); }

  private PackageInfo getPackageInfo() throws Exception {
    return getReactApplicationContext().getPackageManager().getPackageInfo(getReactApplicationContext().getPackageName(), 0);
  }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public double getFirstInstallTimeSync() {
    try {
      return (double)getPackageInfo().firstInstallTime;
    } catch (Exception e) {
      return -1;
    }
  }
  @ReactMethod
  public void getFirstInstallTime(Promise p) { p.resolve(getFirstInstallTimeSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public double getLastUpdateTimeSync() {
    try {
      return (double)getPackageInfo().lastUpdateTime;
    } catch (Exception e) {
      return -1;
    }
  }
  @ReactMethod
  public void getLastUpdateTime(Promise p) { p.resolve(getLastUpdateTimeSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getDeviceNameSync() {
    try {
      String bluetoothName = Settings.Secure.getString(getReactApplicationContext().getContentResolver(), "bluetooth_name");
      if (bluetoothName != null) {
        return bluetoothName;
      }

      if (Build.VERSION.SDK_INT >= 25) {
        String deviceName = Settings.Global.getString(getReactApplicationContext().getContentResolver(), Settings.Global.DEVICE_NAME);
        if (deviceName != null) {
          return deviceName;
        }
      }
    } catch (Exception e) {
      // same as default unknown return
    }
    return "unknown";
  }
  @ReactMethod
  public void getDeviceName(Promise p) { p.resolve(getDeviceNameSync()); }

  @SuppressLint({"HardwareIds", "MissingPermission"})
  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getSerialNumberSync() {
    try {
      if (Build.VERSION.SDK_INT >= 26) {
        if (getReactApplicationContext().checkCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
          return Build.getSerial();
        }
      }
    } catch (Exception e) {
      // This is almost always a PermissionException. We will log it but return unknown
      System.err.println("getSerialNumber failed, it probably should not be used: " + e.getMessage());
    }

    return "unknown";
  }
  @ReactMethod
  public void getSerialNumber(Promise p) { p.resolve(getSerialNumberSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getDeviceSync() {  return Build.DEVICE; }
  @ReactMethod
  public void getDevice(Promise p) { p.resolve(getDeviceSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getBuildIdSync() { return Build.ID; }
  @ReactMethod
  public void getBuildId(Promise p) { p.resolve(getBuildIdSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public int getApiLevelSync() { return Build.VERSION.SDK_INT; }
  @ReactMethod
  public void getApiLevel(Promise p) { p.resolve(getApiLevelSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getBootloaderSync() { return Build.BOOTLOADER; }
  @ReactMethod
  public void getBootloader(Promise p) { p.resolve(getBootloaderSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getDisplaySync() { return Build.DISPLAY; }
  @ReactMethod
  public void getDisplay(Promise p) { p.resolve(getDisplaySync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getFingerprintSync() { return Build.FINGERPRINT; }
  @ReactMethod
  public void getFingerprint(Promise p) { p.resolve(getFingerprintSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getHardwareSync() { return Build.HARDWARE; }
  @ReactMethod
  public void getHardware(Promise p) { p.resolve(getHardwareSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getHostSync() { return Build.HOST; }
  @ReactMethod
  public void getHost(Promise p) { p.resolve(getHostSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getProductSync() { return Build.PRODUCT; }
  @ReactMethod
  public void getProduct(Promise p) { p.resolve(getProductSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getTagsSync() { return Build.TAGS; }
  @ReactMethod
  public void getTags(Promise p) { p.resolve(getTagsSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getTypeSync() { return Build.TYPE; }
  @ReactMethod
  public void getType(Promise p) { p.resolve(getTypeSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getSystemManufacturerSync() { return Build.MANUFACTURER; }
  @ReactMethod
  public void getSystemManufacturer(Promise p) { p.resolve(getSystemManufacturerSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getCodenameSync() { return Build.VERSION.CODENAME; }
  @ReactMethod
  public void getCodename(Promise p) { p.resolve(getCodenameSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getIncrementalSync() { return Build.VERSION.INCREMENTAL; }
  @ReactMethod
  public void getIncremental(Promise p) { p.resolve(getIncrementalSync()); }

  @SuppressLint("HardwareIds")
  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getUniqueIdSync() { return getString(getReactApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID); }

  @SuppressLint("HardwareIds")
  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getAndroidIdSync() { return getUniqueIdSync(); }
  @ReactMethod
  public void getAndroidId(Promise p) { p.resolve(getAndroidIdSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public double getMaxMemorySync() { return (double)Runtime.getRuntime().maxMemory(); }
  @ReactMethod
  public void getMaxMemory(Promise p) { p.resolve(getMaxMemorySync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getDeviceTypeSync() { return getDeviceType().getValue(); }
  @ReactMethod
  public void getDeviceType(Promise p) { p.resolve(getDeviceTypeSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public double getTotalMemorySync() {
    ActivityManager actMgr = (ActivityManager) getReactApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
    ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
    if (actMgr != null) {
      actMgr.getMemoryInfo(memInfo);
    } else {
      System.err.println("Unable to getMemoryInfo. ActivityManager was null");
      return -1;
    }
    return (double)memInfo.totalMem;
  }
  @ReactMethod
  public void getTotalMemory(Promise p) { p.resolve(getTotalMemorySync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  @SuppressWarnings({"ConstantConditions", "deprecation"})
  public String getInstanceIdSync() {
    try {
      if (Class.forName("com.google.android.gms.iid.InstanceID") != null) {
        return com.google.android.gms.iid.InstanceID.getInstance(getReactApplicationContext()).getId();
      }
    } catch (ClassNotFoundException e) {
      System.err.println("N/A: Add com.google.android.gms:play-services-gcm to your project.");
    }
    return "unknown";
  }
  @ReactMethod
  public void getInstanceId(Promise p) { p.resolve(getInstanceIdSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getBaseOsSync() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return Build.VERSION.BASE_OS;
    }
    return "unknown";
  }
  @ReactMethod
  public void getBaseOs(Promise p) { p.resolve(getBaseOsSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getPreviewSdkIntSync() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return Integer.toString(Build.VERSION.PREVIEW_SDK_INT);
    }
    return "unknown";
  }
  @ReactMethod
  public void getPreviewSdkInt(Promise p) { p.resolve(getPreviewSdkIntSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getSecurityPatchSync() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return Build.VERSION.SECURITY_PATCH;
    }
    return "unknown";
  }
  @ReactMethod
  public void getSecurityPatch(Promise p) { p.resolve(getSecurityPatchSync()); }

  @ReactMethod
  public void getUserAgent(Promise p) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        p.resolve(WebSettings.getDefaultUserAgent(getReactApplicationContext()));
      } else {
        p.resolve(System.getProperty("http.agent"));
      }
    } catch (RuntimeException e) {
      p.resolve(System.getProperty("http.agent"));
    }
  }

  @SuppressLint({"HardwareIds", "MissingPermission"})
  @ReactMethod(isBlockingSynchronousMethod = true)
  public String getPhoneNumberSync() {
    if (getReactApplicationContext() != null &&
            (getReactApplicationContext().checkCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && getReactApplicationContext().checkCallingOrSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getReactApplicationContext().checkCallingOrSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED))) {
      TelephonyManager telMgr = (TelephonyManager) getReactApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
      if (telMgr != null) {
        return telMgr.getLine1Number();
      } else {
        System.err.println("Unable to getPhoneNumber. TelephonyManager was null");
      }
    }
    return "unknown";
  }
  @ReactMethod
  public void getPhoneNumber(Promise p) { p.resolve(getPhoneNumberSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableArray getSupportedAbisSync() {
    WritableArray array = new WritableNativeArray();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      for (String abi : Build.SUPPORTED_ABIS) {
        array.pushString(abi);
      }
    } else {
      array.pushString(Build.CPU_ABI);
    }
    return array;
  }
  @ReactMethod
  public void getSupportedAbis(Promise p) { p.resolve(getSupportedAbisSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableArray getSupported32BitAbisSync() {
    WritableArray array = new WritableNativeArray();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      for (String abi : Build.SUPPORTED_32_BIT_ABIS) {
        array.pushString(abi);
      }
    }
    return array;
  }
  @ReactMethod
  public void getSupported32BitAbis(Promise p) { p.resolve(getSupported32BitAbisSync()); }

  @ReactMethod(isBlockingSynchronousMethod = true)
  public WritableArray getSupported64BitAbisSync() {
    WritableArray array = new WritableNativeArray();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      for (String abi : Build.SUPPORTED_64_BIT_ABIS) {
        array.pushString(abi);
      }
    }
    return array;
  }
  @ReactMethod
  public void getSupported64BitAbis(Promise p) { p.resolve(getSupported64BitAbisSync()); }


  private WritableMap getPowerStateFromIntent (Intent intent) {
    if(intent == null) {
      return null;
    }

    int batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int batteryScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    int isPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
    int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

    float batteryPercentage = batteryLevel / (float)batteryScale;

    String batteryState = "unknown";

    if(isPlugged == 0) {
      batteryState = "unplugged";
    } else if(status == BATTERY_STATUS_CHARGING) {
      batteryState = "charging";
    } else if(status == BATTERY_STATUS_FULL) {
      batteryState = "full";
    }

    PowerManager powerManager = (PowerManager)getReactApplicationContext().getSystemService(Context.POWER_SERVICE);
    boolean powerSaveMode = false;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      powerSaveMode = powerManager.isPowerSaveMode();
    }

    WritableMap powerState = Arguments.createMap();
    powerState.putString(BATTERY_STATE, batteryState);
    powerState.putDouble(BATTERY_LEVEL, batteryPercentage);
    powerState.putBoolean(LOW_POWER_MODE, powerSaveMode);

    return powerState;
  }

  private void sendEvent(ReactContext reactContext,
                         String eventName,
                         @Nullable Object data) {
    reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, data);
  }
}
