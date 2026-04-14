# media-service

Bu loyiha Spring Boot + WebSocket + Kurento asosida real-time media qo'ng'iroq servisi bo'lishi uchun yozilgan. Kodning markaziy vazifasi:

- foydalanuvchilarni room ichiga kiritish
- WebRTC signaling xabarlarini qabul qilish
- Kurento orqali media endpointlar yaratish
- participantlar orasida media oqimini ulash
- room ichidagi media oqimini faylga yozib borish

Quyida loyiha hozirgi kod holatiga tayangan holda eng mayda detallargacha tushuntirilgan.

## 1. Ishlatilgan texnologiyalar

- Java 21
- Spring Boot 4
- Spring WebSocket
- Jackson `ObjectMapper`
- Kurento Client
- Lombok

`build.gradle.kts` ichida asosiy dependencylar:

- `spring-boot-starter-webmvc`
- `spring-boot-starter-websocket`
- `org.kurento:kurento-client:7.3.0`
- `com.fasterxml.jackson.core:jackson-databind`

## 2. Loyihaning asosiy classlari

### `MediaServiceApplication`

Bu Spring Boot start nuqtasi.

Vazifasi:

- Spring konteynerni ko'taradi
- beanlarni yaratadi
- config classlarni ishga tushiradi

### `KurentoConfig`

Bu class Kurento serverga ulanish uchun `KurentoClient` bean yaratadi.

Property:

- `kurento.ws.url`
- default qiymat: `ws://localhost:8888/kurento`

Demak application ishga tushganda server shu URL orqali Kurento Media Server bilan bog'lanishga harakat qiladi.

### `WebSocketConfig`

Bu class WebSocket endpointlarni sozlaydi.

Hozirgi config quyidagilarni qiladi:

- `/ws` endpoint ochadi
- `SockJS` fallback yoqilgan
- application prefix: `/app`
- user prefix: `/user`
- simple broker prefix: `/topic`

Muhim eslatma:

Loyihada signaling logikasi `TextWebSocketHandler` orqali yozilgan, lekin config esa STOMP broker usulida yozilgan. Kodning hozirgi ko'rinishida `CallController` shu config orqali alohida handler sifatida register qilinmagan. Ya'ni README bu yerda kodning niyatini va ichki oqimini tushuntiradi, lekin runtime integratsiya qismi hozir to'liq tugallanmagan bo'lishi mumkin.

### `CallController`

Bu class browserdan kelgan signaling xabarlarni qabul qiladi.

U `TextWebSocketHandler`dan meros olgan.

Asosiy methodlar:

- `handleTextMessage(...)`
- `afterConnectionClosed(...)`

### `RoomService`

Bu loyiha ichidagi eng asosiy business logic joyi.

Vazifalari:

- room yaratish
- room ichida participantlarni saqlash
- har user uchun `WebRtcEndpoint` yaratish
- endpointlarni bir-biriga ulash
- ICE candidate larni qayta ishlash
- kerak bo'lsa recordingni boshlash yoki to'xtatish

### `RecordingService`

Bu class room ichidagi media oqimini faylga yozadi.

Vazifalari:

- `RecorderEndpoint` yaratish
- participant endpointlarini recorderga ulash
- recordingni boshlash
- recordingni to'xtatish

### `Room`

Bu oddiy model class.

Ichida:

- `roomId`
- `MediaPipeline`
- `participants`

Har bir room uchun bitta Kurento `MediaPipeline` yaratiladi.

### `UserSession`

Har bir foydalanuvchi uchun session ma'lumotini saqlaydi.

Ichida:

- `username`
- `roomId`
- `WebSocketSession`
- `WebRtcEndpoint`

Bu class signaling session va Kurento media endpoint orasidagi bog'lovchi object.

## 3. Umumiy arxitektura

Yuqori darajada oqim quyidagicha:

1. Browser serverga WebSocket orqali ulanadi.
2. Browser JSON ko'rinishida signaling xabar yuboradi.
3. `CallController` xabarni o'qiydi.
4. `RoomService` room va user holatini boshqaradi.
5. `RoomService` Kurento orqali media endpointlar yaratadi.
6. Participantlar media jihatdan bir-biriga ulanadi.
7. Roomda 2 ta participant bo'lsa `RecordingService` yozishni boshlaydi.
8. Userlar chiqib ketganda room tozalanadi va recording to'xtatiladi.

## 4. Signaling xabarlari qanday ishlaydi

`CallController` kiruvchi matnli WebSocket xabarni `ObjectMapper` orqali `JsonNode`ga parse qiladi.

Server kutayotgan fieldlar:

- `type`
- `username`
- `room`

Ba'zi xabarlar uchun qo'shimcha fieldlar:

- `sdp`
- `candidate`
- `sdpMid`
- `sdpMLineIndex`

### 4.1 `join` xabari

Browser yuboradi:

```json
{
  "type": "join",
  "username": "ali",
  "room": "room-1"
}
```

Server ichida bo'ladigan ishlar:

1. `CallController` yangi `UserSession` yaratadi.
2. `sessions` map ichiga `ws.getId()` bo'yicha saqlaydi.
3. `RoomService.joinRoom(roomId, user)` chaqiriladi.
4. `RoomService` room bor-yo'qligini tekshiradi.
5. Agar room bo'lmasa yangi `Room` yaratadi.
6. Shu room uchun `MediaPipeline` yaratadi.
7. Yangi user uchun `WebRtcEndpoint` yaratadi.
8. Shu endpointga `IceCandidateFoundListener` o'rnatadi.
9. User room ichiga qo'shiladi.
10. Roomdagi oldingi participantlar bilan media connect qilinadi.
11. Agar room size `2` bo'lsa recording boshlanadi.
12. So'ng controller browserga `ready` xabar yuboradi.

Server javobi:

```json
{
  "type": "ready"
}
```

### 4.2 `offer` xabari

Browser yuboradi:

```json
{
  "type": "offer",
  "username": "ali",
  "room": "room-1",
  "sdp": "v=0..."
}
```

Server ichidagi oqim:

1. `CallController` `sdp` ni oladi.
2. `RoomService.processOffer(...)` ni chaqiradi.
3. `RoomService` roomni topadi.
4. Room ichidan username bo'yicha `UserSession` ni topadi.
5. Userning `WebRtcEndpoint`iga `processOffer(sdpOffer)` beradi.
6. Kurento SDP answer qaytaradi.
7. `CallController` browserga `answer` yuboradi.

Server javobi:

```json
{
  "type": "answer",
  "sdp": "v=0..."
}
```

### 4.3 `ice` xabari

Browser yuboradi:

```json
{
  "type": "ice",
  "username": "ali",
  "room": "room-1",
  "candidate": "candidate:...",
  "sdpMid": "0",
  "sdpMLineIndex": 0
}
```

Server ichida:

1. `CallController` candidate fieldlarni o'qiydi.
2. `RoomService.addIceCandidate(...)` ni chaqiradi.
3. `RoomService` roomni topadi.
4. Room ichidan userni topadi.
5. `new IceCandidate(candidate, sdpMid, sdpMLineIndex)` yaratadi.
6. Uni userning `WebRtcEndpoint`iga beradi.

### 4.4 Serverdan browserga `iceCandidate`

Kurento yangi ICE candidate topsa:

1. `RoomService.joinRoom(...)` ichida oldindan qo'yilgan listener ishlaydi.
2. Listener `ObjectNode` yasaydi.
3. `type = iceCandidate`
4. candidate ma'lumotlarini JSONga joylaydi.
5. `newUser.sendMessage(...)` orqali o'sha user browseriga yuboradi.

Xabar ko'rinishi:

```json
{
  "type": "iceCandidate",
  "candidate": "candidate:...",
  "sdpMid": "0",
  "sdpMLineIndex": 0
}
```

### 4.5 User chiqib ketganda `peer-left`

WebSocket yopilganda:

1. `afterConnectionClosed(...)` ishlaydi.
2. `sessions` mapdan user olinadi.
3. `RoomService.leaveRoom(roomId, username)` chaqiriladi.
4. Room ichidan user o'chiriladi.
5. Qolgan participantlarga `peer-left` xabari yuboriladi.

Xabar ko'rinishi:

```json
{
  "type": "peer-left",
  "username": "ali"
}
```

## 5. Room yaratish va boshqarish tartibi

`RoomService` ichida:

```java
private final Map<String, Room> rooms = new ConcurrentHashMap<>();
```

Bu map:

- key: `roomId`
- value: `Room`

### Room yaratilish jarayoni

`joinRoom(...)` ichida:

1. `rooms.putIfAbsent(roomId, createRoom(roomId))`
2. `createRoom(roomId)` ichida `kurentoClient.createMediaPipeline()` chaqiriladi
3. yangi `Room(roomId, pipeline)` qaytariladi

Natija:

- har room uchun bitta `MediaPipeline`
- shu room ichidagi hamma participantlar shu pipeline bilan ishlaydi

### Participant qo'shilishi

User qo'shilganda:

1. `WebRtcEndpoint` yaratiladi
2. `newUser.setWebRtcEndpoint(endpoint)` qilinadi
3. `room.addParticipant(newUser)` qilinadi

### Participantlar bir-biriga ulanishi

Room ichida allaqachon boshqa userlar bo'lsa:

```java
existing.getWebRtcEndpoint().connect(newUser.getWebRtcEndpoint());
newUser.getWebRtcEndpoint().connect(existing.getWebRtcEndpoint());
```

Bu nimani anglatadi:

- mavjud userdan yangi userga media boradi
- yangi userdan mavjud userga media boradi
- ya'ni ikki tomonlama media yo'li quriladi

## 6. Audio/video yozib olish qanday ishlaydi

Bu loyiha recordingni `RecordingService` orqali qiladi.

### 6.1 Recording qachon boshlanadi

`RoomService.joinRoom(...)` ichida:

```java
if (room.size() == 2) {
    recordingService.startRecording(room);
}
```

Demak:

- 1-user kirganda recording boshlanmaydi
- aynan 2-user kirgan vaqtda recording boshlanadi
- bu logika ikki participantli call uchun yozilganga o'xshaydi

### 6.2 Recording qayerga yoziladi

`RecordingService` ichida:

```java
@Value("${recordings.path:/var/recordings/}")
private String recordingsPath;
```

Default papka:

- `/var/recordings/`

Fayl nomi:

```java
String filePath = recordingsPath + room.getRoomId() + ".webm";
```

Misol:

- room id `room-1` bo'lsa
- fayl ` /var/recordings/room-1.webm`

### 6.3 Recorder qanday yaratiladi

`startRecording(Room room)` ichida:

1. fayl path hisoblanadi
2. `RecorderEndpoint` yaratiladi
3. `MediaProfileSpecType.WEBM` beriladi
4. room ichidagi barcha participant endpointlari recorderga ulanadi
5. `recorder.record()` chaqiriladi
6. recorder map ichiga saqlanadi

Kod mantiqi:

```java
RecorderEndpoint recorder = new RecorderEndpoint
        .Builder(room.getPipeline(), "file://" + filePath)
        .withMediaProfile(MediaProfileSpecType.WEBM)
        .build();
```

Bu yerda:

- `room.getPipeline()` bilan shu room pipeline ichida recorder yaratiladi
- `file://` prefiksi Kurentoga bu local file ekanini bildiradi
- `WEBM` profil container formatni bildiradi

### 6.4 Participantlar recorderga qanday ulanadi

```java
for (UserSession user : room.getParticipants()) {
    user.getWebRtcEndpoint().connect(recorder);
}
```

Bu juda muhim qism.

Ma'nosi:

- har userning `WebRtcEndpoint`idan recorder endpointga media oqimi uzatiladi
- recorder shu kirayotgan oqimlarni faylga yozadi

Hozirgi kodga qarab quyidagini aytish mumkin:

- recording alohida faqat audio uchun filtrlanmagan
- `connect(recorder)` media oqimini recorderga uzatadi
- amalda WebRTC endpointdan qanday track kelayotgan bo'lsa, o'sha yozilishi mumkin
- format `webm`

### 6.5 Recording qachon to'xtaydi

`RoomService.leaveRoom(...)` ichida:

```java
if (room.size() == 0) {
    recordingService.stopRecording(roomId);
    room.close();
    rooms.remove(roomId);
}
```

Demak:

- oxirgi user ham chiqib ketganda recording to'xtaydi
- `RecorderEndpoint.stop()`
- `RecorderEndpoint.release()`
- room pipeline ham `release()` qilinadi

### 6.6 Recording objectlari qayerda saqlanadi

`RecordingService` ichida:

```java
private final Map<String, RecorderEndpoint> recorders = new ConcurrentHashMap<>();
```

Bu map:

- key: `roomId`
- value: `RecorderEndpoint`

Maqsadi:

- keyinroq room bo'yicha recorderni topish
- stop vaqtida aynan o'sha recorderni release qilish

## 7. User lifecycle to'liq ketma-ketlikda

Quyida 2 ta foydalanuvchi bir roomga kirgan paytdagi to'liq oqim bor.

### 1-bosqich: birinchi user kiradi

1. Browser WebSocket connection ochadi.
2. `join` xabar yuboradi.
3. `CallController` user uchun `UserSession` yaratadi.
4. `RoomService` yangi room yaratadi.
5. `MediaPipeline` yaratiladi.
6. Birinchi user uchun `WebRtcEndpoint` yaratiladi.
7. ICE listener qo'yiladi.
8. User roomga qo'shiladi.
9. Roomda faqat bitta user bo'lgani uchun recording hali boshlanmaydi.
10. Server `ready` yuboradi.

### 2-bosqich: ikkinchi user kiradi

1. Ikkinchi browser `join` yuboradi.
2. `CallController` ikkinchi user uchun session yaratadi.
3. `RoomService` mavjud roomni topadi.
4. Ikkinchi user uchun yangi `WebRtcEndpoint` yaratiladi.
5. ICE listener qo'yiladi.
6. Ikkinchi user roomga qo'shiladi.
7. `existing -> newUser` connect qilinadi.
8. `newUser -> existing` connect qilinadi.
9. Room size `2` bo'lgani uchun recording boshlanadi.
10. Shu roomdagi barcha endpointlar recorderga ulanadi.
11. Recorder `.webm` faylga yozishni boshlaydi.
12. Server `ready` yuboradi.

### 3-bosqich: offer/answer almashinuvi

1. Browser `offer` yuboradi.
2. Server Kurento `processOffer()` chaqiradi.
3. SDP `answer` qaytaradi.
4. Browser remote description sifatida o'rnatadi.

### 4-bosqich: ICE candidate almashinuvi

1. Browser local ICE candidate larni `ice` type bilan serverga yuboradi.
2. Server ularni `WebRtcEndpoint`ga qo'shadi.
3. Kurento topgan candidate lar browserga `iceCandidate` qilib qaytadi.

### 5-bosqich: qo'ng'iroq davom etadi

1. Media oqimi participantlar orasida yuradi.
2. Shu bilan birga recorderga ham oqadi.
3. Recorder uni diskka yozib boradi.

### 6-bosqich: user chiqib ketadi

1. WebSocket session yopiladi.
2. `afterConnectionClosed` ishlaydi.
3. User `sessions` mapdan o'chiriladi.
4. Roomdan participant olib tashlanadi.
5. Qolganlarga `peer-left` yuboriladi.
6. Agar room bo'sh bo'lsa recording to'xtaydi.
7. Pipeline release qilinadi.
8. Room `rooms` mapdan o'chiriladi.

## 8. Thread safety va memory strukturalari

Loyihada `ConcurrentHashMap` ishlatilgan:

- `CallController.sessions`
- `RoomService.rooms`
- `Room.participants`
- `RecordingService.recorders`

Buning maqsadi:

- parallel WebSocket ulanishlarda map bilan ishlash xavfsizroq bo'lishi
- bir nechta user bir vaqtda kirib-chiqqanda oddiy `HashMap`dagi race condition larni kamaytirish

## 9. Hozirgi koddagi muhim cheklovlar

Bu bo'lim juda muhim, chunki README faqat niyatni emas, amaldagi holatni ham tushuntirishi kerak.

### 1. WebSocket config va controller mos emas

`CallController` oddiy `TextWebSocketHandler`.

Lekin `WebSocketConfig` STOMP broker config.

Shu sabab:

- `CallController` avtomatik `/ws` endpointga ulanib qolmaydi
- alohida `WebSocketHandlerRegistry` bilan register qilish kerak bo'lishi mumkin
- yoki butun signalingni STOMP `@MessageMapping`ga o'tkazish kerak bo'ladi

### 2. Recording aynan 2-userda start bo'ladi

Kod:

```java
if (room.size() == 2) {
    recordingService.startRecording(room);
}
```

Demak:

- 3-user keyinroq kirsa, recorderga avtomatik ulanmasligi mumkin
- bu guruh qo'ng'irog'i uchun yetarli emas

### 3. User chiqib ketsa endpoint release qilinmayapti

`leaveRoom(...)` ichida participant roomdan o'chiriladi, lekin alohida userning `WebRtcEndpoint`ini `release()` qilish ko'rinmaydi.

Bu resurs sizib chiqishiga olib kelishi mumkin.

### 4. Null tekshiruvlar kam

Masalan:

- room topilmasa
- user topilmasa
- endpoint null bo'lsa

ba'zi methodlarda `NullPointerException` bo'lishi mumkin.

### 5. DTO lar hozir deyarli ishlatilmaydi

`JoinMessage` va `SignalMessage` mavjud, lekin controller amalda raw `JsonNode` bilan ishlayapti.

## 10. Recording audio-onlymi yoki audio+videomi

Hozirgi koddan kelib chiqib:

- recording audio-only deb qat'iy aytib bo'lmaydi
- recording media oqimidan nima kelayotgan bo'lsa shuni yozishga urinadi
- `WebRtcEndpoint -> RecorderEndpoint` ulanishi bor
- `WEBM` media profil ishlatilgan

Demak amaliy xulosa:

- agar browser faqat audio yuborsa, audio yoziladi
- agar browser audio va video yuborsa, audio+video yozilishi mumkin
- kod ichida "faqat audio trackni yoz" degan alohida cheklov yo'q

## 11. Application ishga tushganda nimalar sodir bo'ladi

1. `MediaServiceApplication.main()` Spring Bootni ishga tushiradi.
2. `KurentoConfig` `KurentoClient` bean yaratadi.
3. `RecordingService`, `RoomService` kabi servicelar bean bo'ladi.
4. `StartupBanner` application tayyor bo'lgach log chiqaradi.
5. WebSocket config endpointlarni sozlaydi.

## 12. Konfiguratsiya propertylari

Hozir kod ichida ko'ringan asosiy propertylar:

- `kurento.ws.url`
- `recordings.path`
- `server.port`
- `server.servlet.context-path`
- `spring.application.name`

Defaultlar:

- `kurento.ws.url = ws://localhost:8888/kurento`
- `recordings.path = /var/recordings/`

## 13. Qisqa xulosa

Loyiha g'oyasi quyidagicha:

- har room uchun bitta Kurento `MediaPipeline`
- har user uchun bitta `WebRtcEndpoint`
- signaling xabarlari orqali browser va server SDP/ICE almashadi
- endpointlar o'zaro ulanadi
- room 2 ta userga yetganda recorder ishga tushadi
- recorder media oqimini `.webm` faylga yozadi
- oxirgi user chiqib ketganda room va recording yopiladi

Amaldagi recording logikasi roomdagi media oqimini yozadi, lekin hozirgi kod holatida runtime wiring, resource cleanup va multi-user kengaytmasi hali qo'shimcha ishlov talab qiladi.
