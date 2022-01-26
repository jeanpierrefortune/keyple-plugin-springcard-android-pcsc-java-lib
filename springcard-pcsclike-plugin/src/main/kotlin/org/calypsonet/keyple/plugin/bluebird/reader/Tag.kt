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
package org.calypsonet.keyple.plugin.bluebird.reader

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.calypsonet.keyple.plugin.bluebird.BluebirdSupportContactlessProtocols

@Parcelize
data class Tag(val currentProtocol: BluebirdSupportContactlessProtocols, val data: ByteArray?) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Tag

        if (currentProtocol != other.currentProtocol) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data.contentEquals(other.data)) return false
        } else if (other.data != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = currentProtocol.hashCode()
        result = 31 * result + (data?.contentHashCode() ?: 0)
        return result
    }
}

sealed class NfcResult
class NfcResultSuccess(val tag: Tag) : NfcResult()
class NfcResultError(val error: Throwable) : NfcResult()
