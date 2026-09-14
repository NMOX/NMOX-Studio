# Tutorial: Estúdio de blocos

<!-- languages -->
[English](block-studio.md) · [Español](block-studio.es.md) · [Français](block-studio.fr.md) · [Deutsch](block-studio.de.md) · [Русский](block-studio.ru.md) · [Українська](block-studio.uk.md) · [Polski](block-studio.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](block-studio.id.md) · [Filipino](block-studio.tl.md) · [Tiếng Việt](block-studio.vi.md) · [简体中文](block-studio.zh.md) · [हिन्दी](block-studio.hi.md) · [עברית](block-studio.he.md) · [العربية](block-studio.ar.md)
<!-- /languages -->

O Estúdio de blocos é um compositor no estilo do Scratch para Web
Components **de verdade**. Você encaixa blocos tipados e ele gera um custom
element autocontido (shadow DOM, estado, listeners) — mais um servidor de
visualização ao vivo para você vê-lo rodar. Clique num bloco para realçar
as linhas exatas que ele produziu.

![O Estúdio de blocos — a paleta de peças, a tela com a raiz de um componente e o custom element gerado, com o mapeamento clique-na-peça para o código](../images/tabs/block-studio.png)

## Abrir

`⌥⌘5`, ou a aba **Estúdio de blocos**.

## Passos

1. **Dê um nome ao elemento.** Todo custom element precisa de uma tag com
   hífen. Comece um componente e dê a ele uma tag como `hello-badge`.

2. **Acrescente blocos da paleta.** Arraste um bloco **Elemento** (um nó do
   DOM) e dê a ele um texto; acrescente um campo **Estado**; acrescente um
   **Ao evento** que alterna uma classe no clique. Só aninhamentos válidos
   são permitidos — a tela mostra onde dá para soltar e recusa os inválidos,
   inclusive ao carregar.

3. **Leia o código.** O painel do meio mostra o `text/javascript` gerado —
   um custom element completo. Clique em qualquer bloco e as linhas que ele
   produziu ficam realçadas; o mapeamento é exato.

4. **Veja ao vivo.** Aperte **Visualizar**. O Estúdio de blocos serve o
   componente a partir de um servidor em memória e o renderiza; o `⇄` e a
   Pesquisa rápida mostram a URL ao vivo. Componentes do mesmo espaço de
   trabalho podem até usar uns aos outros.

5. **Salve.** **Salvar componente** grava `src/components/<tag>.js` — de
   forma atômica, sem nunca sobrescrever um arquivo editado à mão. O espaço
   de trabalho inteiro fica em `.nmoxblocks.json`; **Abrir componente…**
   reimporta um arquivo que você (ou o estúdio) escreveu, desde que ele
   ainda esteja no dialeto dos blocos.

## O que você aprendeu

- A saída é um custom element real, sem framework, pronto para publicar.
- O mapeamento bloco↔código vale nos dois sentidos: edições dentro do
  dialeto reimportam sem problema.
- Um espaço de trabalho guarda vários componentes; trocar de um para outro
  é uma fronteira de desfazer.

## Próximos passos

- Componha componentes a partir de componentes — um bloco que nomeia a tag
  de um irmão o renderiza aninhado na visualização.
