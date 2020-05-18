package com.accounts_SupplyChain

import net.corda.core.contracts.TypeOnlyCommandData
import com.accounts_SupplyChain.states.InvoiceState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import org.junit.Test

class ContractTests {

    // A pre-defined dummy command.
    class DummyCommand : TypeOnlyCommandData()
    private var ledgerServices = MockServices(listOf("net.corda.training"))

    private val ledgerServices = MockServices(listOf("com.accounts_SupplyChain.contracts")) //listOf("com.accounts_SupplyChain.contracts")
    private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
    private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "New York", "US"))
    private val loanValue = 1


    @Test
    fun mustIncludeIssueCommand() {
        val iou = InvoiceState(1.POUNDS, ALICE.party, BOB.party)
        ledgerServices.ledger {
            transaction {
                output(IOUContract.IOU_CONTRACT_ID,  iou)
                command(listOf(ALICE.publicKey, BOB.publicKey), DummyCommand()) // Wrong type.
                this.fails()
            }
            transaction {
                output(IOUContract.IOU_CONTRACT_ID, iou)
                command(listOf(ALICE.publicKey, BOB.publicKey), IOUContract.Commands.Issue()) // Correct type.
                this.verifies()
            }
        }
    }

    }
}