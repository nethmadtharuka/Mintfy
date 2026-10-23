package com.mintfy.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.FirebaseApp
import com.mintfy.app.ui.theme.MintfyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check Firebase initialization
        val firebaseApp = FirebaseApp.initializeApp(this)

        if (firebaseApp != null) {
            Log.d("MintfyFirebase", "Firebase initialized successfully!")
            Log.d("MintfyFirebase", "Project ID: ${firebaseApp.options.projectId}")
            Log.d("MintfyFirebase", "App ID: ${firebaseApp.options.applicationId}")
        } else {
            Log.e("MintfyFirebase", "Firebase initialization failed!")
        }

        enableEdgeToEdge()

        setContent {
            MintfyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Mintfy",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MintfyTheme {
        Greeting("Mintfy")
    }
}