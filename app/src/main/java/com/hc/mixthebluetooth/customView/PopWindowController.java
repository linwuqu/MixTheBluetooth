package com.hc.mixthebluetooth.customView;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import androidx.core.widget.PopupWindowCompat;

import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.storage.Storage;

import java.util.HashMap;
import java.util.Map;

/**
 * PopWindow 配置模型 — 将 UI 状态与业务逻辑分离。
 *
 * 设计原则：
 * - 每个 CheckBox/Switch 选项抽象为 PopOption，不再与具体 View ID 耦合
 * - PopWindowController 负责弹出/关闭/保存/加载，PopOption 负责值本身
 * - 一个 Fragment 可以有多个 PopWindowController，各自管理独立的配置组
 *
 * 使用示例（FragmentMessage 重构后）：
 *
 * <pre>{@code
 * // 1. 定义配置
 * private final PopWindowController settingsWindow = new PopWindowController(
 *         R.layout.pop_window_message_fragment,
 *         new PopOption(KEY_HEX_SEND,    R.id.pop_fragment_hex_send),
 *         new PopOption(KEY_HEX_READ,    R.id.pop_fragment_hex_read),
 *         new PopOption(KEY_DATA,        R.id.pop_fragment_data),
 *         new PopOption(KEY_TIME,        R.id.pop_fragment_time),
 *         new PopOption(KEY_CLEAR,       R.id.pop_fragment_clear_recycler),
 *         new PopOption(KEY_NEWLINE,     R.id.pop_fragment_newline)
 * );
 *
 * // 2. 显示弹窗
 * settingsWindow.show(view, this, new DismissListener() {
 *     @Override public void onDismiss() { refreshState(); }
 *     @Override public void onClear()   { recycler.clear(); }
 * });
 *
 * // 3. 读取当前状态
 * boolean isHexSend  = settingsWindow.isChecked(KEY_HEX_SEND);
 * boolean isHexRead  = settingsWindow.isChecked(KEY_HEX_READ);
 * }</pre>
 *
 * 如果需要自定义点击行为（如清空按钮），使用 onItemClick：
 *
 * <pre>{@code
 * settingsWindow.onItemClick(R.id.pop_fragment_clear, v -> {
 *     recycler.clear();
 *     settingsWindow.dismiss();
 * });
 * }</pre>
 */
public class PopWindowController {

    // ─── 单个选项模型 ───────────────────────────────────────────────

    /**
     * 单个弹出选项。
     * @param key     SharedPreferences 存储键
     * @param viewId  对应布局中的 CheckBox/Switch ID
     */
    public static final class PopOption {
        public final String key;
        public final int    viewId;

        public PopOption(String key, int viewId) {
            this.key   = key;
            this.viewId = viewId;
        }
    }

    // ─── 生命周期回调 ───────────────────────────────────────────────

    public interface DismissListener {
        /** 弹窗关闭时调用（保存状态已完成） */
        void onDismiss();
        /** 清空按钮点击 */
        void onClear();
    }

    /** 自定义项点击回调 */
    public interface ItemClickListener {
        void onClick(View v);
    }

    // ─── 状态 ──────────────────────────────────────────────────────

    private final int             layoutRes;
    private final PopOption[]     options;
    private final Storage         storage;
    private final Map<Integer, ItemClickListener> itemClickListeners = new HashMap<>();

    private View                contentView;
    private PopupWindow         popupWindow;
    private DismissListener      dismissListener;

    // ─── 构造 ──────────────────────────────────────────────────────

    public PopWindowController(int layoutRes, PopOption... options) {
        this.layoutRes = layoutRes;
        this.options   = options;
        this.storage   = null; // 需要在 show() 时传入 Context
    }

    public PopWindowController(Activity activity, int layoutRes, PopOption... options) {
        this.layoutRes = layoutRes;
        this.options   = options;
        this.storage   = new Storage(activity);
    }

    // ─── 显示 / 关闭 ───────────────────────────────────────────────

    /**
     * 显示弹窗。
     * @param anchor  锚点 View（弹出位置）
     * @param ctx     Context
     * @param listener 关闭回调
     */
    public void show(View anchor, Activity ctx, DismissListener listener) {
        this.dismissListener = listener;
        Storage st = storage != null ? storage : new Storage(ctx);

        View content = LayoutInflater.from(anchor.getContext()).inflate(layoutRes, null);
        contentView = content;

        popupWindow = new PopupWindow(content,
                ctx.getWindowManager().getDefaultDisplay().getWidth(),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);

        popupWindow.setTouchable(true);
        popupWindow.setAnimationStyle(R.style.pop_window_anim);

        content.measure(
                makeDropDownMeasureSpec(popupWindow.getWidth()),
                makeDropDownMeasureSpec(popupWindow.getHeight()));

        int offsetX = anchor.getWidth() - content.getMeasuredWidth();
        PopupWindowCompat.showAsDropDown(popupWindow, anchor, offsetX, 0, Gravity.START);

        loadState(st);
        applyClickListeners(content);

        popupWindow.setOnDismissListener(() -> {
            saveState(st);
            if (dismissListener != null) dismissListener.onDismiss();
        });
    }

    public void dismiss() {
        if (popupWindow != null) popupWindow.dismiss();
    }

    // ─── 状态加载 / 保存 ────────────────────────────────────────────

    private void loadState(Storage st) {
        for (PopOption opt : options) {
            CheckBoxSample cb = contentView.findViewById(opt.viewId);
            if (cb != null) {
                cb.setChecked(st.getData(opt.key));
            }
        }
    }

    private void saveState(Storage st) {
        for (PopOption opt : options) {
            CheckBoxSample cb = contentView.findViewById(opt.viewId);
            if (cb != null) {
                st.saveData(opt.key, cb.isChecked());
            }
        }
    }

    // ─── 点击事件 ──────────────────────────────────────────────────

    private void applyClickListeners(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyClickListeners(group.getChildAt(i));
            }
        } else {
            Integer viewId = view.getId();
            if (viewId != View.NO_ID && itemClickListeners.containsKey(viewId)) {
                view.setOnClickListener(itemClickListeners.get(viewId));
            }
        }
    }

    /**
     * 注册自定义项点击行为。
     * @param viewId  布局中的 View ID
     * @param action  点击行为
     */
    public void onItemClick(int viewId, ItemClickListener action) {
        itemClickListeners.put(viewId, action);
        if (contentView != null) {
            View v = contentView.findViewById(viewId);
            if (v != null) v.setOnClickListener(action);
        }
    }

    /**
     * 注册复选框切换行为。
     * @param viewId  CheckBox 的 ID（支持点击关联 TextView 触发）
     */
    public void onToggle(int viewId) {
        onItemClick(viewId, v -> {
            CheckBoxSample cb = contentView.findViewById(viewId);
            if (cb != null) cb.toggle();
        });
        // 支持点击关联文字触发
        View textView = contentView.findViewById(viewId + 1); // convention: id+1 = _text
        if (textView != null) {
            final CheckBoxSample finalCb = contentView.findViewById(viewId);
            if (finalCb != null) {
                textView.setOnClickListener(v -> finalCb.toggle());
            }
        }
    }

    // ─── 查询方法（给调用方在 dismiss 回调中使用）─────────────────

    public boolean isChecked(String key) {
        if (contentView == null) return false;
        for (PopOption opt : options) {
            if (opt.key.equals(key)) {
                CheckBoxSample cb = contentView.findViewById(opt.viewId);
                return cb != null && cb.isChecked();
            }
        }
        return false;
    }

    public boolean isChecked(int viewId) {
        if (contentView == null) return false;
        CheckBoxSample cb = contentView.findViewById(viewId);
        return cb != null && cb.isChecked();
    }

    // ─── 工具 ──────────────────────────────────────────────────────

    @SuppressWarnings("ResourceType")
    private static int makeDropDownMeasureSpec(int measureSpec) {
        int mode;
        if (measureSpec == ViewGroup.LayoutParams.WRAP_CONTENT) {
            mode = View.MeasureSpec.UNSPECIFIED;
        } else {
            mode = View.MeasureSpec.EXACTLY;
        }
        return View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(measureSpec), mode);
    }
}
