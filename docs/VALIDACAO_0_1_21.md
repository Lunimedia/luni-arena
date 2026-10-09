# Relatório de validação — 0.1.21

Executado em 09/10/2026 no ambiente de preparação, com Android SDK Platform 36, Build Tools 35.0.0 e 36.0.0, CMake 3.22.1, NDK r27c, Gradle 8.11.1 e JDK 17. Este relatório registra testes de compilação e não substitui o teste em celular e tablet pela Play.

| Verificação | Resultado |
| --- | --- |
| `python3 tests/test_release_cores.py` | 7 testes passaram. |
| `python3 tests/test_lan_session.py` | Cenários de sala cheia, saída, reconexão e ressincronização passaram em loopback. |
| `python3 tools/check_core.py` | Os dois núcleos e a ponte JNI gerada têm segmentos LOAD alinhados a 16 KiB. |
| `gradle --no-daemon assembleDebug` | **BUILD SUCCESSFUL**; APK debug local de aproximadamente 16 MiB, pacote `app.luni.arena`, `versionCode 23`, `versionName 0.1.21`, `targetSdk 36`. |
| `gradle --no-daemon bundleRelease -x verifyReleaseCores` | **BUILD SUCCESSFUL**; R8, CMake e empacotamento AAB de validação concluídos. O AAB local não tem a URL pública da fonte e **não foi incluído nesta entrega nem é apropriado para upload**. A trava permanece no Gradle do ZIP. |
| Inspeção do APK e AAB de validação | `libbsnes_mercury_performance_libretro_android.so`, `libmesen_s_libretro_android.so`, `liblunibridge.so` e `libtranslate_jni.so` presentes e com alinhamento ELF de 16 KiB. |
| `python3 tools/release_preflight.py` | Aponta apenas a pendência técnica `SOURCE_URL.txt` com URL pública real. |

Antes do envio comercial, publique o código-fonte exato desta versão e coloque sua URL no asset; rode `py -3 tools\verify_release_cores.py` e `py -3 tools\check_bundle_native.py CAMINHO_DO_AAB`. Confira depois a compatibilidade de páginas de 16 KiB no relatório da Play. Revisão das licenças GPLv3/ML Kit, atribuição/proveniência dos cheats, política de privacidade personalizada e testes em aparelhos reais ainda dependem do titular. O status **BUILD SUCCESSFUL** não significa aprovação jurídica ou da Play Store.
