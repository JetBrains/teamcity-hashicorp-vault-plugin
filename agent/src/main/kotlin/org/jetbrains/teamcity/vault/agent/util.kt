package org.jetbrains.teamcity.vault.agent

fun String.ensureHasPrefix(prefix: String) = if (!this.startsWith(prefix)) "$prefix$this" else this
