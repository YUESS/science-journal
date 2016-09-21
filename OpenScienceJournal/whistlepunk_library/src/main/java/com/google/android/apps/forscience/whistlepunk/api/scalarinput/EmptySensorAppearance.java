/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.google.android.apps.forscience.whistlepunk.api.scalarinput;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.google.android.apps.forscience.javalib.Consumer;
import com.google.android.apps.forscience.whistlepunk.AxisNumberFormat;
import com.google.android.apps.forscience.whistlepunk.R;
import com.google.android.apps.forscience.whistlepunk.SensorAnimationBehavior;
import com.google.android.apps.forscience.whistlepunk.SensorAppearance;

import java.text.NumberFormat;

/**
 * Provides sensible "blank" values for all methods of {@link SensorAppearance}.
 *
 * To use, override with whatever values you have, and leave the rest with default behaviors.
 *
 * @see {ScalarInputSpec}
 */
class EmptySensorAppearance implements SensorAppearance {
    private static final int DEFAULT_DRAWABLE = R.drawable.ic_sensor_raw_white_24dp;

    @Override
    public String getName(Context context) {
        return "";
    }

    @Override
    public String getUnits(Context context) {
        return "";
    }

    @Override
    public Drawable getIconDrawable(Context context) {
        return context.getResources().getDrawable(DEFAULT_DRAWABLE);
    }

    @Override
    public String getShortDescription(Context context) {
        return null;
    }

    @Override
    public boolean hasLearnMore() {
        return false;
    }

    @Override
    public void loadLearnMore(Context context, Consumer<LearnMoreContents> onLoad) {
        throw new UnsupportedOperationException("I told you I don't have learnMore");
    }

    @Override
    public SensorAnimationBehavior getSensorAnimationBehavior() {
        return SensorAnimationBehavior.createDefault();
    }

    @Override
    public NumberFormat getNumberFormat() {
        return new AxisNumberFormat();
    }
}
