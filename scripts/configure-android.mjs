import fs from 'node:fs';
import path from 'node:path';

const rootDir = process.cwd();
const manifestPath = path.join(rootDir, 'android/app/src/main/AndroidManifest.xml');
const mainActivityPath = path.join(
  rootDir,
  'android/app/src/main/java/com/gmax/wildwood/MainActivity.java'
);

function patchManifest() {
  if (!fs.existsSync(manifestPath)) {
    throw new Error(`AndroidManifest.xml not found at ${manifestPath}`);
  }

  let xml = fs.readFileSync(manifestPath, 'utf8');

  // 1. Ensure android:hardwareAccelerated="true" on <application>
  if (!/android:hardwareAccelerated=/.test(xml)) {
    xml = xml.replace('<application', '<application\n        android:hardwareAccelerated="true"');
  } else {
    xml = xml.replace(/android:hardwareAccelerated="[^"]*"/, 'android:hardwareAccelerated="true"');
  }

  // 2. Patch main <activity> orientation and configChanges
  const desiredConfigChanges =
    'orientation|keyboardHidden|keyboard|screenSize|locale|smallestScreenSize|screenLayout|uiMode';

  xml = xml.replace(/<activity\b([\s\S]*?)>/, (match, attrs) => {
    let updated = attrs;

    if (/android:screenOrientation=/.test(updated)) {
      updated = updated.replace(
        /android:screenOrientation="[^"]*"/,
        'android:screenOrientation="sensorLandscape"'
      );
    } else {
      updated += '\n            android:screenOrientation="sensorLandscape"';
    }

    if (/android:configChanges=/.test(updated)) {
      updated = updated.replace(
        /android:configChanges="[^"]*"/,
        `android:configChanges="${desiredConfigChanges}"`
      );
    } else {
      updated += `\n            android:configChanges="${desiredConfigChanges}"`;
    }

    return `<activity${updated}>`;
  });

  // 3. Ensure VIBRATE permission is declared
  const vibrateTag = '<uses-permission android:name="android.permission.VIBRATE" />';
  if (!xml.includes('android.permission.VIBRATE')) {
    if (xml.includes('</manifest>')) {
      xml = xml.replace('</manifest>', `    ${vibrateTag}\n</manifest>`);
    } else {
      xml += `\n${vibrateTag}\n`;
    }
  }

  fs.writeFileSync(manifestPath, xml, 'utf8');
  console.log('[configure-android] Patched AndroidManifest.xml cleanly.');
}

function writeMainActivity() {
  const activityDir = path.dirname(mainActivityPath);
  fs.mkdirSync(activityDir, { recursive: true });

  const javaSource = `package com.gmax.wildwood;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        enableImmersiveFullscreen();
        configureWebViewForWebGLAndAudio();
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWebViewForWebGLAndAudio();
        enableImmersiveFullscreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableImmersiveFullscreen();
        }
    }

    private void enableImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), decorView);
        if (controller != null) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            //noinspection deprecation
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    private void configureWebViewForWebGLAndAudio() {
        if (getBridge() == null) return;
        WebView webView = getBridge().getWebView();
        if (webView == null) return;

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
    }
}
`;

  fs.writeFileSync(mainActivityPath, javaSource, 'utf8');
  console.log('[configure-android] Configured MainActivity.java for immersive fullscreen WebGL2 & audio.');
}

function cleanChatAssetsFromDist() {
  const aistudioAssets = path.join(rootDir, 'dist/assets/aistudio');
  if (fs.existsSync(aistudioAssets)) {
    fs.rmSync(aistudioAssets, { recursive: true, force: true });
    console.log('[configure-android] Excluded temporary chat assets from dist bundle.');
  }
}

cleanChatAssetsFromDist();
patchManifest();
writeMainActivity();
