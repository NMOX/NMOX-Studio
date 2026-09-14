# Do Navegador web à origem: escolher, saltar, reestilizar

<!-- languages -->
[English](browser-to-source.md) · [Español](browser-to-source.es.md) · [Français](browser-to-source.fr.md) · [Deutsch](browser-to-source.de.md) · [Русский](browser-to-source.ru.md) · [Українська](browser-to-source.uk.md) · [Polski](browser-to-source.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](browser-to-source.id.md) · [Filipino](browser-to-source.tl.md) · [Tiếng Việt](browser-to-source.vi.md) · [简体中文](browser-to-source.zh.md) · [हिन्दी](browser-to-source.hi.md) · [עברית](browser-to-source.he.md) · [العربية](browser-to-source.ar.md)
<!-- /languages -->

*Uma sentada só. Você vai clicar num elemento no Navegador web embutido, cair
no arquivo que o produziu, mudar o estilo dele pelo DevTools e ver a
mudança chegar à sua folha de estilo — sem redigitar nada.*

A divisão mais antiga do desenvolvimento web é que o navegador e o editor
sabem coisas diferentes: o navegador sabe *de qual elemento você está
falando*, o editor sabe *onde o código mora*, e você leva a informação de
um para o outro na mão. O Navegador web do NMOX Studio fecha essa divisão.
Este tutorial percorre o ciclo inteiro numa página que você cria em dois
minutos.

![O painel DOM do DevTools com um h1 escolhido na página: Escolher elemento, Abrir origem e Editar estilo… ao lado da árvore ao vivo](../images/story-06-devtools-pick.png)

## 1. Crie uma página

Crie uma pasta com dois arquivos (o **Novo arquivo** do Estúdio de projeto
serve, ou qualquer jeito que você preferir):

`index.html`

```html
<!doctype html>
<html>
<head>
    <title>Loop Demo</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <header class="hero">
        <h1 id="headline">Hello, loop</h1>
        <p class="tagline">watch this paragraph change color</p>
    </header>
</body>
</html>
```

`style.css`

```css
.hero {
    background: #222;
    color: white;
    padding: 2rem;
}

.tagline {
    color: gray;
    font-style: italic;
}
```

## 2. Abra no Navegador web

Abra a aba **Navegador web** (⌥⌘4), digite o caminho do arquivo na barra de
endereço como uma URL `file://` — por exemplo
`file:///Users/you/NMOX/loopdemo/index.html` — e aperte Return.

> Uma página servida por um dos dispositivos que servem do rack
> (IGNITION, VELOCITY, HALO e companhia) funciona exatamente do mesmo
> jeito — o Navegador web sabe a que projeto pertence cada servidor no ar. O
> que **não** funciona é um site remoto: o ciclo só confia em páginas que
> consegue rastrear até arquivos no seu disco, e diz isso em vez de
> adivinhar.

Clique em **DevTools** na barra do Navegador web e selecione a aba **DOM**.

## 3. Escolha um elemento na página

Clique em **Escolher elemento**. O cursor na página vira uma mira. Agora
clique no título dentro da própria página.

Três coisas acontecem ao mesmo tempo: o clique é engolido (nada de
navegação), a árvore DOM seleciona `h1#headline` e um contorno azul
envolve o elemento na página. O painel de detalhes se enche com os
atributos e os estilos computados — incluindo um veredito de contraste
WCAG quando as duas cores são conhecidas.

## 4. Salte para a origem

Com o elemento selecionado, clique em **Abrir origem** (dar dois cliques
no nó da árvore faz o mesmo). O editor abre `index.html` com o cursor na
linha exata que produziu o elemento.

Como ele encontra a linha, e quando se recusa:

- Um elemento **com id** é encontrado por esse id — ids são únicos,
  então o resultado é exato.
- Um elemento **sem id** é encontrado como a N-ésima ocorrência da sua
  tag na ordem do documento, ignorando comentários e o conteúdo de
  `<script>`/`<style>` (um `<div>` dentro de um comentário ou de uma
  string JS não é um elemento).
- Um elemento que **só existe porque um script o criou** não está no seu
  código-fonte — a barra de status diz “provavelmente gerado por script”
  em vez de saltar para o lugar errado.
- Uma página que não vem de um arquivo local — um site remoto, um
  servidor de desenvolvimento desconhecido — é recusada com “não servido
  por um projeto aqui”.

As recusas são o ponto: um salto que pode estar errado é pior do que
nenhum salto.

## 5. Reestilize — e veja a origem mudar

Selecione a linha de descrição (`p.tagline`) — escolha-a na página ou
clique nela na árvore — e aperte **Editar estilo…**. Na caixa de diálogo,
escolha a propriedade `color`, digite o valor `tomato` e aperte OK.

Duas coisas acontecem, nesta ordem:

1. **A página se redesenha na hora.** O ajuste é aplicado primeiro inline,
   então você sempre vê o que pediu.
2. **A folha de estilo de origem muda.** A barra de status informa
   `Salvo em style.css  (.tagline)` — abra `style.css` e
   `color: gray;` virou `color: tomato;`, no mesmo lugar, com todos os
   outros bytes intactos.

A regra a editar é escolhida perguntando à *página* quais regras de folha
de estilo casaram com o elemento — a resposta da própria cascata, a última
que casa vence — então a escrita cai na regra que realmente estiliza o que
você vê, mesmo quando o mesmo seletor aparece duas vezes num arquivo.

## 6. Os limites honestos

Editar estilo… se recusa, com o motivo na barra de status, sempre que
escrever seria um chute ou destruiria trabalho. A pré-visualização inline
continua valendo em todos os casos — você vê o ajuste; a mensagem diz por
que ele não foi salvo.

| Situação | O que ele diz |
|-----------|--------------|
| A regra mora num bloco `<style>` inline | “a regra está em um `<style>` inline, não em um arquivo de folha de estilo” |
| A folha de estilo é remota ou vem de um servidor desconhecido | “não é servida por um projeto aqui” |
| O `.css` tem um irmão `.scss`/`.less`/`.sass` | “é saída compilada — edite a origem do pré-processador” (uma escrita aqui se perderia na próxima compilação) |
| O arquivo tem alterações não salvas num editor | “tem alterações não salvas no editor — salve primeiro” |
| Nenhuma regra de folha de estilo casa com o elemento | “Aplicado apenas na página — nenhuma regra de folha de estilo corresponde a este elemento” |

## 7. Feche o ciclo

Se a página está sendo servida por um dispositivo do rack, você nem
precisa recarregar: o recarregar-ao-salvar do Navegador web observa os
salvamentos de arquivos web e atualiza as páginas locais sozinho. Escolher
→ ajustar → origem atualizada → página recarregada a partir dessa origem.
O navegador e o editor, uma superfície só.
