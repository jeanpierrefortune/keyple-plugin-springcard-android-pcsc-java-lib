/* **************************************************************************************
 * Copyright (c) 2018-2019 SpringCard - https://www.springcard.com/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package com.springcard.pcsclike.communication

import android.content.Context
import com.springcard.pcsclike.SCardError
import com.springcard.pcsclike.SCardReader
import com.springcard.pcsclike.SCardReaderList
import com.springcard.pcsclike.ccid.CcidCommand
import com.springcard.pcsclike.ccid.CcidResponse
import com.springcard.pcsclike.utils.toHexString
import java.lang.Exception
import timber.log.Timber

internal enum class SubState {
  Idle,
  Authenticate,
  ReadingInfo,
  ConnectingToCards
}

internal abstract class CommunicationLayer(protected var scardReaderList: SCardReaderList) {

  private val TAG = this::class.java.simpleName
  protected abstract var lowLayer: LowLevelLayer

  private var creatingSubState: SubState = SubState.Idle
  private var authenticateStep = 0

  /* Actions */

  fun connect(ctx: Context) {

    if (scardReaderList.isSleeping) {
      scardReaderList.postCallback {
        scardReaderList.callbacks.onReaderListError(
            scardReaderList, SCardError(SCardError.ErrorCodes.BUSY, "Error: Device is sleeping"))
      }
      return
    }

    scardReaderList.enterExclusive()
    scardReaderList.machineState.setNewState(State.Creating)
    // scardReaderList.libHandler.post {
    lowLayer.connect(ctx)
    // }
  }

  fun disconnect() {
    scardReaderList.enterExclusive()
    scardReaderList.machineState.setNewState(State.Closing)
    lowLayer.disconnect()
  }

  internal fun writeCommand(ccidCommand: CcidCommand) {
    /* Update sqn, save it and cipher */
    val updatedCcidBuffer = scardReaderList.ccidHandler.updateCcidCommand(ccidCommand)
    Timber.d("Writing ${ccidCommand.raw.toHexString()} in PC_to_RDR")
    lowLayer.write(updatedCcidBuffer.asList())
  }

  abstract fun wakeUp()

  /* Events */

  fun onCreateFinished() {

    if (scardReaderList.ccidHandler.isSecure && !scardReaderList.ccidHandler.authenticateOk) {
      creatingSubState = SubState.Authenticate
      /* Trigger 1st auth step */
      authenticateStep = 1
      writeCommand(
          scardReaderList.ccidHandler.scardControl(
              scardReaderList.ccidHandler.ccidSecure.hostAuthCmd()))
    } else if (scardReaderList.infoToRead.size > 0) {
      creatingSubState = SubState.ReadingInfo
      /* Trigger 1st read command */
      if (scardReaderList.infoToRead[0].size == 1) {
        /* If the array is just one byte, it is the index of the slot we want to know the status */
        writeCommand(scardReaderList.ccidHandler.scardStatus(scardReaderList.infoToRead[0][0]))
      } else {
        writeCommand(scardReaderList.ccidHandler.scardControl(scardReaderList.infoToRead[0]))
      }
    } else if (scardReaderList.slotsToConnect.size > 0) {
      creatingSubState = SubState.ConnectingToCards
      /* Trigger 1st connect */
      writeCommand(
          scardReaderList.ccidHandler.scardConnect(
              scardReaderList.slotsToConnect[0].index.toByte()))
    } else {
      creatingSubState = SubState.Idle
      Timber.d("Everything is done -> post onCreated callback")
      scardReaderList.machineState.setNewState(State.Idle)
    }
  }

  fun onDisconnected() {
    when (scardReaderList.machineState.currentState) {
      State.Closed -> {
        Timber.w("Impossible to close device if it's already closed")
      }
      State.Idle, State.Sleeping -> {
        scardReaderList.enterExclusive()
        scardReaderList.machineState.setNewState(State.Closing)
        lowLayer.close()
        scardReaderList.machineState.setNewState(State.Closed)
      }
      State.Creating, State.WakingUp, State.WritingCmdAndWaitingResp -> {
        scardReaderList.lastError =
            SCardError(
                SCardError.ErrorCodes.DEVICE_NOT_CONNECTED,
                "Device disconnected while processing command")
        scardReaderList.machineState.setNewState(State.Closing)
        lowLayer.close()
        scardReaderList.machineState.setNewState(State.Closed)
      }
      State.Closing -> {
        lowLayer.close()
        scardReaderList.machineState.setNewState(State.Closed)
      }
      else -> {
        Timber.w("Impossible state: ${scardReaderList.machineState.currentState}")
      }
    }
  }

  fun onStatusReceived(data: ByteArray) {
    val listSlotsUpdated = mutableListOf<SCardReader>()
    val error = scardReaderList.ccidHandler.interpretCcidStatus(data, listSlotsUpdated)

    if (error.code != SCardError.ErrorCodes.NO_ERROR) {
      onCommunicationError(error)
      return
    }

    if (scardReaderList.machineState.currentState != State.Creating) {
      for (slot in listSlotsUpdated) {
        scardReaderList.postCallback {
          scardReaderList.callbacks.onReaderStatus(slot, slot.cardPresent, slot.cardConnected)
        }
      }
      scardReaderList.mayConnectCard()
    }
  }

  fun onResponseReceived(data: ByteArray) {
    val ccidResponse: CcidResponse
    try {
      ccidResponse = scardReaderList.ccidHandler.getCcidResponse(data)
    } catch (e: Exception) {
      scardReaderList.commLayer.onCommunicationError(
          SCardError(SCardError.ErrorCodes.OTHER_ERROR, "Error while decode CCID response"))
      return
    }

    Timber.d("Received ${ccidResponse.raw.toHexString()} in RDR_to_PC")

    when (creatingSubState) {
      SubState.Idle -> interpretResponse(ccidResponse)
      SubState.Authenticate -> interpretResponseAuthenticate(ccidResponse)
      SubState.ReadingInfo -> interpretResponseInfo(ccidResponse)
      SubState.ConnectingToCards -> interpretResponseConnectingToCard(ccidResponse)
      else -> Timber.w("Impossible SubState: $creatingSubState")
    }
  }

  fun onCommunicationError(error: SCardError) {
    Timber.e("${error.code.name}: ${error.detail}")
    scardReaderList.lastError = error
    scardReaderList.exitExclusive()
    disconnect()
  }

  fun onDeviceState(isGoingToSleep: Boolean) {
    if (isGoingToSleep) {
      scardReaderList.machineState.setNewState(State.Sleeping)
    } else {
      scardReaderList.machineState.setNewState(State.Idle)
    }
  }

  /* Utilities func */

  private fun interpretResponse(ccidResponse: CcidResponse) {

    val slot = scardReaderList.readers[ccidResponse.slotNumber.toInt()]

    /* Update slot status (present, powered) */
    scardReaderList.ccidHandler.interpretSlotsStatusInCcidHeader(ccidResponse.slotStatus, slot)

    /* Check slot error */
    val error =
        scardReaderList.ccidHandler.interpretSlotsErrorInCcidHeader(
            ccidResponse.slotError, ccidResponse.slotStatus, slot)
    if (error.code != SCardError.ErrorCodes.NO_ERROR) {
      if (ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_DataBlock.value ||
          ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_SlotStatus.value) {
        scardReaderList.readers[ccidResponse.slotNumber.toInt()].cardError = true
      }
      Timber.d("Error, do not process CCID packet")
      scardReaderList.machineState.setNewState(State.Idle)
      scardReaderList.postCallback { scardReaderList.callbacks.onReaderOrCardError(slot, error) }
      return
    }

    Timber.d("Frame complete, length = ${ccidResponse.length}")
    when {
      ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_Escape.value ->
          when (scardReaderList.ccidHandler.commandSend) {
            CcidCommand.CommandCode.PC_To_RDR_Escape -> {
              /* call setNewState before processing because it will unlock the state machine */
              scardReaderList.machineState.setNewState(State.Idle)
              scardReaderList.postCallback {
                scardReaderList.callbacks.onControlResponse(scardReaderList, ccidResponse.payload)
              }
            }
            else ->
                onCommunicationError(
                    SCardError(
                        SCardError.ErrorCodes.DIALOG_ERROR,
                        "Unexpected CCID response (${String.format("%02X", ccidResponse.code)}) for command : ${scardReaderList.ccidHandler.commandSend}"))
          }
      ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_DataBlock.value ->
          when (scardReaderList.ccidHandler.commandSend) {
            CcidCommand.CommandCode.PC_To_RDR_XfrBlock -> {
              if (ccidResponse.slotNumber > scardReaderList.readers.size) {
                onCommunicationError(
                    SCardError(
                        SCardError.ErrorCodes.PROTOCOL_ERROR,
                        "Error, slot number specified (${ccidResponse.slotNumber}) greater than maximum slot (${scardReaderList.readers.size - 1}), maybe the packet is incorrect"))
              } else {
                /* call setNewState before processing because it will unlock the state machine */
                scardReaderList.machineState.setNewState(State.Idle)
                scardReaderList.postCallback {
                  scardReaderList.callbacks.onTransmitResponse(slot.channel, ccidResponse.payload)
                }
              }
            }
            CcidCommand.CommandCode.PC_To_RDR_IccPowerOn -> {

              /* save ATR */
              slot.channel.atr = ccidResponse.payload

              /* Call callback */

              /* Eventually remove slot in list if auto-connect */
              if (scardReaderList.slotsToConnect.size > 0 &&
                  scardReaderList.slotsToConnect[0].index.toByte() == ccidResponse.slotNumber) {
                scardReaderList.slotsToConnect.removeAt(0)
                /* call setNewState before processing because it will unlock the state machine */
                scardReaderList.machineState.setNewState(State.Idle)
                scardReaderList.postCallback {
                  scardReaderList.callbacks.onReaderStatus(
                      slot, slot.cardPresent, slot.cardConnected)
                }
              } else {
                /* set cardConnected flag */
                slot.cardConnected = true
                /* call setNewState before processing because it will unlock the state machine */
                scardReaderList.machineState.setNewState(State.Idle)
                scardReaderList.postCallback {
                  scardReaderList.callbacks.onCardConnected(slot.channel)
                }
              }
            }
            else ->
                onCommunicationError(
                    SCardError(
                        SCardError.ErrorCodes.DIALOG_ERROR,
                        "Unexpected CCID response (${String.format("%02X", ccidResponse.code)}) for command : ${scardReaderList.ccidHandler.commandSend}"))
          }
      ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_SlotStatus.value ->
          when (scardReaderList.ccidHandler.commandSend) {
            CcidCommand.CommandCode.PC_To_RDR_GetSlotStatus -> {
              /* Do nothing */
              Timber.d("Reader Status, Cool! ...but useless")

              /* Update slot concerned */
              scardReaderList.ccidHandler.interpretSlotsStatusInCcidHeader(
                  ccidResponse.slotStatus, slot)
            }
            CcidCommand.CommandCode.PC_To_RDR_IccPowerOff -> {
              slot.cardConnected = false
              slot.channel.atr = ByteArray(0)
              /* call setNewState before processing because it will unlock the state machine */
              scardReaderList.machineState.setNewState(State.Idle)
              scardReaderList.postCallback {
                scardReaderList.callbacks.onCardDisconnected(slot.channel)
              }
            }
            CcidCommand.CommandCode.PC_To_RDR_XfrBlock -> {
              if (slot.cardPresent && !slot.cardPowered) {
                val error =
                    SCardError(
                        SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR,
                        "Transmit invoked, but card not powered")
                /* call setNewState before processing because it will unlock the state machine */
                scardReaderList.machineState.setNewState(State.Idle)
                scardReaderList.postCallback {
                  scardReaderList.callbacks.onReaderOrCardError(slot, error)
                }
              }
              // TODO CRA else ...
            }
            CcidCommand.CommandCode.PC_To_RDR_IccPowerOn -> {
              val channel = slot.channel
              slot.channel.atr = ccidResponse.payload
              slot.cardConnected = true
              /* call setNewState before processing because it will unlock the state machine */
              scardReaderList.machineState.setNewState(State.Idle)
              scardReaderList.postCallback { scardReaderList.callbacks.onCardConnected(channel) }
              // TODO onReaderOrCardError
            }
            else ->
                onCommunicationError(
                    SCardError(
                        SCardError.ErrorCodes.DIALOG_ERROR,
                        "Unexpected CCID response (${String.format("%02X", ccidResponse.code)}) for command : ${scardReaderList.ccidHandler.commandSend}"))
          }
      else ->
          onCommunicationError(
              SCardError(
                  SCardError.ErrorCodes.DIALOG_ERROR,
                  "Unknown CCID response (${String.format("%02X", ccidResponse.code)}) for command : ${scardReaderList.ccidHandler.commandSend}"))
    }
  }

  private fun interpretResponseAuthenticate(ccidResponse: CcidResponse) {
    if (authenticateStep == 1) {

      if (scardReaderList.ccidHandler.ccidSecure.deviceRespStep1(ccidResponse.payload)) {
        authenticateStep = 2
        writeCommand(
            scardReaderList.ccidHandler.scardControl(
                scardReaderList.ccidHandler.ccidSecure.hostCmdStep2(
                    ccidResponse.payload.toMutableList())))
      } else {
        onCommunicationError(
            SCardError(
                SCardError.ErrorCodes.AUTHENTICATION_ERROR, "Authentication failed at step 1"))
      }
    } else if (authenticateStep == 2) {
      if (scardReaderList.ccidHandler.ccidSecure.deviceRespStep3(ccidResponse.payload)) {
        scardReaderList.ccidHandler.authenticateOk = true
        Timber.d("Authenticate succeed")
        onCreateFinished()
      } else {
        onCommunicationError(
            SCardError(
                SCardError.ErrorCodes.AUTHENTICATION_ERROR, "Authentication failed at step 3"))
      }
    }
  }

  private fun interpretResponseInfo(ccidResponse: CcidResponse) {

    /* Remove command we just processed */
    val commandSend = scardReaderList.infoToRead[0].clone()
    scardReaderList.infoToRead.removeAt(0)

    /* Check if it was a Scard Control command*/
    when {
      scardReaderList.ccidHandler.commandSend == CcidCommand.CommandCode.PC_To_RDR_Escape -> {

        val cla = commandSend[0]
        val ins = commandSend[1]

        /* cf https://docs.springcard.com/books/SpringCore/Host_interfaces/Logical/Direct_Protocol/CONTROL_class/index */

        if (cla != 0x58.toByte()) {
          scardReaderList.commLayer.onCommunicationError(
              SCardError(
                  SCardError.ErrorCodes.DUMMY_DEVICE,
                  "Wrong CLA in infoToRead list ${cla.toHexString()}"))
          return
        }

        when (ins) {
          /* GET_DATA */
          0x20.toByte() -> {
            val identifier = commandSend[2]
            when (identifier) {
              /* Firmware revision string */
              0x06.toByte() -> {
                try {
                  scardReaderList.constants.setVersionFromRevString(
                      ccidResponse.payload.drop(1).toByteArray().toString(charset("ASCII")))
                } catch (e: Exception) {
                  scardReaderList.commLayer.onCommunicationError(
                      SCardError(
                          SCardError.ErrorCodes.DUMMY_DEVICE,
                          "Incorrect firmware revision: ${ccidResponse.payload.toString(charset("ASCII"))}"))
                  return
                }
              }
              else -> {
                scardReaderList.commLayer.onCommunicationError(
                    SCardError(
                        SCardError.ErrorCodes.DUMMY_DEVICE,
                        "Wrong Identifier in infoToRead list ${identifier.toHexString()}"))
                return
              }
            }
          }
          /* CCID_GET_SLOT_NAME */
          0x21.toByte() -> {
            /* Get index of slot being processed */
            val slotIndex = commandSend[2].toInt()

            if (slotIndex > scardReaderList.readers.size) {
              scardReaderList.commLayer.onCommunicationError(
                  SCardError(
                      SCardError.ErrorCodes.DUMMY_DEVICE, "Slot number is too much: $slotIndex"))
              return
            }

            /* Response */
            val slotName =
                ccidResponse
                    .payload
                    .slice(1 until ccidResponse.payload.size)
                    .toByteArray()
                    .toString(charset("ASCII"))
            Timber.d("Slot $slotIndex name : $slotName")
            scardReaderList.readers[slotIndex].name = slotName
            scardReaderList.readers[slotIndex].index = slotIndex

            if (!scardReaderList.constants.slotsName.contains(slotName)) {
              scardReaderList.constants.slotsName.add(slotName)
            }
          }
          else -> {
            scardReaderList.commLayer.onCommunicationError(
                SCardError(
                    SCardError.ErrorCodes.DUMMY_DEVICE,
                    "Wrong INS in infoToRead list ${cla.toHexString()}"))
            return
          }
        }
      }
      scardReaderList.ccidHandler.commandSend ==
          CcidCommand.CommandCode.PC_To_RDR_GetSlotStatus -> {
        /* Update slot concerned */
        scardReaderList.ccidHandler.interpretSlotsStatusInCcidHeader(
            ccidResponse.slotStatus, scardReaderList.readers[ccidResponse.slotNumber.toInt()])
      }
      else -> {
        scardReaderList.commLayer.onCommunicationError(
            SCardError(
                SCardError.ErrorCodes.DUMMY_DEVICE,
                "Wrong Command code ins response: ${scardReaderList.ccidHandler.commandSend}"))
        return
      }
    }

    /* Get next info */
    onCreateFinished()
  }

  private fun interpretResponseConnectingToCard(ccidResponse: CcidResponse) {

    val slot = scardReaderList.readers[ccidResponse.slotNumber.toInt()]

    /* Remove reader we just processed */
    scardReaderList.slotsToConnect.remove(slot)

    /* Update slot status (present, powered) */
    scardReaderList.ccidHandler.interpretSlotsStatusInCcidHeader(ccidResponse.slotStatus, slot)

    /* Check slot error */
    val error =
        scardReaderList.ccidHandler.interpretSlotsErrorInCcidHeader(
            ccidResponse.slotError, ccidResponse.slotStatus, slot)
    if (error.code != SCardError.ErrorCodes.NO_ERROR) {
      Timber.w("Error, do not process CCID packet")
      Timber.w("Error: ${error.code.name}, ${error.detail}")

      /* Connect next card */
      if (scardReaderList.slotsToConnect.size > 0) {
        writeCommand(
            scardReaderList.ccidHandler.scardConnect(
                scardReaderList.slotsToConnect[0].index.toByte()))
      } else {
        Timber.d("ConnectingToCards succeed")
        onCreateFinished()
      }
      return
    }

    Timber.d("Frame complete, length = ${ccidResponse.length}")

    if (ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_DataBlock.value) {
      /* save ATR */
      slot.channel.atr = ccidResponse.payload
    } else {
      Timber.w("Unexpected CCID response code: ${ccidResponse.code}")
    }

    /* Connect next card */
    if (scardReaderList.slotsToConnect.size > 0) {
      writeCommand(
          scardReaderList.ccidHandler.scardConnect(
              scardReaderList.slotsToConnect[0].index.toByte()))
    } else {
      Timber.d("ConnectingToCards succeed")
      onCreateFinished()
    }
  }
}
