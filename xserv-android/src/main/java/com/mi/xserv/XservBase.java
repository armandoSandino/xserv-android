/***
 * XservBase
 * <p/>
 * Copyright (C) 2015 Giovanni Amati
 * <p/>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see http://www.gnu.org/licenses/.
 ***/

package com.mi.xserv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class XservBase {
    protected final Handler mHandler = new Handler(Looper.getMainLooper());
    private WeakReference<OnXservEventListener> mDelegate;

    public XservBase() {
        mDelegate = new WeakReference<>(null);
    }

    public void setOnEventListener(OnXservEventListener onEventListener) {
        mDelegate = new WeakReference<>(onEventListener);
    }

    protected String getDeviceID() {
        OnXservEventListener delegate = mDelegate.get();

        String deviceID = null;
        if (delegate != null) {
            try {
                deviceID = Settings.Secure.getString(((Context) delegate).getContentResolver(),
                        Settings.Secure.ANDROID_ID);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (deviceID == null) {
            deviceID = UUID.randomUUID().toString();
        }

        return deviceID;
    }

    protected String getLanguage() {
        // it-IT
        return Locale.getDefault().toString().replace("_", "-");
    }

    protected int getTimeZoneOffset() {
        // GMT es. italia +1
        TimeZone timezone = TimeZone.getDefault();
        int seconds = timezone.getOffset(Calendar.ZONE_OFFSET) / 1000;
        double minutes = seconds / 60;
        double hours = minutes / 60;
        return (int) hours;
    }

    protected int getTimeZoneDst() {
        // Daylight savings
        Date today = new Date();
        TimeZone timezone = TimeZone.getDefault();
        boolean isDST = timezone.inDaylightTime(today);
        return isDST ? 1 : 0;
    }

    protected void onOpenConnection() {
        final OnXservEventListener delegate = mDelegate.get();

        if (delegate != null) {
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    delegate.OnOpenConnection();
                }
            });
        }
    }

    protected void onCloseConnection(final Exception e) {
        final OnXservEventListener delegate = mDelegate.get();

        if (delegate != null) {
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    delegate.OnCloseConnection(e);
                }
            });
        }
    }

    protected void onErrorConnection(final Exception e) {
        final OnXservEventListener delegate = mDelegate.get();

        if (delegate != null) {
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    delegate.OnErrorConnection(e);
                }
            });
        }
    }

    protected void onReceiveEvents(final JSONObject json) {
        final OnXservEventListener delegate = mDelegate.get();

        if (delegate != null) {
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    delegate.OnReceiveEvents(json);
                }
            });
        }
    }

    protected void onReceiveOpsResponse(final JSONObject json) {
        final OnXservEventListener delegate = mDelegate.get();

        if (delegate != null) {
            mHandler.post(new Runnable() {

                @Override
                public void run() {
                    delegate.OnReceiveOpsResponse(json);
                }
            });
        }
    }

}
