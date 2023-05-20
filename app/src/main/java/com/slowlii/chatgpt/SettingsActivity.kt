package com.slowlii.chatgpt

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class ChatGPTkeyActivity : AppCompatActivity() {
    private lateinit var editText: EditText
    private lateinit var saveButton: Button
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        editText = findViewById(R.id.editText)
        saveButton = findViewById(R.id.saveButton)

        sharedPreferences = getSharedPreferences("MyPreferences", MODE_PRIVATE)
        val savedText = sharedPreferences.getString("OpenAiApiKey", "")
        editText.setText(savedText)

        saveButton.setOnClickListener {
            val text = editText.text.toString()
            saveText(text)
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveText(text: String) {
        val editor = sharedPreferences.edit()
        editor.putString("OpenAiApiKey", text)
        editor.apply()
    }
}
