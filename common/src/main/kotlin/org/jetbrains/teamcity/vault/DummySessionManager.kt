package org.jetbrains.teamcity.vault

import org.springframework.vault.authentication.SessionManager

class DummySessionManager :SessionManager {
    override fun getSessionToken() = null
}