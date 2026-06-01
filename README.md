# Mekanik AI - Automotive Diagnostic Assistant

Mekanik AI is a cutting-edge Android application designed to provide intelligent, real-time automotive assistance. It leverages a hybrid AI architecture, combining powerful cloud-based models with local offline inference for a seamless diagnostic experience.

## 🚀 Features

- **Hybrid AI Engine:** Seamlessly utilizes `Google Gemma` for online high-fidelity reasoning and lightweight models like `Llama 3.2 1B` (via MediaPipe) for privacy-focused offline assistance.
- **Real-time Streaming Responses:** Experience near-instant feedback with word-by-word streaming using Server-Sent Events (SSE), optimized with `Flow` for zero UI stutter.
- **Advanced Multimodal Support:** Upload images of your vehicle's engine, dashboard, or specific parts. High-resolution images are processed using suspend-based Base64 encoding to prevent memory pressure.
- **Performance-First Architecture:** Engineered to keep the Main Thread clear. Heavy operations like model loading, image processing, and complex Regex sanitization are offloaded to `Dispatchers.IO` and `Dispatchers.Default`.
- **Sleek Floating Widget:** A persistent chat interface built with Jetpack Compose. Includes optimized drag gestures with throttled state persistence to ensure System UI responsiveness.
- **Automotive Focused:** Specifically tuned to understand and assist with vehicle-related queries, troubleshooting codes, and maintenance advice.

## ⚠️ Real-World Usage Policy

Mekanik AI is strictly intended for real-world automotive assistance. This application does not implement or use any simulator, mock system, fake data, or artificial scenario.

### Requirements
- **Real Inputs Only:** Use only real inputs (user data, camera images, files, or live APIs).
- **No Placeholders:** Do not generate fake, demo, or placeholder outputs for end users.
- **Production Integrity:** No "test mode", "sandbox mode", or simulation features are included in production.
- **Data-Driven AI:** AI responses are based only on real or provided data, not assumed or simulated results.

### Development Note
Mock/simulated data is allowed only during development but is fully removed or disabled in production builds. If real data is unavailable, the system will clearly inform the user instead of guessing or simulating results.

### Core Principle
**The app must behave as a real automotive assistant, not a simulator.**

## 🛠️ Installation

1. **Navigate to Releases:** Go to the [Releases](https://github.com/JavierSiliacay/mekanik-ai/releases) page of this repository.
2. **Download the APK:** Look for the latest release and under the **Assets** section, click on `Mekanik-AI.apk` to download it.
3. **Transfer to Device:** If you downloaded the file on a PC, transfer it to your Android device. If downloaded directly on your phone, locate it in your **Downloads** folder.
4. **Enable Unknown Sources:** Before installing, ensure that your device allows installations from unknown sources. You can usually find this in **Settings > Security** or by searching for "Install unknown apps" in your device settings.
5. **Install and Run:** Tap the APK file and follow the on-screen prompts to install **Mekanik AI**. Once installed, open the app and start chatting!

### 📴 Offline Model Setup

Since Mekanik AI follows a **Real-World Only** policy, the app provides real tools to obtain and manage model binaries.

#### Method in Offline Mode: Direct In-App Download (Easiest)
1. Open **AI Configuration** in the app.
2. Scroll to **Offline Model Management**.
3. Tap **DOWNLOAD** on your preferred model (**Llama 3.2 1B**, **SmolLM2 1.7B**, or **Qwen 2.5 1.5B**).
4. The app will fetch the real binary directly from Hugging Face and install it automatically.


## 🏗️ Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Concurrency:** Kotlin Coroutines & Flow (Strict Threading Policy)
- **AI Integration:** Google Gemma (Hugging Face Router) & MediaPipe LLM Inference (Llama 3.2, SmolLM2, Qwen 2.5)
- **Image Loading:** Coil
- **Networking:** Retrofit, OkHttp, SSE
- **Database:** Room (for chat history)
- **Persistence:** Throttled SharedPreferences for high-frequency UI state


---
*Developed by [Javier Siliacay](https://github.com/JavierSiliacay)*


