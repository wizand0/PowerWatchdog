package ru.wizand.powerwatchdog.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import ru.wizand.powerwatchdog.R
import ru.wizand.powerwatchdog.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var vb: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)

        val navController = findNavController(R.id.nav_host_fragment)
        vb.bottomNav.setupWithNavController(navController)
    }
}