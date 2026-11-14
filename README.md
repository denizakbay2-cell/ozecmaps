# ozecmaps: Türkiye için navigasyon

<p align="center"><img src="src/main/webapp/assets/img/logo.jpg" alt="ozecmaps logosu" width="220"></p>

Türkiye için adım adım navigasyon. Bir yer ya da işletme arar veya haritaya dokunursunuz, uygulama araç, bisiklet ya da yürüyüş için rotayı hesaplar, Türkçe yol tarifi verir ve navigasyon modunda sizi sesli olarak yönlendirir.

Rotaları **Java ile yazılmış kendi A\* motorumuz** hesaplar:
- Ayrıntılı bölgede (Kadıköy ve çevresi) bütün yollarda, yol olaylarıyla birlikte.
- Türkiye genelinde araç yollarında: 3,3 milyon kavşaklık ulusal grafikte ([ADR-0008](docs/adr/ADR-0008.md)).

Araç rotalarında İstanbul trafiği İBB'nin açık verisinden hesaba katılır ([ADR-0009](docs/adr/ADR-0009.md)). Arama, ayrıca Kadıköy dışındaki yürüyüş ve bisiklet rotaları OpenStreetMap topluluğunun açık servislerinden gelir ([ADR-0007](docs/adr/ADR-0007.md)).

![Kadıköy İskelesi'nden Caddebostan'a araç rotası ve altında soluk çizilen alternatif rota](docs/images/route.jpg)

## Özellikler

- **Yer ve işletme arama:** Türkiye'nin her yerinde semt, cadde, iskele, hastane ve işletme ("Migros", "eczane", "Starbucks") aranabilir. Sonuçlar haritanın baktığı yere göre sıralanır. Aynı adlı yerler (örneğin Migros şubeleri) birbirinden ayırt edilir: her sonuçta sokağı ve semti ("Mühürdar Caddesi, Osmanağa"), uzaklığı ve haritada numaralı bir iğnesi vardır, en yakını en üsttedir. Türkçe karakter olmadan yazılsa da bulur: "kadikoy iskelesi". Başlangıç ve varış haritaya dokunarak da seçilebilir, işaretçiler sürüklenebilir.
- **İşletmeler haritada:** Kadıköy'deki yaklaşık 6.200 işletme (kafe, restoran, eczane, market, banka…) yakınlaşınca simgeleriyle görünür. Tıklayınca türü, çalışma saatleri, telefonu ve web sitesi gösterilir; "Buraya git" ile rota başlar.
- **Rota:** Araç, bisiklet ve yürüyüş için rota hesaplanır. Tek yönlü sokaklara, dönel kavşaklara ve yaya yollarına uyar. Araçla Edirne'den Van'a kadar her rotayı kendi motorumuz 1,5 saniyenin altında bulur.
- **Ev, İş ve kaydedilen yerler:** Ana ekrandaki Ev ve İş düğmeleri tek dokunuşla oraya rota çizer. Ayarlı değilse dokunup bir yer arayın ya da haritada seçin, o yer kaydedilir. "Nereye" kutusundaki yıldızla hedef Ev, İş ya da Kaydedilenler'e eklenir. Kayıtlar bu cihazda (tarayıcıda) tutulur.
- **Varış saatini paylaşma:** Rota kartındaki ve navigasyon çubuğundaki paylaş düğmesi "Yoldayım: Sarıgül Sokağı. Tahmini varış 21:22 (14 dk kaldı)." mesajını, hedefi açan bir bağlantıyla telefonun paylaşma menüsüne verir; menü yoksa panoya kopyalar. Bağlantıda yalnızca hedef vardır, sizin konumunuz paylaşılmaz.
- **Ara durak:** "Ara durak ekle" ile başlangıç ve varış arasına en fazla üç durak eklenir; haritaya dokunup "Durak ekle" de denebilir. Rota durak durak hesaplanır, her bacağın trafiği o bacağın başlayacağı saate göredir. Navigasyon durağa yaklaşınca "200 metre sonra ara durağa ulaşacaksınız" der.
- **Yol üstünde arama:** Rota kartındaki "Yol üstünde" çipleri rotanın yakınındaki benzinlikleri, şarj istasyonlarını, eczaneleri, marketleri ya da ATM ve bankaları listeler: "2,4 km sonra · sağda, yoldan 40 m · Bağdat Caddesi". Sonuçlar haritada da simgeleriyle görünür. "Durak" düğmesi yeri ara durak yapar ve rotadaki sırasına göre öteki durakların arasına yerleştirir. Türkiye'nin her yerinde çalışır; yerler OpenStreetMap'ten çıkarılıp uygulamayla gelir ([ADR-0012](docs/adr/ADR-0012.md)).
- **Alternatif rota:** En hızlı rotanın altında, ondan belirgin biçimde farklı ikinci bir rota daha soluk çizilir. İki rotanın süresi haritada küçük etiketlerde yazar. Soluk rotanın herhangi bir yerine dokununca o rotaya geçilir; panelde de "Alternatif rota" satırı vardır ([ADR-0010](docs/adr/ADR-0010.md)).
- **Trafik:**
  - Araç rotaları İstanbul'da o saatin olağan trafiğini hesaba katar. Bu bilgi İBB'nin iki aylık saatlik yoğunluk verisinden, yaklaşık 2.400 bölge için çıkarıldı.
  - Hemen yola çıkılıyorsa İBB'nin canlı trafik endeksine göre düzeltilir.
  - Rota özetinde "Trafik nedeniyle +12 dk" gibi gecikme görünür.
  - Kalkış saati seçilebilir ("1 saat sonra", "Yarın 08:00").
  - **Trafik** düğmesi yolları akıcı, orta, yoğun ve çok yoğun diye renklendirir; yanında şehrin o anki durumu yazar ("çok yoğun").
- **Yol tarifi:** Türkçe ve adım adım, pusula yönü yerine bulunduğunuz yere göre: "Düz devam edin", "Sola dönün · Bağdat Caddesi", "Dönel kavşakta 3. çıkışa girin". İsimsiz yollar türüyle anılır: "merdiven", "yaya yolu".
- **Konumunuz:** "Konumum" düğmesiyle gerçek konumunuz haritada mavi nokta ve doğruluk halkasıyla görünür. Alanın içindeyseniz başlangıç olur. Dışındaysanız harita size kayar ve alana ne kadar uzakta olduğunuzu söyler.
- **Navigasyon modu:**
  - konumunuzu takip eder,
  - sıradaki manevrayı ekranda ve cihazın kendi sesiyle söyler ("300 metre sonra sağa dönün"). Uygulama cihazın sunduğu en doğal Türkçe sesi seçer ([ADR-0011](docs/adr/ADR-0011.md)),
  - rotadan çıkınca rotayı yeniden hesaplar,
  - kalan süreyi ve varış saatini günceller,
  - haritayı okun arkasından, önünüzdeki yolu göstererek izler. Haritayı kaydırıp ileriye bakabilirsiniz; **Konumuma odaklan** düğmesi takibi geri getirir,
  - rotayı trafiğe göre renklendirir: akıcı yerler açık yeşil (marka rengi), yavaş yerler sarı, turuncu ya da kırmızı. Bu renkler navigasyondan önce de rotanın üzerinde görünür.
  Kadıköy dışındaysanız rota **simülasyonla** sürülür. Ekrandaki etiket okun "Canlı konum" mu yoksa "Simülasyon" mu olduğunu gösterir.
- **Yol olayları:** Kaza, yol çalışması, yoğun trafik ve kapalı yol haritaya işaretlenebilir. Ayrıntılı bölgedeki rotalar bunu hemen hesaba katar: kapalı yoldan araç ve bisiklet geçmez, diğer olaylarda araçlar yavaşlar.
- **Arayüz:**
  - Sade ve modern, logonun renkleriyle (antrasit ve fıstık yeşili).
  - Sakin bir vektör harita (OpenFreeMap) üzerinde çalışır; cihaz karanlık temadaysa harita da karanlık olur.
  - İlk açılışta bir karşılama ekranı uygulamayı tanıtır; logoya dokununca yeniden açılır.
  - Arama kutusunun altında hızlı hedefler (Kadıköy İskelesi, Moda, Taksim, Ankara…) ve son aramalar durur.
  - Telefonda panel aşağıdan açılan bir sayfadır, tutamaçla büyür.
  - Navigasyon ekranında büyük dönüş kartı, yakındaki ikinci manevra ("Sonra"), hız, ilerleme çubuğu var; gidilen yol griye döner.
- **Uygulama gibi kurulum (PWA):** Chrome ve Android'de "Uygulamayı yükle" düğmesi, iPhone'da Safari'nin *Ana Ekrana Ekle* menüsüyle ozecmaps simgesiyle ve tam ekran açılır. Uygulamanın kendi dosyaları önbellekte tutulur, rota ve harita için internet gerekir.
- **API:** Bütün işlevler `/api` altında bir REST API ile de kullanılabilir. Hatalar tek bir biçimde döner: RFC 9457 *Problem Details*.

<p align="center"><img src="docs/images/navigation.jpg" alt="Telefonda navigasyon modu" width="300"></p>

![Karşılama ekranı](docs/images/intro.jpg)

![Kadıköy'den Ankara'ya kendi motorumuzla araç rotası, karanlık tema](docs/images/national-route.jpg)

![İstanbul'da İBB verisiyle trafik katmanı](docs/images/traffic.jpg)

## Hızlı başlangıç (Docker)

Bilgisayarda yalnızca Docker gerekir; JDK, Maven ya da PostgreSQL kurmanız gerekmez.

```bash
docker compose up --build
```

<http://localhost:8080> adresini açın. İlk açılışta şunlar olur:
- veritabanı şeması kurulur,
- Kadıköy'ün yol ağı ve yaklaşık 3.500 yer veritabanına aktarılır (birkaç saniye sürer),
- örnek olarak üç yol olayı eklenir.

Durdurmak için `Ctrl+C`. `docker compose down -v` verileri de siler.

**Türkiye geneli araç rotaları için (isteğe bağlı):** Ulusal grafiği bir kez üretin:

```bash
scripts/build-national-graph.sh     # ~650 MB indirir, osmium ve Java ile data/turkey-car.graph üretir (~290 MB)
```

Yol üstündeki yerlerin listesi depoyla gelir. Yenilemek için ulusal grafikten sonra `scripts/build-poi-index.sh` çalıştırın; aynı kesiti kullanır.

Betik yalnızca Docker ister. Docker'a en az 4 GB bellek verilmeli; çalışırken 3 GB disk kullanır. `docker compose` dosyayı `data/` klasöründen okur. Uygulama açılışta grafiği yaklaşık 2 saniyede yükler ve toplam 800 MB kadar bellek kullanır. Dosya yoksa uygulama yine çalışır: Kadıköy dışındaki araç rotaları da OSRM'den gelir.

> **buildx eklentisi olmayan Colima:** Önce imajı derleyin, sonra derlemeden başlatın:
> `docker build -t city-navigator-app . && docker compose up --no-build`
>
> **8080 portu doluysa:** `APP_PORT=8081 docker compose up` komutuyla başlatıp <http://localhost:8081> adresini açın.

## Docker olmadan çalıştırma

JDK 21, bir PostgreSQL veritabanı ve [Payara Micro 7](https://payara.fish/downloads/payara-micro/) gerekir.

```bash
./mvnw verify                      # derle, bütün testleri çalıştır, target/city-navigator.war üret
set -a; source .env; set +a        # DB_URL, DB_USER, DB_PASSWORD (bkz. Yapılandırma)
java -jar payara-micro-7.2026.11.jar --deploy target/city-navigator.war --contextroot /
```

NetBeans ve IntelliJ projeyi Maven web uygulaması olarak açar.

## Yapılandırma

Ayarlar yalnızca ortam değişkenlerinden okunur; kodda parola yoktur.

| Değişken | Örnek | Açıklama |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/city_navigator` | Veritabanı adresi |
| `DB_USER` | `navigator` | |
| `DB_PASSWORD` | yerel parolanız | |
| `DATABASE_URL` | `postgresql://kullanıcı:parola@host:5432/db` | Bulut platformları bunu verir. Docker imajı yukarıdaki üç değişkene çevirir |
| `DEMO_DATA` | `true` | Hiç olay olmayan bir veritabanına üç örnek olay ekler |
| `ADMIN_TOKEN` | rastgele bir değer | Tanımlıysa olay bildirmek ve yer eklemek `X-Admin-Token` başlığı ister. Rota ve arama herkese açık kalır |
| `PORT` | `8080` | Docker imajının HTTP portu |
| `EXTERNAL_SERVICES` | `true` | `false` olursa açık servisler (OSRM, Photon, İBB endeksi) kapanır. Uygulama kendi verisiyle çalışır: ayrıntılı bölge, varsa ulusal grafik ve olağan trafik profili |
| `NATIONAL_GRAPH_FILE` | `/data/turkey-car.graph` | Ulusal araç grafiği. Yoksa, bozuksa ya da heap yetmiyorsa motor kapalı kalır ve günlüğe nedeni yazılır |
| `TRAFFIC_INDEX_URL` | `https://api.ibb.gov.tr/tkmservices/api/TrafficData/v1/TrafficIndexHistory/` | İBB trafik endeksi servisi |
| `ROUTING_SERVICE_URL` | `https://routing.openstreetmap.de/routed-{profile}/route/v1/driving/` | Kendi kurulan bir OSRM sunucusu (`{profile}`: car, bike, foot) |
| `GEOCODER_URL` | `https://photon.komoot.io` | Kendi kurulan bir Photon sunucusu |

`.env.example` dosyasını `.env` olarak kopyalayıp doldurun. `.env` git'e girmez, `docker compose` onu kendiliğinden okur.

## İnternete açma (Render)

Depodaki [Render Blueprint](render.yaml) dosyası web servisini (Docker, ücretsiz plan) ve bir PostgreSQL 17 veritabanını birlikte kurar:

1. [render.com](https://render.com)'a GitHub hesabınızla girin.
2. **New → Blueprint** seçin, bu depoyu ve yayınlanacak dalı seçip onaylayın.
3. İlk dağıtım yeşile dönünce servis adresini açın (`https://city-navigator-….onrender.com`).
4. Olay bildirmek için servisin *Environment* sekmesindeki `ADMIN_TOKEN` değerini kopyalayın ve arayüzdeki anahtar düğmesine girin.

Ücretsiz planın sınırları:
- 15 dakika istek gelmezse servis uyur, ilk istek yaklaşık bir dakika bekler.
- Ücretsiz veritabanı 30 gün sonra silinir. Uygulama bir sonraki açılışta yol ağını yeniden aktarır, kaybolan yalnızca olaylar ve elle eklenen yerlerdir.
- Bellek 512 MB'dır. Uygulama ulusal grafik olmadan aynı sınırla yerelde denendi ve yaklaşık 400 MB kullandı. Ulusal grafik ücretsiz plana sığmaz. Burada Kadıköy dışındaki araç rotaları OSRM'den gelir; trafik profili ve canlı endeks yine çalışır. Ulusal motor için en az 1,5 GB bellekli bir sunucu gerekir (ör. Render'ın ücretli planları ya da Oracle Cloud'un ücretsiz sanal makinesi). Grafik dosyası imaja konmaz; sunucuya ayrıca kopyalanmalı ve `NATIONAL_GRAPH_FILE` ile gösterilmelidir.

## API

| Yöntem | Yol | Açıklama |
|---|---|---|
| `GET` | `/api/ping` | Sağlık denetimi: rota grafiği yüklenince 200, yüklenirken 503 |
| `GET` | `/api/map` | Alanın adı, sınırları, merkezi, düğüm ve parça sayısı, verinin tarihi, ulusal grafik (`nationalGraph`), trafik verisi olan bölge sayısı |
| `GET` | `/api/route?from=enlem,boylam&to=enlem,boylam&mode=car\|bicycle\|foot&optimize=fastest\|shortest&departAt=2026-11-16T08:00+03:00&alternatives=true` | Rota: `via=enlem,boylam;enlem,boylam` ile en fazla üç ara duraktan geçer (bu durumda alternatif verilmez). Mesafe, süre, geometri, adım adım tarif (durağa varış `WAYPOINT` adımıdır), rotadaki olaylar, `trafficDelaySeconds` ve trafiğin yavaşlattığı kesimler (`traffic`: geometrideki başlangıç ve bitiş noktası ile serbest akış hızının oranı). `engine` hangi motorun hesapladığını söyler: `astar`, `national` ya da `osrm`. `departAt` verilmezse kalkış şimdidir. `alternatives=true` ile, varsa belirgin biçimde farklı ikinci rota aynı biçimde `alternatives` alanında gelir |
| `GET` | `/api/traffic` | İstanbul'un trafik endeksi, bu saatteki olağan değeri, seviyesi ("çok yoğun") ve kıyas ("normalden yoğun") |
| `GET` | `/api/traffic/roads?minLat=…&minLon=…&maxLat=…&maxLon=…` | Görünümdeki yollar ve şu anki hız katsayıları (1 = serbest akış), en fazla 2.500 yol |
| `GET` | `/api/places?q=moda&limit=8&lat=…&lon=…` | Yer ve işletme arama; `lat`/`lon` yakındaki sonuçları öne alır. `detail` yerin sokağını ve semtini verir |
| `GET` | `/api/places/street?lat=…&lon=…` | Bir noktadaki yolun ya da yerin adı |
| `GET` | `/api/places/along?need=fuel\|charging\|pharmacy\|money\|market&mode=car\|bicycle\|foot&line=…` | Rotanın yakınındaki yerler. `line` rotanın kodlanmış çizgisidir (Google'ın biçimi, beş ondalık). Rotaya yayılmış en fazla 20 yer döner; her birinin rotadaki yeri (`alongMeters`), rotaya uzaklığı (`offsetMeters`) ve rotanın hangi yanında olduğu (`side`) yazar |
| `GET` | `/api/businesses?minLat=…&minLon=…&maxLat=…&maxLon=…` | Sokak ölçeğindeki bir görünümdeki işletmeler (ayrıntılı bölge) |
| `POST` `DELETE` | `/api/places`, `/api/places/{id}` | Elle yer ekleme ve silme. OpenStreetMap'ten gelen yerler silinmez |
| `GET` `POST` | `/api/incidents` | Etkin olaylar / olay bildirme: `type`, `lat`, `lon`, isteğe bağlı `description`, `durationMinutes` |
| `GET` | `/api/incidents/{id}` | Tek bir olay ve kapsadığı yol parçaları |
| `POST` | `/api/incidents/{id}/resolve` | Olayı kaldırma (yalnızca bir kez) |

```bash
curl 'localhost:8080/api/route?from=40.9917,29.0227&to=40.9813,29.0262&mode=foot'
curl -X POST localhost:8080/api/incidents -H 'Content-Type: application/json' \
     -d '{"type": "CLOSURE", "lat": 40.990605, "lon": 29.029107, "durationMinutes": 60}'
```

Hata kodları:
- `400`: geçersiz istek (alan alan liste),
- `401`: yönetici anahtarı eksik,
- `404`: olay ya da yer bulunamadı,
- `409`: çakışma (aynı adlı yer, eşzamanlı güncelleme),
- `422`: kural ihlali. Örnekler: nokta Türkiye dışında, yakında yol yok, iki nokta arasında rota yok, kalkış 7 günden sonra, olay zaten kaldırılmış,
- `503`: Türkiye geneli rota ya da adres servisi şu an yanıt vermiyor ya da rota motoru çok yoğun.

## Nasıl çalışır

```
OpenStreetMap kesiti ──(ilk açılış, tek transaction, JDBC toplu yazma)──▶ PostgreSQL (yollar, yerler, işletmeler)
                                                                         │ açılışta okunur
                    olay bildirildi ──(commit sonrası CDI olayı)──▶ bellekteki ayrıntılı grafik ─┐
Türkiye PBF ─(osmium + BuildNationalGraph, çevrimdışı)─▶ turkey-car.graph ─▶ ulusal grafik ──┤ A* + tarif üretici
İBB saatlik yoğunluk ─(BuildTrafficProfile, çevrimdışı)─▶ olağan trafik profili ─────────────┤ (trafik katsayısı)
İBB trafik endeksi (canlı, önbellekli) ─▶ canlı düzeltme ─────────────────────────────────────┘
                                                                         │
tarayıcı (Leaflet) ◀── JSON ── JAX-RS ◀── servisler ──▶ OSRM / Photon istemcileri (yürüyüş, bisiklet, arama)
```

- **Grafik:** Yaklaşık 55 bin düğüm ve 62 bin yol parçası, ilkel dizilerde (CSR komşuluk listesi) durur. Belleğe yaklaşık 350 ms'de yüklenir.
- **Yola oturtma:** Seçilen nokta yaklaşık 220 m'lik bir ızgara indeksiyle en yakın uygun yola izdüşürülür. Rota yolun ortasından, tek yön kuralına uyarak başlar.
- **A\*:** Tahmin, hedefe kuş uçuşu mesafenin en yüksek hıza bölünmesidir ve gerçek maliyeti hiçbir zaman aşmaz. Bu yüzden A* her zaman en iyi rotayı bulur. Kadıköy'de bir rota 1–6 ms sürer. Testler, A*'ın Dijkstra ile aynı sonucu çok daha az düğüm gezerek bulduğunu doğrular.
- **Ağırlık (Strategy):** `FASTEST` süreyi, `SHORTEST` mesafeyi en aza indirir. Hızlar yol sınıfına ve tabelalı hız sınırına göre belirlenir. Kapalı yol parçası sonsuz maliyetlidir, yavaşlama hızı düşürür.
- **Alternatif rota (ceza yöntemi):**
  - En iyi rotanın yolları 1,4 kat pahalılaştırılır (`PenaltyWeighting`, bir dekoratör) ve A* yeniden çalışır.
  - Bulunan rota en iyisiyle en fazla %75 ortak olabilir ve en fazla 1,3 kat sürebilir. Fazla benzerse ceza 2,0'a çıkar.
  - Kadıköy'de ek süre birkaç milisaniye, Edirne–Van'da 1,6 saniyedir.
- **Yol üstünde arama:** Türkiye'deki 74 bin yer türe göre ayrılır ve yaklaşık 2 km'lik ızgara hücrelerinde durur. Arama yalnızca rotanın geçtiği hücrelere bakar ve her yerin rotaya en yakın noktasını bulur. Rota 20 parçaya bölünür, her parçadan yola en yakın yer seçilir. Tarayıcı uzun rotaları göndermeden önce Douglas–Peucker ile en fazla 300 noktaya indirir.
- **Yanıtlar:** Payara JSON'u ve web dosyalarını gzip'le sıkıştırır (`docker/post-boot.asadmin`). `PacedWriter` büyük yanıtları 64 KB'lık parçalarla yazar.
- **Ses:**
  - Yol tarifini cihazın kendi sesi okur (tarayıcının Web Speech API'si) ve ücretsizdir. Uygulama cihazdaki en doğal Türkçe sesi seçer: adında "Natural", "Premium", "Enhanced" ya da "Gelişmiş" geçen sesler önce gelir.
  - Cümleler sırayla okunur ve hiçbiri yarıda kesilmez; acil anons ("Şimdi sağa dönün") bekleyenlerin yerine geçer.
- **Tarif:** Cadde adı değiştiğinde ya da rota yolun doğal devamından ayrıldığında bir adım eklenir. Dönel kavşaklar çıkış numarasıyla tek adım sayılır. Bölünmüş yolda arka arkaya gelen iki dönüş birleştirilir.
- **Olaylar:** Olay ve kapsadığı parçalar tek transaction'da saklanır. Rota motoru değişikliği yalnızca commit başarılı olursa alır (`@Observes(during = AFTER_SUCCESS)`) ve trafik görüntüsünü kilitsiz biçimde yenisiyle değiştirir.
- **Ulusal grafik:**
  - Yalnızca kavşaklar düğümdür; aradaki noktalar yolun şeklidir.
  - 21 milyon OSM düğümü 3,3 milyon kavşağa, 4,4 milyon kenara iner. Hepsi ilkel dizilerde, yaklaşık 390 MB.
  - Aynı A* başlangıç ve varışı kenarın ortasından alır, rota çizgisini şekil noktalarından kurar.
  - Aynı anda en fazla iki ülke çapında arama çalışır.
- **Trafik:**
  - Her ~600 × 900 m'lik bölge için hafta içi, cumartesi ve pazar, saat saat serbest akış hızının olağan oranı tutulur.
  - Araç kenarının süresi bu oranla uzar.
  - Kalkış şimdiyse İBB endeksinin olağanına oranı bu değeri ±%25'e kadar düzeltir.

Katmanlar: `resource → service → repository → entity`. Rota çekirdeği (`routing`) yalnızca `java.*` kullanır. Bu kurallar her derlemede ArchUnit ile denetlenir.

## Başka bir bölge

```bash
scripts/fetch-osm.sh 40.955 29.015 41.010 29.105   # güney batı kuzey doğu
```

Betik Overpass API'den yolları ve yerleri indirip `src/main/resources/osm` altına yazar. Ardından:
1. `src/main/resources/osm/area.properties` dosyasında alanın adını ve merkezini güncelleyin.
2. Uygulamayı boş bir veritabanıyla başlatın: `docker compose down -v && docker compose up --build`.

Bellek için alanı bir ilçe ya da küçük bir şehir büyüklüğünde tutun (yaklaşık 50 km²).

## Testler

```bash
./mvnw verify
```

Toplam 258 test var:
- rota motoru: geometri, grafik, uzamsal indeks, A*, alternatif rota, trafik, tarif üretimi, yol üstündeki yerler,
- gerçek Kadıköy verisi üzerinde A* ile Dijkstra karşılaştırması,
- ulusal grafik: kavşak sıkıştırma, dosya biçimi, aynı yollarda ayrıntılı motorla aynı en kısa mesafeler,
- trafik profili, İBB endeks istemcisi, çevrimdışı araçlar,
- OpenStreetMap etiket yorumlama,
- entity kuralları,
- servisler (Mockito),
- HTTP katmanı ve hata eşleme,
- ArchUnit katman kuralları.

JaCoCo raporu `target/site/jacoco/` altında oluşur. Satır kapsamı yaklaşık %92, rota motorunda %99. Repository sınıfları gerçek veritabanı ister. Onlar için Testcontainers ile sözleşme testleri bir sonraki adım.

## Bilinen sınırlar

- Kendi motorumuz dönüş yasaklarını (OSM `restriction` ilişkileri) uygulamaz, yani bazı kavşaklarda yasak bir dönüş önerebilir. Ulusal grafikte feribot yoktur; feribotla gidilen yerler için OSRM'e geçilir.
- Yol olayları yalnızca iki ucu da ayrıntılı bölgede olan rotalarda hesaba katılır.
- Trafik yalnızca İstanbul için vardır. Olağan trafik 2024 sonbaharı verisidir. Canlı endeks bütün şehir için tek bir sayıdır, tek bir yoldaki kazayı göstermez.
- Kadıköy dışındaki yürüyüş ve bisiklet rotaları, ulusal grafik yüklenmemişse araç rotaları ve arama açık servislere bağlıdır. Bu rotalarda yol olayları ve trafik hesaba katılmaz, yalnızca "en hızlı" rota vardır. Servisler yavaşsa ya da yanıt vermiyorsa uygulama bunu söyler.
- Haritada simgesiyle görünen işletmeler yalnızca ayrıntılı bölgedekilerdir. Diğer şehirlerde işletmeler aramayla bulunur; harita karoları da işletme simgelerini gösterir.
- Toplu taşıma ve vapur rotası yok. Veri yalnızca içe aktarılan alanı kapsar.
- Yol üstündeki yerler bir OpenStreetMap kesitinin anlık görüntüsüdür. Yeni açılan yerler liste yenilenene kadar görünmez. Nöbetçi eczane ve çalışma saati bilgisi yoktur. Rotaya uzaklık kuş uçuşudur: bölünmüş yolun karşısındaki bir yere dönülemeyebilir, bu yüzden yerin rotanın hangi yanında olduğu yazar.
- Sesin kalitesi cihaza bağlıdır. Mac'teki Chrome Türkçe için yalnızca macOS'un "Yelda" sesini sunar ve bu ses robotik duyulur. Daha doğal bir ses için Mac'te *Sistem Ayarları › Erişilebilirlik › Seslendirilmiş İçerik* bölümünde Sistem Sesi listesinden *Sesleri Yönet…* deyip Türkçe'de Yelda'nın gelişmiş (Enhanced) sürümü indirilebilir; Safari bu sesi kullanır, Chrome listesinde görünürse uygulama onu kendiliğinden seçer. Edge'in Türkçe "Natural" sesleri ve Android'in Google sesleri de doğaldır.
- Mac'teki Chrome'un konuşma motoru, konuşma tam başlarken kesilirse Chrome yeniden başlatılana kadar bütün sayfalarda susar. Uygulama bu yüzden hiçbir cümleyi kesmez. Motor yine de yanıt vermezse uygulama bunu ekranda söyler; Chrome'u tamamen kapatıp (Cmd+Q) açmak düzeltir.
- Konum, tarayıcının konum iznine bağlıdır. Mac'te ayrıca *Sistem Ayarları › Gizlilik ve Güvenlik › Konum Servisleri* bölümünde tarayıcıya izin verilmelidir.
- Tarayıcılar konumu yalnızca `https://` ya da `localhost` adresine verir. Telefonda gerçek GPS ile denemek için uygulamanın internetteki (https) sürümü gerekir: Render ya da bir tünel.

## Mimari kararlar

[docs/adr](docs/adr) klasöründe:
- [Katmanlı monolit ve çerçeveden bağımsız rota çekirdeği](docs/adr/ADR-0001.md)
- [Rota motorunu kendimiz yazıyoruz (A\*)](docs/adr/ADR-0002.md)
- [Yol ağı PostgreSQL'de, rota grafiği bellekte](docs/adr/ADR-0003.md)
- [Yol olayları: tek transaction ve commit sonrası olay](docs/adr/ADR-0004.md)
- [Harita verisi, altlık ve lisanslar](docs/adr/ADR-0005.md)
- [Dağıtım: Payara Micro, Docker ve Render](docs/adr/ADR-0006.md)
- [Türkiye geneli: ayrıntılı bölge dışında açık servisler](docs/adr/ADR-0007.md)
- [Türkiye geneli araç rotaları kendi motorumuzla: sıkıştırılmış ulusal grafik](docs/adr/ADR-0008.md)
- [İstanbul trafiği: İBB'nin saatlik yoğunluk verisi ve canlı trafik endeksi](docs/adr/ADR-0009.md)
- [Alternatif rota: ceza yöntemi](docs/adr/ADR-0010.md)
- [Sesli yönlendirme: cihazın kendi sesi](docs/adr/ADR-0011.md)
- [Yol üstünde arama: uygulamayla gelen yer listesi](docs/adr/ADR-0012.md)

## Teknolojiler

Java 21, Jakarta EE 11 (JAX-RS, CDI, JPA, JSON-P, Bean Validation, JTA) ve Payara Micro 7.2026.11. Veritabanı tarafında PostgreSQL 17 ve Flyway 13. Arayüz Leaflet 1.9.4 ile yazıldı. Testlerde JUnit 5, AssertJ, Mockito, ArchUnit ve JaCoCo kullanılıyor.

## Veri ve lisanslar

- Harita verisi © [OpenStreetMap](https://www.openstreetmap.org/copyright) katkıda bulunanlar, [ODbL](https://opendatacommons.org/licenses/odbl/) lisansıyla. Projeyle gelen `src/main/resources/osm` kesiti de ODbL altındadır.
- Harita karoları OpenStreetMap'in [karo kullanım politikasına](https://operations.osmfoundation.org/policies/tiles/) uygun olarak, düşük trafikle ve atıfla kullanılır.
- Türkiye geneli rotalar [FOSSGIS](https://routing.openstreetmap.de/about.html) sunucularındaki OSRM'den, arama ve adresler [Photon](https://photon.komoot.io)'dan (komoot) gelir. İkisi de OpenStreetMap verisi kullanır. Ayrıntılı bölge dışındaki rotaların başlangıç ve varış noktaları ile arama metinleri bu servislere gönderilir. İstekler kullanım koşullarına uygun olarak sınırlanır ve önbelleğe alınır.
- Ulusal grafik (`data/turkey-car.graph`) Geofabrik'in OpenStreetMap kesitinden üretilir ve ODbL altındadır. Depoya girmez, betikle üretilir.
- Yol üstündeki yerlerin listesi (`src/main/resources/pois/turkey-pois.csv.gz`) aynı kesitten üretilir ve ODbL altındadır. Başlığında verinin tarihi ve atıf yazar.
- Trafik verisi [İBB Açık Veri Portalı](https://data.ibb.gov.tr)'ndandır ([İBB Açık Veri Lisansı](https://data.ibb.gov.tr/license)): saatlik trafik yoğunluk veri seti (Ekim–Kasım 2024) ve trafik endeksi. Uygulamayla gelen `src/main/resources/traffic` profili bu veriden üretilmiştir ve kaynağını başlığında belirtir. Endeks isteği kullanıcıya ait hiçbir bilgi taşımaz.
- Harita altlığı [OpenFreeMap](https://openfreemap.org)'ten gelir (© [OpenMapTiles](https://www.openmaptiles.org/), veri © OpenStreetMap). Anahtar ve kota istemez, atıf ister; atıf haritanın köşesinde durur.
- Leaflet BSD-2 (`src/main/webapp/assets/vendor/leaflet/LICENSE`), MapLibre GL JS BSD-3 ve maplibre-gl-leaflet ISC lisanslıdır (`src/main/webapp/assets/vendor/maplibre/`). Inter yazı tipi SIL Open Font License 1.1 ile gelir (`src/main/webapp/assets/fonts/OFL.txt`).
- ozecmaps adı ve logosu projeye aittir (`src/main/webapp/assets/img/logo.jpg`).
