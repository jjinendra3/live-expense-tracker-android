package app.liveexpense.capture

// Each rule comes from a real message of that format. Add a rule when a format lands in Unparsed.
object BankRules {
    private const val AMOUNT = """(?<amount>\d[\d,]*(?:\.\d{1,2})?)"""

    val all = listOf(
        BankRule(
            instrument = "IDBI",
            senderTokens = setOf("IDBI"),
            pattern = Regex(
                """IDBI Bank Acct (?<account>\S+) debited for Rs\.? ?$AMOUNT on \S+; """ +
                    """Bal Rs\.? ?\S+ (?<payee>.+?) credited\. UPI:(?<reference>\d+)""",
            ),
        ),
        BankRule(
            instrument = "HSBC CC",
            senderTokens = setOf("HSBC"),
            pattern = Regex(
                """HSBC Credit Card (?<account>\S+) used at (?<payee>.+?) for INR $AMOUNT on \S+\. Avl limit""",
            ),
        ),
        BankRule(
            instrument = "slice CC",
            senderTokens = setOf("SLCBNK"),
            pattern = Regex(
                """Rs\.? ?$AMOUNT spent on your credit card (?<account>\S+) at (?<payee>.+?) on \S+""" +
                    """ \(UPI Ref: (?<reference>\d+)\)\..*- slice""",
            ),
        ),
        BankRule(
            instrument = "HDFC CC",
            senderTokens = setOf("HDFC"),
            pattern = Regex(
                """Txn Rs\.? ?$AMOUNT\s+On HDFC Bank Card (?<account>\d+)\s+At (?<payee>.+?)\s+by UPI (?<reference>\d+)""",
            ),
        ),
    )
}
