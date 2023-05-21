package com.slowlii.chatgpt

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.GestureDetector
import android.view.Gravity
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.ImageButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.chaquo.python.*
import com.chaquo.python.android.AndroidPlatform
import com.google.android.material.navigation.NavigationView
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var messageContainer: LinearLayout // Контейнер для сообщений
    private lateinit var messageEditText: EditText // Поле ввода сообщения
    private lateinit var sendButton: ImageButton // Кнопка отправки сообщения
    private lateinit var messageScrollView: ScrollView

    private lateinit var sentMessage: String
    private var hasError: Boolean = false

    private var chatHistory: MutableList<MutableMap<String, String>> = mutableListOf()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация Chaquopy
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // Инициализация элементов пользовательского интерфейса
        messageContainer = findViewById(R.id.messageContainer)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        messageScrollView = findViewById(R.id.messageScrollView)

        // Определение DrawerLayout
        val drawerLayout: DrawerLayout = findViewById(R.id.drawerLayout)

        // Определение бокового меню
        val navigationView: NavigationView = findViewById(R.id.navigationView)

        // Добавляем в navigationView меню из 'layout/drawer_menu'
        val headerView = layoutInflater.inflate(R.layout.drawer_menu, navigationView, false)
        navigationView.addHeaderView(headerView)

        // Прослушиваем кнопку настроек ключа API
        val settingsButton: Button = headerView.findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener {
            val intent = Intent(this, ChatGPTkeyActivity::class.java)
            startActivity(intent)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Toolbar, а иначе верхний элемент окна с кнопками активности, например, кнопки вызова меню
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )

        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()


        // Настройки свайпа бокового окна
        val gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val diffX = e2.x.minus(e1.x)
                val diffY = e2.y.minus(e1.y)

                if (diffX > SWIPE_THRESHOLD && diffY < SWIPE_THRESHOLD) {
                    drawerLayout.openDrawer(GravityCompat.START)
                    return true
                }
                return false
            }
        })

        val rootView = findViewById<View>(android.R.id.content)

        rootView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

//        // Делаем чат интеллигентным помощником
//        chatHistory.add(mutableMapOf("role" to "system", "content" to "You are a intelligent assistant."))

        // Устанавливаем слушатель событий клавиатуры
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val heightDiff = rootView.rootView.height - rootView.height
            if (heightDiff > dpToPx(this, 200)) {
                // Клавиатура открыта
                scrollToBottom()
            }
        }

        // Прослушиваем кнопку "Отправить"
        sendButton.setOnClickListener {
            sentMessage = messageEditText.text.toString() // Получение текста сообщения из поля ввода

            if (sentMessage.isNotBlank()) {
                // Проверяем наличие ошибки в предыдущем сообщении собеседника
                if (!hasError) {
                    displaySentMessage(sentMessage) // Отображение отправленного сообщения
                    messageEditText.text.clear() // Очистка поля ввода

                    // Формируем подсказку, объединяя предыдущие сообщения и новое сообщение пользователя
                    chatHistory.add(mutableMapOf("role" to "user", "content" to sentMessage))

                    // Вызов функции askGpt из Python с использованием Chaquopy
                    askGpt(sentMessage)
                } else {
                    // Не удалось отправить сообщение. Предыдущее сообщение содержит ошибку
                    // Скрываем клавиатуру
                    hideKeyboard()
                }
            } else {
                Log.d("TAG", "Отправлено пустое сообщение")
            }
        }

    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(messageEditText.windowToken, 0)
    }

    companion object {
        private const val SWIPE_THRESHOLD = 100
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    // Функция для опускания чата в самый конец
    private fun scrollToBottom() {
        messageScrollView.post {
            messageScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    // Функция возвращает chatHistory как json в строке
    private fun getChatHistoryJson(): String {
        val jsonArray = JSONArray(chatHistory)
        return jsonArray.toString()
    }

    // Функция для вызова функции askGpt из Python с использованием Chaquopy
    private fun askGpt(content: String) {
        val sharedPreferences = getSharedPreferences("MyPreferences", MODE_PRIVATE)
        val openAiApiKey = sharedPreferences.getString("OpenAiApiKey", "")

        // Вызов функции askGpt в фоновом потоке с использованием Coroutine
        GlobalScope.launch(Dispatchers.IO) {
            val responseText = withContext(Dispatchers.Default) {
                val py = Python.getInstance()
                val module = py.getModule("chat_gpt")
                module.callAttr("askGPT", getChatHistoryJson(), content, openAiApiKey).toString()
            }

            // Обновление интерфейса в главном потоке
            withContext(Dispatchers.Main) {
                if (responseText.startsWith("Ошибка:")) {
                    hasError = true
                    // Выполняем код, если строка начинается с "Ошибка: "
                    displayReceivedMessage(responseText)
                } else {
                    hasError = false
                    // Выполняем код, если строка не начинается с "Ошибка: "
                    chatHistory.add(mutableMapOf("role" to "assistant", "content" to responseText))
                    displayReceivedMessage(responseText)
                    Log.d("MY_TAG", responseText)
                }
            }
        }
    }


    // Функция отображения сообщения отправителя
    private fun displaySentMessage(message: String) {
        // Создание нового TextView для отображения отправленного сообщения
        val messageTextView = TextView(this)
        messageTextView.text = message
        messageTextView.setBackgroundResource(R.drawable.sent_message_bg)
        messageTextView.setTextColor(resources.getColor(R.color.sent_message_text_color))
        messageTextView.setTextAppearance(R.style.ChatMessageTextSender) // Стиль текста
        messageTextView.setPadding(16, 8, 16, 8)

        // Установка параметров макета для выравнивания сообщения вправо
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT
        layoutParams.gravity = Gravity.END

        messageTextView.layoutParams = layoutParams

        // Добавление длительного нажатия для вызова контекстного меню
        messageTextView.setOnLongClickListener { view ->
            showContextMenuForMessage(view, message)
            true
        }

        // Добавление TextView в контейнер сообщений и регистрация для контекстного меню
        messageContainer.addView(messageTextView)
        registerForContextMenu(messageTextView)

        // После добавления сообщения прокрутите чат вниз
        scrollToBottom()
    }

    // Функция отображения сообщения chatGPT
    private fun displayReceivedMessage(message: String) {
        // Создание нового TextView для отображения полученного сообщения
        val messageTextView = TextView(this)
        messageTextView.text = message
        messageTextView.setBackgroundResource(R.drawable.received_message_bg)

        if (hasError) {
            // Устанавливаем красный цвет текста для сообщений с ошибкой
            messageTextView.setTextAppearance(R.style.ChatMessageErrorTextReceiver) // Стиль текста
        } else {
            messageTextView.setTextAppearance(R.style.ChatMessageTextReceiver) // Стиль текста
        }

        messageTextView.setPadding(16, 8, 16, 8)

        // Установка параметров макета для выравнивания сообщения влево
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT
        layoutParams.gravity = Gravity.START

        messageTextView.layoutParams = layoutParams

        // Добавление длительного нажатия для вызова контекстного меню
        messageTextView.setOnLongClickListener { view ->
            showContextMenuForMessage(view, message)
            true
        }

        // Добавление TextView в контейнер сообщений и регистрация для контекстного меню
        messageContainer.addView(messageTextView)
        registerForContextMenu(messageTextView)

        // Если сообщение является ошибкой, добавьте кнопку "Отправить сообщение заново"
        if (hasError) {
            val retryButton = Button(this)
            retryButton.text = "Отправить сообщение заново"

            // Настройка фона кнопки
            retryButton.setBackgroundResource(R.drawable.received_message_bg) // Замените R.drawable.retry_button_bg на ваш фоновый ресурс

            // Настройка цвета текста кнопки
            retryButton.setTextColor(resources.getColor(R.color.received_message_text_color)) // Замените R.color.retry_button_text_color на ваш цвет текста

            // Настройка оформления кнопки
            retryButton.setTextAppearance(R.style.ChatMessageTextReceiver) // Замените R.style.RetryButtonTextStyle на ваш стиль текста кнопки

            // Установка размеров кнопки и отступов
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT // ширина кнопки в ширину экрана
            layoutParams.gravity = Gravity.CENTER_HORIZONTAL // Размещение по горизонтали посередине
            layoutParams.topMargin = 8 // Отступ сверху
            layoutParams.bottomMargin = 8 // Отступ снизу
            retryButton.layoutParams = layoutParams

            retryButton.setOnClickListener {
                // При нажатии на кнопку вызовите функцию отправки сообщения заново
                resendMessage(sentMessage)
            }
            messageContainer.addView(retryButton)
        }else {
            // После добавления сообщения прокрутите чат вниз
            scrollToBottom()
        }
    }

    // Функция повторной отправки сообщения
    private fun resendMessage(message: String) {
        // Вызов функции askGpt из Python с использованием Chaquopy
        askGpt(message)

        // Удаление сообщения с ошибкой и кнопки "Отправить сообщение заново"
        val childCount = messageContainer.childCount
        for (i in childCount - 1 downTo 0) {
            val childView = messageContainer.getChildAt(i)
            if (childView is TextView && childView.text.toString().startsWith("Ошибка: ")) {
                messageContainer.removeView(childView)
            } else if (childView is Button && childView.text == "Отправить сообщение заново") {
                messageContainer.removeView(childView)
            }
        }
    }

    // Функция добавляет разделительную линию после сообщения
    private fun addDividingLine() {
        val divider = View(this)
        divider.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            1 // Толщина линии
        )
        divider.setBackgroundColor(Color.parseColor("#CCCCCC")) // Цвет линии

        messageContainer.addView(divider)
    }

    // Функция показывает контекстное меню сообщения
    private fun showContextMenuForMessage(view: View, message: String) {
        val menu = PopupMenu(this, view) // Создание контекстного меню с привязкой к определенному View
        menu.menuInflater.inflate(R.menu.message_context_menu, menu.menu) // Заполнение меню из ресурса menu/message_context_menu.xml
        menu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_copy_message -> { // Если выбран пункт "Копировать сообщение"
                    copyMessageToClipboard(message) // Вызов метода для копирования сообщения в буфер обмена
                    true
                }
                else -> false
            }
        }
        menu.show() // Показать контекстное меню
    }

    // Функция копирует сообщение в буфер обмена
    private fun copyMessageToClipboard(message: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager // Получение сервиса буфера обмена
        val clip = ClipData.newPlainText("message", message) // Создание ClipData с текстом сообщения
        clipboard.setPrimaryClip(clip) // Установка ClipData в буфер обмена
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (v is TextView) {
            menu.setHeaderTitle("Действия с сообщением") // Заголовок контекстного меню
            menuInflater.inflate(R.menu.message_context_menu, menu) // Заполнение меню из ресурса menu/message_context_menu.xml
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val messageView = messageContainer.getChildAt(menuInfo.position) as TextView
        val message = messageView.text.toString()

        return when (item.itemId) {
            R.id.action_copy_message -> {
                copyMessageToClipboard(message)
                true
            }
            // Добавьте другие обработчики элементов меню, если необходимо
            else -> super.onContextItemSelected(item)
        }
    }
}

