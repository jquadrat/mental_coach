package com.mentalcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.mentalcoach.media.PhotoStore
import com.mentalcoach.media.Speaker
import com.mentalcoach.ui.VisitViewModel
import com.mentalcoach.ui.nav.MentalCoachRoot
import com.mentalcoach.ui.theme.MentalCoachTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var speaker: Speaker

    @Inject lateinit var photoStore: PhotoStore

    private val vm: VisitViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MentalCoachTheme {
                MentalCoachRoot(vm, speaker, photoStore)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Stop speaking when the visitor leaves the app.
        speaker.stop()
    }
}
