package com.example.myapp

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.io.*
import java.net.Socket

// Наследуемся от Activity (так проще с темами)
class MainActivity : Activity() {
    private lateinit var outputText: TextView
    private lateinit var cmdInput: EditText
    private lateinit var pathText: TextView
    private lateinit var connectBtn: Button
    private lateinit var ipInput: EditText
    private lateinit var scrollView: ScrollView

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var connected = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- УБИРАЕМ БЕЛЫЕ ПОЛОСЫ (Для Xiaomi и др.) ---
        window.statusBarColor = Color.parseColor("#121212")
        window.navigationBarColor = Color.parseColor("#121212")

        // Основной контейнер
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(30, 30, 30, 30)
        }

        val headerTitle = TextView(this).apply {
            text = "REMOTE TERMINAL"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setPadding(0, 10, 0, 10)
        }

        pathText = TextView(this).apply {
            text = "Путь: ---"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 5, 0, 20)
        }

        ipInput = EditText(this).apply {
            setText("192.168.1.81")
            setTextColor(Color.WHITE)
            hint = "IP сервера"
            setHintTextColor(Color.DKGRAY)
        }

        connectBtn = Button(this).apply {
            text = "ПОДКЛЮЧИТЬСЯ"
            setBackgroundColor(Color.parseColor("#222222"))
            setTextColor(Color.WHITE)
        }

        outputText = TextView(this).apply {
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 13f
        }

        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            addView(outputText)
        }

        cmdInput = EditText(this).apply {
            hint = "Введите команду..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            isEnabled = false
            imeOptions = EditorInfo.IME_ACTION_SEND
            setRawInputType(android.text.InputType.TYPE_CLASS_TEXT)
        }

        layout.addView(headerTitle)
        layout.addView(pathText)
        layout.addView(ipInput)
        layout.addView(connectBtn)
        layout.addView(scrollView)
        layout.addView(cmdInput)
        
        setContentView(layout)

        connectBtn.setOnClickListener { if (!connected) connect() else disconnect() }

        cmdInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val cmd = cmdInput.text.toString().trim()
                if (cmd.isNotEmpty()) {
                    handleCommand(cmd)
                    cmdInput.text.clear()
                }
                true
            } else false
        }
    }

    private fun handleCommand(cmd: String) {
        when (cmd.lowercase()) {
            "cls" -> outputText.text = ""
            "exit" -> disconnect()
            else -> sendToServer(cmd)
        }
    }

    private fun connect() {
        val ip = ipInput.text.toString().trim()
        Thread {
            try {
                socket = Socket(ip, 7777)
                writer = socket!!.getOutputStream().bufferedWriter()
                reader = socket!!.getInputStream().bufferedReader()
                connected = true
                handler.post {
                    connectBtn.text = "ОТКЛЮЧИТЬСЯ"
                    connectBtn.setBackgroundColor(Color.parseColor("#880000"))
                    cmdInput.isEnabled = true
                    ipInput.isEnabled = false
                    showLog("✅ Соединение установлено\n")
                    sendToServer("") 
                }
            } catch (e: Exception) {
                handler.post { showLog("❌ Ошибка: ${e.message}\n") }
            }
        }.start()
    }

    private fun disconnect() {
        Thread {
            try { writer?.write("exit\n"); writer?.flush(); socket?.close() } catch (e: Exception) {}
            connected = false
            handler.post {
                connectBtn.text = "ПОДКЛЮЧИТЬСЯ"
                connectBtn.setBackgroundColor(Color.parseColor("#222222"))
                cmdInput.isEnabled = false
                ipInput.isEnabled = true
                pathText.text = "Путь: ---"
                showLog("🔌 Сеанс завершен\n")
            }
        }.start()
    }

    private fun sendToServer(cmd: String) {
        if (!connected) return
        Thread {
            try {
                writer?.write(cmd + "\n")
                writer?.flush()
                val response = StringBuilder()
                var line: String?
                while (true) {
                    line = reader?.readLine()
                    if (line == null || line == "END_OF_OUTPUT") break
                    if (line.startsWith("[PATH]")) {
                        val path = line.replace("[PATH]", "")
                        handler.post { pathText.text = "Путь: $path" }
                    } else {
                        response.append(line).append("\n")
                    }
                }
                handler.post {
                    if (cmd.isNotEmpty()) outputText.append("\n> $cmd\n")
                    outputText.append(response.toString())
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            } catch (e: Exception) {
                handler.post { showLog("❌ Ошибка сети\n"); disconnect() }
            }
        }.start()
    }

    private fun showLog(msg: String) {
        outputText.append(msg)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}