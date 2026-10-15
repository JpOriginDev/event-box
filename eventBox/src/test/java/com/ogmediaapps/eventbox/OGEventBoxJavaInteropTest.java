package com.ogmediaapps.eventbox;

import android.widget.ImageView;

import org.junit.Test;
import static org.junit.Assert.*;

public class OGEventBoxJavaInteropTest {

    @Test
    public void testJavaFluentBubbleStyling() {
        OGEventBoxLog.setBubbleIconResId(123)
            .setBubbleIconScaleType(ImageView.ScaleType.CENTER_CROP)
            .setBubbleBackgroundColor(0xFF0D9379)
            .setBubbleBorderColor(0xFFCCE6E1)
            .setBubbleBorderWidthDp(1.5f)
            .setBubbleCornerRadiusDp(-1f);

        assertEquals(123, OGEventBoxLog.getBubbleIconResId());
        assertEquals(ImageView.ScaleType.CENTER_CROP, OGEventBoxLog.getBubbleIconScaleType());
        assertEquals(0xFF0D9379, OGEventBoxLog.getBubbleBackgroundColor());
        assertEquals(0xFFCCE6E1, OGEventBoxLog.getBubbleBorderColor());
        assertEquals(1.5f, OGEventBoxLog.getBubbleBorderWidthDp(), 0.001f);
        assertEquals(-1f, OGEventBoxLog.getBubbleCornerRadiusDp(), 0.001f);
    }

    @Test
    public void testJavaFluentDebugModeSettings() {
        OGEventBoxLog.setDebugModeEnabled(true)
            .setDebugModeDurationHours(2.5);

        assertTrue(OGEventBoxLog.getDebugModeEnabled());
        assertEquals(2.5, OGEventBoxLog.getDebugModeDurationHours(), 0.001);

        // Reset
        OGEventBoxLog.setDebugModeEnabled(false)
            .setDebugModeDurationHours(0.5);
        assertFalse(OGEventBoxLog.getDebugModeEnabled());
        assertEquals(0.5, OGEventBoxLog.getDebugModeDurationHours(), 0.001);
    }
}
