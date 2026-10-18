# Narko-Monitoring — Android ilova

Bu ilova kompyuterda (BlackArch'da) ishlab turgan `gemini_improved.py` botiga ulanib,
dashboardni telefonda ko'rsatadi va Telegram hisobini (API_ID/API_HASH/telefon raqam)
ilova ichidan sozlash imkonini beradi. Kuzatuvning o'zi (OCR, ovozli xabar, AI tekshiruvi)
hamon kompyuterda ishlaydi — ilova faqat ko'rish/sozlash uchun.

## .apk faylni qanday olish (GitHub Actions orqali, hech narsa o'rnatmasdan)

1. GitHub'da bepul hisob oching (agar yo'q bo'lsa): https://github.com/signup
2. Yangi bo'sh repository (masalan `narkomonitor-app`) yarating.
3. Shu papkadagi barcha fayllarni (shu jumladan yashirin `.github` papkasini) o'sha
   repoga yuklang:
   ```
   git init
   git add .
   git commit -m "Narko-monitoring Android ilovasi"
   git branch -M main
   git remote add origin https://github.com/<FOYDALANUVCHI_NOMINGIZ>/narkomonitor-app.git
   git push -u origin main
   ```
4. GitHub saytida repo ichida **Actions** bo'limini oching — "Build APK" workflow avtomatik
   ishga tushadi (1-3 daqiqa davom etadi).
5. Workflow tugagandan so'ng, shu run sahifasining pastida **Artifacts** bo'limidan
   `narkomonitor-app-debug` faylini yuklab oling — ichida `app-debug.apk` bor.
6. `.apk` faylni telefonga ko'chirib, o'rnating (Android "noma'lum manbalardan o'rnatish"ga
   ruxsat so'rashi mumkin — ruxsat bering, chunki bu shaxsiy ilova, Play Do'kondan emas).

## Ilovani ishlatish

1. Ilovani ochganda "Sozlamalar" ekrani chiqadi.
2. **Server ulanishi**: server manzilini (masalan `http://192.168.1.25:8080` yoki ngrok
   linki), dashboard login/parolni kiritib "Saqlash va ulanish" tugmasini bosing.
3. **Telegram hisobini ulash** (faqat bot hali birorta Telegram hisobiga ulanmagan bo'lsa
   kerak): API_ID, API_HASH (my.telegram.org saytidan) va telefon raqamni kiritib
   "Tasdiqlash kodini yubor" tugmasini bosing. Telegram'dan kelgan kodni keyingi
   ekranda kiritasiz (2FA yoqilgan bo'lsa, parol ham so'raladi).
