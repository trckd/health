package com.tracked.health

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.widget.Button
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.graphics.Color

class PermissionsRationaleActivity : Activity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create a simple layout programmatically
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val titleText = TextView(this).apply {
            text = "Health Data Privacy Policy"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        
        val bodyText = TextView(this).apply {
            text = """
                This app requests access to your step count data to provide you with:
                
                • Step tracking and progress monitoring
                • Health insights and trends
                • Background step count updates
                
                Your health data:
                • Remains on your device and is not shared with third parties
                • Is only used to provide the features you've requested
                • Can be revoked at any time through Health Connect settings
                
                We take your privacy seriously and only access the minimum data necessary to provide our services.
            """.trimIndent()
            textSize = 16f
            setPadding(0, 0, 0, 32)
        }
        
        val closeButton = Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        }
        
        layout.addView(titleText)
        layout.addView(bodyText)
        layout.addView(closeButton)
        scrollView.addView(layout)
        
        setContentView(scrollView)
    }
} 