package com.accounts_SupplyChain.flows


import net.corda.core.flows.*
import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import com.r3.corda.lib.accounts.workflows.accountService
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount


import com.accounts_SupplyChain.contracts.InvoiceStateContract
import com.accounts_SupplyChain.states.InvoiceState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AnonymousParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import net.corda.core.node.StatesToRecord
import net.corda.core.utilities.ProgressTracker
import net.corda.core.node.services.vault.QueryCriteria

@StartableByRPC
@StartableByService
@InitiatingFlow
class SendInvoice(
        val whoAmI: String,
        val whereTo:String,
        val amount:Int
) : FlowLogic<String>(){

    companion object {
        object GENERATING_KEYS : ProgressTracker.Step("Generating Keys for transactions.")
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction for between accounts")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
        object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_KEYS,
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()



    @Suspendable
    override fun call(): String {

        //Generate key for transaction
        progressTracker.currentStep = GENERATING_KEYS
        val myAccount = accountService.accountInfo(whoAmI).single().state.data
        val myKey = subFlow(NewKeyForAccount(myAccount.identifier.id)).owningKey

        val targetAccount = accountService.accountInfo(whereTo).single().state.data
        val targetAcctAnonymousParty = subFlow(RequestKeyForAccount(targetAccount))



        //generating State for transfer
        progressTracker.currentStep = GENERATING_TRANSACTION
        val output = InvoiceState(amount, AnonymousParty(myKey),targetAcctAnonymousParty,UUID.randomUUID())
        val transactionBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
        transactionBuilder.addOutputState(output)
                .addCommand(InvoiceStateContract.Commands.Create(), listOf(targetAcctAnonymousParty.owningKey,myKey))

        //Pass along Transaction
        progressTracker.currentStep = SIGNING_TRANSACTION
        val locallySignedTx = serviceHub.signInitialTransaction(transactionBuilder, listOfNotNull(ourIdentity.owningKey,myKey))


        //Collect sigs
        progressTracker.currentStep =GATHERING_SIGS
        val sessionForAccountToSendTo = initiateFlow(targetAccount.host)
        val accountToMoveToSignature = subFlow(CollectSignatureFlow(locallySignedTx, sessionForAccountToSendTo, targetAcctAnonymousParty.owningKey))
        val signedByCounterParty = locallySignedTx.withAdditionalSignatures(accountToMoveToSignature)

        progressTracker.currentStep =FINALISING_TRANSACTION
        subFlow(FinalityFlow(signedByCounterParty, listOf(sessionForAccountToSendTo).filter { it.counterparty != ourIdentity }))
        return "Invoice send to " + targetAccount.host.name.organisation + "'s "+ targetAccount.name + " team."
    }
}

@InitiatedBy(SendInvoice::class)
class SendInvoiceResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>(){
    @Suspendable
    override fun call() {
        //placeholder to record account information for later use
        val accountMovedTo = AtomicReference<AccountInfo>()
        /* Tateyama add line 104 */
        val accountSender = AtomicReference<AccountInfo>()

        //extract account information from transaction
        val transactionSigner = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val keyStateMovedTo = stx.coreTransaction.outRefsOfType(InvoiceState::class.java).first().state.data.recipient
                keyStateMovedTo.let {
                    accountMovedTo.set(accountService.accountInfo(keyStateMovedTo.owningKey)?.state?.data)
                }
                if (accountMovedTo.get() == null) {
                    throw IllegalStateException("Account to move to was not found on this node")
                }
                /* Tateyama add line from 117 to 123 */
                val keyStateMovedFrom = stx.coreTransaction.outRefsOfType(InvoiceState::class.java).first().state.data.sender
                keyStateMovedFrom.let {
                    accountSender.set(accountService.accountInfo(keyStateMovedFrom.owningKey)?.state?.data)
                }
                if (accountSender.get() == null) {
                    throw IllegalStateException("Sender Information was not found on this node")
                }

            }
        }
        //record and finalize transaction
        val transaction = subFlow(transactionSigner)
        if (counterpartySession.counterparty != serviceHub.myInfo.legalIdentities.first()) {
            val recievedTx = subFlow(ReceiveFinalityFlow(counterpartySession, expectedTxId = transaction.id, statesToRecord = StatesToRecord.ALL_VISIBLE))
            val accountInfo = accountMovedTo.get()
            if (accountInfo != null) {
                subFlow(BroadcastToCarbonCopyReceiversFlow(accountInfo, recievedTx.coreTransaction.outRefsOfType(InvoiceState::class.java).first()))
            }
        }
    }

}
/*
*
* UUID is prefered to be used when identifying account, because there
* might be multiple accounts from different nodes that has the same name.
*
* */

//class SendInvoice(
//        val whoAmI: UUID,
//        val whereToUUID:UUID,
//        val amount:Int
//) : FlowLogic<String>(){
//
//    companion object {
//        object GENERATING_KEYS : ProgressTracker.Step("Generating Keys for transactions.")
//        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction for between accounts")
//        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
//        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
//        object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
//            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
//        }
//
//        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
//            override fun childProgressTracker() = FinalityFlow.tracker()
//        }
//
//        fun tracker() = ProgressTracker(
//                GENERATING_KEYS,
//                GENERATING_TRANSACTION,
//                VERIFYING_TRANSACTION,
//                SIGNING_TRANSACTION,
//                GATHERING_SIGS,
//                FINALISING_TRANSACTION
//        )
//    }
//
//    override val progressTracker = tracker()
//
//
//
//    @Suspendable
//    override fun call(): String {
//
//        //Create a key for Loan transaction
//        progressTracker.currentStep = GENERATING_KEYS
//        val myAccount = accountService.accountInfo(whoAmI)?.state!!.data
//        val myKey = subFlow(NewKeyForAccount(myAccount.identifier.id)).owningKey
//        val targetAccount = accountService.accountInfo(whereToUUID)?.state!!.data
//        val targetAcctAnonymousParty = subFlow(RequestKeyForAccount(targetAccount))
//
//
//
//        //generating State for transfer
//        progressTracker.currentStep = GENERATING_TRANSACTION
//        val output = InvoiceState(amount, AnonymousParty(myKey),targetAcctAnonymousParty,UUID.randomUUID())
//        val transactionBuilder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
//        transactionBuilder.addOutputState(output)
//                .addCommand(InvoiceStateContract.Commands.Create(), listOf(targetAcctAnonymousParty.owningKey,myKey))
//
//        //Pass along Transaction
//        progressTracker.currentStep = SIGNING_TRANSACTION
//        val locallySignedTx = serviceHub.signInitialTransaction(transactionBuilder, listOfNotNull(ourIdentity.owningKey,myKey))
//
//
//        //Collect sigs
//        progressTracker.currentStep =GATHERING_SIGS
//        val sessionForAccountToSendTo = initiateFlow(targetAccount.host)
//        val accountToMoveToSignature = subFlow(CollectSignatureFlow(locallySignedTx, sessionForAccountToSendTo, targetAcctAnonymousParty.owningKey))
//        val signedByCounterParty = locallySignedTx.withAdditionalSignatures(accountToMoveToSignature)
//
//        progressTracker.currentStep =FINALISING_TRANSACTION
//        val fullySignedTx = subFlow(FinalityFlow(signedByCounterParty, listOf(sessionForAccountToSendTo).filter { it.counterparty != ourIdentity }))
//        val movedState = fullySignedTx.coreTransaction.outRefsOfType(
//                InvoiceState::class.java
//
//        ).single()
//        return "Invoice send to " + targetAccount.host + "'s "+ targetAccount.name
//    }
//}
//
//@InitiatedBy(SendInvoice::class)
//class SendInvoiceResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>(){
//    @Suspendable
//    override fun call() {
//        val accountMovedTo = AtomicReference<AccountInfo>()
//        val transactionSigner = object : SignTransactionFlow(counterpartySession) {
//            override fun checkTransaction(tx: SignedTransaction) {
//                val keyStateMovedTo = tx.coreTransaction.outRefsOfType(InvoiceState::class.java).first().state.data.recipient
//                keyStateMovedTo?.let {
//                    accountMovedTo.set(accountService.accountInfo(keyStateMovedTo.owningKey)?.state?.data)
//                }
//                if (accountMovedTo.get() == null) {
//                    throw IllegalStateException("Account to move to was not found on this node")
//                }
//
//            }
//        }
//        val transaction = subFlow(transactionSigner)
//        if (counterpartySession.counterparty != serviceHub.myInfo.legalIdentities.first()) {
//            val recievedTx = subFlow(
//                    ReceiveFinalityFlow(
//                            counterpartySession,
//                            expectedTxId = transaction.id,
//                            statesToRecord = StatesToRecord.ALL_VISIBLE
//                    )
//            )
//            val accountInfo = accountMovedTo.get()
//            if (accountInfo != null) {
//                subFlow(BroadcastToCarbonCopyReceiversFlow(accountInfo, recievedTx.coreTransaction.outRefsOfType(InvoiceState::class.java).first()))
//            }
//
//
//
//        }
//    }
//
//}




