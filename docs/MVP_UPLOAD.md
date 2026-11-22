# MVP: ортақ негіз және 4-қатысушы

Бұл — аралық branch, толық іске қосылатын жоба емес.

## Енгізілген файлдар

Бірінші commit — «Жобаның ортақ негізін қосу» (21 файл):
- `pom.xml`, `.gitignore`;
- `src/main/java/kz/halyk/model/` ішіндегі 10 Java файлы;
- `src/main/java/kz/halyk/db/Database.java`;
- `src/main/java/kz/halyk/dao/` ішіндегі 4 Java файлы;
- `src/main/java/kz/halyk/common/Money.java`, `Snapshot.java`;
- `src/main/resources/schema.sql`;
- `src/test/java/kz/halyk/BankTestBase.java`.

Екінші commit — «Фрод мониторинг пен CSV есептерін қосу» (4 файл):
- `src/main/java/kz/halyk/member4/FraudService.java`;
- `src/main/java/kz/halyk/member4/ReportService.java`;
- `src/test/java/kz/halyk/Member4Test.java`;
- осы `docs/MVP_UPLOAD.md` нұсқаулығы.

Фрод модулі жаңа алушыға 100 000 KZT-ден жоғары аударымды белгілейді және 10 минутта 5-тен көп сәтті аударымды бақылайды. Есеп модулі клиент пен валюта бойынша қорытынды, фрод белгілері, өтінімдер және несие кестесін CSV форматына шығарады.

## Қалған қатысушылардың келесі қадамдары

Әр қатысушы осы ортақ негізден өз branch-ін ашып, өз аккаунтынан кодын commit/push жасап, PR ұсынады:

| Қатысушы | Код қалтасы | Тест |
| --- | --- | --- |
| 1 | `src/main/java/kz/halyk/member1/` — `ClientService`, `ExchangeService` | `src/test/java/kz/halyk/Member1Test.java` |
| 2 | `src/main/java/kz/halyk/member2/` — `TransferService` | `src/test/java/kz/halyk/Member2Test.java` |
| 3 | `src/main/java/kz/halyk/member3/` — `LoanCalculator` | `src/test/java/kz/halyk/Member3Test.java` |

Барлық модуль қосылған соң `src/main/java/kz/halyk/Main.java` біріктіріліп, толық құрастыру мен тесттер қайта орындалады. Бұл draft PR main-ге автоматты түрде біріктірілмейді.

## Тәуелділіктер және нақты тексеру нәтижесі

2026-11-22 күні Maven 3.9.9 және JDK 26.0.2.1 арқылы `mvn -B test` орындалды (`pom.xml`: Java release 17).
Нәтиже: **BUILD FAILURE**, шығу коды 1, негізгі кодты компиляциялау кезінде 5 қате; тесттер орындалмады.

- `FraudService` үшін `member1/ExchangeService` жоқ.
- `ReportService` үшін `member3/LoanCalculator` және оның `Schedule` түрі жоқ.
- `BankTestBase` және `Member4Test` сонымен бірге `member1/ClientService`, `member1/ExchangeService`, `member2/TransferService` қызметтеріне тәуелді. Негізгі компиляция тоқтағандықтан тест компиляциясына жетпеді.

«27 тест өтті» деген бұрынғы толық жоба нәтижесі бұл branch-ке қолданылмайды. Жетіспейтін модульдер, жалған stub және тестті өшіру енгізілген жоқ.

## Сақталған және енгізілмеген файлдар

`README.md`, `docs/research.md`, `docs/UML/`, `docs/main.java/Clients`, `docs/main.java/aydarymdar` өзгеріссіз сақталған. Соңғы екі жеке бағдарлама жаңа модельдермен біріктірілген жоқ.

`member1/`, `member2/`, `member3/`, олардың тесттері және `Main.java` бұл жүктеуге кірмейді. Оларды кейін қатысушылар қосуы үшін `.gitignore` арқылы бұғаттаған жоқпыз; жүктеу нақты файлдар тізімімен шектелді.
ZIP/JAR, құрастырылған файлдар, `target/`, `dist/`, `data/`, `exports/`, `.idea/`, `*.iml`, дерекқор файлдары және `.env` файлдары еленбейді. Құпия деректерді репозиторийге қоспау керек.
