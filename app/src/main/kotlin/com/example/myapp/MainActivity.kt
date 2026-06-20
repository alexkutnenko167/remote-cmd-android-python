package com.example.myapp

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View // ВОТ ЭТОГО НЕ ХВАТАЛО
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.io.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

class MainActivity : Activity() {
    private lateinit var outputText: TextView
    private lateinit var cmdInput: EditText
    private lateinit var pathText: TextView
    private lateinit var connectBtn: Button
    private lateinit var ipInput: EditText
    private lateinit var scrollView: ScrollView
    private lateinit var terminalLayout: LinearLayout
    private lateinit var explorerLayout: LinearLayout
    private lateinit var fileListContainer: LinearLayout
    
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var connected = false
    private val handler = Handler(Looper.getMainLooper())
    
    private val commandHistory = mutableListOf<String>()
    private var historyIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#121212")
        window.navigationBarColor = Color.parseColor("#121212")

        // 1. ГЛАВНЫЙ КОНТЕЙНЕР
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(30, 30, 30, 30)
        }

        val headerTitle = TextView(this).apply {
            text = "Remote CMD v2.2"
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
            setPadding(0, 5, 0, 10)
        }

        val prefs = getSharedPreferences("RemoteCMD_Prefs", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("last_ip", "192.168.1.81")

        ipInput = EditText(this).apply {
            setText(savedIp)
            setTextColor(Color.WHITE)
            hint = "IP сервера"
            setHintTextColor(Color.DKGRAY)
        }

        connectBtn = Button(this).apply {
            text = "ПОДКЛЮЧИТЬСЯ"
            setBackgroundColor(Color.parseColor("#222222"))
            setTextColor(Color.WHITE)
        }

        // --- 2. ВКЛАДКИ ---
        val tabLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
        }
        val btnTermMode = Button(this).apply { text = "Терминал"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val btnExplMode = Button(this).apply { text = "Проводник"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }	
        tabLayout.addView(btnTermMode)
        tabLayout.addView(btnExplMode)

        // --- 3. ТЕРМИНАЛ ---
        terminalLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        outputText = TextView(this).apply {
            setTextColor(Color.parseColor("#00FF00"))
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextIsSelectable(true)
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

        val historyPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val btnPrev = Button(this).apply {
            text = "▲"
            setOnClickListener {
                if (commandHistory.isNotEmpty() && historyIndex > 0) {
                    historyIndex--
                    cmdInput.setText(commandHistory[historyIndex])
                    cmdInput.setSelection(cmdInput.text.length)
                }
            }
        }

        val btnNext = Button(this).apply {
            text = "▼"
            setOnClickListener {
                if (historyIndex < commandHistory.size - 1) {
                    historyIndex++
                    cmdInput.setText(commandHistory[historyIndex])
                } else {
                    historyIndex = commandHistory.size
                    cmdInput.text.clear()
                }
                cmdInput.setSelection(cmdInput.text.length)
            }
        }
        historyPanel.addView(btnPrev); historyPanel.addView(btnNext)

        terminalLayout.addView(scrollView)
        terminalLayout.addView(cmdInput)
        terminalLayout.addView(historyPanel)

        // --- 4. ПРОВОДНИК ---
        explorerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        val explorerScroll = ScrollView(this).apply { 
            layoutParams = LinearLayout.LayoutParams(-1, -1) 
        }
        fileListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        explorerScroll.addView(fileListContainer)
        explorerLayout.addView(explorerScroll)

        layout.addView(headerTitle)
        layout.addView(pathText)
        layout.addView(ipInput)
        layout.addView(connectBtn)
        layout.addView(tabLayout)
        layout.addView(terminalLayout)
        layout.addView(explorerLayout)
        
        setContentView(layout)

        // ЛОГИКА ПЕРЕКЛЮЧЕНИЯ
        btnTermMode.setOnClickListener {
            terminalLayout.visibility = View.VISIBLE
            explorerLayout.visibility = View.GONE
        }
        btnExplMode.setOnClickListener {
            if (!connected) return@setOnClickListener
            terminalLayout.visibility = View.GONE
            explorerLayout.visibility = View.VISIBLE
            handler.postDelayed({ sendToServer("__list__") }, 50)
        }

        connectBtn.setOnClickListener { if (!connected) connect() else disconnect() }
        cmdInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val cmd = cmdInput.text.toString().trim()
                if (cmd.isNotEmpty()) { handleCommand(cmd); cmdInput.text.clear() }
                true
            } else false
        }

        startAutoDiscovery()
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
                
                val result = response.toString().trim()

                handler.post {
                    if (cmd == "__list__") {
                        updateExplorerUI(result)
                    } else if (result.startsWith("PATH_CHANGED|")) {
                        sendToServer("__list__") // Авто-обновление списка
                    } else {
                        if (cmd.isNotEmpty() && !cmd.startsWith("__")) {
                            outputText.append("\n> $cmd\n")
                            outputText.append(result + "\n")
                            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                        }
                    }
                }
            } catch (e: Exception) {
                handler.post { showLog("❌ Ошибка сети\n"); disconnect() }
            }
        }.start()
    }

    private fun updateExplorerUI(data: String) {
        fileListContainer.removeAllViews()
        val btnUp = Button(this).apply {
            text = "📁 .. [НАЗАД]"
            setTextColor(Color.YELLOW)
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setOnClickListener { sendToServer("cd ..") }
        }
        fileListContainer.addView(btnUp)

        if (data == "EMPTY_DIR" || data.isEmpty()) {
            fileListContainer.addView(TextView(this).apply { text = "Папка пуста"; setTextColor(Color.GRAY); setPadding(30,30,30,30) })
            return
        }

        data.split("\n").forEach { line ->
            if (line.contains("|")) {
                val parts = line.split("|")
                val type = parts[0]
                val name = parts[1]

                val btn = Button(this).apply {
                    text = if (type == "DIR") "📁 $name" else "📄 $name"
                    setTextColor(if (type == "DIR") Color.CYAN else Color.WHITE)
                    setBackgroundColor(Color.TRANSPARENT)
                    gravity = android.view.Gravity.START
                    transformationMethod = null
                    setOnClickListener {
                        if (type == "DIR") {
                            sendToServer("cd \"$name\"")
                        } else {
                            sendToServer("__open__ \"$name\"")
                        }
                    }
                }
                fileListContainer.addView(btn)
            }
        }
    }

    // Остальные методы (connect, disconnect, handleCommand, discovery и т.д.)
    private fun handleCommand(cmd: String) {
        if (cmd.isNotEmpty()) {
            if (commandHistory.isEmpty() || commandHistory.last() != cmd) commandHistory.add(cmd)
            historyIndex = commandHistory.size
        }
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
                getSharedPreferences("RemoteCMD_Prefs", Context.MODE_PRIVATE).edit().putString("last_ip", ip).apply()
                handler.post {
                    connectBtn.text = "ОТКЛЮЧИТЬСЯ"; connectBtn.setBackgroundColor(Color.parseColor("#880000"))
                    cmdInput.isEnabled = true; ipInput.isEnabled = false
                    showLog("✅ Подключено\n"); sendToServer("")
                }
            } catch (e: Exception) { handler.post { showLog("❌ Ошибка: ${e.message}\n") } }
        }.start()
    }

    private fun disconnect() {
        Thread {
            try { writer?.write("exit\n"); writer?.flush(); socket?.close() } catch (e: Exception) {}
            connected = false
            handler.post {
                connectBtn.text = "ПОДКЛЮЧИТЬСЯ"; connectBtn.setBackgroundColor(Color.parseColor("#222222"))
                cmdInput.isEnabled = false; ipInput.isEnabled = true
                pathText.text = "Путь: ---"; showLog("🔌 Отключено\n")
            }
        }.start()
    }

    private fun showLog(msg: String) {
        outputText.append(msg)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun startAutoDiscovery() {
        Thread {
            try {
                val udpSocket = DatagramSocket()
                udpSocket.broadcast = true; udpSocket.soTimeout = 2000
                val msg = "WHERE_IS_REMOTE_CMD?".toByteArray()
                val packet = DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), 7778)
                udpSocket.send(packet); val buf = ByteArray(1024); val receivePacket = DatagramPacket(buf, buf.size)
                udpSocket.receive(receivePacket)
                if (String(receivePacket.data, 0, receivePacket.length) == "I_AM_REMOTE_CMD_SERVER") {
                    val serverIp = receivePacket.address.hostAddress
                    handler.post { ipInput.setText(serverIp); Toast.makeText(this, "Сервер найден!", Toast.LENGTH_SHORT).show() }
                }
                udpSocket.close()
            } catch (e: Exception) {}
        }.start()
    }
}