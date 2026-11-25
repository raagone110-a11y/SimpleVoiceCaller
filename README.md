# SimpleVoiceCaller

Bahut simple Android app — background mein voice sunta hai, wake word bolne par call lagata hai aur cut karta hai.

## Features
- Wake word: **Jarvis** / **Hey Jarvis** / **हे जार्विस**
- Continuous listening (Foreground Service)
- Commands:
  - `Jarvis call [name or number]`
  - `Jarvis last call` / `Jarvis recent call` / `Jarvis jo abhi call aayi`
  - `Jarvis end call` / `Jarvis cut call` / `Jarvis call kaat do`
- Pure Java (Java 8 compatible)
- Minimal UI

## Permissions Required
- Microphone
- Phone (CALL_PHONE)
- Call Log (READ_CALL_LOG) — recent call ke liye
- Contacts (optional)
- Notifications

## How to Build
1. Android Studio mein open karo
2. Ya GitHub Actions se APK download karo (Actions tab → latest run → Artifacts)

## Limitations
- Modern Android mein call end (hang up) restricted hai. Kai phones par kaam karega, kai par nahi.
- Continuous listening battery use karta hai. Zarurat padne par stop kar dena.

## Wake Word & Commands (Hinglish supported)
Bolte time clear bolo:
- "Hey Jarvis, call Maa"
- "Jarvis last call"
- "Jarvis call kaat do"
