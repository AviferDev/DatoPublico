package es.aviferdev.datopublico.app

import androidx.compose.ui.window.ComposeUIViewController

/** Entry point de iOS: el framework `ComposeApp` expone esta factoría al host. */
fun MainViewController() = ComposeUIViewController { App() }
