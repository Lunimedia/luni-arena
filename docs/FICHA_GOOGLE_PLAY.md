# Ficha pronta para Google Play — Luni Arena

Copie o texto abaixo para a página principal de detalhes, com idioma padrão **Português (Brasil)**. Não afirme que o aplicativo fornece jogos. Verifique que os recursos descritos funcionem no AAB enviado. As capturas precisam vir do aplicativo real e usar apenas conteúdo próprio ou autorizado.

## Nome (até 30 caracteres)

Luni Arena

## Descrição breve (até 80 caracteres)

Sua biblioteca de jogos 16 bits com controles flexíveis e partidas em rede local

## Descrição completa (até 4.000 caracteres)

Luni Arena organiza suas ROMs locais em uma biblioteca com arte original, busca, favoritos e acesso rápido aos jogos recentes. Escolha arquivos do seu aparelho ou uma pasta pelo seletor do Android.

Jogue com controles na tela ajustáveis ou conecte um controle Bluetooth ou USB. Ajuste o direcional, analógico, tamanho e posição dos botões. Quando um controle externo é detectado, a interface de jogo se adapta sem encerrar a partida.

Convide amigos na mesma rede local: cada pessoa usa seu aparelho e sua própria cópia idêntica da ROM. O aplicativo permite salas com até cinco jogadores em jogos compatíveis e oferece o modo console compartilhado. Para jogar entre redes diferentes, é necessário configurar uma VPN por conta própria e informar IP e porta; o aplicativo não fornece servidor público. A fluidez depende da rede, do aparelho e do jogo.

Recursos extras incluem estados de salvamento, replays, ajustes de imagem, perfis por jogo e cheats para partidas solo. As descrições dos cheats podem receber tradução no aparelho com ML Kit, identificada como Powered by Google Translate. Os códigos devem ser conferidos antes da ativação.

O aplicativo não inclui, vende, hospeda ou baixa ROMs, BIOS ou jogos. Você é responsável por usar arquivos sobre os quais tenha os direitos necessários. Imagens de jogos de terceiros não fazem parte da biblioteca padrão; você pode importar imagens próprias. Luni Arena é um projeto independente e não tem afiliação com fabricantes ou editoras de jogos.

## Recursos gráficos e capturas

- `store-assets/icone-play-512.png`: ícone original, 512 × 512 px, PNG.
- `store-assets/grafico-play-1024x500.png`: gráfico de destaque original, 1024 × 500 px, PNG sem transparência.
- Adicione no mínimo duas capturas de tela reais da compilação entregue pelo Play. Sugestão: biblioteca com cartões de arte original e edição de controles. Para mostrar uma partida, use um jogo homebrew cuja licença permita a captura promocional. Se quiser promover tablets, faça também capturas reais no A9.
- Não use os prints antigos de ROMs comerciais nem personagens, títulos estilizados, trilhas ou artes alheias sem licença para divulgação comercial.

## Configuração sugerida da ficha

- Tipo: aplicativo; categoria **Ferramentas** ou a categoria atual mais adequada no Console (o app é o emulador, não o jogo fornecido pela loja).
- País inicial: Brasil; preço inicial sugerido **R$ 14,90**, pagamento único. Revise o valor e os países na tela de preços antes de salvar.
- E-mail de suporte: use um endereço real que você monitore. Site e telefone são opcionais se o Console não exigir no seu caso.
- URL de política de privacidade: publique `docs/POLITICA_PRIVACIDADE_PARA_SITE.html` como página pública HTTPS, substituindo o contato indicado; teste sem login em janela anônima.
- Classificação indicativa e público alvo: responda ao questionário conforme a **funcionalidade efetiva do app**. Não assuma classificação de um jogo escolhido pelo usuário nem invente idade.
- Declarações: o projeto não mostra anúncios nem usa compras internas; confirme no AAB final. Complete Segurança dos dados após conferir também as práticas dos SDKs do build.

## Versão

Nesta entrega: `app.luni.arena`, `versionCode 22`, `versionName 0.1.20-preview`, Android API mínima 26, alvo 36, ARM64. O nome ainda contém `preview`: antes da primeira versão pública, altere `versionName` para uma versão estável verdadeira somente depois de completar o teste de release. Nunca reduza o `versionCode` ao atualizar no Play. O ZIP não inclui núcleos nativos de release nem AAB assinado.
