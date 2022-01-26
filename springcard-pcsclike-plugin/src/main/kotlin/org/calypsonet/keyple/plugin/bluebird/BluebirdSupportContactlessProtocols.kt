/* **************************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.calypsonet.keyple.plugin.bluebird

enum class BluebirdSupportContactlessProtocols constructor(val key: String, val value: Int) {
    NFC_A_BB("NFC_A", 0x01),
    NFC_B_BB("NFC_B", 0x02),
    NFC_INNO_BB("NFC_INNO", 0x04),
    NFC_ALL("NFC_ALL", 0x01 or 0x02 or 0x04);

    companion object {
        fun findEnumByKey(key: String): BluebirdSupportContactlessProtocols {
            for (value in values()) {
                if (value.key == key) {
                    return value
                }
            }
            throw IllegalStateException("BluebirdSupportContactlessProtocols '$key' is not defined")
        }

        fun findEnumByValue(value: Int): BluebirdSupportContactlessProtocols {
            for (contractPriorityEnum in values()) {
                if (contractPriorityEnum.value == value) {
                    return contractPriorityEnum
                }
            }
            throw IllegalStateException("BluebirdSupportContactlessProtocols is not defined")
        }
    }
}
