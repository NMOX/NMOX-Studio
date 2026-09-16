# Tutorial: Estúdio de API

<!-- languages -->
[English](api-studio.md) · [Español](api-studio.es.md) · [Français](api-studio.fr.md) · [Deutsch](api-studio.de.md) · [Русский](api-studio.ru.md) · [Українська](api-studio.uk.md) · [Polski](api-studio.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](api-studio.id.md) · [Filipino](api-studio.tl.md) · [Tiếng Việt](api-studio.vi.md) · [简体中文](api-studio.zh.md) · [हिन्दी](api-studio.hi.md) · [עברית](api-studio.he.md) · [العربية](api-studio.ar.md)
<!-- /languages -->

O Estúdio de API é uma bancada REST no estilo do Postman embutida na IDE.
Você monta requisições, faz verificações sobre a resposta e — algo que só
ele faz — cada resposta recebe uma nota pelos padrões de cabeçalhos de
segurança da web.

![Um 200 ao vivo em 331ms — e a aba Padrões dando nota aos cabeçalhos de segurança da resposta](../images/pt/api-studio.png)

## Abrir

`⌥⌘8`, ou a linha **Estúdio de API** na coluna FERRAMENTAS da página
Bem-vindo.

## Passos

1. **Faça uma requisição.** No construtor de requisições, ponha o método
   `GET` e a URL `https://httpbin.org/json`. Aperte **Enviar**. O corpo da
   resposta chega formatado; a linha de status mostra o código, o tempo e o
   tamanho. (Uma resposta desgovernada não faz mal nenhum — os corpos passam
   por um limite de 8 MB.)

2. **Acrescente uma verificação.** Na aba **Testes**, acrescente
   `Status is 200` e `Body contains slideshow`. Envie de novo — cada
   verificação mostra um ✓ verde ou um ✗ vermelho com o valor real.

3. **Leia a nota de segurança.** Abra a aba **Padrões**. O Estúdio de API
   avalia HSTS, CSP, X-Content-Type-Options, a proteção contra
   clickjacking, Referrer-Policy e mais, e dá uma nota em letra — a
   conferência que um desenvolvedor web de 2026 faz em securityheaders.com,
   embutida em cada envio.

4. **Use uma variável.** Crie um ambiente com `base =
   https://httpbin.org` e depois ponha a URL de uma requisição como
   `{{base}}/get`. Troque de ambiente para reapontar todas as requisições de
   uma vez. Se o rack tiver um servidor de desenvolvimento no ar, o Estúdio
   de API ainda oferece a URL dele como `{{baseUrl}}`.

5. **Acrescente autenticação com segurança.** Na aba **Auth**, escolha
   Bearer ou Basic e digite um token. O token **nunca** é gravado no
   `.nmoxapi.json` versionável — ele mora no chaveiro do sistema
   operacional, associado à requisição.

6. **Importe o que você já tem.** O botão **Importar…** lê um comando curl
   colado (o “Copy as cURL” das ferramentas de desenvolvedor do navegador),
   um arquivo de requisições `.http`/`.rest` ou uma especificação OpenAPI 3
   (JSON ou YAML) — cada um vira requisições de verdade, e um cabeçalho
   `Authorization` é levado direto para o campo Auth guardado no chaveiro,
   em vez de parar no seu arquivo de trabalho. **Copiar curl** faz o
   caminho inverso: o comando exato que o Enviar rodaria, na sua área de
   transferência.

7. **Pergunte ao KVASIR sobre uma resposta ruim.** Quando um envio volta
   errado, aperte **Explicar…**. Antes, um diálogo de consentimento diz
   exatamente o que sairia da sua máquina — o método, a URL com os
   *valores* da query mascarados, o status, os cabeçalhos seguros (os
   cabeçalhos de credenciais já retirados e contados) e um corpo limitado —
   e nada é enviado até você dizer que sim. Recuse e nada roda; aceite e a
   explicação abre como uma conversa em que você pode fazer perguntas de
   acompanhamento.

## O que você aprendeu

- Requisições, ambientes e verificações ficam salvos por projeto no
  `.nmoxapi.json` (sem os segredos).
- A nota de segurança transforma “funcionou?” em “está seguro?”.
- Os envios podem ser cancelados (o botão Enviar vira **Cancelar**) e nunca
  travam o resto da IDE.

## Próximos passos

- Aponte uma requisição para um servidor do rack no ar pela oferta
  `{{baseUrl}}`.
- Veja o [Estúdio de banco de dados](db-studio.pt.md) para o equivalente
  em bancos de dados.
