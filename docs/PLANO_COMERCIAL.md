# Luni Arena — comercialização e infraestrutura doméstica

Referência: 29/09/2026. Nome de trabalho **Luni Arena**. Alternativas: **Luni Link** (rede), **Luni Orbit** (marca ampla), **Luni Duo** (cooperação), **Luni Play** (simples) e **Luni Nexo** (comunidade). Antes de investir em registro ou material de divulgação, pesquise marcas no INPI/TMview, nomes na Play e domínios. Nenhum nome foi liberado juridicamente. O ID `app.luni.arena` é provisório até a decisão de marca.

## Situação das ideias

| Função | Agora | Para terminar |
| --- | --- | --- |
| Emulação comercial | Ponte preparada para bsnes-mercury GPLv3 | Testes reais, desempenho no A9, fonte exata e compatibilidade |
| Diagnóstico da sala | Cinco sondagens na entrada escolhem reserva 3–6 quadros | Carga de 3–5 aparelhos, queda e detecção de dessincronização |
| Dupla no mesmo personagem | Convidado do console ajuda P1 | Confirmar em aparelhos e botões conflitantes |
| Desafios por replay | Gravação, importação, Arena Fantasma e placar | Convite assíncrono com fluxo de aceitação |
| Cápsula de momentos | Marcações locais vinculadas à ROM/núcleo | Exportação, importação e salto para marcador |
| Câmera de jogo dividido | Corte manual por jogador e ajuste por ROM | Reconhecer menu↔corrida sem cortar tela errada |
| Sala viva | Console compartilhado troca jogo sem perder controles | Sala LAN de imagens individuais precisa de novo protocolo de retomada |
| Capas automáticas | Cliente HTTPS, cache local, fonte IGDB e gateway em Python | Parceria comercial, credenciais, imagens autorizadas e teste fim a fim |
| Internet nativa | Ainda não implementada | TLS, autenticação, relay, mitigação de abuso e medição de latência |

## Posso montar isso no PC de casa?

**Sim.** Um PC ligado e com boa conexão pode hospedar código e backups próprios e, futuramente, sinalização de convites e relay. Para jogar agora em 4G com esta prévia, **Tailscale nos celulares** fornece endereços privados: todos entram na mesma rede Tailscale; o host cria a sala, e cada convidado digita o IPv4 Tailscale do telefone do host e a porta `35671`. O host encontra esse IP no app Tailscale. O broadcast de descoberta pode não cruzar a VPN. Cada aparelho precisa da mesma ROM e do mesmo APK. O PC não é necessário nessa opção. Conexões podem ser diretas ou usar relay, com latência diferente; **zero milissegundo não é possível**.

Não encaminhe a porta LAN `35671` do telefone diretamente da internet pública: o protocolo atual não autentica nem cifra a partida. Cloudflare Tunnel para TCP bruto exige cliente próprio no lado do jogador, então não é um botão de ligar dentro deste APK. Um serviço nativo futuro no PC usaria TLS, convites com segredo temporário, sala autenticada, regras de acesso e transporte dos quadros sem ROMs. Se a operadora usar CGNAT, será preciso túnel de saída ou relay externo. O PC desligado interromperia salas dele, e um relay distante aumentaria o atraso.

## Requisitos antes da Play Store paga

1. **Licenças:** Snes9x do protótipo proíbe o uso comercial. bsnes-mercury Performance é GPLv3: é possível vender, mas os destinatários podem redistribuir e é necessário fornecer o código-fonte correspondente do núcleo e da obra combinada. Este nightly não está vinculado a um commit de fonte no ZIP; fixe a revisão e audite todas as dependências para a versão final. O texto GPLv3 está em `COPYING`.
2. **Conteúdo:** não inclua ROM, BIOS, logos, capas ou imagens promocionais de jogos sem autorização. A integração IGDB só deve ser habilitada após parceria comercial com permissão para as imagens usadas; veja `docs/CAPAS_IGDB.md`. Os dados obtidos do catálogo de cheats e seus termos/proveniência ainda exigem revisão antes da venda; a versão atual usa regras locais **e ML Kit Translation**. Confira a atribuição da tradução e a compatibilidade de licença do SDK com o código GPL antes de distribuir.
3. **Tecnologia:** API alvo 36 (exigência atual em setembro de 2026), AAB assinado, bibliotecas ARM64 compatíveis com páginas de 16 KiB, testes em telefone/tablet, política de privacidade e formulário de Segurança dos dados de acordo com o comportamento real. `python tools/check_core.py` verifica alinhamento, mas não corrige a biblioteca de terceiros.
4. **Conta:** inscrição Play Console de US$ 25 uma única vez. Contas pessoais novas podem exigir teste fechado com 12 participantes ativos por 14 dias para produção. Defina o preço antes de oferecer o app gratuitamente: um aplicativo que já foi oferecido como grátis não pode depois virar pago mantendo o mesmo pacote.
5. **Envio:** depois de cumprir os pontos anteriores, use **Build → Generate Signed Bundle / APK → Android App Bundle**, guarde a chave de upload e comece em teste fechado. O APK de debug não é a versão da loja. **Este projeto ainda não passou pelos pontos 1–3.** O build release exige `release-cores.properties` conforme `docs/PROTECAO_E_PUBLICACAO.md`.

Fontes oficiais para revisar na data de publicação:

- Núcleo: https://docs.libretro.com/library/bsnes_mercury_performance/ e https://github.com/libretro/bsnes-mercury
- API da Play: https://support.google.com/googleplay/android-developer/answer/11926878?hl=pt-br
- AAB e 16 KiB: https://developer.android.com/guide/app-bundle e https://developer.android.com/guide/practices/page-sizes
- Propriedade intelectual: https://support.google.com/googleplay/android-developer/answer/9888072?hl=pt-BR
- Conta e testes: https://support.google.com/googleplay/android-developer/answer/6112435?hl=pt-BR e https://support.google.com/googleplay/android-developer/answer/14151465?hl=pt-BR
- Preço: https://support.google.com/googleplay/android-developer/answer/6334373?hl=pt-BR
- Marcas: https://www.gov.br/inpi/pt-br/projetos-estrategicos/inpi-negocios/tmview/tmview-ferramenta-online-de-busca-de-marcas
- VPN e túnel: https://tailscale.com/docs/reference/connection-types e https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/routing-to-tunnel/protocols/
