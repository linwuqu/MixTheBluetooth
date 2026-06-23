package com.hc.mixthebluetooth.ui.cgm;

import android.content.Context;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.application.auth.LogoutHelper;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.cgm.CgmResult;
import com.hc.mixthebluetooth.api.cgm.CgmWorkflow;
import com.hc.mixthebluetooth.databinding.FragmentUnifiedMessageBinding;

public class CgmFragment extends BTFragment<FragmentUnifiedMessageBinding> {
    private CgmController controller;
    private boolean streamExpanded = false;

    @Override
    protected void initChannels() {
        register(StaticConstants.CH_BT_EVENT);
    }

    @Override
    protected void initAllImpl(View view, Context context) {
        controller = new CgmController(
                requireContext(),
                Profiles.cgm(),
                new BindingHost(viewBinding, requireContext()),
                new FragmentGateway()
        );
        controller.init();
        viewBinding.btnLogout.setOnClickListener(v -> showLogoutConfirm());

        // 点击数据流卡片头部展开/收起
        viewBinding.streamCardHeader.setOnClickListener(v -> {
            streamExpanded = !streamExpanded;
            if (streamExpanded) {
                viewBinding.recyclerMessage.setVisibility(View.VISIBLE);
                viewBinding.streamCardArrow.setRotation(90f);
            } else {
                viewBinding.recyclerMessage.setVisibility(View.GONE);
                viewBinding.streamCardArrow.setRotation(0f);
            }
        });
    }

    private void showLogoutConfirm() {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出当前账号吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出", (d, w) -> {
                    Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show();
                    LogoutHelper.performLogout();
                })
                .show();
    }

    @Override
    protected void updateStateImpl(String sign, Object data) {
        if (StaticConstants.CH_BT_EVENT.equals(sign) && controller != null) {
            controller.onEvent(data);
        }
    }

    @Override
    protected FragmentUnifiedMessageBinding getViewBinding() {
        return FragmentUnifiedMessageBinding.inflate(getLayoutInflater());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (controller != null) {
            controller.release();
        }
    }

    private static final class BindingHost implements CgmController.HostView {
        private final FragmentUnifiedMessageBinding binding;
        private final Context hostContext;

        BindingHost(@NonNull FragmentUnifiedMessageBinding binding,
                    @NonNull Context hostContext) {
            this.binding = binding;
            this.hostContext = hostContext;
        }

        @Override
        public ViewGroup region(@NonNull CgmController.Region region) {
            if (region == CgmController.Region.ACTION) return binding.actionRegion;
            if (region == CgmController.Region.SUMMARY) return binding.summaryRegion;
            if (region == CgmController.Region.MAIN) return binding.mainRegion;
            if (region == CgmController.Region.SECONDARY) return binding.secondaryRegion;
            if (region == CgmController.Region.DEBUG) return binding.debugRegion;
            return binding.contentRoot;
        }

        @Override
        public RecyclerView messageList() {
            RecyclerView rv = binding.recyclerMessage;
            rv.setLayoutManager(new LinearLayoutManager(hostContext));
            return rv;
        }

        @Override
        public void configureMessageList(@NonNull RecyclerView.Adapter<?> adapter) {
            RecyclerView rv = binding.recyclerMessage;
            rv.setLayoutManager(new LinearLayoutManager(hostContext));
            rv.setAdapter(adapter);
        }

        @Override
        public ViewGroup header() {
            return null;
        }

        @Override
        public TextView bottomInfo() {
            TextView tv = new TextView(hostContext);
            tv.setVisibility(View.GONE);
            return tv;
        }
    }

    private final class FragmentGateway implements CgmController.Gateway {
        @Override
        public void postText(@NonNull DeviceModule module, @NonNull String text) {
            if (!isAdded()) {
                return;
            }
            if (Looper.myLooper() != Looper.getMainLooper()) {
                requireActivity().runOnUiThread(() -> postText(module, text));
                return;
            }
            byte[] bytes = Codec.encodeText(requireContext(), text);
            sendDataToActivity(
                    StaticConstants.CMD_BT_POST,
                    new CgmBluetoothEvent.BTPost(module, bytes)
            );
        }

        @Override
        public void confirmDeleteCache(@NonNull Runnable onConfirm) {
            if (!isAdded()) {
                return;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle("确认删除缓存")
                    .setMessage("设备缓存删除后无法恢复。请确认本次数据已经保存或上传成功。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (dialog, which) -> onConfirm.run())
                    .show();
        }

        @Override
        public void onCgmWorkflowUpdate(@NonNull CgmWorkflow.Update update) {
            if (!isAdded() || update.message == null || update.message.isEmpty()) {
                return;
            }
            if (!update.error && update.commandText == null && !update.uploadStarted && !update.deleteConfirmed) {
                return;
            }
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), update.message, Toast.LENGTH_SHORT).show()
            );
        }

        @Override
        public void onCgmWorkflowResult(@NonNull CallResult<CgmResult> result) {
            if (result.isPending() || !isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (result.isOk() && result.data != null) {
                    controller.onCgmResult(result.data);
                    Toast.makeText(requireContext(), "CGM 数据已生成", Toast.LENGTH_SHORT).show();
                } else {
                    Log.w("CgmFragment", "CGM flow failed: " + result.message);
                    Toast.makeText(requireContext(), "CGM 失败: " + result.message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
