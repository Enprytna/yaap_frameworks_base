/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.settings.brightness;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.app.Activity;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;

import javax.inject.Inject;

/** A dialog that provides controls for adjusting the screen brightness. */
public class BrightnessDialog extends Activity {

    private BrightnessController mBrightnessController;
    private final BrightnessSlider.Factory mToggleSliderFactory;
    private final BroadcastDispatcher mBroadcastDispatcher;

    private ImageView mAutoBrightnessIcon;

    private final CustomSettingsObserver mCustomSettingsObserver = new CustomSettingsObserver();
    private class CustomSettingsObserver extends ContentObserver {
        CustomSettingsObserver() {
            super(new Handler(Looper.getMainLooper()));
        }

        void observe() {
            getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.QS_SHOW_AUTO_BRIGHTNESS_BUTTON),
                    false, this, UserHandle.USER_ALL);
        }

        void stop() {
            getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (mAutoBrightnessIcon == null) return;
            boolean show = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.QS_SHOW_AUTO_BRIGHTNESS_BUTTON, 1) == 1;
            mAutoBrightnessIcon.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Inject
    public BrightnessDialog(
            BroadcastDispatcher broadcastDispatcher,
            BrightnessSlider.Factory factory) {
        mBroadcastDispatcher = broadcastDispatcher;
        mToggleSliderFactory = factory;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Window window = getWindow();

        window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.requestFeature(Window.FEATURE_NO_TITLE);

        // Calling this creates the decor View, so setLayout takes proper effect
        // (see Dialog#onWindowAttributesChanged)
        window.getDecorView();
        window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        setContentView(R.layout.brightness_mirror_container);
        FrameLayout frame = findViewById(R.id.brightness_mirror_container);
        // The brightness mirror container is INVISIBLE by default.
        frame.setVisibility(View.VISIBLE);

        BrightnessSlider controller = mToggleSliderFactory.create(this, frame);
        controller.init();
        frame.addView(controller.getRootView(), MATCH_PARENT, WRAP_CONTENT);
        mAutoBrightnessIcon = controller.getIconView();
        boolean show = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.QS_SHOW_AUTO_BRIGHTNESS_BUTTON, 1) == 1;
        mAutoBrightnessIcon.setVisibility(show ? View.VISIBLE : View.GONE);
        mBrightnessController = new BrightnessController(this, mAutoBrightnessIcon,
                controller, mBroadcastDispatcher);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mBrightnessController.registerCallbacks();
        MetricsLogger.visible(this, MetricsEvent.BRIGHTNESS_DIALOG);
        mCustomSettingsObserver.observe();
    }

    @Override
    protected void onStop() {
        super.onStop();
        MetricsLogger.hidden(this, MetricsEvent.BRIGHTNESS_DIALOG);
        mBrightnessController.unregisterCallbacks();
        mCustomSettingsObserver.stop();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }
}
