# Marka w aplikacji: audyt „Moonlight” i nadpisania

Stan na 11.11.2026, upstream moonlight-android v12.2 scalony w `app/`.

Cel: aplikacja nie może wyglądać na oficjalną aplikację Moonlight (`docs/naming.md`, zasada 2),
a atrybucja upstreamu (GPLv3) ma zostać. Pakiety Java `com.limelight.*` i `namespace 'com.limelight'`
zostają bez zmian (koszt rebase).

## Zasada techniczna: nadpisania we flavorze, nie edycja upstreamu

Pliki upstreamu w `app/src/main/res/` zostają nietknięte, żeby nie generować konfliktów przy scalaniu
nowych wersji. Wszystkie zmiany są w `app/src/nonRoot/res/`: Android scala zasoby flavoru nad `main`,
a zasób o tej samej nazwie, typie i kwalifikatorze z flavoru wygrywa (także gdy w `main` jest `.png`,
a we flavorze `.xml`). Każdy plik nadpisania ma komentarz `<!-- THUMBCAST: ... -->`.

Jedyny flavor to `nonRoot` (flavor `root` usunięty, DECISIONS.md pkt i), więc nadpisania obejmują każdy build.

## Audyt

Kategorie: **a** sugeruje oficjalną aplikację → nadpisujemy; **b** atrybucja albo szczegół techniczny → zostaje;
**c** niepewne → decyzja właściciela.

### Etykieta, ikona, grafiki

| Zasób | Gdzie widoczny | Kat. | Decyzja |
|---|---|---|---|
| `app_label` | nazwa w launcherze, ustawieniach, TV | a | już było: `resValue` w `app/build.gradle` („Thumbcast”, „Thumbcast (Debug)”) |
| `mipmap-anydpi-v26/ic_launcher.xml` + `mipmap-*/ic_launcher_foreground.png`, kolor `ic_launcher_background` | ikona aplikacji | a | nadpisane: nowa ikona adaptacyjna (TYMCZASOWA) |
| `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png` | ikona na API < 26 (u nas nieużywana, minSdk 26) | a | nadpisane aliasami XML, żeby logo upstreamu nie trafiało do APK |
| `drawable-xhdpi/atv_banner.png` (`android:banner`) | baner na Android TV | a | nadpisane: baner wektorowy 160x90 dp (TYMCZASOWY, bez napisu) |
| `drawable/ic_lime_layer.xml` (w `drawable/ic_channel.xml`) | logo kanału na ekranie głównym Android TV (`TvChannelHelper`) | a | nadpisane naszym znakiem |
| `drawable/app_icon.png` | nigdzie (brak odwołań w v12.2), ale w APK | a | nadpisane aliasem ikony |
| `drawable-xhdpi/ouya_icon.png` („Moonlight Game Streaming”) | konsola OUYA (martwa platforma) | a | nadpisane aliasem baneru TV; OUYA szuka `.png`, więc baneru nie pokaże |
| `mipmap-*/ic_pc_scut*` | skrót do komputera (ikona monitora) | b | bez marki, zostaje |

### Teksty (`strings.xml`)

Domyślna lokalizacja (`values/`) ma 11 tekstów z „Moonlight”. Klucze i decyzje:

| Klucz | Kat. | Decyzja |
|---|---|---|
| `nettest_text_waiting` | a | „Thumbcast is testing…” |
| `nettest_text_success` | a + b | „blocking Thumbcast”; nazwa narzędzia „Moonlight Internet Hosting Tool” zostaje (b), ale patrz c3 |
| `nettest_text_failure`, `nettest_text_blocked` | a | „blocking Thumbcast” |
| `nettest_text_inconclusive` | b | test łączy się z serwerami projektu Moonlight, więc zostaje; w `values` i `values-pl` doprecyzowane: „serwery projektu Moonlight” |
| `message_decoding_error` | a | „Thumbcast has crashed…” |
| `summary_seekbar_deadzone` | a | „…than what Thumbcast is configured to use” |
| `summary_checkbox_usb_bind_all` | a | „Use Thumbcast's USB driver…” (sterownik USB jest częścią aplikacji) |
| `summary_language_list` | a | „Language to use for Thumbcast” |
| `local_network_rationale` | a | „Thumbcast requires…” (tylko w `values`, upstream nie ma tłumaczeń) |
| `summary_privacy_policy` | b/c | zostaje; preferencja jest zakomentowana w `xml/preferences.xml`, tekst nieużywany (patrz c2) |

Dodatkowo tylko w pojedynczych lokalizacjach:
`values-ja` `error_manager_not_running` („uruchom ponownie Moonlight”) i `values-ko` `text_native_res_dialog`
(„Moonlight nie odpowiada za…”): kategoria a, nadpisane.

**Lokalizacje.** 31 katalogów `values*` z `strings.xml`; „Moonlight” występuje w 26 (domyślna, `pl` i 24 inne;
bez marki: `ckb`, `eo`, `fa`, `lv`, `ta`). Liczba wystąpień na plik: 9–11, wyjątki `ro` 3 i `iw` 1 (tylko nazwa narzędzia).

Decyzja dla lokalizacji innych niż domyślna i polska: **nadpisujemy, podmieniając samo słowo** „Moonlight” na
„Thumbcast” w tekstach kategorii a, zamiast angielskiego zastępstwa albo zostawienia upstreamu:

- zostawienie upstreamu daje „Moonlight” w UI dla ok. 20 języków, czyli dokładnie to, czego zasada zabrania;
- angielskie zastępstwo psuje UI (pojedyncze zdania po angielsku wśród przetłumaczonych);
- nazwa jest nieodmienną nazwą własną w alfabecie łacińskim, więc podmiana zachowuje tłumaczenie.
  Odmiana z sufiksem (cs „Moonlightu” → „Thumbcastu”, hu „Moonlightot” → „Thumbcastot”, tr „Moonlight'ı” → „Thumbcast'ı”)
  pozostaje poprawna, bo obie nazwy kończą się na spółgłoskę i mają samogłoski tylne. Nie weryfikował tego native speaker.

Pliki `app/src/nonRoot/res/values-XX/strings_branding.xml` generuje `tools/branding/gen_locale_overrides.py`
(23 pliki, `iw` nie potrzebuje). Skrypt pomija nazwę narzędzia („Moonlight Internet Hosting Tool” i tłumaczenia),
`nettest_text_inconclusive` i `summary_privacy_policy`, a klucze z „Moonlight” spoza swojej listy zgłasza do decyzji (kod wyjścia 1).
`values/` i `values-pl/` są pisane ręcznie (`strings_branding.xml`), bo po polsku lepiej brzmi „aplikacja Thumbcast”.

**Po każdym scaleniu upstreamu** uruchom `python tools/branding/gen_locale_overrides.py` i porównaj ręczne pliki
`values`/`values-pl` z nowymi tekstami upstreamu. Nadpisanie zamraża tekst: jeśli upstream poprawi tłumaczenie
któregoś z tych kluczy, zobaczymy je dopiero po ponownym wygenerowaniu.

### Kod Java i manifest

| Miejsce | Kat. | Decyzja |
|---|---|---|
| `HelpLauncher`: linki do `github.com/moonlight-stream/moonlight-docs` (Setup Guide, Troubleshooting, GameStream EOL FAQ), wołane z `PcView` i `utils/Dialog` | b/c | zostaje: dokumentacja upstreamu jest nadal trafna dla hosta i sieci, nie ma naszej; patrz c1 |
| `ServerHelper.CONNECTION_TEST_SERVER` (`android.conntest.moonlight-stream.org`), `ComputerManagerService` STUN `stun.moonlight-stream.org` | c | korzystamy z infrastruktury upstreamu; patrz c4 |
| `xml/preferences.xml`: kategoria „Help” z linkami do Moonlight i polityki prywatności | b | zakomentowana w upstreamie, niewidoczna |
| `xml/preferences.xml`: przestrzeń nazw `schemas.moonlight-stream.com/.../seekbar`, `SeekBarPreference.SEEKBAR_SCHEMA_URL` | b | identyfikator techniczny, niewidoczny |
| `Game.java`: tagi blokad Wi-Fi „Moonlight High Perf Lock”, „Moonlight Low Latency Lock”; `JmDNSDiscoveryAgent` „Limelight mDNS”; `NvHTTP` „Limelight-RSA” | b | techniczne (widoczne najwyżej w `dumpsys`) |
| `AddComputerManually`: schemat `moonlight://` do parsowania adresu | b | wewnętrzny, niewidoczny |
| `MoonBridge`: `System.loadLibrary("moonlight-core")` | b | nazwa biblioteki natywnej upstreamu |
| `AndroidManifest.xml`: etykiety usług/aktywności („Streaming Settings” itd.) | b | bez marki |
| `fastlane/metadata`: „Thumbcast is based on Moonlight (GPLv3)… not an official Moonlight project” | b | poprawna atrybucja, zostaje |

### Podsumowanie liczbowe

- a (nadpisane): 3 grupy grafik ikony/baneru + 3 grafiki dodatkowe (`ic_lime_layer`, `app_icon`, `ouya_icon`);
  10 kluczy tekstowych w `values` (9 w `values-pl`), 7–8 kluczy w każdej z 22 innych lokalizacji, 3 w `values-ro`.
- b (zostaje): nazwa narzędzia upstreamu, „serwery projektu Moonlight”, linki do dokumentacji upstreamu, identyfikatory techniczne,
  atrybucja w `fastlane` i `NOTICE`.
- c (do decyzji): poniżej.

## Do decyzji właściciela

- **c1. Linki pomocy.** „Setup Guide” i „Troubleshooting” prowadzą do wiki Moonlight. Treść o hoście (Sunshine, porty)
  jest trafna, ale nie opisuje warstwy dotyku ani Tailscale. Zostawić do czasu własnej strony pomocy, potem
  przepiąć `HelpLauncher` (zmiana w Javie upstreamu, mały konflikt) na `docs/` w repo.
- **c2. Polityka prywatności.** Tekst „View Moonlight's privacy policy” i URL `moonlight-stream.org/privacy.html` są
  nieużywane (zakomentowane). Przed publikacją w sklepie potrzebna własna polityka; wtedy dodać preferencję z naszym URL.
- **c3. „Moonlight Internet Hosting Tool” w `nettest_text_success`.** Narzędzie dotyczy GeForce Experience i przekierowania
  portów, a DECISIONS.md wybiera Tailscale/ZeroTier bez przekierowania portów. Rada jest myląca dla naszych użytkowników.
  Możliwa zmiana: zdanie o Tailscale (wymaga nowego tłumaczenia; teraz zostawiono tekst upstreamu).
- **c4. Serwery upstreamu.** Test sieci (`android.conntest.moonlight-stream.org`) i STUN (`stun.moonlight-stream.org`)
  to infrastruktura projektu Moonlight. Fork wysyła do niej ruch użytkowników Thumbcast. Do decyzji: zostawić
  (z informacją w polityce prywatności), zapytać upstream o zgodę albo wyłączyć/zastąpić.

## Ikona i baner: TYMCZASOWE

Ikona adaptacyjna, logo kanału TV i baner TV to **placeholdery** do czasu właściwego projektu graficznego:
punkt dotyku z dwoma kręgami fali, biały na tle `#1D6B5F`. Bez znaków towarowych i bez napisu na banerze
(wytyczne Android TV zalecają nazwę aplikacji na banerze; dodać w docelowym projekcie).

Pliki (`app/src/nonRoot/res/`):

| Plik | Nadpisuje |
|---|---|
| `drawable/thumbcast_launcher_foreground.xml` | nowy: znak 108x108 dp w strefie bezpiecznej (r ≤ 33) |
| `drawable/thumbcast_launcher_legacy.xml` | nowy: znak na okrągłym tle |
| `mipmap-anydpi-v26/ic_launcher.xml` | ikona adaptacyjna upstreamu (foreground, background, monochrome) |
| `values/ic_launcher_background.xml` | kolor tła ikony |
| `mipmap-{mdpi…xxxhdpi}/ic_launcher.xml`, `ic_launcher_foreground.xml` | 10 rastrowych PNG upstreamu (aliasy `<inset>`) |
| `drawable-xhdpi/atv_banner.xml` | baner TV |
| `drawable/ic_lime_layer.xml` | logo kanału TV |
| `drawable/app_icon.xml`, `drawable-xhdpi/ouya_icon.xml` | nieużywane grafiki z logo upstreamu |

Kwalifikatory: przy minSdk 26 launcher zawsze wybiera `mipmap-anydpi-v26/ic_launcher`, więc rastrowe
`mipmap-<gęstość>/ic_launcher.png` nie mogłyby wygrać. Nadpisujemy je mimo to, żeby logo upstreamu nie było w APK.
Docelowa ikona: podmienić `thumbcast_launcher_foreground`, kolor tła, `ic_lime_layer` i `atv_banner`; aliasy zostają.

## Weryfikacja (11.11.2026, `:app:assembleNonRootDebug`)

`aapt2 dump badging app-nonRoot-debug.apk`:

```
package: name='io.github.serwur.thumbcast.debug' versionCode='315' versionName='12.2' …
application-label:'Thumbcast (Debug)'
application: label='Thumbcast (Debug)' icon='res/mipmap-anydpi-v26/ic_launcher.xml' banner='res/drawable-xhdpi-v4/atv_banner.xml'
```

`aapt2 dump resources`: `mipmap/ic_launcher` i `mipmap/ic_launcher_foreground` mają we wszystkich gęstościach pliki `.xml`
(żadnego `.png`), `color/ic_launcher_background` = `#ff1d6b5f`, `drawable/atv_banner`, `ic_lime_layer`, `app_icon`, `ouya_icon`
to nasze `.xml`. Pozostałe „Moonlight” w tabeli zasobów występują tylko w `nettest_text_inconclusive` (24 konfiguracje),
`nettest_text_success` (25, nazwa narzędzia) i `summary_privacy_policy` (19, nieużywany), czyli w kategorii b.
