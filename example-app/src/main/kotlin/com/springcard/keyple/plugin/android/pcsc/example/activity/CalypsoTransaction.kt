/*
 * Copyright (c) 2018-2022 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc.example.activity

import com.springcard.keyple.plugin.android.pcsc.example.util.CalypsoClassicInfo
import org.calypsonet.terminal.reader.CardReader
import org.calypsonet.terminal.reader.ObservableCardReader
import org.calypsonet.terminal.reader.selection.CardSelectionManager
import org.eclipse.keyple.card.calypso.CalypsoExtensionService
import org.eclipse.keyple.core.service.KeyplePluginException
import org.eclipse.keyple.core.service.ObservableReader
import org.eclipse.keyple.core.service.SmartCardServiceProvider
import timber.log.Timber

class CalypsoTransaction(val cardReader: CardReader) {

  private fun initiateScheduledCardSelection() {
    val cardSelectionManager: CardSelectionManager
    val calypsoCardExtensionProvider: CalypsoExtensionService
    try {
      /* Prepare a Calypso card selection */
      cardSelectionManager = SmartCardServiceProvider.getService().createCardSelectionManager()

      /* Calypso selection: configures a card selection with all the desired attributes to make the selection and read additional information afterwards */
      calypsoCardExtensionProvider = CalypsoExtensionService.getInstance()

      val smartCardService = SmartCardServiceProvider.getService()
      smartCardService.checkCardExtension(calypsoCardExtensionProvider)

      cardSelectionManager.prepareSelection(
          calypsoCardExtensionProvider
              .createCardSelection()
              .filterByDfName(CalypsoClassicInfo.AID)
              .prepareReadRecord(
                  CalypsoClassicInfo.SFI_EnvironmentAndHolder,
                  CalypsoClassicInfo.RECORD_NUMBER_1.toInt()))

      cardSelectionManager.prepareSelection(
          calypsoCardExtensionProvider
              .createCardSelection()
              .filterByDfName("315449432E494341")
              .prepareReadRecord(
                  CalypsoClassicInfo.SFI_EnvironmentAndHolder,
                  CalypsoClassicInfo.RECORD_NUMBER_1.toInt()))

      cardSelectionManager.prepareSelection(
          calypsoCardExtensionProvider
              .createCardSelection()
              .filterByDfName("A0000004040125090101")
              .prepareReadRecord(
                  CalypsoClassicInfo.SFI_EnvironmentAndHolder,
                  CalypsoClassicInfo.RECORD_NUMBER_1.toInt()))

      cardSelectionManager.scheduleCardSelectionScenario(
          cardReader as ObservableReader,
          ObservableCardReader.DetectionMode.REPEATING,
          ObservableCardReader.NotificationMode.ALWAYS)
    } catch (e: KeyplePluginException) {
      Timber.e(e)
    } catch (e: Exception) {
      Timber.e(e)
    }
  }
}
