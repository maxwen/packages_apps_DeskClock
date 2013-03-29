/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.deskclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

/**
 * Displays the time
 */
public class DigitalClock extends LinearLayout {

    private final static String HOURS_24 = "kk";
    private final static String HOURS = "h";
    private final static String MINUTES = ":mm";
    private final static String AM_PM = " aa";

    private final static int SLEEP_CYCLE = 90;
    private final static int APPROX_BEDTIME = 194;
    private final static int BEDTIMES = 4;

    private Calendar mCalendar;
    private String mHoursFormat;
    private TextView mTimeDisplayHours, mTimeDisplayMinutes;
    private AmPm mAmPm;
    private ContentObserver mFormatChangeObserver;
    private boolean mLive = true;
    private boolean mAttached;
    private final Typeface mRobotoThin;
    private String mTimeZoneId;
    private SharedPreferences mPrefs;

    /* called by system on minute ticks */
    private final Handler mHandler = new Handler();
    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mLive && intent.getAction().equals(
                            Intent.ACTION_TIMEZONE_CHANGED)) {
                    mCalendar = Calendar.getInstance();
                }
                // Post a runnable to avoid blocking the broadcast.
                mHandler.post(new Runnable() {
                        public void run() {
                            updateTime();
                        }
                });
            }
        };

    static class AmPm {
        private final TextView mAmPm;
        private final String mAmString, mPmString;

        AmPm(View parent) {
            mAmPm = (TextView) parent.findViewById(R.id.am_pm);

            String[] ampm = new DateFormatSymbols().getAmPmStrings();
            mAmString = ampm[0];
            mPmString = ampm[1];
        }

        void setShowAmPm(boolean show) {
            mAmPm.setVisibility(show ? View.VISIBLE : View.GONE);
        }

        void setIsMorning(boolean isMorning) {
            mAmPm.setText(isMorning ? mAmString : mPmString);
        }

        void setTextColor(int color) {
            mAmPm.setTextColor(color);
        }

        CharSequence getAmPmText() {
            return mAmPm.getText();
        }
    }

    private class FormatChangeObserver extends ContentObserver {
        public FormatChangeObserver() {
            super(new Handler());
        }
        @Override
        public void onChange(boolean selfChange) {
            setDateFormat();
            updateTime();
        }
    }

    private class ColorChangeObserver extends ContentObserver {
        public ColorChangeObserver() {
            super(new Handler());
        }
        @Override
        public void onChange(boolean selfChange) {
            setColors();
            updateTime();
        }
    }

    public DigitalClock(Context context) {
        this(context, null);
    }

    public DigitalClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRobotoThin = Typeface.createFromAsset(context.getAssets(),"fonts/Roboto-Thin.ttf");
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mPrefs =  PreferenceManager.getDefaultSharedPreferences(getContext());

        mTimeDisplayHours = (TextView)findViewById(R.id.timeDisplayHours);
        mTimeDisplayMinutes = (TextView)findViewById(R.id.timeDisplayMinutes);
        mTimeDisplayMinutes.setTypeface(mRobotoThin);
        mAmPm = new AmPm(this);
        mCalendar = Calendar.getInstance();

        setDateFormat();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (Log.LOGV) Log.v("onAttachedToWindow " + this);

        if (mAttached) return;
        mAttached = true;

        if (mLive) {
            /* monitor time ticks, time changed, timezone */
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            getContext().registerReceiver(mIntentReceiver, filter);
        }

        /* monitor 12/24-hour display preference */
        mFormatChangeObserver = new FormatChangeObserver();
        getContext().getContentResolver().registerContentObserver(
                Settings.System.CONTENT_URI, true, mFormatChangeObserver);

        updateTime();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (!mAttached) return;
        mAttached = false;

        if (mLive) {
            getContext().unregisterReceiver(mIntentReceiver);
        }
        getContext().getContentResolver().unregisterContentObserver(
                mFormatChangeObserver);
    }


    void updateTime(Calendar c) {
        mCalendar = c;
        updateTime();
    }

    public void updateTime(int hour, int minute) {
        // set the alarm text
        final Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        mCalendar = c;
        updateTime();
    }

    private void updateTime() {
        if (mLive) {
            mCalendar.setTimeInMillis(System.currentTimeMillis());
        }
        if (mTimeZoneId != null) {
            mCalendar.setTimeZone(TimeZone.getTimeZone(mTimeZoneId));
        }

        StringBuilder fullTimeStr = new StringBuilder();
        CharSequence newTime = DateFormat.format(mHoursFormat, mCalendar);
        mTimeDisplayHours.setText(newTime);
        fullTimeStr.append(newTime);
        newTime = DateFormat.format(MINUTES, mCalendar);
        fullTimeStr.append(newTime);
        mTimeDisplayMinutes.setText(newTime);

        boolean isMorning = mCalendar.get(Calendar.AM_PM) == 0;
        mAmPm.setIsMorning(isMorning);
        if (!Alarms.get24HourMode(getContext())) {
            fullTimeStr.append(mAmPm.getAmPmText());
        }

        setColors();

        // Update accessibility string.
        setContentDescription(fullTimeStr);
    }

    private void setDateFormat() {
        mHoursFormat = Alarms.get24HourMode(getContext()) ? HOURS_24 : HOURS;
        mAmPm.setShowAmPm(!Alarms.get24HourMode(getContext()));
    }

    private void setColors() {
        int colorTime = mPrefs.getInt("digital_clock_time_color",
            getContext().getResources().getColor(R.color.clock_white));

        mTimeDisplayHours.setTextColor(colorTime);
        mTimeDisplayMinutes.setTextColor(colorTime);
        mAmPm.setTextColor(colorTime);
    }
    void setLive(boolean live) {
        mLive = live;
    }

    public void setTimeZone(String id) {
        mTimeZoneId = id;
        updateTime();
    }

    public String getSuggestedSleepTimes() {
        // The DateFormat pattern
        String pattern = mHoursFormat + MINUTES + AM_PM;
        if (Alarms.get24HourMode(getContext())) {
            pattern = mHoursFormat + MINUTES;
        }

        // Keeps the set of suggested sleep times
        final Set<Date> dateSet = new TreeSet<Date>();

        final Calendar calendar = Calendar.getInstance();
        // Get the alarm time
        calendar.setTime(mCalendar.getTime());
        // The fourteen minutes it takes to fall asleep plus two sleep cycles
        calendar.add(Calendar.MINUTE, -APPROX_BEDTIME);
        // Return four possible sleep times
        for (int i = 0; i < BEDTIMES; i++) {
            // Go back one sleep cycle from here to calculate the times
            calendar.add(Calendar.MINUTE, -SLEEP_CYCLE);
            dateSet.add(calendar.getTime());
        }

        // Format each sleep time and separate them by a comma
        final StringBuilder builder = new StringBuilder();
        final Iterator<Date> iterator = dateSet.iterator();
        while (iterator.hasNext()) {
            final Date date = iterator.next();
            builder.append(DateFormat.format(pattern, date));
            if (iterator.hasNext()) {
                builder.append(", ");
            }
        }
        return builder.toString();
    }
}
