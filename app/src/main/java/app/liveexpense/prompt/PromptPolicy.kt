package app.liveexpense.prompt

import app.liveexpense.data.Categories
import app.liveexpense.data.PayeeMemory

sealed interface PromptDecision {
    data class AutoFile(val category: String) : PromptDecision
    data class Ask(val suggested: String?) : PromptDecision
}

// Two matching choices before filing silently, so one mistaken tap can't misfile a payee forever.
private const val AUTO_FILE_AFTER_CONFIRMATIONS = 2
private const val PROMPT_CATEGORY_COUNT = 4

fun decidePrompt(memory: PayeeMemory?): PromptDecision = when {
    memory == null -> PromptDecision.Ask(suggested = null)
    memory.confirmations >= AUTO_FILE_AFTER_CONFIRMATIONS -> PromptDecision.AutoFile(memory.category)
    else -> PromptDecision.Ask(suggested = memory.category)
}

fun rememberChoice(existing: PayeeMemory?, payeeKey: String, category: String): PayeeMemory {
    val confirmations = if (existing?.category == category) existing.confirmations + 1 else 1
    return PayeeMemory(payeeKey, category, confirmations)
}

fun payeeKey(payee: String): String = payee.trim().lowercase()

fun promptCategories(suggested: String?, mostUsed: List<String>): List<String> =
    (listOfNotNull(suggested) + mostUsed + Categories.all)
        .filter { it != Categories.NOT_AN_EXPENSE }
        .distinct()
        .take(PROMPT_CATEGORY_COUNT)
