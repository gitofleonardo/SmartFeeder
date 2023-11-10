package com.google.android.gsa.overlay.ui.panel;

import android.util.FloatProperty;
import android.util.Log;

final class SlidingPanelLayoutProperty extends FloatProperty<SlidingPanelLayout> {

    SlidingPanelLayoutProperty(String str) {
        super(str);
    }

    @Override
    public void setValue(SlidingPanelLayout object, float value) {
        object.setPanelX((int) value);
    }

    @Override
    public Float get(SlidingPanelLayout object) {
        return (float) object.panelX;
    }
}