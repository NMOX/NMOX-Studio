# Tutorial: Image Kit (Web) — comprima suas imagens

<!-- languages -->
[English](image-kit.md) · [Español](image-kit.es.md) · [Français](image-kit.fr.md) · [Deutsch](image-kit.de.md) · [Русский](image-kit.ru.md) · [Українська](image-kit.uk.md) · [Polski](image-kit.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](image-kit.id.md) · [Filipino](image-kit.tl.md) · [Tiếng Việt](image-kit.vi.md) · [简体中文](image-kit.zh.md) · [हिन्दी](image-kit.hi.md) · [עברית](image-kit.he.md) · [العربية](image-kit.ar.md)
<!-- /languages -->

As imagens costumam ser o que há de mais pesado num site. O Image Kit
encontra os JPEGs e PNGs do seu projeto e os comprime para a web: irmãos
`.min.jpg` menores por recodificação em Java puro (nada a instalar),
redução de tamanho opcional e irmãos `.webp` pelo seu próprio `cwebp`,
quando ele estiver instalado. Na prova ao vivo desta versão, um papel de
parede de 17,8 MB virou um `.min.jpg` de 347 KB e um `.webp` de 342 KB —
98% menor.

## As leis que ele cumpre

- **Os originais nunca são tocados.** As saídas são irmãs
  (`photo.min.jpg`, `photo.webp`), e uma saída que já existe é pulada e
  avisada — nunca sobrescrita.
- **Uma “otimização” que não economiza nada é descartada**: uma compressão
  que recupera menos de 10% é apagada e relatada como *já enxuta*, em vez
  de entregar um arquivo “otimizado” maior. (Uma saída redimensionada é
  mantida de qualquer jeito — menos pixels era justamente o objetivo.)
- **A recodificação de PNG fica de fora de propósito.** O ImageIO não
  supera um otimizador de PNG de verdade, então para PNGs o ganho honesto
  é o irmão WebP.

## Passos

1. **Aponte para um projeto** e escolha **Arquivo ▸ Adicionar ao projeto ▸
   Image Kit (Web)…**. O diálogo diz quantas imagens encontrou e o peso
   total delas (node_modules e saídas de compilação são puladas, assim como
   as próprias saídas `.min.` — comprimir uma compressão só somaria perdas).

2. **Escolha a compressão.** Qualidade JPEG (85 visualmente sem perdas / 80
   padrão da web / 70 agressiva), uma largura máxima opcional (2560 destaque
   retina / 1600 conteúdo / 800 miniaturas) e — se o `cwebp` estiver no seu
   PATH — irmãos WebP. Se não estiver, a caixa de seleção diz isso e onde
   obtê-lo (`brew install webp`); o Doutor do ambiente também o sonda.

3. **Leia o relatório.** Por arquivo: o que foi escrito, tamanhos antes →
   depois, ou o motivo honesto de nada ter sido escrito (“já existe”, “já
   enxuta”). O total de bytes economizados fica no topo, junto com um trecho
   `<picture>` pronto para copiar que serve o WebP onde há suporte e cai
   para o original nos outros casos.

## O que você aprendeu

- Otimização de imagens para a web sem nenhuma ferramenta obrigatória — e
  com o seu próprio `cwebp` quando você o tiver.
- As leis da família de kits, nunca sobrescrever e relatar com honestidade,
  valem também para pixels.
