# ट्यूटोरियल: लर्निंग स्पेस

<!-- languages -->
[English](learning-spaces.md) · [Español](learning-spaces.es.md) · [Français](learning-spaces.fr.md) · [Deutsch](learning-spaces.de.md) · [Русский](learning-spaces.ru.md) · [Українська](learning-spaces.uk.md) · [Polski](learning-spaces.pl.md) · [Português (Brasil)](learning-spaces.pt.md) · [Bahasa Indonesia](learning-spaces.id.md) · [Filipino](learning-spaces.tl.md) · [Tiếng Việt](learning-spaces.vi.md) · [简体中文](learning-spaces.zh.md) · **हिन्दी** · [עברית](learning-spaces.he.md) · [العربية](learning-spaces.ar.md)
<!-- /languages -->

![नया लर्निंग स्पेस चयनक — अंतर्निहित ट्यूटोरियल में खोज, और उपलब्धता की जाँच जो पहले ही बता देती है कि इस मशीन पर उस स्पेस का उपकरण है या नहीं](../images/tabs/learning-spaces.png)

लर्निंग स्पेस किसी भाषा, फ़्रेमवर्क या लाइब्रेरी को सीखने के लिए एक
आत्मनिर्भर सैंडबॉक्स है: NMOX Studio नमूना कोड, क़दम-दर-क़दम ट्यूटोरियल
और एक ऐसा रैक बनाता है जिसमें **रैक के भीतर असली REPL** पहले से जुड़ा है,
जिसमें आप टाइप करते हैं। 93 स्पेस अंतर्निहित हैं।

<!-- screenshot: a learning space open — sample code, the tutorial pane, and the REPL device with typed input -->

## इसे खोलें

`फ़ाइल ▸ नया लर्निंग स्पेस…` (लॉन्चर हर अंतर्निहित स्पेस की सूची देता है)।

## चरण

1. **एक स्पेस चुनें।** कोई एक चुनें — Python, Rust, Solid, htmx, Solidity,
   Elm, किसी सिस्टम भाषा का REPL, E2E/Playwright स्पेस, आदि। चयनक पहले जाँच
   लेता है कि दुभाषिया/टूलचेन उपलब्ध है या नहीं।

2. **उसे बनने दें।** NMOX Studio स्पेस को `~/.nmox/learn/<slug>` के नीचे
   बनाता है: एक छोटा, चलता हुआ नमूना और एक ट्यूटोरियल जो आपको उसमें
   घुमाता है और संबंधित कंसोल या डिवाइस की ओर इशारा करता है।

3. **REPL में टाइप करें।** पहले से जुड़े रैक में एक **REPL** डिवाइस है
   जिसकी ENGINE घुंडी स्पेस की भाषा पर सेट है (26 इंजन, हर एक के
   force-interactive फ़्लैग पहले से भरे हुए)। कोई एक्सप्रेशन टाइप करें,
   Enter दबाएँ — आउटपुट REPL स्क्रीन पर बहता है। दुभाषिया नहीं है?
   **INSTALL** बटन उसे रैक से ही संस्थापित कर देता है।

4. **ट्यूटोरियल का पालन करें।** चरणों से गुज़रें; नमूना कोड असली है और
   चलता है, और स्पेस को बदलना आपकी मर्ज़ी है।

## आपने अभी क्या सीखा

- लर्निंग स्पेस एक पूरा प्रोजेक्ट + ट्यूटोरियल + जुड़ा हुआ रैक है, सिर्फ़
  एक स्निपेट नहीं।
- REPL एक असली इंटरैक्टिव प्रक्रिया है, कोई रिकॉर्ड की हुई प्रस्तुति नहीं।
- आप अपने स्पेस जोड़ सकते हैं: `~/.nmox/learn-catalog.d/` में कोई `*.json`
  रख दें और वह चयनक में शामिल हो जाता है (ढाँचे के लिए
  [learning-spaces.md](../learning-spaces.md) देखें)।

## आगे

- फ़्रेमवर्क स्पेस (Astro/SvelteKit/Nuxt/Next) अपने रैक कंसोल
  (COSMOS/KINETIC/NIMBUS/NEXUS) की ओर इशारा करते हैं।
