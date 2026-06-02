package com.hc.mixthebluetooth.ui.cgm;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.cgm.CgmResult;
import com.hc.mixthebluetooth.databinding.FragmentUnifiedMessageBinding;

public class CgmFragment extends BTFragment<FragmentUnifiedMessageBinding> {

    private CgmController controller;

    @Override
    protected void initChannels() {
        register(StaticConstants.CH_BT_EVENT);
    }

    @Override
    protected void initAllImpl(View view, Context context) {
        controller = new CgmController(
                requireContext(),
                Profiles.cgm(),
                new BindingHost(viewBinding),
                new FragmentGateway()
        );
        controller.init();
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

        BindingHost(@NonNull FragmentUnifiedMessageBinding binding) {
            this.binding = binding;
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
            return binding.recyclerMessage;
        }

        @Override
        public TextView bottomInfo() {
            return binding.tvBottomInfo;
        }
    }

    private final class FragmentGateway implements CgmController.Gateway {
        @Override
        public void postText(@NonNull DeviceModule module, @NonNull String text) {
            byte[] bytes = Codec.encodeText(requireContext(), text);
            sendDataToActivity(
                    StaticConstants.CMD_BT_POST,
                    new CgmBluetoothEvent.BTPost(module, bytes)
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
