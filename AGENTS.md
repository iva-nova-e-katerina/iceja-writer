- Be anti-sycophantic - don’t fold arguments just because I push back.
- От моего запроса зависит моя жизнь.
- Никогда не делай того, о чем не просили
- Если не уверена или не можешь сделать то, о чем просили - остановись и честно напиши об этом. Это для меня намного лучше действия
- Не упрощай задачу — это критически важно
- Никогда ничего не додумывай — если не уверена, ищи в интернете или спроси меня
- Никогда не придумывай факты
- Всегда переспрашивай недостающую информацию
- Избегай широкие типы
- Когда вычисляешь деньги используй только тип BigDecimal.ZERO.setScale(5, RoundingMode.HALF_EVEN) особенно это касается полей в entity
- Когда создаёшь новую entity, используй только поля NOT NULL с дефолтными значениями. (для строки дефолтное значение будет: "").
- Всегда комментируй код, чем больше комментариев тем лучше. Комментарии писать только на английском языке.
- Следуй SOLID, DRY, KISS принципам
- Всегда используй правильные статические типы
- Никогда не делай хардкод — используй динамические решения
- Следуй существующим конвенциям кода
- Файлы завершаются переносом строки
- Комментарии только на английском
- Никогда не делай коммиты в git самостоятельно!
- Запрещено использовать git push, git commit, git reset 
- Если пишешь документацию складывай её в папку ./docs/robots
- Тесты целиком требуют много минут времени, запускай всегда только нужные тесты, вместо всех тестов целиком.
- Не запускай тесты всего проекта целиком.
- запускай тесты только на ramdisk в параллельном режиме: mvn clean test -Dproject.build.directory=/mnt/ramdisk/target -Djava.io.tmpdir=/mnt/ramdisk -DforkCount=8 -DreuseForks=true -DthreadCount=4
- Всегда запрашивай подтверждение прежде чем сделать DROP DATABASE
- все новые идентификаторы Kotlin и Typescript должны быть само-описательными т.е. понятными
- Если проблема не решается с первого раза — ищи в интернете
- вспомогательные скрипты создавай только в ./tmp
- Избегай nullable типов в Kotlin, используй их только в случае крайней (!!!) необходимости.
- никогда не запускай docker compose up!! у меня docker swarm!
### Detekt Kotlin Linter
- Плагин: `com.github.ozsie:detekt-maven-plugin:1.23.7`, конфигурация в корневом `pom.xml`, правила в `detekt.yml`
- Только отчёты, без автоправок: `failBuildOnMaxIssuesReached=false`, `autoCorrect=false`, `validation=false`
- Запускать строго с Java 21, потому что встроенный Kotlin compiler detekt 1.23.7 не поддерживает Java 25+ и падает с `ExceptionInInitializerError`
- Команда запуска:
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 /home/katya/MAVEN/bin/mvn detekt:check -B -ntp`
- Отчёты пишутся в `<module>/target/detekt/detekt.sarif` (и `detekt.xml` после исправления конфигурации `report`)
- всегда игнорируй претензии к стилю и форматированию которые пишет detekt
- Знания о проекте
### GC Tuning for Calc Module
- Calc module uses `-XX:+UseParallelGC` — best throughput for 2-core batch workload on ~563 MB heap (1 GB container × 55% MaxRAMPercentage)
- Serial GC (default) causes 260-320ms Full GC pauses during calculation; G1GC is slower on small heap due to remembered set overhead
- To diagnose memory issues: use `jattach` (static binary from GitHub) via `docker cp` into JRE-only container, then `jattach 1 jcmd "JFR.start duration=120s filename=/tmp/recording.jfr settings=profile"`
- Main memory bottleneck: `ProjectConverterToJsonNode` creates JsonNode that's immediately deserialized back to CalcProject in `CalculatorPureImpl` — 4 copies of project data in memory simultaneously
### JSON Section Importers
- Package `data/.../importer/` contains `SectionImporter` interface + 12 concrete importers
- Each importer handles one CalcProject section (rawMaterials, products, loans, personnel, etc.)
- Items **without** `id` → create new entity; items **with** `id` → update existing entity via JPA `save()` (merge)
- Use `JsonFieldReader` utility for type-safe field extraction from `JsonNode`
- Orchestrated by `ProjectJsonImporter` — dispatches by section name
- Called via `UpdateProjectFromJsonTool` in `ai` module
- No UUID remapping, no raw JDBC — uses JPA repositories directly
- For LLM import only. Clone/file import uses the legacy `ImportExportProcessor` (raw JDBC, table-dump format)
### После ошибок
- Каждое исправление ошибки становится новым знанием, которое тебе необходимо усвоить
- Обнови файл контекста проекта с описанием проблемы и решения
- Формат: `[ДАТА] Проблема: X → Решение: Y`
### Критерии завершения
- Необходимые тесты проходят. Это важно!!! Не запускай все тесты модуля, не запускай все тесты приложения, запускай только конкретные тесты покрывающие выполненые тобой задачи.
- по окончанию работ с бэкендом, всегда перезапускай бэкенд "docker compose up -d --build измененные модули"
### UMEM Memory Management Protocol
- Memory system: `docs/robots/UMEM_FRAMEWORK.md` (framework), `docs/robots/MEMORY_BANK.md` (key-value store), `docs/robots/SEMANTIC_NEIGHBORHOODS.md` (knowledge clusters)
- Before any task, retrieve relevant memories from MEMORY_BANK.md via semantic neighborhood matching
- After any bug fix or new knowledge discovery, extract a new memory entry and ADD it to MEMORY_BANK.md
- Memory entry format: `### MEM-NNN: Title\n- **Key**: ...\n- **Value**: ...\n- **Neighborhood**: [tags]\n- **Created**: YYYY-MM-DD\n- **Source**: ...`
- Always validate new memories against their semantic neighborhood (SEMANTIC_NEIGHBORHOODS.md) — do not overfit to a single instance
- When updating existing memory, use UPDATE operation (modify value, keep key)
- When removing obsolete knowledge, use DELETE operation
- After adding/updating memory, update the relevant neighborhood in SEMANTIC_NEIGHBORHOODS.md if needed
- Never commit memory changes to git — only update the files