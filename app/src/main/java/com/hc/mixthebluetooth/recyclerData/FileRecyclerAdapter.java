package com.hc.mixthebluetooth.recyclerData;

import android.content.Context;

import com.hc.basiclibrary.file.GetFilesUtils;
import com.hc.basiclibrary.recyclerAdapterBasic.ItemClickListener;
import com.hc.basiclibrary.recyclerAdapterBasic.RecyclerCommonAdapter;
import com.hc.basiclibrary.recyclerAdapterBasic.ViewHolder;
import com.hc.mixthebluetooth.R;

import java.io.File;
import java.util.List;
import java.util.Map;

public class FileRecyclerAdapter extends RecyclerCommonAdapter<Map<String, Object>> {
    public FileRecyclerAdapter(Context context, List<Map<String, Object>> maps, int layoutId) {
        super(context, maps, layoutId);
    }

    @Override
    protected void convert(ViewHolder holder, Map<String, Object> item, int position, ItemClickListener itemClickListener) {
        holder.setText(R.id.item_file_name,(String)item.get(GetFilesUtils.FILE_INFO_NAME));

        if ((boolean)item.get(GetFilesUtils.FILE_INFO_ISFOLDER)){
            holder.setText(R.id.item_file_hint,item.get(GetFilesUtils.FILE_INFO_NUM_SONDIRS)+" 个文件夹和 "
                    +item.get(GetFilesUtils.FILE_INFO_NUM_SONFILES)+" 个文件")
                    .setImageResource(R.id.item_file_ic,R.drawable.item_folder);
        }else {
            File file = (File) item.get(GetFilesUtils.FILE_INFO_PATH);
            holder.setText(R.id.item_file_hint,GetFilesUtils.getInstance().getFileSize(file.toString()))
                    .setImageResource(R.id.item_file_ic,R.drawable.item_file);
        }
    }
}
