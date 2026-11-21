# خطة تحويل SmartAccountant Pro إلى نظام متعدد الشركات (Multi-Tenant)

> **الحالة:** خطة فقط — لم يتم تعديل أي كود بعد.
> **التاريخ:** 2026-11-14

---

## 1. الهدف

تحويل التطبيق من "تطبيق محلي بشركة افتراضية واحدة" إلى **نظام SaaS متعدد المستأجرين (Multi-Tenant)** حيث:

- كل **شركة (Tenant)** معزولة تماماً عن غيرها في Room وFirestore.
- كل **موظف (Member)** ينتمي لشركة واحدة أو أكثر ويدخل عبر آلية آمنة.
- لا توجد شركة افتراضية ثابتة — الشركة النشطة تُحدد ديناميكياً من الجلسة.

---

## 2. الوضع الحالي (Baseline)

| المكوّن | الحالة الحالية |
|---------|----------------|
| عزل `companyId` في Room | ✅ موجود — كل الجداول وDAOs مفلترة بـ `companyId` |
| `SessionStore` | ⚠️ موجود لكن يُستخدم جزئياً — يحفظ `currentCompanyId` و`currentUserId` و`currentMemberId` |
| `default-company-001` | ❌ ثابتة في `DatabaseSeeder` و`AppSessionState.settings` و`AuthLoginScreen` |
| `CompanyBootstrapService` | ⚠️ مكتمل منطقياً (إنشاء شركة، دعوة، دخول يدوي، سحب بيانات) لكن **غير مربوط بأي UI** |
| Firebase Auth | ⚠️ Anonymous Auth موجود في `FirebaseAuthService` — غير موثّق كاستراتيجية أساسية |
| QR | ⚠️ `BarcodeScannerService` موجود للمخزون — غير مستخدم للعضوية |
| تسجيل الخروج | ❌ لا يوجد مسح لبيانات Room عند الخروج |

---

## 3. المراحل والخطوات

### المرحلة 1 — الجلسة الديناميكية (إزالة الشركة الافتراضية)

**الهدف:** لا توجد `default-company-001` في أي مكان. الشركة النشطة تأتي من `SessionStore` فقط.

- [ ] **1.1** إزالة `default-company-001` من `DatabaseSeeder.seedIfNeeded()` — عدم إنشاء شركة افتراضية عند أول تشغيل.
- [ ] **1.2** تغيير `AppSessionState.settings` الافتراضي: بدل `AppSettingsEntity(companyId = "default-company-001")` نجعل `settings` nullable أو نؤجل تحميله حتى تحديد الشركة.
- [ ] **1.3** في `MainViewModel.init`: إذا لم يوجد `currentCompanyId` في `SessionStore` → حالة `needsCompanySelection = true` (شاشة اختيار/إنشاء شركة) بدل تحميل شركة افتراضية.
- [ ] **1.4** في `AuthLoginScreen`: إزالة الـ fallback `?: "default-company-001"` — عرض رسالة "لا توجد شركة نشطة" إذا كانت `activeCompany == null`.
- [ ] **1.5** ضمان أن `SessionStore.currentMemberId` يُحفظ دائماً عند `unlockWithUser` ويُمسح عند الخروج.
- [ ] **1.6** إضافة حالة `AppSessionState.needsOnboarding` للتمييز بين: (أول تشغيل / لا شركة / لا مستخدم).

**المخرجات:** التطبيق لا يعمل بدون شركة محددة — أول شاشة هي "إنشاء شركة / الانضمام / دخول يدوي".

---

### المرحلة 2 — Firebase Anonymous Auth كهوية الجهاز

**الهدف:** كل جهاز يحصل على `firebaseUid` ثابت = هوية العضوية في Firestore.

- [ ] **2.1** توثيق reliance على `FirebaseAuthService.ensureAuthenticatedSession()` — يُستدعى مرة واحدة عند أول تشغيل ويُخزّن `uid` في `SessionStore.currentMemberId`.
- [ ] **2.2** عند إنشاء شركة: المالك يُسجَّل في `companies/{companyId}/members/{firebaseUid}` تلقائياً (موجود جزئياً في `unlockWithUser` — يجب تعميمه).
- [ ] **2.3** ضمان أن `UserEntity.firebaseUid` يُملأ دائماً بعد أول مزامنة.
- [ ] **2.4** معالجة حالة غياب `google-services.json`: إظهار وضع محلي صريح مع تعطيل كل ميزات السحابة (موجود — يجب التأكد من عدم وجود استدعاءات تتجاوز `isFirebaseConfigured()`).
- [ ] **2.5** (اختياري/مستقبلي) الترقية من Anonymous إلى Email/Password أو Phone Auth لحل مشكلة "أي شخص يملك التطبيق ينشئ عضوية" — **خارج نطاق هذه الخطة لكنه خطر أمني موثّق**.

---

### المرحلة 3 — ثلاث طرق لتسجيل الدخول/الانضمام

**الهدف:** شاشة واحدة `CompanyAccessScreen` بثلاث تبويبات:

#### 3.1 إنشاء شركة جديدة (Create Company)
- [ ] **3.1.1** نموذج: اسم الشركة (عربي)، هاتف، اسم المالك، بريد المالك، PIN المالك.
- [ ] **3.1.2** استدعاء `CompanyBootstrapService.createCompany(...)` — ينشئ محلياً + سحابياً + عضوية المالك.
- [ ] **3.1.3** عند النجاح: حفظ `currentCompanyId` + `currentUserId` + `currentMemberId` في `SessionStore` → `bindCompanyFlows` → دخول مباشر كمالك.
- [ ] **3.1.4** (بديل محلي) `MainViewModel.createNewCompany` الحالي — توحيد المنطق: إما استخدام Bootstrap فقط عند توفر Firebase، أو نسخة محلية كاملة عند غيابه.

#### 3.2 مسح QR للموظف (Employee QR Scan)
- [ ] **3.2.1** الموظف يعرض بطاقة QR من تطبيقه (انظر المرحلة 4) — المحتوى: `InvitePayload` JSON أو رابط `smartacc://join?companyId=..&memberId=..&email=..`.
- [ ] **3.2.2** الجهاز الجديد يمسح QR عبر `BarcodeScannerService` (CameraX + ML Kit — موجود).
- [ ] **3.2.3** تحليل المحتوى → `CompanyBootstrapService.parseInviteJson` أو parsing للرابط.
- [ ] **3.2.4** طلب PIN العضو → `joinWithInvite(inviteJson, pin)` → سحب كل بيانات الشركة → `unlockWithUser`.
- [ ] **3.2.5** حفظ الجلسة في `SessionStore` والانتقال للوحة التحكم.

#### 3.3 دخول يدوي بكود الشركة (Manual Cloud Login)
- [ ] **3.3.1** نموذج: كود الشركة (6 خانات من `generateCompanyCode`) + البريد + PIN.
- [ ] **3.3.2** استدعاء `CompanyBootstrapService.manualCloudLogin(companyId, email, pin)`.
- [ ] **3.3.3** عند النجاح: نفس مسار 3.2.5.
- [ ] **3.3.4** validation: كود الشركة 6 أحرف من مجموعة `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`.

**ملاحظة:** الطريقتان 3.2 و3.3 تتطلبان Firebase مفعّلاً. عند غيابه تُخفى التبويبات وتبقى "إنشاء شركة محلية" فقط.

---

### المرحلة 4 — توليد بطاقات QR للموظفين

**الهدف:** كل موظف يملك بطاقة QR شخصية يدخل بها زملاؤه/أجهزتهم الجديدة.

- [ ] **4.1** في `AdminUsersScreen` (أو شاشة الملف الشخصي): زر "بطاقة انضمام QR" لكل مستخدم.
- [ ] **4.2** توليد `InvitePayload(companyId, email, memberId)` عبر `buildInviteJson` — `memberId` = معرف وثيقة العضو في `members/{firebaseUid}`.
- [ ] **4.3** عرض QR في Dialog using مكتبة `com.google.zxing:core` (موجودة) — إنشاء `Bitmap` من `QRCodeWriter`.
- [ ] **4.4** options: مشاركة البطاقة كصورة/PDF عبر `MessagingShareService`، أو طباعة حرارية عبر `BluetoothPrinterService`.
- [ ] **4.5** زر "مسح بطاقة" في شاشة الدخول (3.2) — reuse `BarcodeScannerService`.
- [ ] **4.6** صلاحية: بطاقة QR لا تكفي وحدها — PIN مطلوب دائماً (موجود في `joinWithInvite`).

---

### المرحلة 5 — مسح بيانات Room عند تسجيل الخروج

**الهدف:** الخروج = تنظيف كامل للبيانات المحلية للشركة الحالية (خصوصية + منع تسرب بيانات بين المستأجرين).

- [ ] **5.1** دالة `clearCompanyData(companyId)` في `AppDatabase` أو `DatabaseSeeder` — تحذف كل الصفوف التي `companyId = X` من كل الجداول الـ14.
- [ ] **5.2** تنفيذها عبر DAO: إضافة `@Query("DELETE FROM ... WHERE companyId = :companyId")` لكل جدول (أو `deleteByCompany` موجودة — التحقق).
- [ ] **5.3** ربطها بزر "تسجيل الخروج / حذف بيانات الشركة" في `SettingsScreen` أو `DashboardScreen`.
- [ ] **5.4** تدفق الخروج:
  1. `syncEngine.stopListeners()`
  2. محاولة `syncPendingNow` أخيرة (اختياري — مع تأكيد المستخدم)
  3. `clearCompanyData(companyId)`
  4. `sessionStore.clear()` أو مسح `currentCompanyId`/`currentUserId`/`currentMemberId`
  5. `_session.update { AppSessionState(isLoading=false, needsOnboarding=true) }`
  6. العودة لشاشة `CompanyAccessScreen`
- [ ] **5.5** تحذير واضح: "سيتم حذف جميع البيانات المحلية نهائياً — النسخة السحابية تبقى".
- [ ] **5.6** (اختياري) "خروج بدون حذف" للمستخدمين الذين يريدون الاحتفاظ بالبيانات محلياً — toggle في الإعدادات.

---

## 4. التغييرات على الملفات (خريطة مبدئية)

| الملف | التغيير |
|-------|---------|
| `data/local/DatabaseSeeder.kt` | إزالة الشركة الافتراضية |
| `ui/viewmodel/MainViewModel.kt` | حالات onboarding، ربط Bootstrap، دالة logout+clear |
| `ui/screens/AuthLoginScreen.kt` | إزالة fallback، إعادة توجيه لـ CompanyAccessScreen |
| `ui/screens/CompanyAccessScreen.kt` | **جديد** — 3 تبويبات (إنشاء/QR/يدوي) |
| `ui/screens/AdminUsersScreen.kt` | زر توليد QR للموظف |
| `ui/screens/SettingsScreen.kt` | زر تسجيل الخروج مع مسح البيانات |
| `data/local/dao/AllDaos.kt` | دوال `deleteByCompany` إن لم تكن موجودة |
| `data/local/AppDatabase.kt` | دالة `clearCompanyData` |
| `data/remote/CompanyBootstrapService.kt` | ربط كامل + معالجة الأخطاء |
| `ui/navigation/AppNavigation.kt` | route جديد `company_access` |

---

## 5. قواعد Firestore المطلوبة (تذكير)

```javascript
// كل جهاز يسجل عضويته في members/{firebaseUid}
match /companies/{companyId}/members/{memberId} {
  allow read, write: if request.auth != null && request.auth.uid == memberId;
}
// باقي المجموعات تتطلب عضوية في نفس الشركة
match /companies/{companyId}/{document=**} {
  allow read, write: if request.auth != null &&
    exists(/databases/$(database)/documents/companies/$(companyId)/members/$(request.auth.uid));
}
```

**ملاحظة أمنية:** Anonymous Auth يعني أن أي شخص يملك التطبيق يمكنه إنشاء `firebaseUid` خاص به وقراءة أي `companyId` يعرفه. كود الشركة (6 خانات) ليس سراً كافئاً. التخفيفات المقترحة لاحقاً: App Check، أو الانتقال لـ Email/Password.

---

## 6. معايير القبول (Definition of Done)

- [ ] لا يوجد أي hardcoded `default-company-001` في الكود.
- [ ] أول تشغيل يعرض شاشة اختيار: إنشاء شركة / مسح QR / دخول يدوي.
- [ ] إنشاء شركة جديدة يعمل محلياً وسحابياً (عند توفر Firebase).
- [ ] مسح QR ينضم لشركة موجودة بعد إدخال PIN صحيح فقط.
- [ ] دخول يدوي بكود شركة + بريد + PIN يعمل.
- [ ] كل موظف يستطيع توليد وعرض بطاقة QR خاصة به.
- [ ] تسجيل الخروج يمسح كل بيانات Room للشركة الحالية ويعود لشاشة الاختيار.
- [ ] `BUILD SUCCESSFUL` بدون أخطاء.

---

## 7. المخاطر والاعتبارات

| الخطر | التخفيف |
|-------|---------|
| Anonymous Auth غير آمن لبيانات مالية | توثيق الخطر + خطة ترقية لـ Email/Phone لاحقاً |
| مسح Room قد يفقد بيانات غير مُزامنة | مزامنة أخيرة إجبارية قبل المسح + تأكيد المستخدم |
| تعارض `companyId` بين UUID المحلي وكود 6 خانات | توحيد: كود الشركة = `companyId` في Firestore (كما في Bootstrap) |
| `CompanyBootstrapService` غير مجرّب | اختبار يدوي شامل قبل الربط بالـ UI |
| حجم البيانات عند السحب | `pullCompanyData` يسحب كل شيء — قد يحتاج pagination لاحقاً |

---

*الخطة جاهزة للتنفيذ — بانتظار الموافقة لبدء المرحلة 1.*
