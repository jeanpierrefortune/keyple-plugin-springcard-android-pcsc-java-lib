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
package org.calypsonet.keyple.plugin.bluebird.example.activity

import android.Manifest
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.view.MenuItem
import androidx.core.view.GravityCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.android.synthetic.main.activity_main.drawerLayout
import kotlinx.android.synthetic.main.activity_main.toolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.calypsonet.keyple.plugin.bluebird.BluebirdContactReader
import org.calypsonet.keyple.plugin.bluebird.BluebirdContactlessReader
import org.calypsonet.keyple.plugin.bluebird.BluebirdPlugin
import org.calypsonet.keyple.plugin.bluebird.BluebirdPluginFactoryProvider
import org.calypsonet.keyple.plugin.bluebird.BluebirdSupportContactlessProtocols
import org.calypsonet.keyple.plugin.bluebird.example.R
import org.calypsonet.keyple.plugin.bluebird.example.dialog.PermissionDeniedDialog
import org.calypsonet.keyple.plugin.bluebird.example.util.CalypsoClassicInfo
import org.calypsonet.keyple.plugin.bluebird.example.util.PermissionHelper
import org.calypsonet.terminal.calypso.WriteAccessLevel
import org.calypsonet.terminal.calypso.card.CalypsoCard
import org.calypsonet.terminal.reader.CardReaderEvent
import org.calypsonet.terminal.reader.ObservableCardReader
import org.calypsonet.terminal.reader.selection.CardSelectionManager
import org.calypsonet.terminal.reader.selection.CardSelectionResult
import org.calypsonet.terminal.reader.selection.ScheduledCardSelectionsResponse
import org.eclipse.keyple.card.calypso.CalypsoExtensionService
import org.eclipse.keyple.core.common.KeyplePluginExtensionFactory
import org.eclipse.keyple.core.service.ConfigurableReader
import org.eclipse.keyple.core.service.KeyplePluginException
import org.eclipse.keyple.core.service.ObservableReader
import org.eclipse.keyple.core.service.SmartCardServiceProvider
import org.eclipse.keyple.core.util.ByteArrayUtil
import timber.log.Timber

/**
 * Activity launched on app start up that display the only screen available on this example app.
 */
class MainActivity : AbstractExampleActivity() {

    private lateinit var cardSelectionManager: CardSelectionManager
    private lateinit var cardProtocol: BluebirdSupportContactlessProtocols

    private val areReadersInitialized = AtomicBoolean(false)

    private lateinit var progress: ProgressDialog

    private enum class TransactionType {
        DECREASE,
        INCREASE
    }

    override fun initContentView() {
        setContentView(R.layout.activity_main)
        initActionBar(toolbar, "Keyple demo", "Bluebird Plugin")
    }

    /**
     * Called when the activity (screen) is first displayed or resumed from background
     */
    override fun onResume() {
        super.onResume()

        progress = ProgressDialog(this)
        progress.setMessage(getString(R.string.please_wait))
        progress.setCancelable(false)

        // Check whether readers are already initialized (return from background) or not (first launch)
        if (!areReadersInitialized.get()) {
            addActionEvent("Enabling NFC Reader mode")
            addResultEvent("Please choose a use case")
            progress.show()
            initReaders()
        } else {
            addActionEvent("Start PO Read Write Mode")
            (poReader as ObservableReader).startCardDetection(ObservableCardReader.DetectionMode.REPEATING)
        }
    }

    /**
     * Initializes the PO reader (Contact Reader) and SAM reader (Contactless Reader)
     */
    override fun initReaders() {
        Timber.d("initReaders")
        // Connexion to Bluebird lib take time, we've added a callback to this factory.
        GlobalScope.launch {
            val pluginFactory: KeyplePluginExtensionFactory?
            try {
                pluginFactory = withContext(Dispatchers.IO) {
                    BluebirdPluginFactoryProvider.getFactory(this@MainActivity)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showAlertDialog(e, finish = true, cancelable = false)
                }
                return@launch
            }

            // Get the instance of the SmartCardService (Singleton pattern)
            val smartCardService = SmartCardServiceProvider.getService()

            // Register the Bluebird with SmartCardService, get the corresponding generic Plugin in return
            val bluebirdPlugin = smartCardService.registerPlugin(pluginFactory)

            // Get and configure the PO reader
            poReader = bluebirdPlugin.getReader(BluebirdContactlessReader.READER_NAME)
            (poReader as ObservableReader).setReaderObservationExceptionHandler { pluginName, readerName, e ->
                Timber.e("An unexpected reader error occurred: $pluginName:$readerName : $e")
            }

            // Set the current activity as Observer of the PO reader
            (poReader as ObservableReader).addObserver(this@MainActivity)

            cardProtocol = BluebirdSupportContactlessProtocols.NFC_ALL
            // Activate protocols for the PO reader
            (poReader as ConfigurableReader).activateProtocol(
                cardProtocol.key,
                cardProtocol.key
            )

            // Get and configure the SAM reader
            samReader = bluebirdPlugin.getReader(BluebirdContactReader.READER_NAME)

            PermissionHelper.checkPermission(
                this@MainActivity, arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    BluebirdPlugin.BLUEBIRD_SAM_PERMISSION
                )
            )

            areReadersInitialized.set(true)

            // Start the NFC detection
            (poReader as ObservableReader).startCardDetection(ObservableCardReader.DetectionMode.REPEATING)

            withContext(Dispatchers.Main) {
                progress.dismiss()
            }
        }
    }

    /**
     * Called when the activity (screen) is destroyed or put in background
     */
    override fun onPause() {
        if (areReadersInitialized.get()) {
            addActionEvent("Stopping PO Read Write Mode")
            // Stop NFC card detection
            (poReader as ObservableReader).stopCardDetection()
        }
        super.onPause()
    }

    /**
     * Called when the activity (screen) is destroyed
     */
    override fun onDestroy() {
        poReader?.let {
            // stop propagating the reader events
            (poReader as ObservableReader).removeObserver(this)
        }

        // Unregister the Bluebird plugin
        SmartCardServiceProvider.getService().plugins.forEach {
            SmartCardServiceProvider.getService().unregisterPlugin(it.name)
        }

        super.onDestroy()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        when (item.itemId) {
            R.id.usecase1 -> {
                clearEvents()
                addHeaderEvent("Running Calypso Read transaction (without SAM)")
                configureCalypsoTransaction(::runPoReadTransactionWithoutSam)
            }
            R.id.usecase2 -> {
                clearEvents()
                addHeaderEvent("Running Calypso Read transaction (with SAM)")
                configureCalypsoTransaction(::runPoReadTransactionWithSam)
            }
            R.id.usecase3 -> {
                clearEvents()
                addHeaderEvent("Running Calypso Read/Write transaction")
                configureCalypsoTransaction(::runPoReadWriteIncreaseTransaction)
            }
            R.id.usecase4 -> {
                clearEvents()
                addHeaderEvent("Running Calypso Read/Write transaction")
                configureCalypsoTransaction(::runPoReadWriteDecreaseTransaction)
            }
        }
        return true
    }

    override fun onReaderEvent(readerEvent: CardReaderEvent?) {
        addResultEvent("New ReaderEvent received : ${readerEvent?.type?.name}")
        useCase?.onEventUpdate(readerEvent)
    }

    private fun configureCalypsoTransaction(responseProcessor: (selectionsResponse: ScheduledCardSelectionsResponse) -> Unit) {
        addActionEvent("Prepare Calypso PO Selection with AID: ${CalypsoClassicInfo.AID}")
        try {
            /* Prepare a Calypso PO selection */
            cardSelectionManager =
                SmartCardServiceProvider.getService().createCardSelectionManager()

            /* Calypso selection: configures a PoSelector with all the desired attributes to make the selection and read additional information afterwards */
            calypsoCardExtensionProvider = CalypsoExtensionService.getInstance()

            val smartCardService = SmartCardServiceProvider.getService()
            smartCardService.checkCardExtension(calypsoCardExtensionProvider)

            val poSelectionRequest =
                calypsoCardExtensionProvider.createCardSelection()
            poSelectionRequest
                .filterByDfName(CalypsoClassicInfo.AID)
                .filterByCardProtocol(cardProtocol.key)

            /* Prepare the reading order and keep the associated parser for later use once the
             selection has been made. */
            poSelectionRequest.prepareReadRecordFile(
                CalypsoClassicInfo.SFI_EnvironmentAndHolder,
                CalypsoClassicInfo.RECORD_NUMBER_1.toInt()
            )

            /*
             * Add the selection case to the current selection (we could have added other cases
             * here)
             */
            cardSelectionManager.prepareSelection(poSelectionRequest)

            /*
            * Provide the SeReader with the selection operation to be processed when a PO is
            * inserted.
            */
            cardSelectionManager.scheduleCardSelectionScenario(
                poReader as ObservableReader,
                ObservableCardReader.DetectionMode.REPEATING,
                ObservableCardReader.NotificationMode.ALWAYS
            )

            useCase = object : UseCase {
                override fun onEventUpdate(event: CardReaderEvent?) {
                    CoroutineScope(Dispatchers.Main).launch {
                        when (event?.type) {
                            CardReaderEvent.Type.CARD_MATCHED -> {
                                addResultEvent("PO detected with AID: ${CalypsoClassicInfo.AID}")
                                responseProcessor(event.scheduledCardSelectionsResponse)
                                (poReader as ObservableReader).finalizeCardProcessing()
                            }

                            CardReaderEvent.Type.CARD_INSERTED -> {
                                addResultEvent("PO detected but AID didn't match with ${CalypsoClassicInfo.AID}")
                                (poReader as ObservableReader).finalizeCardProcessing()
                            }

                            CardReaderEvent.Type.CARD_REMOVED -> {
                                addResultEvent("PO removed")
                            }
                            else -> {
                                // Do nothing
                            }
                        }
                    }
                    // eventRecyclerView.smoothScrollToPosition(events.size - 1)
                }
            }

            // notify reader that se detection has been launched
            addActionEvent("Waiting for PO presentation")
        } catch (e: KeyplePluginException) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        } catch (e: Exception) {
            Timber.e(e)
            addResultEvent("Exception: ${e.message}")
        }
    }

    private fun runPoReadTransactionWithSam(selectionsResponse: ScheduledCardSelectionsResponse) {
        runPoReadTransaction(selectionsResponse, true)
    }

    private fun runPoReadTransactionWithoutSam(selectionsResponse: ScheduledCardSelectionsResponse) {
        runPoReadTransaction(selectionsResponse, false)
    }

    private fun runPoReadTransaction(
        selectionsResponse: ScheduledCardSelectionsResponse,
        withSam: Boolean
    ) {

        GlobalScope.launch(Dispatchers.IO) {
            try {
                /*
                 * print tag info in View
                 */

                addActionEvent("Process selection")
                val selectionsResult =
                    cardSelectionManager.parseScheduledCardSelectionsResponse(selectionsResponse)

                if (selectionsResult.activeSelectionIndex != -1) {
                    addResultEvent("Selection successful")
                    val calypsoPo = selectionsResult.activeSmartCard as CalypsoCard

                    /*
                     * Retrieve the data read from the parser updated during the selection process
                     */
                    val efEnvironmentHolder =
                        calypsoPo.getFileBySfi(CalypsoClassicInfo.SFI_EnvironmentAndHolder)
                    addActionEvent("Read environment and holder data")

                    addResultEvent(
                        "Environment and Holder file: ${
                            ByteArrayUtil.toHex(
                                efEnvironmentHolder.data.content
                            )
                        }"
                    )

                    addHeaderEvent("2nd PO exchange: read the event log file")

                    val poTransaction = if (withSam) {
                        addActionEvent("Create Po secured transaction with SAM")

                        // Configure the card resource service to provide an adequate SAM for future secure operations.
                        // We suppose here, we use a Identive contact PC/SC reader as card reader.
                        val bluebirdPlugin = SmartCardServiceProvider.getService()
                            .getPlugin(BluebirdPlugin.PLUGIN_NAME)
                        setupCardResourceService(
                            bluebirdPlugin,
                            CalypsoClassicInfo.SAM_READER_NAME_REGEX,
                            CalypsoClassicInfo.SAM_PROFILE_NAME
                        )

                        /*
                         * Create Po secured transaction.
                         *
                         * check the availability of the SAM doing a ATR based selection, open its physical and
                         * logical channels and keep it open
                         */
                        calypsoCardExtensionProvider.createCardTransaction(
                            poReader,
                            calypsoPo,
                            getSecuritySettings()
                        )
                    } else {
                        // Create Po unsecured transaction
                        calypsoCardExtensionProvider.createCardTransactionWithoutSecurity(
                            poReader,
                            calypsoPo
                        )
                    }

                    /*
                     * Prepare the reading order and keep the associated parser for later use once the
                     * transaction has been processed.
                     */
                    poTransaction.prepareReadRecordFile(
                        CalypsoClassicInfo.SFI_EventLog,
                        CalypsoClassicInfo.RECORD_NUMBER_1.toInt()
                    )

                    poTransaction.prepareReadRecordFile(
                        CalypsoClassicInfo.SFI_Counter1,
                        CalypsoClassicInfo.RECORD_NUMBER_1.toInt()
                    )

                    /*
                     * Actual PO communication: send the prepared read order, then close the channel
                     * with the PO
                     */
                    addActionEvent("Process PO Command for counter and event logs reading")

                    if (withSam) {
                        addActionEvent("Process PO Opening session for transactions")
                        poTransaction.processOpening(WriteAccessLevel.LOAD)
                        addResultEvent("Opening session: SUCCESS")
                        val counter = readCounter(selectionsResult)
                        val eventLog = ByteArrayUtil.toHex(readEventLog(selectionsResult))

                        addActionEvent("Process PO Closing session")
                        poTransaction.processClosing()
                        addResultEvent("Closing session: SUCCESS")

                        // In secured reading, value read elements can only be trusted if the session is closed without error.
                        addResultEvent("Counter value: $counter")
                        addResultEvent("EventLog file: $eventLog")
                    } else {
                        poTransaction.processCardCommands()
                        addResultEvent("Counter value: ${readCounter(selectionsResult)}")
                        addResultEvent(
                            "EventLog file: ${
                                ByteArrayUtil.toHex(
                                    readEventLog(
                                        selectionsResult
                                    )
                                )
                            }"
                        )
                    }

                    addResultEvent("End of the Calypso PO processing.")
                    addResultEvent("You can remove the card now")
                } else {
                    addResultEvent("The selection of the PO has failed. Should not have occurred due to the MATCHED_ONLY selection mode.")
                }
            } catch (e: KeyplePluginException) {
                Timber.e(e)
                addResultEvent("Exception: ${e.message}")
            } catch (e: Exception) {
                Timber.e(e)
                addResultEvent("Exception: ${e.message}")
            }
        }
    }

    private fun readCounter(selectionsResult: CardSelectionResult): Int {
        val calypsoPo = selectionsResult.activeSmartCard as CalypsoCard
        val efCounter1 = calypsoPo.getFileBySfi(CalypsoClassicInfo.SFI_Counter1)
        return efCounter1.data.getContentAsCounterValue(CalypsoClassicInfo.RECORD_NUMBER_1.toInt())
    }

    private fun readEventLog(selectionsResult: CardSelectionResult): ByteArray? {
        val calypsoPo = selectionsResult.activeSmartCard as CalypsoCard
        val efCounter1 = calypsoPo.getFileBySfi(CalypsoClassicInfo.SFI_EventLog)
        return efCounter1.data.content
    }

    private fun runPoReadWriteIncreaseTransaction(selectionsResponse: ScheduledCardSelectionsResponse) {
        runPoReadWriteTransaction(selectionsResponse, TransactionType.INCREASE)
    }

    private fun runPoReadWriteDecreaseTransaction(selectionsResponse: ScheduledCardSelectionsResponse) {
        runPoReadWriteTransaction(selectionsResponse, TransactionType.DECREASE)
    }

    private fun runPoReadWriteTransaction(
        selectionsResponse: ScheduledCardSelectionsResponse,
        transactionType: TransactionType
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                addActionEvent("1st PO exchange: aid selection")
                val selectionsResult =
                    cardSelectionManager.parseScheduledCardSelectionsResponse(selectionsResponse)

                if (selectionsResult.activeSelectionIndex != -1) {
                    addResultEvent("Calypso PO selection: SUCCESS")
                    val calypsoPo = selectionsResult.activeSmartCard as CalypsoCard
                    addResultEvent("AID: ${ByteArrayUtil.fromHex(CalypsoClassicInfo.AID)}")

                    val bluebirdPlugin =
                        SmartCardServiceProvider.getService().getPlugin(BluebirdPlugin.PLUGIN_NAME)
                    setupCardResourceService(
                        bluebirdPlugin,
                        CalypsoClassicInfo.SAM_READER_NAME_REGEX,
                        CalypsoClassicInfo.SAM_PROFILE_NAME
                    )

                    addActionEvent("Create Po secured transaction with SAM")
                    // Create Po secured transaction
                    val poTransaction = calypsoCardExtensionProvider.createCardTransaction(
                        poReader,
                        calypsoPo,
                        getSecuritySettings()
                    )

                    when (transactionType) {
                        TransactionType.INCREASE -> {
                            /*
                            * Open Session for the debit key
                            */
                            addActionEvent("Process PO Opening session for transactions")
                            poTransaction.processOpening(WriteAccessLevel.LOAD)
                            addResultEvent("Opening session: SUCCESS")

                            poTransaction.prepareReadRecordFile(
                                CalypsoClassicInfo.SFI_Counter1,
                                CalypsoClassicInfo.RECORD_NUMBER_1.toInt()
                            )
                            poTransaction.processCardCommands()

                            poTransaction.prepareIncreaseCounter(
                                CalypsoClassicInfo.SFI_Counter1,
                                CalypsoClassicInfo.RECORD_NUMBER_1.toInt(),
                                10
                            )
                            addActionEvent("Process PO increase counter by 10")
                            poTransaction.processClosing()
                            addResultEvent("Increase by 10: SUCCESS")
                        }
                        TransactionType.DECREASE -> {
                            /*
                        * Open Session for the debit key
                        */
                            addActionEvent("Process PO Opening session for transactions")
                            poTransaction.processOpening(WriteAccessLevel.DEBIT)
                            addResultEvent("Opening session: SUCCESS")

                            poTransaction.prepareReadRecordFile(
                                CalypsoClassicInfo.SFI_Counter1,
                                CalypsoClassicInfo.RECORD_NUMBER_1.toInt()
                            )
                            poTransaction.processCardCommands()

                            /*
                         * A ratification command will be sent (CONTACTLESS_MODE).
                         */
                            poTransaction.prepareDecreaseCounter(
                                CalypsoClassicInfo.SFI_Counter1,
                                CalypsoClassicInfo.RECORD_NUMBER_1.toInt(),
                                1
                            )
                            addActionEvent("Process PO decreasing counter and close transaction")
                            poTransaction.processClosing()
                            addResultEvent("Decrease by 1: SUCCESS")
                        }
                    }

                    addResultEvent("End of the Calypso PO processing.")
                    addResultEvent("You can remove the card now")
                } else {
                    addResultEvent("The selection of the PO has failed. Should not have occurred due to the MATCHED_ONLY selection mode.")
                }
            } catch (e: KeyplePluginException) {
                Timber.e(e)
                addResultEvent("Exception: ${e.message}")
            } catch (e: Exception) {
                Timber.e(e)
                addResultEvent("Exception: ${e.message}")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PermissionHelper.MY_PERMISSIONS_REQUEST_ALL -> {
                val storagePermissionGranted = grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (!storagePermissionGranted) {
                    PermissionDeniedDialog()
                        .apply {
                            show(
                                supportFragmentManager,
                                PermissionDeniedDialog::class.java.simpleName
                            )
                        }
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }
}
