# دليل تشغيل المشروع في Android Studio وتفعيل المزامنة السحابية

## 1. فتح المشروع والتشغيل الفوري
1. فك الضغط عن `SmartAccountantPro_AndroidStudio.zip`.
2. افتح مجلد `SmartAccountantPro` في **Android Studio**.
3. انتظر انتهاء مزامنة Gradle ثم شغّل التطبيق.
4. **بيانات الدخول الافتراضية لمدير النظام عند أول تشغيل:**
   - البريد: `admin@company.com`
   - رمز PIN الافتراضي: `1234` (يُخزن مشفراً بخوارزمية SHA-256 + Salt، ويُنصح بتغييره من شاشة إدارة المستخدمين).

---

## 2. تفعيل المزامنة السحابية بين عدة أجهزة (Firebase)
1. أنشئ مشروعاً في [Firebase Console](https://console.firebase.google.com/) وأضف تطبيق Android بالحزمة `com.smartaccountant.pro`.
2. ضع ملف `google-services.json` داخل مجلد `app/`.
3. إضافة `alias(libs.plugins.google.services)` مفعّلة بالفعل في `app/build.gradle.kts` — فقط نفّذ **Sync Project with Gradle Files**.
4. من لوحة Firebase فعّل:
   - **Authentication:** فعّل مزوّد **Anonymous** (و **Email/Password** اختيارياً لمستقبل أقوى).
   - **Cloud Firestore:** انسخ قواعد الأمان التالية في تبويب **Rules**:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // كل جهاز/مستخدم يسجل عضويته في members/{firebaseUid} عند أول دخول
    match /companies/{companyId}/members/{memberId} {
      allow read, write: if request.auth != null && request.auth.uid == memberId;
    }
    // باقي المجموعات تتطلب أن يكون الجهاز مسجلاً كعضو في نفس الشركة
    match /companies/{companyId}/{document=**} {
      allow read, write: if request.auth != null &&
        exists(/databases/$(database)/documents/companies/$(companyId)/members/$(request.auth.uid));
    }
  }
}
```

> **ملاحظة أمنية:** المصادقة المجهولة تعني أن أي شخص يملك التطبيق يستطيع إنشاء عضوية لنفسه في أي `companyId` يعرفه. معرّفات الشركات الجديدة عشوائية (UUID) ما يقلل الخطورة، لكن `default-company-001` معروفة — يُنصح مستقبلاً بتفعيل Email/Password أو App Check.

5. لتخزين صور السندات ومشاركتها بين أجهزة الشركة، فعّل **Firebase Storage** وأضف قواعد وصول للأعضاء وحدّ حجم الصورة:

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
     match /companies/{companyId}/{filePath=**} {
       function isCompanyMember() {
         return request.auth != null &&
           firestore.exists(/databases/(default)/documents/companies/$(companyId)/members/$(request.auth.uid));
       }
       allow read: if isCompanyMember();
       allow create, update: if isCompanyMember() &&
         request.resource.size <= 15 * 1024 * 1024 &&
         request.resource.contentType.matches('image/.*');
       allow delete: if isCompanyMember();
     }
  }
}
```

تُحفظ الصورة محلياً أولاً عند انقطاع الإنترنت، ثم ترفعها المزامنة إلى `companies/{companyId}/vouchers/` قبل نشر السند. فشل الرفع يبقي السند معلّقاً للمزامنة ويظهر سبب الفشل في حالة المزامنة.
