# Screen help: a ? icon and a first-time guide

## Intent
New members should understand each feature, and what their plan allows, without asking anyone.

## Goals
- A **?** icon in the top bar opens help for the screen the member is on.
- The first time a member opens each feature after install, its help opens by itself, once.
- After that, help opens only when the member taps **?**.
- Text in Hindi, English and Gujarati, following the app language.

## Non-goals and anti-goals
- No full app tour (the owner chose one screen at a time).
- No time picker for the Thought notification.
- Help must never block a member from a feature, and must not state an access rule the app doesn't follow.

## Approaches
1. **One screen at a time (picked by the owner).** Members learn each feature when they reach it.
2. One full tour after install. Everything at once, but long and easy to skip.

## Design (builder's calls)
- A bottom sheet with the screen's title, a short explanation, then an "Access level" section. One **Got it** button.
- Help topics follow the bottom tab and its inner tab: Thought, Q&A, Daily Activities, Notices, Read, Daily Practice, Freedom Practice, Ask, Videos, Books, Profile.
- "Seen" is saved per topic on the phone and left out of Android backup, so a fresh install starts again.
- The first-time sheet waits until sign-in and onboarding finish, and never opens over another screen or dialog.
- Staff (admins, Swamiji) get the same help. Access text is written by hand and a test checks each number against `PlanAccess.kt`.

## Help text for approval (Hindi; English and Gujarati follow it)

### सुविचार (Home)
यहाँ आप रोजाना स्वामीजी द्वारा लिखित सुविचार पढ़ सकते हैं। सुविचार पर Like और Reply आप access level के हिसाब से कर सकते हैं। सुविचार का नोटिफिकेशन Profile > Settings में चालू या बंद कर सकते हैं। पिछली तारीख का सुविचार पढ़ने के लिए दी गई तारीख पर टैप करें।
- **Free User:** सुविचार पढ़ सकते हैं। 7 दिन बाद Like, 6 महीने बाद Reply।
- **Silver और Gold:** सुविचार पढ़ और Like कर सकते हैं। 7 दिन बाद Reply।

### Q&A
यहाँ आप कोई भी आध्यात्मिक प्रश्न पूछ सकते हैं। स्वामीजी या दूसरे सदस्य उसका उत्तर दे सकते हैं। पोस्ट, Like और Reply आप access level के हिसाब से कर सकते हैं।
- **Free User:** पोस्ट पढ़ सकते हैं। 7 दिन बाद Like। 6 महीने बाद Reply और रोज 1 प्रश्न, सिर्फ टेक्स्ट में। 12 महीने बाद रोज 5 प्रश्न text, photo, audio, video के साथ।
- **Silver:** पोस्ट पढ़ और Like कर सकते हैं। 7 दिन बाद Reply और रोज 1 प्रश्न, सिर्फ टेक्स्ट में। 12 महीने बाद रोज 5 प्रश्न text, photo, audio, video के साथ।
- **Gold:** पोस्ट पढ़ और Like कर सकते हैं। 7 दिन बाद Reply और रोज 1 प्रश्न, सिर्फ टेक्स्ट में। 6 महीने बाद रोज 5 प्रश्न, 12 महीने बाद रोज 10 प्रश्न, text, photo, audio, video के साथ।

### Daily Activities
यहाँ सदस्य अपनी दैनिक सेवा (खाना बनाना, सफ़ाई, व्यायाम, योग) फ़ोटो के साथ साझा करते हैं।
- **Free User:** उपलब्ध नहीं।
- **Silver:** 6 महीने बाद पढ़ सकते हैं, 12 महीने बाद Reply।
- **Gold:** 7 दिन बाद पढ़ सकते हैं, 6 महीने बाद Reply, 12 महीने बाद अपनी पोस्ट।

### सूचनाएँ (Notices)
यहाँ स्वामीजी और Ojas टीम की ज़रूरी सूचनाएँ मिलती हैं।
- **Free User:** पढ़ सकते हैं। 7 दिन बाद Like, 6 महीने बाद Reply।
- **Silver:** पढ़ और Like कर सकते हैं। 7 दिन बाद Reply।
- **Gold:** पढ़, Like और Reply कर सकते हैं।

### Read
यहाँ स्वामीजी के प्रश्न और उत्तर आपकी भाषा में पढ़ या सुन सकते हैं। पढ़ने के बाद "Mark as done" पर टैप करें। यह सबके लिए खुला है।

### दैनिक साधना
यहाँ सुबह, रात, ब्लू हीलिंग और क्विक प्रोटेक्शन की ऑडियो साधना है। रोज करने पर Freedom Points और streak मिलते हैं।
- **Free User:** ब्लू हीलिंग। 6 महीने बाद क्विक प्रोटेक्शन। सुबह और रात की साधना के लिए Silver या Gold चाहिए।
- **Silver:** ब्लू हीलिंग और क्विक प्रोटेक्शन। 7 दिन बाद सुबह और रात की साधना।
- **Gold:** पहले दिन से सब।

### मुक्ति साधना
यहाँ अपने लक्ष्य चुनकर उनका रोज अभ्यास करें और अपनी प्रगति देखें।

### पूछें (Ask)
यहाँ स्वामीजी AI से प्रश्न पूछें। AI स्वामीजी की शिक्षा से उत्तर देता है। स्वामीजी या Ojas टीम से चैट और कॉल अलग से खरीद सकते हैं: टीम से पहले दिन से, स्वामीजी से 6 महीने बाद।
- **Free User, AI प्रश्न रोज:** 5, 6 महीने बाद 50, 12 महीने बाद 100।
- **Silver:** 50, 6 महीने बाद 100।
- **Gold:** 50, 6 महीने बाद 100, 12 महीने बाद असीमित।

### वीडियो
यहाँ Ojas Healing के वीडियो देख सकते हैं।

### पुस्तकें
यहाँ स्वामीजी की पुस्तकें पढ़ सकते हैं, सुन सकते हैं और offline पढ़ने के लिए डाउनलोड कर सकते हैं।

### प्रोफ़ाइल
यहाँ अपनी जानकारी, Freedom Points, membership और Settings (भाषा, थीम, नोटिफिकेशन) मिलते हैं।
- **हीलिंग फ़ोटो:** Silver को 12 महीने बाद, Gold को 6 महीने बाद।
- **प्रोफ़ाइल छुपाना:** Gold को 12 महीने बाद।
