package com.accounts_SupplyChain

import com.accounts_SupplyChain.flows.SendInvoiceResponder
import com.accounts_SupplyChain.states.InvoiceState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test

class FlowSendInvoiceTests {

    lateinit var mockNetwork: MockNetwork
    lateinit var a: StartedMockNode
    lateinit var b: StartedMockNode
    @Before
    fun setup() {
        mockNetwork = MockNetwork(
                listOf("net.corda.training"),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary", "London", "GB")))
        )
        a = mockNetwork.createNode(MockNodeParameters())
        b = mockNetwork.createNode(MockNodeParameters())
        val startedNodes = arrayListOf(a, b)
        // For real nodes this happens automatically, but we have to manually register the flow for tests
        startedNodes.forEach { it.registerInitiatedFlow(SendInvoiceResponder::class.java) }
        mockNetwork.runNetwork()

    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }
    @Test
    fun flowSendInvoice() {
        val whoAmI = a.info.chooseIdentityAndCert().party
        val whoAmI = a.info.l
        val whereTo = b.info.chooseIdentityAndCert().party
        val amount = 100
        val invoice = InvoiceState(amount,whoAmI,whereTo)

    }
}