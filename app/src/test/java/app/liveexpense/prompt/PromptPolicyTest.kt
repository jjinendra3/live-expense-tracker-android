package app.liveexpense.prompt

import app.liveexpense.data.Categories
import app.liveexpense.data.PayeeMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptPolicyTest {
    @Test
    fun `a first-time payee is asked with no suggestion`() {
        assertEquals(PromptDecision.Ask(suggested = null), decidePrompt(memory = null))
    }

    @Test
    fun `a payee chosen once is asked with that category suggested`() {
        val memory = PayeeMemory("cafe", "Food", confirmations = 1)
        assertEquals(PromptDecision.Ask(suggested = "Food"), decidePrompt(memory))
    }

    @Test
    fun `a payee chosen the same way twice is filed without asking`() {
        val memory = PayeeMemory("cafe", "Food", confirmations = 2)
        assertEquals(PromptDecision.AutoFile("Food"), decidePrompt(memory))
    }

    @Test
    fun `choosing a payee for the first time records one confirmation`() {
        assertEquals(PayeeMemory("cafe", "Food", 1), rememberChoice(null, "cafe", "Food"))
    }

    @Test
    fun `choosing the same category again adds a confirmation`() {
        val memory = PayeeMemory("cafe", "Food", 1)
        assertEquals(PayeeMemory("cafe", "Food", 2), rememberChoice(memory, "cafe", "Food"))
    }

    @Test
    fun `choosing a different category restarts the count, so a misfiled payee gets asked again`() {
        val memory = PayeeMemory("cafe", "Food", 5)
        val updated = rememberChoice(memory, "cafe", "Shop")
        assertEquals(PayeeMemory("cafe", "Shop", 1), updated)
        assertEquals(PromptDecision.Ask(suggested = "Shop"), decidePrompt(updated))
    }

    @Test
    fun `payee keys ignore case and surrounding spaces`() {
        assertEquals(payeeKey("EXAMPLE Cafe"), payeeKey("  example cafe "))
    }

    @Test
    fun `the prompt offers four categories by default`() {
        assertEquals(
            listOf("Food", "Grocery", "Travel", "Shop"),
            promptCategories(suggested = null, mostUsed = emptyList()),
        )
    }

    @Test
    fun `the suggested category comes first`() {
        assertEquals(
            listOf("Bills", "Food", "Grocery", "Travel"),
            promptCategories(suggested = "Bills", mostUsed = emptyList()),
        )
    }

    @Test
    fun `most used categories come before the defaults without duplicates`() {
        assertEquals(
            listOf("Health", "Bills", "Food", "Grocery"),
            promptCategories(suggested = "Health", mostUsed = listOf("Bills", "Health")),
        )
    }

    @Test
    fun `not an expense is never one of the four, even when suggested`() {
        assertEquals(
            listOf("Food", "Grocery", "Travel", "Shop"),
            promptCategories(suggested = NOT_AN_EXPENSE_FOR_TEST, mostUsed = listOf(NOT_AN_EXPENSE_FOR_TEST)),
        )
    }

    @Test
    fun `every category is short enough to fit a prompt chip`() {
        // Four chips share about 205dp on a Galaxy S23, and anything longer was cut off there.
        Categories.all.forEach { assertTrue(it, it.length <= 7) }
    }

    private companion object {
        const val NOT_AN_EXPENSE_FOR_TEST = app.liveexpense.data.Categories.NOT_AN_EXPENSE
    }
}
