/********************************************************************************************************
 * @file FDFirmwareGetByIndexMessage.java
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
package com.telink.ble.mesh.core.message.firmwaredistribution;

import com.telink.ble.mesh.core.message.Opcode;
import com.telink.ble.mesh.core.message.firmwareupdate.UpdatingMessage;

/**
 * The Firmware Distribution Upload Start message is an acknowledged message sent by a Firmware Distribution Client to start a firmware image upload to a Firmware Distribution Server.
 * The response to a Firmware Distribution Upload Start message is a Firmware Distribution Upload Status message.
 */
public class FDFirmwareGetByIndexMessage extends UpdatingMessage {

    /**
     * Distribution Firmware Image Index
     * Index of the entry in the Firmware Images List state
     * 2 bytes
     */
    public int distImageIndex;


    /**
     * Constructs a new FDFirmwareGetByIndexMessage object with the specified destination address and application key index.
     *
     * @param destinationAddress The destination address to send the message to.
     * @param appKeyIndex        The index of the application key to use for encryption and decryption.
     */
    public FDFirmwareGetByIndexMessage(int destinationAddress, int appKeyIndex) {
        super(destinationAddress, appKeyIndex);
    }

    /**
     * Creates a simple FDFirmwareGetByIndexMessage object with the specified destination address and application key index.
     * This method sets the response maximum to 1.
     *
     * @param destinationAddress The destination address to send the message to.
     * @param appKeyIndex        The index of the application key to use for encryption and decryption.
     * @return A simple FDFirmwareGetByIndexMessage object.
     */
    public static FDFirmwareGetByIndexMessage getSimple(int destinationAddress, int appKeyIndex) {
        FDFirmwareGetByIndexMessage message = new FDFirmwareGetByIndexMessage(destinationAddress, appKeyIndex);
        message.setResponseMax(1);
        return message;
    }

    /**
     * Gets the opcode value of the FDFirmwareGetByIndexMessage.
     *
     * @return The opcode value.
     */
    @Override
    public int getOpcode() {
        return Opcode.FD_FIRMWARE_GET.value;
    }

    /**
     * Gets the response opcode value of the FDFirmwareGetByIndexMessage.
     *
     * @return The response opcode value.
     */
    @Override
    public int getResponseOpcode() {
        return Opcode.FD_UPLOAD_STATUS.value;
    }
}
