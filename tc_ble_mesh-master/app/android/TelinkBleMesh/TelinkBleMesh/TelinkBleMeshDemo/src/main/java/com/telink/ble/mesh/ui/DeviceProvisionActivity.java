/********************************************************************************************************
 * @file DeviceProvisionActivity.java
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
package com.telink.ble.mesh.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.telink.ble.mesh.SharedPreferenceHelper;
import com.telink.ble.mesh.TelinkMeshApplication;
import com.telink.ble.mesh.core.MeshUtils;
import com.telink.ble.mesh.core.access.BindingBearer;
import com.telink.ble.mesh.core.message.MeshSigModel;
import com.telink.ble.mesh.core.message.config.ConfigStatus;
import com.telink.ble.mesh.core.message.config.ModelPublicationSetMessage;
import com.telink.ble.mesh.core.message.config.ModelPublicationStatusMessage;
import com.telink.ble.mesh.demo.R;
import com.telink.ble.mesh.entity.AdvertisingDevice;
import com.telink.ble.mesh.entity.BindingDevice;
import com.telink.ble.mesh.entity.CompositionData;
import com.telink.ble.mesh.entity.ModelPublication;
import com.telink.ble.mesh.entity.ProvisioningDevice;
import com.telink.ble.mesh.foundation.Event;
import com.telink.ble.mesh.foundation.EventListener;
import com.telink.ble.mesh.foundation.MeshService;
import com.telink.ble.mesh.foundation.event.BindingEvent;
import com.telink.ble.mesh.foundation.event.ProvisioningEvent;
import com.telink.ble.mesh.foundation.event.ScanEvent;
import com.telink.ble.mesh.foundation.event.StatusNotificationEvent;
import com.telink.ble.mesh.foundation.parameter.BindingParameters;
import com.telink.ble.mesh.foundation.parameter.ProvisioningParameters;
import com.telink.ble.mesh.foundation.parameter.ScanParameters;
import com.telink.ble.mesh.model.CertCacheService;
import com.telink.ble.mesh.model.MeshInfo;
import com.telink.ble.mesh.model.NetworkingDevice;
import com.telink.ble.mesh.model.NetworkingState;
import com.telink.ble.mesh.model.NodeInfo;
import com.telink.ble.mesh.model.PrivateDevice;
import com.telink.ble.mesh.model.db.MeshInfoService;
import com.telink.ble.mesh.ui.adapter.DeviceProvisionListAdapter;
import com.telink.ble.mesh.util.Arrays;
import com.telink.ble.mesh.util.MeshLogger;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * scan for unprovision device and provision selected device
 * Created by kee on 2020/11/12.
 */
public class DeviceProvisionActivity extends BaseActivity implements View.OnClickListener, EventListener<String> {

    /**
     * found by bluetooth scan
     */
    private List<NetworkingDevice> devices = new ArrayList<>();

    /**
     * data adapter
     */
    private DeviceProvisionListAdapter mListAdapter;

    /**
     * all all, click to provision/bind all NetworkingDevice
     */
    private Button btn_add_all;

    /**
     * local mesh info
     */
    private MeshInfo mesh;

    /**
     * title refresh icon
     */
    private MenuItem refreshItem;

    /**
     * handler
     */
    private Handler mHandler = new Handler();

    /**
     * is setting publication if time model exist in composition data
     */
    private boolean isPubSetting = false;

    /**
     * is scanning for unprovisioned device
     */
    private boolean isScanning = false;

    /**
     * unprovisioned device count
     */
    private int unpvDevCnt = 0;

    private TextView tv_dev_cnt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!validateNormalStart(savedInstanceState)) {
            return;
        }
        initView();
        addEventListeners();
        mesh = TelinkMeshApplication.getInstance().getMeshInfo();
        startScan();
//        addTestData();
    }

    private void initView() {
        setContentView(R.layout.activity_device_provision_selectable);
        findViewById(R.id.btn_add_all).setVisibility(View.VISIBLE);
        initTitle();
        RecyclerView rv_devices = findViewById(R.id.rv_devices);
        devices = new ArrayList<>();

        mListAdapter = new DeviceProvisionListAdapter(this, devices);
        rv_devices.setLayoutManager(new LinearLayoutManager(this));

        rv_devices.setAdapter(mListAdapter);
        btn_add_all = findViewById(R.id.btn_add_all);
        btn_add_all.setOnClickListener(this);
        tv_dev_cnt = findViewById(R.id.tv_dev_cnt);
        findViewById(R.id.tv_log).setOnClickListener(this);
    }

    private void addEventListeners() {
        TelinkMeshApplication.getInstance().addEventListener(ProvisioningEvent.EVENT_TYPE_PROVISION_BEGIN, this);
        TelinkMeshApplication.getInstance().addEventListener(ProvisioningEvent.EVENT_TYPE_PROVISION_SUCCESS, this);
        TelinkMeshApplication.getInstance().addEventListener(ProvisioningEvent.EVENT_TYPE_PROVISION_FAIL, this);
        TelinkMeshApplication.getInstance().addEventListener(BindingEvent.EVENT_TYPE_BIND_SUCCESS, this);
        TelinkMeshApplication.getInstance().addEventListener(BindingEvent.EVENT_TYPE_BIND_FAIL, this);
        TelinkMeshApplication.getInstance().addEventListener(ScanEvent.EVENT_TYPE_SCAN_TIMEOUT, this);
        TelinkMeshApplication.getInstance().addEventListener(ScanEvent.EVENT_TYPE_DEVICE_FOUND, this);
        TelinkMeshApplication.getInstance().addEventListener(ModelPublicationStatusMessage.class.getName(), this);
    }

    private void initTitle() {
        Toolbar toolbar = findViewById(R.id.title_bar);
        toolbar.inflateMenu(R.menu.device_scan);
        setTitle("Device Scan", "Selectable");
        refreshItem = toolbar.getMenu().findItem(R.id.item_refresh);
        refreshItem.setVisible(false);

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.item_refresh) {
                if (isScanning) {
                    stopScan();
                } else {
                    startScan();
                }
            }
            return false;
        });
    }


    /**
     * scan for unprovisioned devices
     */
    private void startScan() {
        isScanning = true;
        enableUI(false);
        ScanParameters parameters = ScanParameters.getDefault(false, false);
        parameters.setScanTimeout(10 * 1000);
        MeshService.getInstance().startScan(parameters);
    }

    private void stopScan() {
        isScanning = false;
        enableUI(true);
    }

    private void updateRefreshItem(boolean enable) {
        refreshItem.setIcon(isScanning ? R.drawable.ic_stop : R.drawable.ic_refresh);
        refreshItem.setVisible(isScanning || enable);
    }

    /**
     * unprovisioned device found
     */
    private void onDeviceFound(AdvertisingDevice advertisingDevice) {
        // provision service data: 15:16:28:18:[16-uuid]:[2-oobInfo]
        byte[] serviceData = MeshUtils.getMeshServiceData(advertisingDevice.scanRecord, true);
        if (serviceData == null || serviceData.length < 17) {
            MeshLogger.log("serviceData error", MeshLogger.LEVEL_ERROR);
            return;
        }

        final int uuidLen = 16;
        byte[] deviceUUID = new byte[uuidLen];
        System.arraycopy(serviceData, 0, deviceUUID, 0, uuidLen);

        final int oobInfo = MeshUtils.bytes2Integer(serviceData, 16, 2, ByteOrder.LITTLE_ENDIAN);

        if (deviceExists(deviceUUID)) {
            MeshLogger.d("device exists");
            return;
        }

        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.meshAddress = -1;
        nodeInfo.deviceUUID = deviceUUID;
        MeshLogger.d("device mac - " + advertisingDevice.device.getAddress());
//        MeshLogger.d("device uuid calc by md5  - " + Arrays.bytesToHexString(Encipher.calcUuidByMac(advertisingDevice.device.getAddress())));
        MeshLogger.d("device found -> device uuid : " + Arrays.bytesToHexString(deviceUUID) + " -- oobInfo: " + oobInfo + " -- certSupported?" + MeshUtils.isCertSupported(oobInfo));
        nodeInfo.macAddress = advertisingDevice.device.getAddress();

        NetworkingDevice processingDevice = new NetworkingDevice(nodeInfo);
        processingDevice.bluetoothDevice = advertisingDevice.device;
        processingDevice.oobInfo = oobInfo;
        processingDevice.rssi = advertisingDevice.rssi;
        processingDevice.state = NetworkingState.IDLE;
        processingDevice.addLog(NetworkingDevice.TAG_SCAN, "device found");
        devices.add(processingDevice);
        mListAdapter.notifyDataSetChanged();
        updateDeviceCountInfo(1);
    }

    public void updateDeviceCountInfo(int variation) {
        unpvDevCnt += variation;
        runOnUiThread(() -> {
            tv_dev_cnt.setText("unprovisioned device count: " + unpvDevCnt);
        });
    }

    private void startProvision(NetworkingDevice processingDevice) {
        if (isScanning) {
            isScanning = false;
            MeshService.getInstance().stopScan();
        }

        int address = mesh.getProvisionIndex();
        MeshLogger.d("alloc address: " + address);
        if (!MeshUtils.validUnicastAddress(address)) {
            enableUI(true);
            return;
        }

        byte[] deviceUUID = processingDevice.nodeInfo.deviceUUID;
        ProvisioningDevice provisioningDevice = new ProvisioningDevice(processingDevice.bluetoothDevice, processingDevice.nodeInfo.deviceUUID, address);
        provisioningDevice.setRootCert(CertCacheService.getInstance().getRootCert());
        provisioningDevice.setOobInfo(processingDevice.oobInfo);
        processingDevice.state = NetworkingState.PROVISIONING;
        processingDevice.addLog(NetworkingDevice.TAG_PROVISION, "action start -> 0x" + String.format("%04X", address));
        processingDevice.nodeInfo.meshAddress = address;
        mListAdapter.notifyDataSetChanged();

        // check if oob exists
        byte[] oob = MeshInfoService.getInstance().getOobByDeviceUUID(deviceUUID);
//        oob = new byte[]{(byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        if (oob != null) {
            provisioningDevice.setAuthValue(oob);
        } else {
            final boolean autoUseNoOOB = SharedPreferenceHelper.isNoOOBEnable(this);
            provisioningDevice.setAutoUseNoOOB(autoUseNoOOB);
        }
        ProvisioningParameters provisioningParameters = new ProvisioningParameters(provisioningDevice);

        MeshLogger.d("provisioning device: " + provisioningDevice.toString());
        MeshService.getInstance().startProvisioning(provisioningParameters);
    }

    @Override
    public void finish() {
        super.finish();
        MeshService.getInstance().idle(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TelinkMeshApplication.getInstance().removeEventListener(this);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_add_all:
                if (addAll()) {
                    btn_add_all.setEnabled(false);
                    mListAdapter.setProcessing(true);
                    provisionNext();
                } else {
                    toastMsg("no available device found");
                }
                break;
            case R.id.tv_log:
                startActivity(new Intent(this, LogActivity.class));
                break;
        }
    }

    public void provisionNext() {
        enableUI(false);
        NetworkingDevice waitingDevice = getNextWaitingDevice();
        if (waitingDevice == null) {
            MeshLogger.d("no waiting device found");
            enableUI(true);
            return;
        }
        startProvision(waitingDevice);
    }

    private NetworkingDevice getNextWaitingDevice() {
        for (NetworkingDevice device : devices) {
            if (device.state == NetworkingState.WAITING) {
                return device;
            }
        }
        return null;
    }

    private boolean addAll() {
        boolean anyValid = false;
        for (NetworkingDevice device : devices) {
            if (device.state == NetworkingState.IDLE) {
                anyValid = true;
                device.state = NetworkingState.WAITING;
            }
        }
        return anyValid;
    }

    private void enableUI(final boolean enable) {
        MeshLogger.d(String.format("enableUI scanning-%B enable-%B", isScanning, enable));
        MeshService.getInstance().idle(false);
        runOnUiThread(() -> {
            enableBackNav(enable);
            btn_add_all.setEnabled(enable);
            updateRefreshItem(enable);
            mListAdapter.setProcessing(!enable);
        });

    }

    @Override
    public void performed(final Event<String> event) {
        super.performed(event);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (event.getType().equals(ProvisioningEvent.EVENT_TYPE_PROVISION_BEGIN)) {
                    onProvisionStart((ProvisioningEvent) event);
                } else if (event.getType().equals(ProvisioningEvent.EVENT_TYPE_PROVISION_SUCCESS)) {
                    updateDeviceCountInfo(-1);
                    onProvisionSuccess((ProvisioningEvent) event);
                } else if (event.getType().equals(ScanEvent.EVENT_TYPE_SCAN_TIMEOUT)) {
                    isScanning = false;
                    enableUI(true);
                } else if (event.getType().equals(ProvisioningEvent.EVENT_TYPE_PROVISION_FAIL)) {
                    onProvisionFail((ProvisioningEvent) event);

                    // provision next when provision failed
                    provisionNext();
                } else if (event.getType().equals(BindingEvent.EVENT_TYPE_BIND_SUCCESS)) {
                    onKeyBindSuccess((BindingEvent) event);
                } else if (event.getType().equals(BindingEvent.EVENT_TYPE_BIND_FAIL)) {
                    onKeyBindFail((BindingEvent) event);

                    // provision next when binding fail
                    provisionNext();
                } else if (event.getType().equals(ScanEvent.EVENT_TYPE_DEVICE_FOUND)) {
                    AdvertisingDevice device = ((ScanEvent) event).getAdvertisingDevice();
                    onDeviceFound(device);
                } else if (event.getType().equals(ModelPublicationStatusMessage.class.getName())) {
                    MeshLogger.d("pub setting status: " + isPubSetting);
                    if (!isPubSetting) {
                        return;
                    }
                    mHandler.removeCallbacks(timePubSetTimeoutTask);
                    final ModelPublicationStatusMessage statusMessage = (ModelPublicationStatusMessage) ((StatusNotificationEvent) event).getNotificationMessage().getStatusMessage();

                    if (statusMessage.getStatus() == ConfigStatus.SUCCESS.code) {
                        onTimePublishComplete(true, "time pub set success");
                    } else {
                        onTimePublishComplete(false, "time pub set status err: " + statusMessage.getStatus());
                        MeshLogger.log("publication err: " + statusMessage.getStatus());
                    }
                }
            }
        });

    }

    private void onProvisionStart(ProvisioningEvent event) {
        NetworkingDevice pvDevice = getCurrentDevice(NetworkingState.PROVISIONING);
        if (pvDevice == null) return;
        pvDevice.addLog(NetworkingDevice.TAG_PROVISION, "begin");
        mListAdapter.notifyDataSetChanged();
    }

    private void onProvisionFail(ProvisioningEvent event) {
//        ProvisioningDevice deviceInfo = event.getProvisioningDevice();

        NetworkingDevice pvDevice = getCurrentDevice(NetworkingState.PROVISIONING);
        if (pvDevice == null) {
            MeshLogger.d("pv device not found when failed");
            return;
        }
        pvDevice.state = NetworkingState.PROVISION_FAIL;
        pvDevice.addLog(NetworkingDevice.TAG_PROVISION, event.getDesc());
        mListAdapter.notifyDataSetChanged();
    }

    private void onProvisionSuccess(ProvisioningEvent event) {

        ProvisioningDevice remote = event.getProvisioningDevice();


        NetworkingDevice pvDevice = getCurrentDevice(NetworkingState.PROVISIONING);
        if (pvDevice == null) {
            MeshLogger.d("pv device not found when provision success");
            return;
        }

        pvDevice.state = NetworkingState.BINDING;
        pvDevice.addLog(NetworkingDevice.TAG_PROVISION, "success");
        NodeInfo nodeInfo = pvDevice.nodeInfo;
        nodeInfo.elementCnt = remote.getDeviceCapability().eleNum;
        nodeInfo.deviceKey = remote.getDeviceKey();
        nodeInfo.netKeyIndexes.add(MeshUtils.intToHex2(mesh.getDefaultNetKey().index));
        mesh.insertDevice(nodeInfo, true);

        // check if private mode opened
        final boolean privateMode = SharedPreferenceHelper.isPrivateMode(this);

        // check if device support fast bind
        boolean defaultBound = false;
        if (privateMode && remote.getDeviceUUID() != null) {
            PrivateDevice device = PrivateDevice.filter(remote.getDeviceUUID());
            if (device != null) {
                MeshLogger.d("private device");
                final byte[] cpsData = device.getCpsData();
                nodeInfo.compositionData = CompositionData.from(cpsData);
                defaultBound = true;
            } else {
                MeshLogger.d("private device null");
            }
        }

        nodeInfo.setDefaultBind(defaultBound);
        pvDevice.addLog(NetworkingDevice.TAG_BIND, "action start");
        mListAdapter.notifyDataSetChanged();
        int appKeyIndex = mesh.getDefaultAppKeyIndex();

        BindingDevice bindingDevice = new BindingDevice(nodeInfo.meshAddress, nodeInfo.deviceUUID, appKeyIndex);
        bindingDevice.setDefaultBound(defaultBound);
        bindingDevice.setBearer(BindingBearer.GattOnly);
//        bindingDevice.setDefaultBound(false);
        MeshService.getInstance().startBinding(new BindingParameters(bindingDevice));
//        final boolean dfBond = defaultBound;
//        showConfirmDialog("provision success, go next?", (dialog, which) -> {
//
//        });


    }

    private void onKeyBindFail(BindingEvent event) {
        NetworkingDevice deviceInList = getCurrentDevice(NetworkingState.BINDING);
        if (deviceInList == null) return;

        deviceInList.state = NetworkingState.BIND_FAIL;
        deviceInList.addLog(NetworkingDevice.TAG_BIND, "failed - " + event.getDesc());
        mListAdapter.notifyDataSetChanged();
//        mesh.saveOrUpdate(DeviceProvisionActivity.this);
    }

    private void onKeyBindSuccess(BindingEvent event) {
        BindingDevice remote = event.getBindingDevice();

        NetworkingDevice pvDevice = getCurrentDevice(NetworkingState.BINDING);
        if (pvDevice == null) {
            MeshLogger.d("pv device not found when bind success");
            return;
        }
        pvDevice.addLog(NetworkingDevice.TAG_BIND, "success");
        pvDevice.nodeInfo.bound = true;
        // if is default bound, composition data has been valued ahead of binding action
        if (!remote.isDefaultBound()) {
            pvDevice.nodeInfo.compositionData = remote.getCompositionData();
        }
        pvDevice.nodeInfo.save();

        if (setTimePublish(pvDevice)) {
            pvDevice.state = NetworkingState.TIME_PUB_SETTING;
            pvDevice.addLog(NetworkingDevice.TAG_PUB_SET, "action start");
            isPubSetting = true;
            MeshLogger.d("waiting for time publication status");
        } else {
            // no need to set time publish
            pvDevice.state = NetworkingState.BIND_SUCCESS;
            provisionNext();
        }
        mListAdapter.notifyDataSetChanged();
//        mesh.saveOrUpdate(DeviceProvisionActivity.this);
    }


    /**
     * set time publish after key bind success
     *
     * @param networkingDevice target
     * @return
     */
    private boolean setTimePublish(NetworkingDevice networkingDevice) {
        int modelId = MeshSigModel.SIG_MD_TIME_S.modelId;
        int pubEleAdr = networkingDevice.nodeInfo.getTargetEleAdr(modelId);
        if (pubEleAdr != -1) {
            final int period = 30 * 1000;
            final int pubAdr = 0xFFFF;
            int appKeyIndex = TelinkMeshApplication.getInstance().getMeshInfo().getDefaultAppKeyIndex();
            ModelPublication modelPublication = ModelPublication.createDefault(pubEleAdr, pubAdr, appKeyIndex, period, modelId, true);

            ModelPublicationSetMessage publicationSetMessage = new ModelPublicationSetMessage(networkingDevice.nodeInfo.meshAddress, modelPublication);
            boolean result = MeshService.getInstance().sendMeshMessage(publicationSetMessage);
            if (result) {
                mHandler.removeCallbacks(timePubSetTimeoutTask);
                mHandler.postDelayed(timePubSetTimeoutTask, 5 * 1000);
            }
            return result;
        } else {
            return false;
        }
    }

    private Runnable timePubSetTimeoutTask = () -> onTimePublishComplete(false, "time pub set timeout");

    private void onTimePublishComplete(boolean success, String desc) {
        if (!isPubSetting) return;
        MeshLogger.d("pub set complete: " + success + " -- " + desc);
        isPubSetting = false;

        NetworkingDevice pvDevice = getCurrentDevice(NetworkingState.TIME_PUB_SETTING);

        if (pvDevice == null) {
            MeshLogger.d("pv device not found pub set success");
            return;
        }
        pvDevice.addLog(NetworkingDevice.TAG_PUB_SET, success ? "success" : ("failed : " + desc));
        pvDevice.state = success ? NetworkingState.TIME_PUB_SET_SUCCESS : NetworkingState.TIME_PUB_SET_FAIL;
        pvDevice.addLog(NetworkingDevice.TAG_PUB_SET, desc);
        pvDevice.nodeInfo.timePublishConfigured = true;
        pvDevice.nodeInfo.save();
        mListAdapter.notifyDataSetChanged();
//        mesh.saveOrUpdate(DeviceProvisionActivity.this);
        provisionNext();
    }



    /**
     * @param state target state,
     * @return processing device
     */
    private NetworkingDevice getCurrentDevice(NetworkingState state) {
        for (NetworkingDevice device : devices) {
            if (device.state == state) {
                return device;
            }
        }
        return null;
    }


    /**
     * only find in unprovisioned list
     *
     * @param deviceUUID deviceUUID in unprovisioned scan record
     */
    private boolean deviceExists(byte[] deviceUUID) {
        for (NetworkingDevice device : this.devices) {
            if (device.state == NetworkingState.IDLE && Arrays.equals(deviceUUID, device.nodeInfo.deviceUUID)) {
                return true;
            }
        }
        return false;
    }
}
