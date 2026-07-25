package com.traceworthy.app

/**
 * Source of truth for the app's legal copy — shown in the first-run agreement
 * and re-readable later in Learn. Mirrors DISCLAIMER.md.
 */
object LegalText {

    val notLegalAdvice = listOf(
        "TraceWorthy and the documents it generates are provided for general " +
            "informational purposes only and do not constitute legal advice. The " +
            "developer is not a lawyer, and using this app does not create an " +
            "attorney–client relationship.",
        "Laws about harassment, call recording, and evidence differ by state and " +
            "change over time. If you need legal advice about your situation, contact " +
            "a licensed attorney in your state. In an emergency, call 911.",
        "TraceWorthy is provided \"as is,\" without warranty of any kind. You are " +
            "responsible for how you use the app and any documents it produces.",
    )

    val nonAffiliation = listOf(
        "TraceWorthy is an independent, free project for documenting harassing phone " +
            "calls. It is not affiliated with, endorsed by, or connected to any other " +
            "person or company using the same or a similar name, including the unrelated " +
            "business-advisory firm \"TraceWorthy Consulting.\"",
        "All other product names and trademarks are the property of their respective owners.",
    )
}
