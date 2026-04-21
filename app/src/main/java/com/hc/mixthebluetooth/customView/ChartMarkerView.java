package com.hc.mixthebluetooth.customView;

import android.content.Context;
import android.widget.TextView;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import com.hc.mixthebluetooth.R;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChartMarkerView extends MarkerView {

    private TextView tvValue;
    private TextView tvStatus;
    private TextView tvTime;
    private SimpleDateFormat timeFormat;
    private long startTime;
    private float highThreshold;
    private float lowThreshold;

    public ChartMarkerView(Context context, int layoutResource, long startTime, float highThreshold, float lowThreshold) {
        super(context, layoutResource);
        tvValue = findViewById(R.id.tvValue);
        tvStatus = findViewById(R.id.tvStatus);
        tvTime = findViewById(R.id.tvTime);
        timeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        this.startTime = startTime;
        this.highThreshold = highThreshold;
        this.lowThreshold = lowThreshold;
    }

    // callback to refresh the content
    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        float value = e.getY();

        // Display value
        tvValue.setText(String.format(Locale.getDefault(), "%.2f", value));

        // Display time
        long entryTimeMillis = startTime + (long)(e.getX() * 1000);
        tvTime.setText(timeFormat.format(new Date(entryTimeMillis)));

        // Determine and display status
        String status = "";
        boolean showMarker = false;

        if (value > highThreshold) {
            status = "偏高";
            showMarker = true;
        } else if (value < lowThreshold) {
            status = "偏低";
            showMarker = true;
        } else {
            // Value is within normal range, do not show marker content
            status = "";
            tvValue.setText(""); // Clear value text
            tvTime.setText(""); // Clear time text
            // We cannot directly hide the marker view from here, the chart controls visibility based on highlighting.
            // However, by clearing the text, the marker will appear empty.
        }
        tvStatus.setText(status);

        // Although we clear the text for normal range, the marker view itself is still highlighted.
        // To truly hide it, we might need a different approach depending on how the chart handles markers.
        // For now, clearing the text makes it appear empty when within range.

        super.refreshContent(e, highlight);

        // A more effective way to hide the marker when value is in normal range
        // might involve checking 'showMarker' and potentially adjusting the marker view's alpha or dimensions.
        // However, directly modifying the view's visibility state from refreshContent can be problematic with MPAndroidChart's marker handling.
        // Let's rely on clearing the text for now as a simple approach.
    }

    // Override draw to prevent drawing when not needed (normal range)
    // This is an advanced technique and might require more context on the chart's drawing lifecycle.
    // Keeping the simple text clearing approach for now.

    @Override
    public MPPointF getOffset() {
        // Center the marker horizontally and position it above the entry
        return new MPPointF(-(getWidth() / 2), -getHeight());
    }
} 