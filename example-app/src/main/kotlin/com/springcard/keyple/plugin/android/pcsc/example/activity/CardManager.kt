/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc.example.activity

import com.springcard.keyple.plugin.android.pcsc.example.util.CalypsoClassicInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.calypsonet.terminal.calypso.WriteAccessLevel
import org.calypsonet.terminal.calypso.card.CalypsoCard
import org.calypsonet.terminal.calypso.sam.CalypsoSam
import org.calypsonet.terminal.calypso.transaction.CardSecuritySetting
import org.calypsonet.terminal.reader.CardReader
import org.calypsonet.terminal.reader.CardReaderEvent
import org.calypsonet.terminal.reader.ObservableCardReader
import org.calypsonet.terminal.reader.selection.CardSelectionManager
import org.eclipse.keyple.card.calypso.CalypsoExtensionService
import org.eclipse.keyple.core.service.KeyplePluginException
import org.eclipse.keyple.core.service.ObservableReader
import org.eclipse.keyple.core.service.Plugin
import org.eclipse.keyple.core.service.Reader
import org.eclipse.keyple.core.service.SmartCardServiceProvider
import org.eclipse.keyple.core.service.resource.CardResourceProfileConfigurator
import org.eclipse.keyple.core.service.resource.CardResourceService
import org.eclipse.keyple.core.service.resource.CardResourceServiceProvider
import org.eclipse.keyple.core.service.resource.PluginsConfigurator
import org.eclipse.keyple.core.util.ByteArrayUtil
import timber.log.Timber

internal class CardManager(private val activity: MainActivity) {
  private var cardSecuritySetting: CardSecuritySetting? = null
  private lateinit var cardSelectionManager: CardSelectionManager
  private lateinit var cardReader: ObservableReader
  private var timestamp: Long = 0
  /**
   * Schedules a card selection targeting three applications:
   * - Keyple demo kit card, AID = 315449432e49434131
   * - Navigo B prime card, AID = 315449432E494341
   * - Navigo B card, AID = A0000004040125090101 In all three cases, the environment file is read as
   * soon as the application is selected.
   * @param cardReader The card reader.
   */
  fun initiateScheduledCardSelection(cardReader: CardReader) {
    this.cardReader = cardReader as ObservableReader
    val smartCardService = SmartCardServiceProvider.getService()
    cardSelectionManager = smartCardService.createCardSelectionManager()
    val calypsoCardExtensionProvider: CalypsoExtensionService
    try {
      calypsoCardExtensionProvider = CalypsoExtensionService.getInstance()

      cardSelectionManager.prepareSelection(
          calypsoCardExtensionProvider
              .createCardSelection()
              .filterByDfName(CalypsoClassicInfo.AID)
              .prepareReadRecord(
                  CalypsoClassicInfo.SFI_EnvironmentAndHolder, CalypsoClassicInfo.RECORD_NUMBER_1))

      cardSelectionManager.prepareSelection(
          calypsoCardExtensionProvider
              .createCardSelection()
              .filterByDfName("315449432E494341")
              .prepareReadRecord(
                  CalypsoClassicInfo.SFI_EnvironmentAndHolder, CalypsoClassicInfo.RECORD_NUMBER_1))

      cardSelectionManager.prepareSelection(
          calypsoCardExtensionProvider
              .createCardSelection()
              .filterByDfName("A0000004040125090101")
              .prepareReadRecord(
                  CalypsoClassicInfo.SFI_EnvironmentAndHolder, CalypsoClassicInfo.RECORD_NUMBER_1))

      cardSelectionManager.scheduleCardSelectionScenario(
          cardReader,
          ObservableCardReader.DetectionMode.REPEATING,
          ObservableCardReader.NotificationMode.ALWAYS)
    } catch (e: KeyplePluginException) {
      Timber.e(e)
    } catch (e: Exception) {
      Timber.e(e)
    }
    activity.notifyResult("Card reader ready.")
  }

  fun onReaderEvent(readerEvent: CardReaderEvent?) {

    timestamp = System.currentTimeMillis()
    activity.notifyResult("New ReaderEvent received : ${readerEvent?.type?.name}")

    CoroutineScope(Dispatchers.Main).launch {
      when (readerEvent?.type) {
        CardReaderEvent.Type.CARD_MATCHED -> {
          val selectionsResult =
              cardSelectionManager.parseScheduledCardSelectionsResponse(
                  readerEvent.scheduledCardSelectionsResponse)
          val calypsoCard = selectionsResult.activeSmartCard as CalypsoCard
          activity.notifyResult(
              "Card ${ByteArrayUtil.toHex(calypsoCard.applicationSerialNumber)} detected with DFNAME: ${ByteArrayUtil.toHex(calypsoCard.dfName)}")
          val efEnvironmentHolder =
              calypsoCard.getFileBySfi(CalypsoClassicInfo.SFI_EnvironmentAndHolder)
          activity.notifyResult(
              "Environment and Holder file:\n${
                            ByteArrayUtil.toHex(
                                efEnvironmentHolder.data.content
                            )
                        }")
          GlobalScope.launch(Dispatchers.IO) {
            try {
              runCardTransaction(cardReader, calypsoCard, cardSecuritySetting)
              val counter =
                  calypsoCard
                      .getFileBySfi(CalypsoClassicInfo.SFI_Counter1)
                      .data
                      .getContentAsCounterValue(CalypsoClassicInfo.RECORD_NUMBER_1)
              val eventLog =
                  ByteArrayUtil.toHex(
                      calypsoCard.getFileBySfi(CalypsoClassicInfo.SFI_EventLog).data.content)
              activity.notifyResult("Counter value: $counter")
              activity.notifyResult("EventLog file:\n$eventLog")
            } catch (e: KeyplePluginException) {
              Timber.e(e)
              activity.notifyResult("Exception: ${e.message}")
            } catch (e: Exception) {
              Timber.e(e)
              activity.notifyResult("Exception: ${e.message}")
            }
          }
          cardReader.finalizeCardProcessing()
        }
        CardReaderEvent.Type.CARD_INSERTED -> {
          activity.notifyResult("Card detected but AID didn't match with ${CalypsoClassicInfo.AID}")
          cardReader.finalizeCardProcessing()
        }
        CardReaderEvent.Type.CARD_REMOVED -> {
          activity.notifyResult("Card removed")
          activity.notifyHeader("Waiting for a card...")
        }
        else -> {
          // Do nothing
        }
      }
    }
  }

  private fun runCardTransaction(
      cardReader: CardReader,
      calypsoCard: CalypsoCard,
      cardSecuritySetting: CardSecuritySetting?
  ) {
    if (cardSecuritySetting == null) {
      runCardTransactionWithoutSam(cardReader, calypsoCard)
    } else {
      runCardTransactionWithSam(cardReader, calypsoCard, cardSecuritySetting)
    }
  }

  private fun runCardTransactionWithoutSam(cardReader: CardReader, calypsoCard: CalypsoCard) {
    val calypsoCardExtensionProvider = CalypsoExtensionService.getInstance()
    val cardTransaction =
        calypsoCardExtensionProvider.createCardTransactionWithoutSecurity(cardReader, calypsoCard)
    /*
     * Prepare the reading of two additional files.
     */
    cardTransaction
        .prepareReadRecord(CalypsoClassicInfo.SFI_EventLog, CalypsoClassicInfo.RECORD_NUMBER_1)
        .prepareReadRecord(CalypsoClassicInfo.SFI_ContractList, CalypsoClassicInfo.RECORD_NUMBER_1)
        .prepareReadRecord(CalypsoClassicInfo.SFI_Contracts, CalypsoClassicInfo.RECORD_NUMBER_1)
        .prepareReadRecord(CalypsoClassicInfo.SFI_Counter1, CalypsoClassicInfo.RECORD_NUMBER_1)

    cardTransaction.processCardCommands()
    val transactionTime = System.currentTimeMillis() - timestamp
    Timber.d("Card transaction ended successfully (%d ms)", transactionTime)
    activity.notifyResult("Transaction time $transactionTime ms")
  }

  private fun runCardTransactionWithSam(
      cardReader: CardReader,
      calypsoCard: CalypsoCard,
      cardSecuritySetting: CardSecuritySetting
  ) {
    val newEventRecord: ByteArray =
        ByteArrayUtil.fromHex("8013C8EC55667788112233445566778811223344556677881122334455")

    val cardTransactionManager =
        CalypsoExtensionService.getInstance()
            .createCardTransaction(cardReader, calypsoCard, cardSecuritySetting)
            .prepareReadRecords(
                CalypsoClassicInfo.SFI_EventLog,
                CalypsoClassicInfo.RECORD_NUMBER_1,
                CalypsoClassicInfo.RECORD_NUMBER_1,
                CalypsoClassicInfo.RECORD_SIZE)
            .prepareReadRecords(
                CalypsoClassicInfo.SFI_ContractList,
                CalypsoClassicInfo.RECORD_NUMBER_1,
                CalypsoClassicInfo.RECORD_NUMBER_1,
                CalypsoClassicInfo.RECORD_SIZE)
            .prepareReadRecords(
                CalypsoClassicInfo.SFI_Counter1,
                CalypsoClassicInfo.RECORD_NUMBER_1,
                CalypsoClassicInfo.RECORD_NUMBER_1,
                CalypsoClassicInfo.RECORD_SIZE)
            .processOpening(WriteAccessLevel.DEBIT)

    /*
    Place for the analysis of the context and the list of contracts
    */

    // read the elected contract
    cardTransactionManager
        .prepareReadRecords(
            CalypsoClassicInfo.SFI_Contracts,
            CalypsoClassicInfo.RECORD_NUMBER_1,
            CalypsoClassicInfo.RECORD_NUMBER_1,
            CalypsoClassicInfo.RECORD_SIZE)
        .processCardCommands()

    /*
    Place for the analysis of the contracts
    */

    // add an event record and close the Secure Session
    cardTransactionManager
        .prepareAppendRecord(CalypsoClassicInfo.SFI_EventLog, newEventRecord)
        .processClosing()

    val transactionTime = System.currentTimeMillis() - timestamp
    Timber.d("Card transaction ended successfully (%d ms)", transactionTime)
    activity.notifyResult("Transaction time $transactionTime ms")
  }

  /**
   * Setup the [CardResourceService] to provide a Calypso SAM C1 resource when requested.
   *
   * @param plugin The plugin to which the SAM reader belongs.
   * @param readerNameRegex A regular expression matching the expected SAM reader name.
   * @param samProfileName A string defining the SAM profile.
   * @throws IllegalStateException If the expected card resource is not found.
   */
  fun setupSecurityService(plugin: Plugin, readerNameRegex: String?, samProfileName: String?) {
    // Create a card resource extension expecting a SAM "C1".
    val samSelection =
        CalypsoExtensionService.getInstance()
            .createSamSelection()
            .filterByProductType(CalypsoSam.ProductType.SAM_C1)

    val samCardResourceExtension =
        CalypsoExtensionService.getInstance().createSamResourceProfileExtension(samSelection)

    // Get the service
    val cardResourceService = CardResourceServiceProvider.getService()

    // Configure the card resource service:
    // - allocation mode is blocking with a 100 milliseconds cycle and a 10 seconds timeout.
    // - the readers are searched in the PC/SC plugin, the observation of the plugin (for the
    // connection/disconnection of readers) and of the readers (for the insertion/removal of
    // cards)
    // is activated.
    // - two card resource profiles A and B are defined, each expecting a specific card
    // characterized by its power-on data and placed in a specific reader.
    // - the timeout for using the card's resources is set at 5 seconds.
    cardResourceService
        .configurator
        .withBlockingAllocationMode(100, 10000)
        .withPlugins(
            PluginsConfigurator.builder()
                .addPluginWithMonitoring(
                    plugin,
                    { reader: Reader ->
                      Timber.e("Nothing to configure for reader '%s'", reader.name)
                    },
                    { pluginName: String, e: Throwable ->
                      Timber.e(e, "An unexpected plugin error occurred in '%s':", pluginName)
                    },
                    { pluginName: String, readerName: String, e: Throwable ->
                      Timber.e(
                          e, "An unexpected reader error occurred: %s:%s", pluginName, readerName)
                    })
                .withUsageTimeout(5000)
                .build())
        .withCardResourceProfiles(
            CardResourceProfileConfigurator.builder(samProfileName, samCardResourceExtension)
                .withReaderNameRegex(readerNameRegex)
                .build())
        .configure()

    cardResourceService.start()

    // verify the resource availability
    val samResource = cardResourceService.getCardResource(samProfileName)
    Timber.i(
        "No valid SAM resource found for profile '$samProfileName' from reader '$readerNameRegex' in plugin '${plugin.name}'")
    if (samResource != null) {
      cardSecuritySetting =
          CalypsoExtensionService.getInstance()
              .createCardSecuritySetting()
              .setSamResource(samResource.reader, samResource.smartCard as CalypsoSam)
              .enableMultipleSession()
      activity.notifyResult("SAM resource found. Continue with security.")
    } else {
      activity.notifyResult("No SAM resource found. Continue without security.")
    }
  }
}
