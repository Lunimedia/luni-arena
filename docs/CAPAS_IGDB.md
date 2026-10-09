# Capas automáticas do Luni Arena

Esta integração consulta a IGDB **somente para jogos já presentes na biblioteca local**. Ela não fornece ROM. O aplicativo dá prioridade à capa importada ou ao quadro salvo pelo jogador, busca capas ausentes em segundo plano e guarda o resultado no armazenamento privado do aparelho. Na biblioteca, a fonte é creditada como **IGDB.com**.

## Pré-requisito comercial

1. Solicite uma [parceria comercial à IGDB](https://api-docs.igdb.com/#faq) por `partner@igdb.com` e confirme por escrito que o seu uso de imagens de capas em um aplicativo Android pago está autorizado. A API sem essa parceria é documentada para uso não comercial. A Play Store exige direitos sobre os materiais de terceiros exibidos no app.
2. Crie uma aplicação **Confidential** no [Twitch Developer Portal](https://dev.twitch.tv/console) e obtenha `Client ID` e `Client Secret`, conforme a [documentação IGDB](https://api-docs.igdb.com/#account-creation).
3. Nunca cole o `Client Secret` no código do Android, no `build.gradle`, no ZIP distribuído ou no telefone. O pequeno servidor em `tools/cover_server.py` usa essas credenciais no PC e entrega apenas a capa ao app.

## Teste privado com o PC de casa

Instale Python 3 e Tailscale no PC e nos celulares de teste, com acesso ao mesmo tailnet. No PowerShell, na pasta do projeto:

```powershell
cd D:\LuniArena\luni-arena
$env:IGDB_COMMERCIAL_APPROVED = '1'
$env:IGDB_CLIENT_ID = 'SEU_CLIENT_ID'
$env:IGDB_CLIENT_SECRET = 'SEU_CLIENT_SECRET'
$env:LUNI_COVER_ACCESS_TOKEN = 'CRIE_UMA_CHAVE_ALEATORIA_DE_32_OU_MAIS_CARACTERES'
py -3 .\tools\cover_server.py
```

Use `IGDB_COMMERCIAL_APPROVED=1` **apenas depois** de receber a confirmação da parceria e da permissão das imagens. Não use chaves de exemplo. Mantenha esta janela aberta; ela imprime `http://127.0.0.1:37841`. O servidor escuta só no próprio PC e não abre uma porta pública.

Em outro PowerShell, com Tailscale ativo:

```powershell
tailscale serve --bg 37841
tailscale serve status
```

O comando mostra uma URL **HTTPS** do seu PC, parecida com `https://meu-pc.minha-rede.ts.net`. No telefone, abra **Luni Arena → ⋮ → Jogos e capas → Catálogo de capas**, cole essa URL e a chave `LUNI_COVER_ACCESS_TOKEN` que você definiu. Salve. As capas ausentes serão buscadas ao mostrar a biblioteca; **Buscar capas ausentes** tenta novamente. A URL HTTPS precisa ser acessível pelo telefone e usar certificado válido. O Tailscale Serve provê HTTPS dentro do tailnet; não use **Funnel** nem encaminhe a porta do servidor sem uma implantação pública com autenticação própria.

O botão **Testar conexão** confirma o acesso ao gateway, mas ainda não confirma que a IGDB aceitou as credenciais. Se uma capa não aparecer, confira a saída da janela do servidor e o título da ROM. É necessário manter o PC ligado para buscar capas novas.

Se houver duas imagens com nomes parecidos, o servidor exige que a entrada seja para SNES e evita trocar capas de jogos com números de sequência diferentes. Jogos modificados ou nomes muito diferentes do catálogo podem continuar com capa original do Luni; nesses casos, importe uma imagem manualmente. Falha de internet ou PC desligado não impede iniciar o jogo. Capas já obtidas continuam no aparelho.

## Para a versão pública paga

Hospede o gateway atrás de HTTPS com autenticação por usuário, limite de requisições e gestão de chaves sem exigir Tailscale dos compradores. O `X-Luni-Key` único desta prévia foi feito para teste privado e não é um sistema de contas comercial. Revise a política de privacidade: só o **nome da ROM** é enviado ao seu gateway para procurar metadados; o arquivo da ROM nunca é enviado. Defina uma forma de corrigir capas erradas e de cumprir solicitações dos titulares. Guarde a documentação da parceria e respeite seus limites de uso, cache e atribuição.

Documentação: [IGDB API](https://api-docs.igdb.com/), [Tailscale Serve](https://tailscale.com/docs/features/tailscale-serve), [política de propriedade intelectual da Play](https://support.google.com/googleplay/android-developer/answer/9888072).
