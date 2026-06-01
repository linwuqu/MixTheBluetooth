package com.hc.mixthebluetooth.fragment;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.databinding.FragmentUnifiedMessageBinding;
import com.hc.mixthebluetooth.uni.Codec;
import com.hc.mixthebluetooth.uni.Controller;
import com.hc.mixthebluetooth.uni.Profiles;

import java.io.File;

public class UniFragment extends BTFragment<FragmentUnifiedMessageBinding> {

    private Controller controller;

    @Override
    protected void initChannels() {
        register(StaticConstants.CH_BT_EVENT);
    }

    @Override
    protected void initAllImpl(View view, Context context) {
        controller = new Controller(
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

    private static final class BindingHost implements Controller.HostView {
        private final FragmentUnifiedMessageBinding binding;

        BindingHost(@NonNull FragmentUnifiedMessageBinding binding) {
            this.binding = binding;
        }

        @Override
        public ViewGroup region(@NonNull Controller.Region region) {
            if (region == Controller.Region.ACTION) return binding.actionRegion;
            if (region == Controller.Region.SUMMARY) return binding.summaryRegion;
            if (region == Controller.Region.MAIN) return binding.mainRegion;
            if (region == Controller.Region.SECONDARY) return binding.secondaryRegion;
            if (region == Controller.Region.DEBUG) return binding.debugRegion;
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

    private final class FragmentGateway implements Controller.Gateway {
        @Override
        public void postText(@NonNull DeviceModule module, @NonNull String text) {
            byte[] bytes = Codec.encodeText(requireContext(), text);
            sendDataToActivity(
                    StaticConstants.CMD_BT_POST,
                    new BTPackage.BTPost(module, bytes)
            );
        }

        @Override
        public void onCacheFileReady(@NonNull File file) {
            Log.d("UniFragment", "CGM cache file ready: " + file.getAbsolutePath());
            AppApi.cgm().uploadAndPoll(file, result -> {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> {
                    if (result.isOk() && result.data != null) {
                        controller.onCgmResult(result.data);
                        Toast.makeText(requireContext(), "CGM 数据已生成", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.w("UniFragment", "CGM flow failed: " + result.message);
                        Toast.makeText(requireContext(), "CGM 失败: " + result.message, Toast.LENGTH_LONG).show();
                    }
                });
            });
        }
    }
}
