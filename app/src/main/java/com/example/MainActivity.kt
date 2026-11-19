package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.example.data.repository.AuthRepository
import com.example.ui.navigation.XChatRoot
import com.example.ui.theme.XChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val authRepository = remember { AuthRepository() }
            LaunchedEffect(Unit) {
                authRepository.attemptSilentSignIn(this@MainActivity)
            }
            XChatTheme {
                XChatRoot(authRepository = authRepository)
            }
        }
    }
}
