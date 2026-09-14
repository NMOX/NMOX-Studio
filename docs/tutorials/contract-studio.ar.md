# درس: استوديو العقود (Web3)

<!-- languages -->
[English](contract-studio.md) · [Español](contract-studio.es.md) · [Français](contract-studio.fr.md) · [Deutsch](contract-studio.de.md) · [Русский](contract-studio.ru.md) · [Українська](contract-studio.uk.md) · [Polski](contract-studio.pl.md) · [Português (Brasil)](contract-studio.pt.md) · [Bahasa Indonesia](contract-studio.id.md) · [Filipino](contract-studio.tl.md) · [Tiếng Việt](contract-studio.vi.md) · [简体中文](contract-studio.zh.md) · [हिन्दी](contract-studio.hi.md) · [עברית](contract-studio.he.md) · **العربية**
<!-- /languages -->

استوديو العقود بيئة شغل كاملة للعقود الذكية: شجرة artifacts لـ
Foundry/Hardhat، وتفاعل ماشي بالـ ABI بقيم رجوع وreverts متفككة، ومراقب
حي للبلوكات والأحداث، ولوحة إشراف على الغاز والحجم — مع قاعدة صارمة:
**مفيش مفتاح خاص بيلمس الـ IDE أبدًا**.

دي الجولة السريعة. لمثال كامل متشغّل — كتابة عقد escrow، واختباره،
وتشغيله على سلسلة محلية — شوفوا
[making-a-smart-contract.md](../making-a-smart-contract.md).

![ANVIL شغال في الراك واستوديو العقود متصل بيه لوحده — السلسلة 31337، والعقد في شجرة الـ artifacts ومعاه نسبة الحجم حسب EIP-170](../images/contract-studio.png)

## افتحوه

`⌥⌘6`، أو تبويب **استوديو العقود**. هتحتاجوا Foundry ‏(`anvil`،
`forge`) متثبت؛ اتأكدوا بـ `أدوات ◂ طبيب البيئة…`.

## الخطوات

1. **شغّلوا سلسلة محلية.** في الراك، ركّبوا **ANVIL** ودوسوا GO — بيشغّل
   devnet ‏EVM محلي بحسابات مفتوحة ومشحونة رصيد من الأول. استوديو العقود
   بيتصل بيه لوحده.

2. **ابنوا الـ artifacts.** في مشروع Foundry، شغّلوا `forge build` (جهاز
   **FORGE**، أو «بناء» بتاع الـ IDE). شجرة الـ artifacts في استوديو
   العقود بتتملى بالعقود اللي اتعمل لها compile.

3. **انشروا واتفاعلوا.** اختاروا عقد، ودوسوا **نشر** (بيستخدم حساب anvil
   مفتوح — من غير ما تدخلوا مفتاح)، وبعدين استخدموا لوحة **تفاعل**:
   `CALL` لـ function من نوع view وشوفوا القيمة الراجعة متفككة؛ و`SEND`
   لمعاملة وتابعوا الإيصال. الـ reverts والأخطاء المخصصة بتتفك لنص مقروء.

4. **راقبوا السلسلة.** لوحة **مراقبة** بتسأل عن البلوكات الجديدة كل كام
   ثانية وبتفك سجلات الأحداث حسب الـ ABIs بتاعتكم. لوحة **إشراف** بتعرض
   جدول الغاز، وأحكام الحجم حسب EIP-170، ودفتر عناوين النشر.

## اللي اتعلمتوه

- الإرسال بيعدّي عن طريق **الحسابات المفتوحة** في الـ devnet — الـ IDE مش
  شايل أي مادة مفاتيح ومفيهوش كود توقيع.
- عناوين RPC السرية في سلسلة المفاتيح بس، وعمرها ما بتتكتب في ملف.
- فيه تأكيد بيحرس أي إرسال لـ endpoint **مش loopback**، فمتقدروش تبعتوا
  بالغلط لسلسلة حقيقية.

## بعد كده

- الجولة الكاملة لعقد الـ escrow:
  [making-a-smart-contract.md](../making-a-smart-contract.md).
- GOVERNOR (بوابة الغاز) والإعداد الجاهز «طاولة Web3» موجودين في الراك.
