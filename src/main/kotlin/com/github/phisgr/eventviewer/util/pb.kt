package com.github.phisgr.eventviewer.util

import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat

private val pretty = JsonFormat.printer().includingDefaultValueFields()
fun Message.prettyPrint(): String = pretty.print(this)

private val oneLine = JsonFormat.printer().includingDefaultValueFields().omittingInsignificantWhitespace()
fun Message.json(): String = oneLine.print(this)

fun fromJson(m: Message, s: String): Message = m.newBuilderForType().also {
    JsonFormat.parser().merge(s, it)
}.build()
