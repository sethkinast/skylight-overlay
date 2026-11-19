package net.cogdev.skylightoverlay;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The bottom menu bar, as an overlay.
 *
 * What it covers, measured from the 2026-10-29 screenshot
 * (1440x2560, density 320):
 * - bar top y = 2452px = 95.78% of height; bar height 108px = 54dp
 * - bar background #E9F1F7 (233,241,247)
 * - native cells are 108px wide, centres 108px apart:
 *   Home 54, Calendar 162, Lists 270, Tasks 378, Rewards 486,
 *   Meals 594, Recipes 702, Photos 810, Sleep 1278, Settings 1386
 * - the four middle items, Rewards through Photos, occupy
 *   x 432-864px = 30.0%-60.2% of width. That was the covered region.
 *
 * No cover any more (Skylight update, 2026-11-19: native icons
 * can be hidden in Skylight settings, so there is nothing to
 * hide). This view is transparent — the native bar background
 * shows through — and it only draws the buttons we register.
 * Unfilled slots are transparent, non-clickable spacers, so
 * taps there pass through to the native bar underneath.
 *
 * Registering a button — the infra Seth asked for:
 *   menuBar.register("Lunch", MenuButtonView.Icon.GEAR, tap, longPress);
 * That is the whole change. Buttons fill slots left to right, in
 * registration order, one native-sized cell each (54dp wide).
 * No layout code, no position math, per button.
 *
 * Currently registered, in order:
 *   1. Hatch — tap: Home; hold 5s: Android Settings.
 *   2. BanKin — tap: open the kids' bank webapp in page mode.
 * Slots 3-4 are blank, ready for the family buttons that
 * will replace Skylight functions (lunch, dinner, etc.).
 */
public class MenuBarView extends LinearLayout {

    public static final int MAX_BUTTONS = 4;
    public static final float COVER_LEFT = 0.300f;
    public static final float COVER_RIGHT = 0.602f;
    public static final float BAR_TOP = 0.9578f;

    private static class Entry {
        final String label;
        final MenuButtonView.Icon icon;
        final Runnable onTap;
        final Runnable onLongPress;

        Entry(String label, MenuButtonView.Icon icon, Runnable onTap, Runnable onLongPress) {
            this.label = label;
            this.icon = icon;
            this.onTap = onTap;
            this.onLongPress = onLongPress;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final List<MenuButtonView> buttons = new ArrayList<>();

    public MenuBarView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setBackgroundColor(Color.TRANSPARENT);
        rebuild();
    }

    /** Register a menu button. Slots fill left to right, max 4. */
    public void register(String label, MenuButtonView.Icon icon,
                         Runnable onTap, Runnable onLongPress) {
        if (entries.size() >= MAX_BUTTONS) return;
        entries.add(new Entry(label, icon, onTap, onLongPress));
        rebuild();
    }

    /**
     * Mark one button active (white cell, like Skylight's
     * selected Calendar), or clear all with null. Used when
     * a page is open: the tab that opened it is the active one.
     */
    public void setActive(String label) {
        for (MenuButtonView button : buttons) {
            button.setSelected(label != null && label.equals(button.getLabel()));
        }
    }

    private void rebuild() {
        removeAllViews();
        buttons.clear();
        for (Entry e : entries) {
            MenuButtonView button = new MenuButtonView(getContext(), e.label, e.icon,
                    e.onTap, e.onLongPress, MenuButtonView.DEFAULT_HOLD_MS);
            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            addView(button, lp);
            buttons.add(button);
        }
        // Blank slots: transparent and not clickable, so taps
        // pass through to the native bar underneath (nothing
        // is hidden any more — see the class comment).
        for (int i = entries.size(); i < MAX_BUTTONS; i++) {
            View spacer = new View(getContext());
            spacer.setBackgroundColor(Color.TRANSPARENT);
            spacer.setClickable(false);
            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            addView(spacer, lp);
        }
    }
}
