# Tutorial: explique qualquer coisa com o KVASIR

<!-- languages -->
[English](explain-anything.md) · [Español](explain-anything.es.md) · [Français](explain-anything.fr.md) · [Deutsch](explain-anything.de.md) · [Русский](explain-anything.ru.md) · [Українська](explain-anything.uk.md) · [Polski](explain-anything.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](explain-anything.id.md) · [Filipino](explain-anything.tl.md) · [Tiếng Việt](explain-anything.vi.md) · [简体中文](explain-anything.zh.md) · [हिन्दी](explain-anything.hi.md) · [עברית](explain-anything.he.md) · [العربية](explain-anything.ar.md)
<!-- /languages -->

O KVASIR começou como um dispositivo do rack que explica execuções que
falharam. Hoje ele alcança quatro lugares — o rack, o editor, o Estúdio de
API e o Estúdio de banco de dados — e todas as faces seguem as mesmas três
leis: **você vê exatamente o que sairia da sua máquina antes que qualquer
coisa saia**, **cada superfície conquista o próprio consentimento** (dizer
sim para erros de compilação nunca autoriza enviar código nem SQL) e **os
segredos não têm como ir junto, por construção** (o que é revelado é
montado pelo estúdio dono dos dados, com os cabeçalhos de credenciais
retirados e as senhas sempre fora de alcance).

![O KVASIR explicando uma execução que falhou de verdade](../images/kvasir-explain.png)

## Antes de começar

Uma chave cobre as quatro faces — do provedor que você escolher: Claude
(Anthropic), ChatGPT (OpenAI) ou Gemini (Google). Aperte **KEY…** no painel
frontal do KVASIR para escolher o provedor e guardar a chave dele no
chaveiro do sistema operacional, ou exporte `ANTHROPIC_API_KEY`,
`OPENAI_API_KEY` ou `GEMINI_API_KEY`. Sem chave, sem chamada — e toda face
diz isso com honestidade.

## As quatro faces

1. **Uma execução que falhou (o rack).** Monte o KVASIR, rode algo que
   falhe e aperte **EXPLAIN**. O que é enviado: o comando, o código de
   saída e até cinco linhas de erro amostradas. Veja o [tutorial do
   KVASIR](kvasir.pt.md) para o passeio completo, incluindo o cabo que
   explica sozinho uma falha do VERITAS, sem as mãos.

2. **O seu código (o editor).** Selecione código em qualquer linguagem →
   clique com o botão direito → **Perguntar ao KVASIR sobre a seleção…** e
   digite uma pergunta. O que é enviado: a seleção limitada, o nome do
   arquivo e a linguagem — nada mais do seu projeto. Esta face tem a
   *própria* barreira de consentimento, porque o consentimento do fluxo de
   falhas promete expressamente que o código-fonte nunca sai da máquina.

3. **Uma resposta de API (o Estúdio de API).** Depois de um envio, aperte
   **Explicar…**. O que é enviado: o método, a URL com os valores da query
   mascarados, o status, os cabeçalhos com as credenciais retiradas e
   contadas, e um corpo limitado. Útil no instante em que aparece um 401 ou
   um cabeçalho CORS estranho.

4. **Um erro de banco de dados (o Estúdio de banco de dados).** Uma
   instrução que falhou ganha um botão **Explicar…** sob a mensagem de
   erro. O que é enviado: o SQL que você rodou — *com os valores literais, e
   a linha do consentimento diz isso*, porque o erro quase sempre é sobre um
   literal — mais a mensagem de erro e o tipo de motor. Nunca a conexão, a
   senha ou as linhas.

Toda face abre uma janela de conversa: faça perguntas de acompanhamento, e
o modelo vê todo o histórico daquela troca (limitado a dez trocas, o que é
dito na transcrição). A escolha **Fast/Deep** (o modelo rápido e o modelo
forte do provedor escolhido) fica lembrada,
e é fixada por conversa para que a transcrição nunca minta sobre quem
respondeu.

## Experimente em dois minutos

O Estúdio de banco de dados é a face mais rápida de demonstrar: abra ⌥⌘7,
crie uma conexão SQLite, rode `SELECT * FROM user;` contra um banco cuja
tabela se chama `users` e aperte **Explicar…** no erro. Leia o diálogo de
consentimento antes de aceitar — ele é a promessa do produto, numa frase.

## O que você aprendeu

- Quatro superfícies, uma costura: cada estúdio monta o que revela, e o
  diálogo de consentimento cita isso ao pé da letra.
- Recusar é respeitado em silêncio e por completo — nenhuma janela, nenhuma
  chamada.
- Um resultado pertence ao espaço de trabalho que o produziu: trocar de
  projeto limpa as respostas e as abas de resultado, então o Explicar nunca
  pode revelar dados de um projeto anterior.
