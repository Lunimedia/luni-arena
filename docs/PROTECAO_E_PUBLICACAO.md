# Luni Arena — preço, proteção e publicação

Atualizado para o kit 0.1.21. Este kit **ainda não está pronto para enviar à produção**: os núcleos e suas fontes já estão incluídos, mas faltam a URL pública da fonte do app, a revisão das licenças e o teste do AAB instalado pelo Play. Este guia não substitui a avaliação das políticas da loja na data do envio.

Para a sequência completa de cadastro, assinatura, testes e publicação, consulte [GUIA_PUBLICACAO_PLAY.md](GUIA_PUBLICACAO_PLAY.md) e o roteiro na raiz `RELEASE_LEIA_PRIMEIRO.md`. O pacote 0.1.21 inclui arte e textos para a ficha, uma política de privacidade no aplicativo, um HTML a publicar com contato real e os núcleos de release. Ele ainda não inclui um AAB comercial assinado.

## Preço proposto

**R$ 14,90, pagamento único no Brasil**, sem anúncios nem assinatura. É um preço inicial para a experiência organizada, controles ajustáveis e partidas locais para até cinco aparelhos. O comprador fornece suas próprias ROMs; a sala pela internet requer uma rede privada como Tailscale, não existe hospedagem pública integrada nesta versão. Snes9x EX+ e RetroArch oferecem alternativas gratuitas; teste a aceitação e as avaliações antes de aumentar o preço. Defina outros países manualmente no Play Console. As taxas e impostos reduzem a receita líquida e dependem da sua conta e da região.

Escolha **app pago desde o primeiro lançamento** se esta for a estratégia: a Play permite mudar de pago para grátis, mas não o inverso mantendo o mesmo app. Testes internos/fechados vêm antes de produção.

## O que esta versão implementa

| Camada | Aplicado | Limite |
| --- | --- | --- |
| Build release | R8 otimiza/renomeia código e reduz recursos; classe `Core` fica estável para JNI | Exige teste em aparelho com AAB release; não torna código secreto nem impede cópias |
| Núcleos nativos | `verifyReleaseCores` bloqueia release sem SHA-256, revisão declarada e alinhamento ELF de 16 KiB | O hash não prova a origem; é obrigatório guardar e fornecer a fonte e os comandos reais usados para compilar |
| Aviso GPL | Menu **Licenças e código-fonte** e texto integral da GPL incorporado no APK | O release exige um endereço HTTPS real em `app/src/main/assets/SOURCE_URL.txt` com a fonte exata dessa versão |
| Chaves | `.gitignore` exclui keystores, cache, `local.properties` e bibliotecas baixadas | Guarde a chave de upload e senhas fora do projeto, do ZIP e de backups públicos |
| Rede | Manifesto já desativa tráfego HTTP claro; integração opcional de capas usa credencial no servidor | Servidor de teste com chave comum não constitui autenticação comercial |

Não há um bloqueio de licença novo dentro do emulador: verificações locais são removíveis e podem bloquear compradores legítimos offline. **Nenhuma proteção elimina a pirataria ou impede todos os modders.** A licença atual `GPL-3.0-only` permite a quem recebeu o programa modificar e redistribuir o código coberto; forneça também o código-fonte correspondente aos binários. Revise com especialista qualquer proteção automática do Play que impeça instalar ou executar uma versão modificada legalmente. A marca, o logotipo e serviços hospedados próprios merecem políticas distintas da licença do código.

## Preparar um AAB para teste no Play

1. Defina definitivamente a marca e o ID `app.luni.arena` antes de criar a ficha. Pesquise marca no INPI e na Play. Garanta que os recursos da ficha e do app não incluam ROMs, logos, personagens, capas nem imagens de jogos sem direitos; use capturas da interface com arte própria ou material autorizado. Anuncie corretamente LAN e uso de VPN para redes externas, sem prometer latência zero.
2. Fixe um commit de **cada** núcleo GPL usado. Compile bsnes-mercury e Mesen-S a partir dessas revisões; registre dependências, comandos, flags e SHA-256 dos `.so`. Coloque os `.so` em `app/src/main/jniLibs/arm64-v8a`. Copie `release-cores.properties.example` para `release-cores.properties`, preencha os dois hashes e commits de 40 dígitos. Hospede **todo o código-fonte correspondente** e instruções de reprodução em um endereço HTTPS acessível aos compradores; substitua o placeholder em `app/src/main/assets/SOURCE_URL.txt` pelo endereço exato deste release. Rode `py -3 tools/verify_release_cores.py` no Windows e `python tools/check_core.py` para conferir também a ponte nativa depois da compilação. Publique o manifesto com a fonte de cada versão. O script de download de *nightly* serve apenas para depuração.
3. Audite os termos e a origem dos cheats, as dependências Java e os núcleos. Resolva especificamente a compatibilidade de distribuição entre o projeto GPLv3 e **ML Kit Translation**; cumpra a atribuição de tradução. Confirme por escrito os direitos para qualquer capa de serviço comercial antes de habilitá-la. Prepare política de privacidade pública e preencha Segurança dos dados de acordo com o tráfego real de capas, cheats e downloads do modelo.
4. No Android Studio, use **Build → Generate Signed Bundle / APK → Android App Bundle** com a variante **release**. Crie uma chave de **upload** privada; ative Play App Signing no primeiro envio para separar a chave de distribuição da chave de upload. Nunca envie `.jks`, senha, token de IGDB ou credenciais junto do projeto. Guarde também o `mapping.txt` do R8 de cada versão para interpretar eventuais falhas. O Gradle recusará o release sem o manifesto dos núcleos. Verifique depois o AAB final e o relatório de compatibilidade de 16 KiB no Play Console.
5. Envie para teste interno/fechado. Instale **a versão entregue pela Play** em celular e tablet e confira abertura das ROMs, núcleo de compatibilidade, controles, cheats, saves, paisagem, sala e desempenho por vários minutos. O APK debug instalado atualmente foi assinado por outra chave: faça **backup dos saves de cada ROM no menu Arquivos e replays** antes de desinstalá-lo para instalar a versão Play; importar depende da mesma ROM. Uma conta pessoal criada após 13/11/2023 exige pelo menos 12 testadores inscritos continuamente por 14 dias antes do acesso à produção.
6. No Play Console, configure perfil de pagamentos, **Produtos → Preço do app → Pago**, Brasil **R$ 14,90**, países desejados e ficha. Ative apenas as proteções que apareçam para sua conta **após** revisar a licença GPL e testar a experiência offline. A verificação automática do instalador é opcional; o recurso avançado antialteração só está disponível a parceiros selecionados. Não presuma que essas opções estejam liberadas.

## Proteção futura dos serviços

Caso ofereça catálogo licenciado ou salas hospedadas em servidor próprio: mantenha credenciais exclusivamente no servidor, limite requisições por conta, proteja sessão e tráfego com HTTPS e verifique tokens da **Play Integrity API no servidor** perto das ações protegidas. Confira `appLicensingVerdict` e o vínculo com sua conta nas respostas verificadas. Use vereditos graduais e trate falhas de rede sem afetar emulação local ou clientes legítimos. Essa integração **não foi implementada** porque este projeto ainda não tem backend público, conta Play vinculada nem credenciais; inserir um segredo no APK não o protegeria.

Referências oficiais:

- [Preço de aplicativos](https://support.google.com/googleplay/android-developer/answer/6334373?hl=pt-BR)
- [Assinatura de apps e chave de upload](https://developer.android.com/studio/publish/app-signing)
- [Otimização R8](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)
- [Play Integrity](https://developer.android.com/google/play/integrity/overview)
- [Proteção automática e elegibilidade](https://support.google.com/googleplay/android-developer/answer/10183279?hl=pt-BR)
- [Testes para novas contas pessoais](https://support.google.com/googleplay/android-developer/answer/14151465?hl=pt-BR)
- [Política de propriedade intelectual](https://support.google.com/googleplay/android-developer/answer/18258653?hl=pt-BR)
- [Licença GPLv3](https://www.gnu.org/licenses/gpl-3.0.html)
