package com.example.oilchangetracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    
    private lateinit var imageViewOdometer: ImageView
    private lateinit var buttonCapture: Button
    private lateinit var editTextMileage: EditText
    private lateinit var buttonSave: Button
    private lateinit var textViewLastChange: TextView
    private lateinit var textViewNextChange: TextView
    private lateinit var editTextInterval: EditText
    private lateinit var buttonUpdateInterval: Button
    
    private val sharedPrefs by lazy { getSharedPreferences("OilChangePrefs", Context.MODE_PRIVATE) }
    private var lastMileage: Int = 0
    private var oilChangeInterval: Int = 5000 // Default 5000 kilometers
    
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                imageViewOdometer.setImageBitmap(it)
                
                // In a real app, we would use ML Kit or similar for OCR to extract mileage
                // For this demonstration, we'll just use manual input
                Toast.makeText(this, "Please enter the odometer reading manually", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize views
        imageViewOdometer = findViewById(R.id.imageViewOdometer)
        buttonCapture = findViewById(R.id.buttonCapture)
        editTextMileage = findViewById(R.id.editTextMileage)
        buttonSave = findViewById(R.id.buttonSave)
        textViewLastChange = findViewById(R.id.textViewLastChange)
        textViewNextChange = findViewById(R.id.textViewNextChange)
        editTextInterval = findViewById(R.id.editTextInterval)
        buttonUpdateInterval = findViewById(R.id.buttonUpdateInterval)
        
        // Load saved data
        loadSavedData()
        
        // Set up click listeners
        buttonCapture.setOnClickListener {
            checkCameraPermission()
        }
        
        buttonSave.setOnClickListener {
            saveOilChange()
        }
        
        buttonUpdateInterval.setOnClickListener {
            updateOilChangeInterval()
        }
        
        // Create notification channel for Android 8.0+
        createNotificationChannel()
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == 
                    PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(cameraIntent)
    }
    
    private fun saveOilChange() {
        val mileageText = editTextMileage.text.toString()
        if (mileageText.isBlank()) {
            Toast.makeText(this, "Please enter the current mileage", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val currentMileage = mileageText.toInt()
            
            // Save the current mileage as the last oil change mileage
            sharedPrefs.edit()
                .putInt("lastMileage", currentMileage)
                .putLong("lastChangeTimestamp", System.currentTimeMillis())
                .apply()
            
            lastMileage = currentMileage
            
            // Update UI
            updateUI()
            
            Toast.makeText(this, "Oil change record saved successfully", Toast.LENGTH_SHORT).show()
            
            // Clear input
            editTextMileage.text.clear()
            imageViewOdometer.setImageResource(R.drawable.placeholder_odometer)
            
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateOilChangeInterval() {
        val intervalText = editTextInterval.text.toString()
        if (intervalText.isBlank()) {
            Toast.makeText(this, "Please enter an interval", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val newInterval = intervalText.toInt()
            if (newInterval < 1000) {
                Toast.makeText(this, "Interval should be at least 1000 km", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Save the new interval
            sharedPrefs.edit()
                .putInt("oilChangeInterval", newInterval)
                .apply()
            
            oilChangeInterval = newInterval
            
            // Update UI
            updateUI()
            
            Toast.makeText(this, "Oil change interval updated", Toast.LENGTH_SHORT).show()
            
            // Clear input
            editTextInterval.text.clear()
            
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadSavedData() {
        lastMileage = sharedPrefs.getInt("lastMileage", 0)
        oilChangeInterval = sharedPrefs.getInt("oilChangeInterval", 5000)
        
        // Update UI with loaded data
        updateUI()
        
        // Display the saved interval in the EditText
        editTextInterval.hint = "Current: $oilChangeInterval km"
    }
    
    private fun updateUI() {
        val lastChangeDate = sharedPrefs.getLong("lastChangeTimestamp", 0)
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val dateStr = if (lastChangeDate > 0) dateFormat.format(Date(lastChangeDate)) else "Never"
        
        textViewLastChange.text = "Last Oil Change: $lastMileage km on $dateStr"
        textViewNextChange.text = "Next Oil Change: ${lastMileage + oilChangeInterval} km"
        
        // Check if we should show a notification
        checkAndScheduleNotification()
    }
    
    private fun checkAndScheduleNotification() {
        // For demonstration purposes, we'll show a notification if we're within 500km of the next change
        val currentMileage = editTextMileage.text.toString().toIntOrNull() ?: lastMileage
        val nextChangeMileage = lastMileage + oilChangeInterval
        
        if (currentMileage > 0 && nextChangeMileage - currentMileage <= 500) {
            val kmRemaining = nextChangeMileage - currentMileage
            
            lifecycleScope.launch(Dispatchers.Main) {
                showOilChangeNotification(kmRemaining)
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Oil Change Reminders"
            val descriptionText = "Notifications for oil change reminders"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("oil_change_channel", name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showOilChangeNotification(kmRemaining: Int) {
        val builder = NotificationCompat.Builder(this, "oil_change_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Oil Change Reminder")
            .setContentText("You have approximately $kmRemaining km until your next oil change")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, builder.build())
    }
}
