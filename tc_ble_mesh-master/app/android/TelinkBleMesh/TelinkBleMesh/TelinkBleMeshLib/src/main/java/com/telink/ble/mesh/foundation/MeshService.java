/********************************************************************************************************
 * @file MeshService.java
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
package com.telink.ble.mesh.foundation;

import android.content.Context;

import androidx.annotation.NonNull;

import com.telink.ble.mesh.core.ble.GattConnection;
import com.telink.ble.mesh.core.ble.GattRequest;
import com.telink.ble.mesh.core.message.MeshMessage;
import com.telink.ble.mesh.core.networking.ExtendBearerMode;
import com.telink.ble.mesh.entity.RemoteProvisioningDevice;
import com.telink.ble.mesh.foundation.parameter.AutoConnectParameters;
import com.telink.ble.mesh.foundation.parameter.BindingParameters;
import com.telink.ble.mesh.foundation.parameter.FastProvisioningParameters;
import com.telink.ble.mesh.foundation.parameter.GattConnectionParameters;
import com.telink.ble.mesh.foundation.parameter.GattOtaParameters;
import com.telink.ble.mesh.foundation.parameter.MeshOtaParameters;
import com.telink.ble.mesh.foundation.parameter.ProvisioningParameters;
import com.telink.ble.mesh.foundation.parameter.ScanParameters;
import com.telink.ble.mesh.util.MeshLogger;

import java.security.Security;

/**
 * Created by kee on 2019/8/26.
 */

public class MeshService implements MeshController.EventCallback {

    // mesh encipher
    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    /**
     * mesh protocol implementation
     */
    private MeshController mController;

    /**
     * singleton for mesh-service
     */
    private static MeshService mThis = new MeshService();

    public static MeshService getInstance() {
        return mThis;
    }

    /**
     * event handler
     */
    private EventHandler mEventHandler;

    /**
     * init mesh engine
     */
    public void init(@NonNull Context context, EventHandler eventHandler) {
        MeshLogger.log("MeshService#init");
        if (mController == null) {
            mController = new MeshController();
        }
        mController.setEventCallback(this);
        mController.start(context);
        this.mEventHandler = eventHandler;
    }

    /**
     * clear mesh engine
     */
    public void clear() {
        MeshLogger.log("MeshService#clear");
        if (this.mController != null) {
            this.mController.stop();
        }
    }

    public void clearNetworkCache() {
        MeshLogger.log("MeshService#clearNetworkCache");
        if (this.mController != null) {
            this.mController.clearNetworkCache();
        }
    }

    /**
     * setup mesh info
     *
     * @param configuration mesh configuration, inner params should not be null
     */
    public void setupMeshNetwork(MeshConfiguration configuration) {
        mController.setupMeshNetwork(configuration);
    }

    /**
     * check bluetooth state
     * state will be received by BluetoothEvent
     * {@link com.telink.ble.mesh.foundation.event.BluetoothEvent}
     */
    public void checkBluetoothState() {
        mController.checkBluetoothState();
    }

    /********************************************************************************
     * mesh api
     ********************************************************************************/

    /**
     * @return is proxy connected && proxy set success (if proxy filter set needed)
     */
    public boolean isProxyLogin() {
        return mController.isProxyLogin();
    }

    /**
     * @return direct connected node address,
     * or 0 : invalid address
     */
    public int getDirectConnectedNodeAddress() {
        return mController.getDirectNodeAddress();
    }

    /**
     * remove device in mesh configuration
     * this action only delete device info in map ,
     * if kick-out device from mesh network is needed, call sendMeshMessage(NodeResetMessage)
     *
     * @param meshAddress target device address
     */
    public void removeDevice(int meshAddress) {
        mController.removeDevice(meshAddress);
    }

    /**
     * get current action mode
     *
     * @return current mode
     * @see com.telink.ble.mesh.foundation.MeshController.Mode
     */
    public MeshController.Mode getCurrentMode() {
        return mController.getMode();
    }

    /**
     * start scanning
     */
    public void startScan(ScanParameters scanParameters) {
        mController.startScan(scanParameters);
    }

    /**
     * stop bluetooth scanning
     */
    public void stopScan() {
        mController.stopScan();
    }

    /**
     * start provisioning if device found by {@link #startScan(ScanParameters)}
     */
    public boolean startProvisioning(ProvisioningParameters provisioningParameters) {
        return mController.startProvisioning(provisioningParameters);
    }

    /**
     * used when {@link com.telink.ble.mesh.entity.ProvisioningDevice#autoStart} is false
     *
     * @param address unicast address
     * @return is continue success
     */
    public boolean continueProvision(int address) {
        return mController.continueProvision(address);
    }

    /**
     * start binding application key for models in node if device provisioned
     */
    public void startBinding(BindingParameters bindingParameters) {
        mController.startBinding(bindingParameters);
    }

    /**
     * scanning an connecting proxy node for mesh control
     */
    public void autoConnect(AutoConnectParameters parameters) {
        mController.autoConnect(parameters);
    }

    /**
     * ota by gatt, [telink private]
     */
    public void startGattOta(GattOtaParameters otaParameters) {
        mController.startGattOta(otaParameters);
    }

    /**
     * ota by mesh
     * support multi node updating at the same time
     */
    public void startMeshOta(MeshOtaParameters meshOtaParameters) {
        mController.startMeshOTA(meshOtaParameters);
    }

    /**
     * stop mesh updating flow
     */
    public void stopMeshOta() {
        mController.stopMeshOta();
    }

    /**
     * remote provisioning when proxy node connected and RP-support device found
     */
    public void startRemoteProvisioning(RemoteProvisioningDevice remoteProvisioningDevice) {
        mController.startRemoteProvision(remoteProvisioningDevice);
    }

    public boolean continueRemoteProvision(int address) {
        return mController.continueRemoteProvision(address);
    }

    /**
     * fast provision, [telink private]
     */
    public void startFastProvision(FastProvisioningParameters fastProvisioningConfiguration) {
        mController.startFastProvision(fastProvisioningConfiguration);
    }

    /**
     * idle
     *
     * @param disconnect if disconnect current gatt connection
     */
    public void idle(boolean disconnect) {
        mController.idle(disconnect);
    }

    public void disconnect() {
        mController.disconnect();
    }

    public void startGattConnection(GattConnectionParameters parameters) {
        mController.startGattConnection(parameters);
    }

    /**
     * send GATT request if the gatt is connected {@link GattConnection#isConnected()}
     *
     * @param request gatt request
     * @return if request sent
     */
    public boolean sendGattRequest(GattRequest request) {
        return mController.sendGattRequest(request);
    }

    /**
     * @return get current gatt connection mtu
     */
    public int getMtu() {
        return mController.getMtu();
    }

    /**
     * send mesh message
     * 1. if message is reliable (with ack), message.responseOpcode should be valued by message ack opcode
     * {@link MeshMessage#getResponseOpcode()}
     * Besides, message.responseMax is suggested to be valued by expected response count,
     * for example, 3 nodes in group(0xC001), 3 is the best value for responseMax when get group status
     * 2. if message is with tid (for example: OnOffSetMessage {@link com.telink.ble.mesh.core.message.generic.OnOffSetMessage})
     * and App do not want to manage tid, valid message.tidPosition should be valued
     * otherwise tid in message will be sent,
     *
     * @param meshMessage message
     */
    public boolean sendMeshMessage(MeshMessage meshMessage) {
        if (meshMessage == null) return false;
        return mController.sendMeshMessage(meshMessage);
    }

    /**
     * Telink-private protocol.
     * get all devices status by send command to OnlineStatus handle
     *
     * @return if online_status supported
     * @see com.telink.ble.mesh.core.ble.UUIDInfo#CHARACTERISTIC_ONLINE_STATUS
     */
    public boolean getOnlineStatus() {
        return mController.getOnlineStatus();
    }


    /**
     * @param extendBearerMode new mode
     */
    public void resetExtendBearerMode(ExtendBearerMode extendBearerMode) {
        mController.resetExtendBearerMode(extendBearerMode);
    }

    /********************************************************************************
     * bluetooth api
     ********************************************************************************/

    /**
     * @return bluetooth enabled
     */
    public boolean isBluetoothEnabled() {
        return mController.isBluetoothEnabled();
    }

    /**
     * enable bluetooth
     */
    public void enableBluetooth() {
        mController.enableBluetooth();
    }

    /**
     * get current connected device macAddress
     */
    public String getCurDeviceMac() {
        return mController.getCurDeviceMac();
    }

    @Override
    public void onEventPrepared(Event<String> event) {
        if (mEventHandler != null) {
            mEventHandler.onEventHandle(event);
        }
    }

}
