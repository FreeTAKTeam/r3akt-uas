package org.r3akt.uas

enum class SetupState(val label: String) {
    OK("OK"), WAITING("WAITING"), PROBLEM("PROBLEM"), NOT_IN_BUILD("NOT IN THIS BUILD"), UNKNOWN("UNKNOWN"),
}

/** One row of the connection/setup checklist; [text] is the source's own report, verbatim where it has one. */
data class SetupItem(val item: String, val state: SetupState, val text: String)
