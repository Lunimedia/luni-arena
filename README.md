# Luni Arena — kit de preparação para release 0.1.21

Leia primeiro [`RELEASE_LEIA_PRIMEIRO.md`](RELEASE_LEIA_PRIMEIRO.md). Esta entrega incorpora os núcleos ARM64 compilados de revisões fixas e as respectivas fontes em `third_party/`. O release **ainda depende da publicação da fonte numa URL sua, revisão das licenças e validação via Play**; não há AAB assinado neste ZIP. O aplicativo mantém a biblioteca incremental da versão 0.1.20. `versionCode` desta entrega: **23**.

## Biblioteca Luni e atualização incremental na 0.1.20

- Na primeira abertura, o app cria **Luni Arena/Jogos** no espaço de arquivos próprio do aplicativo. Em **⋮ → Jogos e capas**, você pode importar várias ROMs de uma vez ou copiar os arquivos de uma pasta autorizada pelo seletor do Android. Os originais não são movidos nem excluídos. ZIP, 7Z, capas com nome correspondente e SRAM solta são aceitos na importação da pasta.
- Você também pode **vincular uma pasta sem copiar**. O Luni confere rapidamente a listagem quando você retorna à biblioteca: arquivos novos entram e os excluídos somem. Um índice local guarda o hash e os membros dos ZIP/7Z de arquivos que conservaram nome, tamanho e data de modificação; os bytes de ROMs sem alteração não são processados novamente. Quando o provedor não fornece metadados confiáveis, o app confere o arquivo para evitar resultados incorretos.
- Um arquivo copiado para a pasta do Luni e ainda presente numa pasta externa vinculada aparece uma só vez. A limpeza automática **não apaga o original** em consequência dessa cópia. **Remover cópia do Luni** pede confirmação e afeta apenas o arquivo importado. **Desvincular pasta externa** para de mostrá-la, sem apagar seus arquivos.
- A pasta própria do aplicativo e suas cópias importadas são apagadas pelo Android ao **desinstalar** o app. Mantenha os originais ou um backup fora dele. O índice pode ser reconstruído e não contém ROMs. Os saves e favoritos continuam vinculados ao hash da ROM. `versionCode` 22 permite instalar sobre a versão anterior com a mesma assinatura; a identidade Lunar Blade 02 e o protocolo LUNI6 continuam.

## Identidade Lunar Blade 02 na 0.1.19

- O ícone **Titanium Lunar** agora aparece no launcher e nos elementos da interface que usam `R.drawable.luni_icon`. O recurso usado pelo Android está em `app/src/main/res/drawable-nodpi/luni_icon.png`.
- `store-assets/icone-play-512.png` apresenta a mesma arte em 512 × 512 pixels. A matriz de alta resolução fica em `store-assets/lunar-blade-02-fonte-1254.png`.
- O vetor anterior e o ícone de loja anterior foram guardados em `docs/identidade-anterior/`. Esta é uma troca de identidade visual: as funções de emulação e o protocolo de sala **LUNI6** permanecem os da 0.1.18. `versionCode` 21 permite instalar esta prévia sobre a anterior, usando a mesma assinatura de desenvolvimento.
- Para voltar ao ícone anterior, não deixe os dois arquivos chamados `luni_icon` como recursos ativos: remova o PNG de `drawable-nodpi/`, devolva o XML para `drawable/` e restaure a arte anterior de `store-assets/`.

## Botões ao virar o telefone na 0.1.18

- Virar o telefone entre as duas posições horizontais mantém o tamanho salvo dos botões A/B/X/Y. O recorte da câmera e os gestos podem mudar a posição segura, sem alterar o tamanho; se faltar espaço na lateral, os botões flutuam sobre uma parte do jogo.
- Preserva os ajustes por jogo, o preset **Padrão · botões maiores** e a correção de foco da 0.1.17. Instale sobre a versão anterior sem limpar os dados; a sala continua com o protocolo **LUNI6**.

## Controle externo: correção da imagem esbranquiçada na 0.1.17

- O foco do controle permanece ativo durante o jogo e no menu, mas não aciona mais o destaque padrão do Android sobre a tela inteira. Isso evita que a imagem fique clara após navegar com controle Bluetooth ou USB e preserva o destaque dos itens do menu.
- Esta atualização parte diretamente da **0.1.16** escolhida para os testes, sem as alterações de memória da 0.1.16.1. O protocolo da sala continua **LUNI6**; o código de versão foi elevado a 19 para atualizar instalações existentes.

Projeto Android separado do Knight Joga, com identificador `app.luni.arena`. Os dois apps podem ficar instalados ao mesmo tempo. **O kit precisa das etapas finais documentadas antes de venda/publicação.** O ZIP não inclui ROM, BIOS nem capa de jogo; contém agora os dois núcleos binários compilados de revisões fixas.

## Diagnóstico e integridade da sala na 0.1.16

- Ao criar uma sala, o cartão **Checklist da sala** mostra o identificador da ROM, o núcleo escolhido, multitap, reserva de quadros e, para cada convidado, a mediana de cinco medições de ida e volta com a variação observada. É possível copiar o diagnóstico; o menu **⋮ → Jogadores e conexão** o atualiza durante a partida. A ROM e o arquivo binário do núcleo devem ser idênticos em todos os aparelhos. A conexão recusa diferenças com uma explicação antes de carregar o jogo.
- A cada 180 quadros, os aparelhos calculam uma soma de verificação do estado serializado no mesmo limite de quadro. Se duas amostras consecutivas de um convidado forem diferentes, o host mantém a partida e a vaga; o convidado entra na recuperação já existente e restaura o estado do host. Uma amostra isolada diferente não força reconexão. A sala usa **LUNI6**, portanto instale **0.1.16-preview em todos os aparelhos**.
- A biblioteca em retrato e paisagem tem uma faixa fixa de acesso a **Jogar, Jogos, Arena e Oficina**. O menu avançado durante a partida tem abas de atalho para Partida, Imagem, Controle, Sala e Radar, conforme o modo. As medições não garantem latência zero nem substituem testes em Wi-Fi e no tablet.

## Salas persistentes e reconexão na 0.1.15

- O lobby acompanha as vagas individuais com estados **online**, **livre** e **voltando**. A saída libera a vaga antes do início; depois, o host conserva a vaga e segue jogando, mesmo sozinho. Uma entrada nova em partida iniciada ou em sala cheia recebe uma explicação na tela, com opção de tentar outra sala.
- A conexão do convidado tenta retomar automaticamente após uma queda. O banner do jogo mostra o andamento, **Tentar agora** e **Sair**; **⋮ → Jogadores da sala** exibe os participantes. O host captura um estado no limite de um quadro, sincroniza o convidado e então ambos continuam. Uma retomada breve pode pausar os demais enquanto o estado é aplicado.
- Se o convidado sair para a biblioteca, seu identificador de vaga fica salvo neste aparelho. Com a mesma ROM selecionada, use **Entrar na sala** e o mesmo IP e porta para voltar à partida ainda aberta. Se o host encerrar ou recriar a sala, a vaga anterior deixa de valer.
- Esta versão introduziu **LUNI5**, substituído por LUNI6 na 0.1.16. A porta TCP continua 35671 e a descoberta UDP continua 35670. Consulte o roteiro de teste em `docs/TESTES.md`.

## Preparação da ficha Google Play na 0.1.14

- Arte original para a ficha em `store-assets/`, texto pronto em `docs/FICHA_GOOGLE_PLAY.md` e guia completo em `docs/GUIA_PUBLICACAO_PLAY.md`. As capturas da loja devem ser feitas no aplicativo real com conteúdo autorizado.
- **⋮ → Privacidade** exibe o tratamento de arquivos, salas locais e serviços opcionais. Publique `docs/POLITICA_PRIVACIDADE_PARA_SITE.html` numa URL HTTPS após preencher seu nome e contato reais; confira o fluxo dos SDKs e faça a declaração de Segurança dos dados no Console.
- Cheats consultados em `libretro-database` têm atribuição CC BY-SA 4.0 no menu de licenças; sua proveniência e o vínculo entre GPLv3 e ML Kit continuam pendentes de revisão antes de um lançamento pago. `versionCode` passou a 15, `versionName` a `0.1.14-preview`. A trava de release da 0.1.13 permanece.

## Preparação de release na 0.1.13

- A variante **release** ativa R8 para otimizar e renomear o código Java e reduzir recursos. A ponte JNI `Core` conserva os nomes usados pela biblioteca nativa; builds debug continuam adequados aos testes normais. É preciso testar também um AAB release instalado via Play antes da publicação.
- O build de release agora é bloqueado sem `release-cores.properties`: nele, cada núcleo precisa ter SHA-256 e revisão de fonte fixados; o verificador exige hash igual ao arquivo e segmentos ELF alinhados a 16 KiB. O arquivo de exemplo está na raiz. Os nightlies baixados automaticamente continuam para testes debug, **não** são uma fonte comercial verificável.
- Assinatura de upload e proteção opcional do Play são configuradas na sua conta, não ficam no ZIP. O código GPLv3 e as bibliotecas nativas precisam ter seu código-fonte correspondente disponibilizado. Confira o [guia de preço, proteção e publicação](docs/PROTECAO_E_PUBLICACAO.md) antes de tentar publicar.

## Correção do tamanho padrão na atualização 0.1.12

- Instale por cima da versão anterior, sem limpar dados. Ao abrir a partida, os tamanhos antigos salvos iguais ao padrão pequeno passam automaticamente, uma única vez, para o preset **Padrão · botões maiores**. A correção vale para a configuração geral e para cada jogo, inclusive direcional, face, L/R e Select/Start.
- Posições e tamanhos alterados para valores diferentes do padrão antigo são mantidos. Depois da migração, se você escolher **Compacto · tamanho antigo** e salvar, essa escolha permanece nas próximas partidas. Um tamanho pequeno salvo antes desta atualização que seja *exatamente* igual ao antigo padrão não pode ser distinguido dele e crescerá uma vez.

## Correção ao abrir jogos na 0.1.11

- A 0.1.10 introduziu uma chamada nova à biblioteca nativa para copiar a imagem. Uma compilação com a biblioteca anterior podia encerrar o processo ao abrir qualquer jogo. Esta prévia volta ao caminho de vídeo usado na 0.1.9, sem essa chamada nova, e preserva os controles Bluetooth/USB-C refinados.
- Se a biblioteca nativa estiver ausente ou incompatível, a partida mostra um erro na tela inicial e registra a falha como **LuniArena** no Logcat, em vez de fechar o aplicativo silenciosamente. A inicialização da HUD também informa falhas recuperáveis.
- Compile com **`-CleanNative`** no script Windows abaixo para recriar o cache CMake/Ninja. O identificador do aplicativo permanece `app.luni.arena`; instale por cima da versão anterior para manter os dados. Teste uma ROM em solo antes de entrar em sala.

## Controles Bluetooth e USB-C na 0.1.10

- A leitura do emulador agora usa o estado publicado sem trava por quadro. Botões e analógicos são acompanhados por **ID do dispositivo**: soltar ou desconectar um controle não apaga as entradas de outro. Direcional por botão tem prioridade sobre o analógico; o chapéu digital tem prioridade sobre o stick. Direções opostas simultâneas são neutralizadas.
- Toques curtos recebidos entre dois quadros são mantidos até o próximo quadro. Amostras históricas dos eventos de joystick são processadas para capturar mudanças rápidas dentro do mesmo evento agrupado; repetições automáticas de teclas não entram novamente como comandos.
- L/R também aceitam L2/R2 quando o controle emite botões ou eixos de gatilho (`LTRIGGER`/`BRAKE`, `RTRIGGER`/`GAS`). Os dois formatos do mesmo gatilho são combinados em um único botão SNES.
- O preset analógico **Equilibrado** reage a partir de 0,20, sujeito à área neutra que o próprio dispositivo reportar; **Ágil** e **Preciso** usam 0,14 e 0,30. A pequena histerese evita trepidações sem manter o direcional acionado dentro da zona neutra do aparelho.
- **⋮ → Luni Radar** mostra separadamente a fila do evento no Android e o tempo desde a entrega ao aplicativo até a coleta do próximo quadro. Essas medidas **não** incluem o rádio Bluetooth, a tela nem a reserva de quadros de uma sala. Compare o mesmo jogo em solo no controle USB-C e no Bluetooth; se o FPS cair, o gargalo está no aparelho, no filtro ou no núcleo. USB-C depende do suporte USB OTG do aparelho e do controle.
- O quadro solo reaproveita o vetor de cinco entradas. A imagem voltou ao caminho de vídeo da 0.1.9, que deve ser validado no aparelho antes de tentar outra otimização de memória.
- Teste no telefone: pressione e solte A rapidamente, mova o analógico perto do centro, use direcional e analógico em sentidos opostos, desconecte/reconecte durante uma partida e compare Luni Radar. Teste também no tablet A9 e numa sala com a mesma versão em todos os aparelhos. **Latência física zero não é possível** e o resultado final depende do controle e da tela.

## Reiniciar a ROM na 0.1.9

- Durante a partida, toque em **⋮ → Reiniciar ROM** no menu rápido e confirme. O comando usa o `retro_reset` do núcleo: volta à tela inicial sem fechar o jogo. Ele não apaga saves `.srm` nem estados gravados; o progresso ainda não salvo dentro do jogo pode ser perdido.
- Em sala, **somente o host** oferece esse botão. O reset é enviado no quadro sincronizado a todos os aparelhos e também registrado nos replays, inclusive se algum participante estiver gravando. Em replay ou Arena Fantasma, o botão não aparece; a reprodução segue os resets que foram gravados.
- A sala mudou de **LUNI4** para **LUNI5** na 0.1.15 e para **LUNI6** na 0.1.16; versões com protocolo diferente são recusadas. Instale **0.1.16-preview em todos os aparelhos**. No console compartilhado, o host reinicia seu único núcleo.

## Controles na tela na 0.1.8

- Uma instalação nova abre os controles com o tamanho do antigo preset **Botões maiores** (20% acima do tamanho anterior), inclusive direcional, botões X/Y/A/B, L/R e Select/Start.
- **Editar controles → Ajustes → Preset de posição → Padrão · botões maiores** restaura esse tamanho e o posicionamento automático; **Compacto · tamanho antigo** recupera o tamanho anterior. O preset canhoto mantém os botões maiores.
- Ao atualizar um aplicativo já instalado, a 0.1.12 migra os tamanhos pequenos antigos automaticamente, conforme descrito acima. Use **Padrão · botões maiores** e **Salvar** se também quiser restaurar posições alteradas.

## Nova apresentação da biblioteca na 0.1.7

- Os cartões sem capa agora usam arte original desenhada pelo aplicativo: luzes, texturas e formas variam entre jogos e ocupam o fundo inteiro. O título principal usa uma tipografia condensada de alto contraste, com tamanho ajustado para caber no espaço. O rodapé fica curto e legível; o nome completo continua abaixo da arte, perto da estrela.
- A capa abrevia títulos longos sem mudar o arquivo ou a identificação da ROM: **Donkey Kong Country 2 → DKC 2**, **Super Mario RPG → MARIO RPG**, **Super Bomberman 5 → BOMBERMAN 5** e **Top Gear 3000 → TOP GEAR 3000**. Outros títulos são resumidos automaticamente, incluindo seu número quando possível. A apresentação também aparece na faixa recente, em paisagem e na ficha do jogo quando não existe uma imagem local.
- As ilustrações são geradas localmente, sem baixar capas de terceiros nem incorporar arte de jogos ao APK. Capas pessoais ou obtidas de uma fonte licenciada ainda têm prioridade. Confira a prévia no celular antes de enviar à Play, e use um build **release** para validar o comportamento comercial.

## Tradução de todos os nomes na 0.1.6

- Em jogo solo, **⋮ → Cheats** usa primeiro o vocabulário de jogos para HP, MP, EXP, atributos e efeitos compostos; depois tenta traduzir **automaticamente cada descrição inglesa restante**, inclusive a de códigos manuais, com um modelo local do [Google Translate](https://translate.google.com/). Frases parcialmente traduzidas pelas regras locais seguem para o modelo; **✦** permite repetir as tentativas.
- O primeiro uso baixa o modelo de tradução com a internet disponível, inclusive dados móveis; depois os resultados aceitos ficam em cache e funcionam offline. A cache antiga, que podia conter traduções sem sentido, não é reutilizada. O app recusa traduções automáticas que alterem números presentes no nome do código.
- A lista mostra **Português ✓** por padrão, sem repetir o inglês em cada linha. **Original EN** alterna os nomes; segure uma linha para ver o original e corrigir uma descrição ambígua. As automáticas são identificadas por **Powered by Google Translate**. O código e a ROM permanecem intactos. Se a rede ou o modelo falharem, toque **✦** para tentar novamente.
- É uma prévia para testes. Antes de vender/publicar, confira o uso e a atribuição do Google Translate na interface e na descrição da loja, além das demais auditorias de licenças já descritas no projeto. Use o mesmo APK **0.1.16** em todos os aparelhos nos testes de sala; esta versão usa LUNI6.

## Tradução e fontes na 0.1.5

- Tradução imediata de efeitos e contexto: `Infinite HP` aparece como **Pontos de vida (HP) infinitos**, `Max HP - Character` como **Pontos de vida (HP) máximos — Personagem**, e `Experience Modifer` como **Alterar experiência (EXP)**. MP também é tratado como pontos de magia. Erros comuns de escrita, qualificadores e abreviações têm regras próprias; o inglês original permanece visível.
- O seletor de fontes virou um **cartão com painel navegável**. Cada arquivo mostra nome completo, origem e contagem; busca e filtro de ativos obedecem à origem escolhida. **PT + EN ✓** é destacado e **Só inglês** permanece disponível.
- Nomes próprios de itens são preservados quando não há correspondência segura. Segure um cheat para ajustar seu nome; o código original não muda. A tradução automática local não cobre todas as frases possíveis do catálogo e deve ser conferida com o inglês exibido abaixo.

## Cheats mais claros na 0.1.4

- Na 0.1.4, a faixa **Fontes do catálogo** separava os arquivos `.cht` por abas; a 0.1.5 a substitui pelo painel descrito acima. Busca e filtro **Ativos** acompanham a origem selecionada.
- Nomes de cheats usam um vocabulário verificado de jogos, mantendo o inglês original quando a tradução puder confundir. **Segure uma linha** para corrigir manualmente o rótulo em português ou restaurar o automático. O código não muda.
- Se o código tiver `??` no byte de valor, escolha de **00 a FF** com busca. O seletor mostra nomes de itens quando **o mesmo arquivo** lista um código concreto para aquele endereço e valor. Os demais valores permanecem numéricos, sem prometer que sejam itens válidos. Confira o código completo antes de **Aplicar e ativar**; seleções incompletas não são enviadas ao núcleo.
- Ativar uma variante desliga outros códigos ativos idênticos ou que gravem no mesmo endereço Action Replay, mesmo em cheats com várias partes. O app preserva a seleção por ROM e fonte. Teste uma ROM com `??`, a troca de valor, a rolagem das abas, o nome editável e os cheats ativos após reiniciar o jogo. Cheats seguem disponíveis apenas no solo.

**Limite:** `??` sozinho não contém os nomes das armaduras, personagens ou itens. Apenas as variantes identificadas no catálogo podem ser nomeadas; as 256 opções hexadecimais servem para consulta e escolha explícita.

## Multitap na prévia 0.1.6

- Em **⋮ → Controles e aparência → Multitap**, escolha **ligado (P1–P5)** ou **desligado (P1–P2)**. O padrão continua ligado. No jogo local, abra **⋮ → Multitap** para trocar sem fechar a partida; se a ROM detectar acessórios só na inicialização, saia e abra o jogo novamente. No console compartilhado, controles P3–P5 podem permanecer conectados quando estiver desligado, mas não comandam o jogo.
- Em sala Wi-Fi, configure no host antes de criar a sala. O convidado recebe automaticamente a escolha, e o lobby mostra duas ou cinco vagas. A opção fica fixa até a próxima sala. Replays gravam o modo de controle para reproduzir corretamente. Para testar esta prévia, instale o mesmo APK **0.1.16** (`LUNI6`) em todos os aparelhos.

## Já implementado no projeto

- Ponte libretro com **bsnes-mercury Performance e Mesen-S (GPLv3)**, Joypad e Multitap. O script busca os dois núcleos ARM64 de prévia e registra seus hashes. Jogos Cx4 e formatos FIG/SWC/BS preferem Mesen-S; solo troca para ele se a tela continuar totalmente preta por cerca de 8 segundos. Na LAN o host envia a escolha do núcleo aos convidados; se um jogo ainda ficar preto, abra seu perfil e ative **Núcleo: compatibilidade** antes de criar outra sala. O novo núcleo precisa de testes reais de desempenho, especialmente no Tab A9.
- Nome e ícone Luni, `targetSdk 36`, AGP 8.10.1, aplicação separada e protocolo local próprio `LUNI6` (incompatível com APKs anteriores na mesma sala).
- Biblioteca de ROMs locais, HUD e controles, filtros, console compartilhado, sala LAN, save states, cheats, desafios e replays herdados do protótipo. A transferência de ROM entre aparelhos foi desligada: todos escolhem sua própria cópia idêntica. Capas vêm de imagem importada, quadro escolhido pelo jogador ou arte original do app.
- Reserva automática de 3 a 6 quadros após cinco amostras da conexão de cada convidado, com ajuste manual opcional. **Dupla no P1** no console compartilhado: um convidado ajuda no mesmo personagem.
- **Cápsula de momentos**: marque pontos durante a gravação e consulte marcadores vinculados ao hash da ROM e do núcleo. Eles são locais e ainda não acompanham o `.kjr` exportado.
- A arte original Luni é o padrão da biblioteca. A busca de Libretro Thumbnails só pode ser ligada manualmente na **compilação debug para teste privado**; imagens antigas dessa prévia sem origem identificada não aparecem na versão release. Imagens escolhidas pelo usuário e capas de um serviço licenciado podem ter prioridade. Para uma versão paga ainda é preciso autorizar o uso de cada imagem externa. Veja [CAPAS_IGDB.md](docs/CAPAS_IGDB.md).

## Compile no Windows

1. Extraia o ZIP para `D:\LuniArena\luni-arena`, fora do OneDrive e de caminhos com acentos.
2. Instale no SDK Manager do Android Studio: **Android SDK Platform 36**, **Build Tools 36.0.0**, **NDK 27.2.12479018** e **CMake 3.22.1**. Use JDK 17 e Python 3. A primeira compilação precisa de internet para Gradle, dependências e os dois núcleos.
3. Abra PowerShell na pasta e execute o script abaixo. Nesta entrega ele reutiliza os dois núcleos já incluídos, sem precisar baixá-los:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\tools\build-windows.ps1 -SdkPath "D:\CAMINHO\DO\Android\Sdk" -CleanNative
   ```

   Consulte **Android SDK Location** no SDK Manager para substituir o caminho. O teste gera `app\build\outputs\apk\debug\app-debug.apk`. Se preferir **Build APK(s)** do Android Studio, os núcleos já estão no projeto. **Não apague nem substitua os `.so` incluídos** para esta versão; isso invalidaria os hashes fixados em `release-cores.properties`. Ao instalar sobre a versão anterior com a mesma assinatura, seus dados continuam no identificador `app.luni.arena`.
4. Instale **o mesmo APK** em todos os aparelhos. Escolha a mesma ROM em cada um. Teste solo, P1–P5, console e LAN; compare FPS no Galaxy Tab A9.
5. Depois do build, rode `python tools/check_core.py`. Se alguma biblioteca indicar alinhamento inferior a 16 KiB, **não publique** até recompilar e testar o núcleo correspondente.

Os commits e binários de release agora estão fixados em `release-cores.properties`, e a fonte dos núcleos acompanha este ZIP. O script `fetch_core.py` continua existindo somente para prévias que **não** incluem binários. Antes de vender, publique o código-fonte correspondente da GPLv3 e resolva as demais pendências em `RELEASE_LEIA_PRIMEIRO.md`. Arte de jogos obtida automaticamente também precisa de autorização para uso comercial. O texto da licença está em `COPYING`.

O Luni usa dados privados diferentes do Knight Joga. Faça backup dos saves antes de desinstalar qualquer app. Estados e replays Snes9x não foram validados no bsnes-mercury. LAN: TCP 35671 e descoberta UDP 35670; console: TCP 35672 e UDP 35673. Em VPN, informe o IP e a porta manualmente.

Consulte [PLANO_COMERCIAL.md](docs/PLANO_COMERCIAL.md) para marca, PC doméstico e Play Store e [TESTES.md](docs/TESTES.md) para a validação restante.
