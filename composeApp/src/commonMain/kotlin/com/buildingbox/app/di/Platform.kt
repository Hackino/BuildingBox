package com.buildingbox.app.di

import org.koin.core.module.Module

/**
 * Platform-provided bindings: AuthGateway, RealtimeDb, CrashReporter.
 * - android / ios  → GitLive Firebase SDK
 * - desktop (jvm)  → Ktor + Firebase REST
 */
expect fun platformModule(): Module
