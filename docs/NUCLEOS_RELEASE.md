# Núcleos ARM64 incluídos no kit 0.1.21

Estes dois binários foram compilados nesta entrega a partir de revisões fixas dos projetos públicos, sem alterações de código nos núcleos. O código correspondente também está em `third_party/`, exportado das revisões abaixo, incluindo os arquivos de licença e de build. Não use `tools/fetch_core.py` para substituir estes binários: esse script obtém *nightlies* móveis e serve só para debug.

| Núcleo | Repositório e revisão | SHA-256 do binário incluído |
| --- | --- | --- |
| bsnes-mercury Performance | `https://github.com/libretro/bsnes-mercury.git` — `79d7f9de218b6ffa65a80bbdc5828532bc239232` | `d4804c776e8107f9eb25bf544a954db54c1bdfc6b4923e4b079f0673ac73bbb4` |
| Mesen-S | `https://github.com/libretro/Mesen-S.git` — `9e4fdeb9b336470bc96beb8765b2e79c86a2da1e` | `9968f49c464e3012378cb697ce36b1c8c367ca644b5e9aa42c86e655feed6d34` |

## Ambiente e comandos da compilação

- Linux x86-64; Android NDK **r27c**, revisão **27.2.12479018**, obtido de `https://dl.google.com/android/repository/android-ndk-r27c-linux.zip`.
- `ndk-build`, `APP_ABI=arm64-v8a`, `APP_PLATFORM=android-26`, `APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true`, `-j4`; `Application.mk` de cada repositório usa `c++_static`. O modo de páginas flexíveis aplica o alinhamento de 16 KiB ao link no NDK r27c.
- Clones oficiais foram colocados, respectivamente, em `/caminho/bsnes-mercury` e `/caminho/Mesen-S`; antes do build, `git checkout --detach <revisão>` em cada clone e `git status --porcelain` vazio. Com `NDK` apontando para a raiz do NDK, os comandos executados foram equivalentes aos seguintes, de dentro de cada clone:

```sh
"$NDK/ndk-build" NDK_PROJECT_PATH="$PWD/target-libretro" APP_BUILD_SCRIPT="$PWD/target-libretro/jni/Android.mk" NDK_APPLICATION_MK="$PWD/target-libretro/jni/Application.mk" APP_ABI=arm64-v8a APP_PLATFORM=android-26 APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true -j4
"$NDK/ndk-build" NDK_PROJECT_PATH="$PWD/Libretro" APP_BUILD_SCRIPT="$PWD/Libretro/jni/Android.mk" NDK_APPLICATION_MK="$PWD/Libretro/jni/Application.mk" APP_ABI=arm64-v8a APP_PLATFORM=android-26 APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true -j4
```

Execute **o primeiro comando apenas no clone bsnes-mercury** e **o segundo apenas no clone Mesen-S**. Os outputs `target-libretro/libs/arm64-v8a/libretro.so` e `Libretro/libs/arm64-v8a/libretro.so` foram copiados, respectivamente, para `app/src/main/jniLibs/arm64-v8a/libbsnes_mercury_performance_libretro_android.so` e `app/src/main/jniLibs/arm64-v8a/libmesen_s_libretro_android.so`, sem modificar os bytes. Os SHA-256 reais também constam em `release-cores.properties`.

Para reconstruir a partir da cópia em `third_party/`, inicie cada pasta em seu próprio repositório Git local antes de compilar ou ajuste a variável de build `GIT_VERSION` para a revisão curta correspondente. Os makefiles embutem `git rev-parse --short HEAD` quando executados em um clone; este detalhe altera os bytes do binário se a revisão Git local mudar. O código de cada núcleo e seu `Android.mk` estão íntegros em `third_party/`.

O verificador `python3 tools/verify_release_cores.py` confere o SHA-256 e os segmentos ELF ARM64 com alinhamento mínimo de 16 KiB. Ele exige ainda a URL HTTPS da **fonte pública deste aplicativo**. Os hashes verificam integridade e não substituem a documentação de procedência nem testes em aparelhos reais.
