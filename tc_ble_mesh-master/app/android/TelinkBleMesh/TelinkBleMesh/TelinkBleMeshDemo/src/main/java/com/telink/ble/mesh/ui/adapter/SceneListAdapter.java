/********************************************************************************************************
 * @file SceneListAdapter.java
 *
 * @brief for TLSR chips
 *
 * @author telink
 * @date Sep. 30, 2017
 *
 * @par Copyright (c) 2017, Telink Semiconductor (Shanghai) Co., Ltd. ("TELINK")
 *
 *          Licensed under the Apache License, Version 2.0 (the "License");
 *          you may not use this file except in compliance with the License.
 *          You may obtain a copy of the License at
 *
 *              http://www.apache.org/licenses/LICENSE-2.0
 *
 *          Unless required by applicable law or agreed to in writing, software
 *          distributed under the License is distributed on an "AS IS" BASIS,
 *          WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *          See the License for the specific language governing permissions and
 *          limitations under the License.
 *******************************************************************************************************/
package com.telink.ble.mesh.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.telink.ble.mesh.TelinkMeshApplication;
import com.telink.ble.mesh.core.MeshUtils;
import com.telink.ble.mesh.core.message.scene.SceneRecallMessage;
import com.telink.ble.mesh.demo.R;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.model.MeshInfo;
import com.telink.ble.mesh.model.NodeInfo;
import com.telink.ble.mesh.model.Scene;
import com.telink.ble.mesh.model.db.MeshInfoService;
import com.telink.ble.mesh.ui.SceneListActivity;
import com.telink.ble.mesh.ui.SceneSettingActivity;

import java.util.List;

/**
 * Scene List
 */
public class SceneListAdapter extends BaseRecyclerViewAdapter<SceneListAdapter.ViewHolder> {
    private List<Scene> sceneList;
    private Context mContext;
    private MeshInfo meshInfo;

    public SceneListAdapter(Context context) {
        mContext = context;
        meshInfo = TelinkMeshApplication.getInstance().getMeshInfo();
    }

    public Scene get(int position) {
        return sceneList.get(position);
    }

    public boolean isEmpty() {
        return this.sceneList == null || this.sceneList.size() == 0;
    }

    public void remove(Scene scene) {
        TelinkMeshApplication.getInstance().getMeshInfo().removeScene(scene);
        MeshInfoService.getInstance().removeScene(scene);
    }

    public void resetData() {
        this.sceneList = TelinkMeshApplication.getInstance().getMeshInfo().scenes;
        this.notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(mContext).inflate(R.layout.item_scene, parent, false);
        ViewHolder holder = new ViewHolder(itemView);
        holder.tv_scene_info = itemView.findViewById(R.id.tv_scene_info);
        holder.iv_recall = itemView.findViewById(R.id.iv_recall);

        holder.iv_edit = itemView.findViewById(R.id.iv_edit);
        holder.rv_inner_device = itemView.findViewById(R.id.rv_inner_device);

        return holder;
    }

    private View.OnClickListener imageClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int position = (int) v.getTag();
            if (v.getId() == R.id.iv_edit) {
                ;
            } else if (v.getId() == R.id.iv_recall) {
//                MeshService.getInstance().cmdSceneRecall(0xFFFF, 0, sceneList.get(position).id, 0, null);

                int appKeyIndex = TelinkMeshApplication.getInstance().getMeshInfo().getDefaultAppKeyIndex();
                SceneRecallMessage recallMessage = SceneRecallMessage.getSimple(0xFFFF,
                        appKeyIndex, sceneList.get(position).sceneId, false, 0);
                MeshService.getInstance().sendMeshMessage(recallMessage);
                // mesh interface
//                MeshService.getInstance().recallScene(0xFFFF, false, 0, sceneList.get(position).id, 0, (byte) 0, null);
            }
        }
    };


    @Override
    public int getItemCount() {
        return sceneList == null ? 0 : sceneList.size();
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);

        Scene scene = sceneList.get(position);
        holder.tv_scene_info.setText(String.format("name: %s \nID: %02X", scene.name, scene.sceneId));

        holder.iv_recall.setOnClickListener(
                v -> ((SceneListActivity) (mContext)).recall(MeshUtils.ADDRESS_BROADCAST, scene.sceneId)
        );
        holder.iv_recall.setTag(position);

        holder.iv_edit.setOnClickListener(v ->
                mContext.startActivity(new Intent(mContext, SceneSettingActivity.class).putExtra("sceneId", sceneList.get(position).sceneId))
        );
        holder.iv_edit.setTag(position);

        holder.rv_inner_device.setLayoutManager(new LinearLayoutManager(mContext));
        holder.rv_inner_device.setAdapter(new SimpleDeviceAdapter(scene));
    }

    class ViewHolder extends RecyclerView.ViewHolder {


        TextView tv_scene_info;
        ImageView iv_recall, iv_edit;
        RecyclerView rv_inner_device;

        public ViewHolder(View itemView) {
            super(itemView);
        }
    }


    class SimpleDeviceViewHolder extends RecyclerView.ViewHolder {

        ImageView iv_address, iv_recall_ele;
        TextView tv_address;

        public SimpleDeviceViewHolder(View itemView) {
            super(itemView);
        }
    }

    class SimpleDeviceAdapter extends BaseRecyclerViewAdapter<SimpleDeviceViewHolder> {

        Scene scene;

        SimpleDeviceAdapter(Scene scene) {
            this.scene = scene;
        }

        @Override
        public SimpleDeviceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(mContext).inflate(R.layout.item_scene_element, parent, false);
            SimpleDeviceViewHolder holder = new SimpleDeviceViewHolder(itemView);
            holder.iv_address = itemView.findViewById(R.id.iv_address);
            holder.iv_recall_ele = itemView.findViewById(R.id.iv_recall_ele);
            holder.tv_address = itemView.findViewById(R.id.tv_address);
            return holder;
        }

        @Override
        public void onBindViewHolder(SimpleDeviceViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
            String address = scene.addressList.get(position);
            int adr = MeshUtils.hexToIntB(address);
            holder.iv_address.setImageResource(R.drawable.ic_element);
            NodeInfo nodeInfo = meshInfo.getDeviceByElementAddress(adr);
            holder.tv_address.setText(nodeInfo.getName() + "\nelement address: 0x" + address);
            holder.iv_recall_ele.setOnClickListener(
                    v -> ((SceneListActivity) (mContext)).recall(adr, scene.sceneId)
            );
        }

        @Override
        public int getItemCount() {
            return this.scene.addressList == null ? 0 : this.scene.addressList.size();
        }
    }

    public String getOnOffDesc(int onOff) {
        if (onOff == 1) {
            return "ON";
        } else if (onOff == 0) {
            return "OFF";
        } else if (onOff == -1) {
            return "OFFLINE";
        }
        return "UNKNOWN";
    }
}
