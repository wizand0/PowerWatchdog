
# Power Watchdog

A reliable Android utility for monitoring power connection state, battery temperature, and system events — with detailed history logging and optional Telegram alerts.

Power Watchdog is designed for devices used as servers, IoT gateways, routers, NAS units, recorders, or any system where stable power and temperature monitoring is important.

---
## 🔥 Features

### ⚡ Power Monitoring
- Detects **power connected / power disconnected** events
- Logs events using **Room Database**
- Foreground Service ensures stable background operation
- Optional **Telegram alert** when power is lost

### ⏱ Monitoring Session Timer
- Displays how long monitoring has been active (in seconds)
- Automatically resets when the service is killed by the system

### 🕒 Monitoring Sessions Log
- Stores monitoring sessions with:
    - Start timestamp
    - End timestamp
    - Total duration
- Visible inside the application's log screen

### 🌡 Battery Temperature Display
- Shows current battery temperature on the Home screen
- Updates periodically

### 🔔 Telegram Notifications
- Customizable:
    - Bot API Token
    - Chat ID
    - Enable/Disable sending
- Sends a message upon **power disconnect event**

### 🧩 Settings & Preferences
- Toggle monitoring on/off
- Clear event log
- Configure Telegram
- See service status

### 📱 Notch & Inset Friendly
- Global **WindowInsets** handling
- No UI elements overlap the camera cutout

---

## 📦 Installation & Build

### Requirements
- Android Studio **Hedgehog or newer**
- Kotlin **1.9+**
- Min SDK: **21**
- Target SDK: **34**
- Required permissions:
    - `POST_NOTIFICATIONS` (Android 13+)
    - `FOREGROUND_SERVICE`
    - `INTERNET`

### Build Steps
```bash
git clone https://github.com/<username>/PowerWatchdog.git
cd PowerWatchdog
```

Open the project in Android Studio:

```
Build → Make Project  
Run → Run 'app'
```

---

## 🔧 Telegram Setup

### 1. Create a Telegram Bot
Open **@BotFather** and type:

```
/newbot
```

You’ll receive a token:

```
123456789:AAE...xyz
```

### 2. Obtain Your Chat ID
Send any message to your bot, then open in a browser:

```
https://api.telegram.org/bot<token>/getUpdates
```

Find:
```json
"chat": { "id": 123456789 }
```

### 3. Enter Data in the App
Go to:

**Settings → Telegram**

- Paste your bot token
- Enter your chat ID
- Enable "Send Telegram alerts"

---

## 🧪 Testing

### Simulate Power Disconnect
You can test the service using ADB:

```bash
adb shell am broadcast -a android.intent.action.ACTION_POWER_DISCONNECTED
```

Check:
- Log entry was created
- Session duration is updated
- Telegram message delivered (if enabled)

---

## 🧱 Technologies Used
- **Kotlin**
- **Coroutines**
- **Android Jetpack**
    - ViewModel
    - LiveData / Flow
    - Room
    - Navigation Component
- **Foreground Service**
- **ViewBinding**
- **Telegram Bot API**
- **WindowInsets API**

---

## 🔮 Future Improvements

### Monitoring
- Wi-Fi scan tracking
- Bluetooth device scan
- Voltage monitoring
- CPU load & temperature

### Integrations
- MQTT event publishing
- Webhook HTTP callbacks
- Multiple Telegram recipients

### UI Enhancements
- Dark mode
- Charts & graphs
- Log export (CSV / JSON)

---

## 📝 License
MIT / Apache 2.0

---

