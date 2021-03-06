# DeezLoader For Android 

# Version 1.1.7 (Lastest version) Recomended version: arm
##### Min android required version: Android 5 (Lollipop)
##### Versión mínima de android requerida: Android 5 (Lollipop)

### Option 1: arm (This should work in most of the devices, size of 15mb / Esta versión debe funcionar en la mayoría de dispositivos)
[DeezLoader-1.1.8-armeabi-v7a.apk](https://gitlab.com/DT3264/DeezLoader-Android/raw/master/Release/DeezLoader-1.1.8-armeabi-v7a-debug.apk)

### Option 2: x86 (In case the arm doesn't work for you, size of 15mb / Por si la versión arm no te sirvió)
[DeezLoader-1.1.8-x86.apk](https://gitlab.com/DT3264/DeezLoader-Android/raw/master/Release/DeezLoader-1.1.8-x86-debug.apk)

### Option 3: General (This should work on all devices, but size of 38mb / Esta versión devería funcionar en todos los dispositivos)
[DeezLoader-1.1.8-General.apk](https://gitlab.com/DT3264/DeezLoader-Android/raw/master/Release/DeezLoader-1.1.8-General-debug.apk)

# Changelog

- 1.1.8
    - Fixed a bug where the app randomly crashed when a song has been downloaded

- 1.1.7
    - For better experience, if the download folder is diferent to the default, the "create album/artist folder" are dissabled (the songs doesn't download if at least one of those options are selected). Thanks to Milo Joseph for help finding this bug :D
    - Charts tab redesigned
    - Start loading screen improved for better understeand of what is being done while the app starts
    - Updated method to make android recognice new songs downloaded to the external storage propelly

- 1.1.6
    - Replaced the webView with a better implementation of itself which is more stable at the time of showing the page
    - Fixed a bug that crashed the app if the progress of a download isn't valid

- 1.1.5
    - Added option to select another download path (in fact, it still download it to the default storage but if another folder has been selected, the app automaticaly move the file to the selected folder when the download has been completed)

- 1.1.4
    - Added notification on download canceled
    - Added notification when a song is already downloaded

- 1.1.3
    - UI Improvements
    - The temp image of the cover now is deleted after the download
    - When a new version of DeezLoader for android is available a popup will appear on start

- 1.1.2
    - Now there are apk variants in case the general one fails

- 1.1.1
    -  Now the songs are reflected on MediaStore after being downloaded
    -  A notification is displayed when a song is being downloaded