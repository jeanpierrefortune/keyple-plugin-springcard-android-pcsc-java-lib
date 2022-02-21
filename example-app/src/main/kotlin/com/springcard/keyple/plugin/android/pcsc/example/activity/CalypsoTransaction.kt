/*
 * Copyright (c) 2018-2022 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc.example.activity

import com.springcard.keyple.plugin.android.pcsc.example.util.CalypsoClassicInfo
import org.calypsonet.terminal.calypso.card.CalypsoCard
import org.calypsonet.terminal.reader.CardReader
import org.calypsonet.terminal.reader.ObservableCardReader
import org.calypsonet.terminal.reader.selection.CardSelectionManager
import org.eclipse.keyple.card.calypso.CalypsoExtensionService
import org.eclipse.keyple.core.service.KeyplePluginException
import org.eclipse.keyple.core.service.ObservableReader
import org.eclipse.keyple.core.service.SmartCardServiceProvider
import timber.log.Timber

class CalypsoTransaction {
  companion object {
    /**
     * Schedules a card selection targeting three applications:
     * - Keyple demo kit card, AID = 315449432e49434131
     * - Navigo B prime card, AID = 315449432E494341
     * - Navigo B card, AID = A0000004040125090101 In all three cases, the environment file is read
     * as soon as the application is selected.
     * @param cardReader The card reader.
     * @return The created card selection manager.
     */
    fun initiateScheduledCardSelection(cardReader: CardReader): CardSelectionManager {
      val cardSelectionManager = SmartCardServiceProvider.getService().createCardSelectionManager()
      val calypsoCardExtensionProvider: CalypsoExtensionService
      try {
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
      return cardSelectionManager
    }

    fun runCardReadTransaction(cardReader: CardReader, calypsoCard: CalypsoCard, withSam: Boolean) {
      val calypsoCardExtensionProvider = CalypsoExtensionService.getInstance()
      val cardTransaction =
          calypsoCardExtensionProvider.createCardTransactionWithoutSecurity(cardReader, calypsoCard)
      //                      if (withSam) {
      //                          val androidPcscPlugin =
      //
      // SmartCardServiceProvider.getService().getPlugin(AndroidPcscPlugin.PLUGIN_NAME)
      //                          setupCardResourceService(
      //                              androidPcscPlugin,
      //                              CalypsoClassicInfo.SAM_READER_NAME_REGEX,
      //                              CalypsoClassicInfo.SAM_PROFILE_NAME)
      //
      //                          /*
      //                           * Create secured card transaction.
      //                           *
      //                           * check the availability of the SAM doing a ATR based
      // selection, open its physical and
      //                           * logical channels and keep it open
      //                           */
      //                          calypsoCardExtensionProvider.createCardTransaction(
      //                              cardReader, calypsoCard, getSecuritySettings())
      //                      } else {
      //                          // Create unsecured card transaction
      //
      // calypsoCardExtensionProvider.createCardTransactionWithoutSecurity(
      //                              cardReader, calypsoCard)
      //                      }

      /*
       * Prepare the reading order and keep the associated parser for later use once the
       * transaction has been processed.
       */
      cardTransaction.prepareReadRecord(
          CalypsoClassicInfo.SFI_EventLog, CalypsoClassicInfo.RECORD_NUMBER_1.toInt())

      cardTransaction.prepareReadRecord(
          CalypsoClassicInfo.SFI_Counter1, CalypsoClassicInfo.RECORD_NUMBER_1.toInt())

      cardTransaction.processCardCommands()
      //                  if (withSam) {
      //                      addActionEvent("Process card Opening session for transactions")
      //                      cardTransaction.processOpening(WriteAccessLevel.LOAD)
      //                      addResultEvent("Opening session: SUCCESS")
      //                      val counter = readCounter(selectionsResult)
      //                      val eventLog = ByteArrayUtil.toHex(readEventLog(selectionsResult))
      //
      //                      addActionEvent("Process card Closing session")
      //                      cardTransaction.processClosing()
      //                      addResultEvent("Closing session: SUCCESS")
      //
      //                      // In secured reading, value read elements can only be trusted if
      // the session is closed
      //                      // without error.
      //                      addResultEvent("Counter value: $counter")
      //                      addResultEvent("EventLog file: $eventLog")
      //                  } else {
      //                      cardTransaction.processCardCommands()
      //                  }
      Timber.d("Card transaction ended successfully")
    }
  }
}
