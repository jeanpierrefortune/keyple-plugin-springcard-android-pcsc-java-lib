/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

import android.os.ConditionVariable
import com.springcard.pcsclike.SCardReader
import org.eclipse.keyple.core.plugin.CardIOException
import org.eclipse.keyple.core.plugin.ReaderIOException
import org.eclipse.keyple.core.plugin.spi.reader.ConfigurableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.insertion.WaitForCardInsertionBlockingSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.processing.DontWaitForCardRemovalDuringProcessingSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.removal.WaitForCardRemovalBlockingSpi
import org.eclipse.keyple.core.util.ByteArrayUtil
import timber.log.Timber

/** Keyple SE Reader's Implementation for the Bluebird (SAM access) reader */
@Suppress("INVISIBLE_ABSTRACT_MEMBER_FROM_SUPER_WARNING")
internal class AndroidPcscReaderAdapter(val sCardReader: SCardReader) :
    AndroidPcscReader,
    ConfigurableReaderSpi,
    ObservableReaderSpi,
    WaitForCardInsertionBlockingSpi,
    DontWaitForCardRemovalDuringProcessingSpi,
    WaitForCardRemovalBlockingSpi {

  private val name: String = sCardReader.name
  private var isContactless: Boolean = false
  private var isCardPresent: Boolean = false

  private val WAIT_RESPONSE_TIMEOUT: Long = 5000
  private val WAIT_CARD_CONNECT_TIMEOUT: Long = 5000
  private val waitCardStatusChange = ConditionVariable()
  private val waitCardConnect = ConditionVariable()
  private val waitCardResponse = ConditionVariable()
  private lateinit var cardResponse: ByteArray

  override fun getName(): String {
    return name
  }

  override fun openPhysicalChannel() {
    Timber.v("Open physical channel")
    try {
      sCardReader.cardConnect()
      waitCardConnect.block(WAIT_CARD_CONNECT_TIMEOUT)
      waitCardConnect.close()
    } catch (e: Exception) {
      throw ReaderIOException(getName() + ": Error while opening Physical Channel", e)
    }
  }

  override fun closePhysicalChannel() {
    Timber.v("Close physical channel")
    sCardReader.channel.disconnect()
  }

  override fun isPhysicalChannelOpen(): Boolean {
    val isCardConnected = sCardReader.cardConnected
    Timber.v("Physical channel is open: %b", isCardConnected)
    return isCardConnected
  }

  override fun checkCardPresence(): Boolean {
    isCardPresent = sCardReader.cardPresent
    Timber.v("Card present: %b", isCardPresent)
    return isCardPresent
  }

  override fun getPowerOnData(): String {
    return ByteArrayUtil.toHex(sCardReader.channel.atr)
  }

  override fun transmitApdu(apduIn: ByteArray?): ByteArray {
    if (apduIn != null) {
      sCardReader.channel.transmit(apduIn)
      waitCardResponse.block(WAIT_RESPONSE_TIMEOUT)
      waitCardResponse.close()
      return cardResponse
    } else {
      throw CardIOException(this.getName() + ": null channel.")
    }
  }

  override fun isContactless(): Boolean {
    return isContactless
  }

  override fun onUnregister() {
    Timber.i("Unregister reader '%s'", name)
  }

  override fun onStartDetection() {
    Timber.i("Starting card detection on reader '%s'", name)
  }

  override fun onStopDetection() {
    Timber.i("Stopping card detection on reader '%s'", name)
  }

  override fun isProtocolSupported(readerProtocol: String?): Boolean {
    return true
  }

  override fun activateProtocol(readerProtocol: String?) {
    // TODO("Not yet implemented")
  }

  override fun deactivateProtocol(readerProtocol: String?) {
    // TODO("Not yet implemented")
  }

  override fun isCurrentProtocol(readerProtocol: String?): Boolean {
    // TODO("Not yet implemented")
    return true
  }

  override fun waitForCardRemoval() {
    do {
      waitCardStatusChange.block()
      waitCardStatusChange.close()
    } while (isCardPresent)
  }

  override fun stopWaitForCardRemoval() {
    waitCardStatusChange.close()
  }

  override fun waitForCardInsertion() {
    do {
      waitCardStatusChange.block()
      waitCardStatusChange.close()
    } while (!isCardPresent)
  }

  override fun stopWaitForCardInsertion() {
    waitCardStatusChange.close()
  }

  override fun setContactless(contactless: Boolean): AndroidPcscReader {
    isContactless = contactless
    return this
  }

  fun onCardPresenceChange(isCardPresent: Boolean) {
    Timber.d("Reader '%s', card presence changed: %b", name, isCardPresent)
    this.isCardPresent = isCardPresent
    waitCardStatusChange.open()
  }

  fun onCardConnected() {
    waitCardConnect.open()
  }

  fun onCardResponseReceived(cardResponse: ByteArray) {
    Timber.d("Reader '%s', %d bytes received from the card", name, cardResponse.size)
    this.cardResponse = cardResponse
    waitCardResponse.open()
  }
}
