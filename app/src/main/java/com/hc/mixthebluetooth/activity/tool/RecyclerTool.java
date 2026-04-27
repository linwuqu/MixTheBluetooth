package com.hc.mixthebluetooth.activity.tool;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hc.basiclibrary.recyclerAdapterBasic.FastScrollLinearLayoutManager;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Recycler 统一管理器 — 统一管理消息列表的创建、刷新、追加、清空。
 *
 * 设计原则：
 * - 一个 Fragment 只需要一个 RecyclerTool 实例
 * - 支持快速滚动（FastScrollLinearLayoutManager）和普通滚动
 * - 数据变更自动 notify，调用方不需要知道 adapter 的存在
 * - 支持换行合并模式（追加到上一个元素）和独立行模式
 *
 * 使用示例（FragmentMessageNew）：
 *
 * <pre>{@code
 * public class MyFragment extends BaseFragment<...> {
 *     private final RecyclerTool recycler = new RecyclerTool();
 *
 *     @Override protected void initRecycler() {
 *         recycler.init(this, R.layout.item_message_fragment)
 *                .layoutManager(RecyclerTool.Layout.FAST_SCROLL)
 *                .maxBytesBeforeAutoClear(400_000)
 *                .build();
 *     }
 *
 *     // 追加一行
 *     private void onLineReceived(String line) {
 *         recycler.addLine(line, null, false, module, false);
 *     }
 *
 *     // 追加发送数据
 *     private void onSend(byte[] data) {
 *         recycler.addLine(null, data, false, module, true);
 *     }
 *
 *     // 清空
 *     private void clear() {
 *         recycler.clear();
 *     }
 *
 *     // 获取字节统计
 *     public long getTotalBytes() { return recycler.getTotalBytes(); }
 * }</pre>
 */
public class RecyclerTool {

    public enum Layout {
        LINEAR,
        FAST_SCROLL   // 带滚动定位的 LinearLayoutManager
    }

    private FragmentMessAdapter adapter;
    private final List<FragmentMessageItem> dataList = new ArrayList<>();

    @Nullable private RecyclerView recyclerView;
    private int maxBytes  = 0;
    private int totalBytes = 0;
    private boolean autoClearEnabled = false;
    private Layout layoutType = Layout.LINEAR;
    private int itemLayout = R.layout.item_message_fragment;
    private android.content.Context ctx;

    // ─── 初始化 ────────────────────────────────────────────────────────

    /**
     * 初始化 RecyclerView。
     * @param owner      Fragment（用于获取 Context 和 LayoutInflater）
     * @param itemLayout item 布局资源 ID
     * @return this（支持链式调用）
     */
    @NonNull
    public RecyclerTool init(@NonNull android.app.Fragment owner, int itemLayout) {
        return init(owner.requireContext(), itemLayout);
    }

    @NonNull
    public RecyclerTool init(@NonNull android.content.Context ctx, int itemLayout) {
        this.ctx = ctx;
        this.itemLayout = itemLayout;
        return this;
    }

    @NonNull
    public RecyclerTool attach(@NonNull RecyclerView rv) {
        this.recyclerView = rv;
        if (adapter == null) {
            adapter = new FragmentMessAdapter(ctx, dataList, itemLayout);
        }
        rv.setAdapter(adapter);

        RecyclerView.LayoutManager lm;
        if (layoutType == Layout.FAST_SCROLL) {
            lm = new FastScrollLinearLayoutManager(ctx);
        } else {
            lm = new LinearLayoutManager(ctx);
        }
        rv.setLayoutManager(lm);
        return this;
    }

    /**
     * 设置布局管理器类型。
     */
    @NonNull
    public RecyclerTool layoutManager(@NonNull Layout type) {
        this.layoutType = type;
        if (recyclerView != null) {
            attach(recyclerView);
        }
        return this;
    }

    /**
     * 设置自动清空阈值。
     * @param bytes 缓存字节数上限，超限自动清空列表
     */
    @NonNull
    public RecyclerTool maxBytesBeforeAutoClear(int bytes) {
        this.maxBytes = bytes;
        this.autoClearEnabled = true;
        return this;
    }

    /**
     * 完成构建，绑定 RecyclerView。
     */
    @SuppressLint("NotifyDataSetChanged")
    @NonNull
    public RecyclerTool build() {
        if (recyclerView != null && adapter == null) {
            adapter = new FragmentMessAdapter(ctx, dataList, itemLayout);
            recyclerView.setAdapter(adapter);
            RecyclerView.LayoutManager lm;
            if (layoutType == Layout.FAST_SCROLL) {
                lm = new FastScrollLinearLayoutManager(ctx);
            } else {
                lm = new LinearLayoutManager(ctx);
            }
            recyclerView.setLayoutManager(lm);
        }
        return this;
    }

    // ─── 数据操作 ──────────────────────────────────────────────────────

    /**
     * 追加一行消息。
     *
     * @param text        显示文本（可为 null，依赖 byteData 渲染）
     * @param byteData    原始字节（可为 null）
     * @param newline     本条数据是否以换行符结尾
     * @param module      蓝牙模块
     * @param showMine    是否显示自己发送的（用于判断颜色）
     */
    @SuppressLint("NotifyDataSetChanged")
    public void addLine(@Nullable String text,
                        @Nullable byte[] byteData,
                        boolean newline,
                        @Nullable com.hc.bluetoothlibrary.DeviceModule module,
                        boolean showMine) {

        if (autoClearEnabled) {
            if (byteData != null) totalBytes += byteData.length;
            if (totalBytes > maxBytes) {
                dataList.clear();
                totalBytes = 0;
                if (adapter != null) adapter.notifyDataSetChanged();
            }
        }

        FragmentMessageItem last = dataList.isEmpty() ? null : dataList.get(dataList.size() - 1);

        if (last != null && last.isAddible() && byteData != null) {
            // 合并到上一条（上一条没有换行符）
            last.addData(Analysis.getByteToString(byteData,
                    com.hc.mixthebluetooth.activity.single.FragmentParameter.getInstance().getCodeFormat(ctx),
                    false, newline),
                    null);
            last.setDataEndNewline(newline);
        } else {
            // 创建新条目
            dataList.add(new FragmentMessageItem(
                    text,
                    byteData,
                    null,
                    false,
                    module,
                    showMine
            ));
            if (!dataList.isEmpty()) {
                dataList.get(dataList.size() - 1).setDataEndNewline(newline);
            }
        }

        if (adapter != null) {
            adapter.notifyItemInserted(dataList.size() - 1);
        }
        scrollToBottom();
    }

    /**
     * 追加纯文本行（不涉及字节解析）。
     */
    @SuppressLint("NotifyDataSetChanged")
    public void addTextLine(@NonNull String text,
                            @Nullable com.hc.bluetoothlibrary.DeviceModule module,
                            boolean showMine) {
        dataList.add(new FragmentMessageItem(text, null, null, false, module, showMine));
        if (adapter != null) {
            adapter.notifyItemInserted(dataList.size() - 1);
        }
        scrollToBottom();
    }

    /**
     * 清空列表。
     */
    @SuppressLint("NotifyDataSetChanged")
    public void clear() {
        dataList.clear();
        totalBytes = 0;
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    /**
     * 获取总字节数。
     */
    public int getTotalBytes() {
        return totalBytes;
    }

    /**
     * 获取列表大小。
     */
    public int size() {
        return dataList.size();
    }

    /**
     * 获取最后一个元素。
     */
    @Nullable
    public FragmentMessageItem last() {
        return dataList.isEmpty() ? null : dataList.get(dataList.size() - 1);
    }

    // ─── 滚动控制 ──────────────────────────────────────────────────────

    /**
     * 平滑滚动到底部。
     */
    public void scrollToBottom() {
        if (recyclerView != null && !dataList.isEmpty()) {
            recyclerView.smoothScrollToPosition(dataList.size() - 1);
        }
    }

    /**
     * 立即滚动到底部。
     */
    public void scrollToBottomNow() {
        if (recyclerView != null && !dataList.isEmpty()) {
            recyclerView.scrollToPosition(dataList.size() - 1);
        }
    }

    // ─── 适配器暴露 ────────────────────────────────────────────────────

    @Nullable
    public FragmentMessAdapter getAdapter() {
        return adapter;
    }

    @NonNull
    public List<FragmentMessageItem> getDataList() {
        return dataList;
    }
}
