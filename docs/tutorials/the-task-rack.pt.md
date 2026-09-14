# Tutorial: o Rack de tarefas

<!-- languages -->
[English](the-task-rack.md) · [Español](the-task-rack.es.md) · [Français](the-task-rack.fr.md) · [Deutsch](the-task-rack.de.md) · [Русский](the-task-rack.ru.md) · [Українська](the-task-rack.uk.md) · [Polski](the-task-rack.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](the-task-rack.id.md) · [Filipino](the-task-rack.tl.md) · [Tiếng Việt](the-task-rack.vi.md) · [简体中文](the-task-rack.zh.md) · [हिन्दी](the-task-rack.hi.md) · [עברית](the-task-rack.he.md) · [العربية](the-task-rack.ar.md)
<!-- /languages -->

O Rack de tarefas é a ideia que define o NMOX Studio: suas ferramentas de
compilar, testar e servir dispostas como um rack de aparelhos que você liga
uns aos outros com cabos de patch. Um dispositivo roda um comando de
verdade; um cabo leva um sinal de verdade. Este tutorial monta uma ligação
mínima — rodar algo e acender um indicador quando terminar — para a
metáfora fazer sentido.

![O rack apontado para um projeto real — dispositivos montados e rodando](../images/task-rack.png)

![Tab vira o rack — os cabos de patch ligam os dispositivos na traseira](../images/rack-rear.png)

## Antes de começar

Abra um projeto (qualquer projeto Node serve; `Arquivo ▸ Novo projeto…` →
“Vanilla JS” se você precisar de um). Abrir um projeto **aponta** o rack
para ele, então cada dispositivo roda no diretório desse projeto.

## Passos

1. **Abra o rack.** Clique na aba **Rack de tarefas** (ou aperte `⌘9`). O
   rack inicial tem um único **MONITOR** — o dispositivo de console que
   mostra a saída dos comandos e as linhas de erro.

2. **Acrescente um executor.** Arraste o **IGNITION** da paleta à esquerda
   para a prateleira. O IGNITION é o dispositivo poliglota de “executar”;
   apontado para um projeto Node, ele roda `npm run dev` (detecta sozinho o
   seu gerenciador de pacotes e a cadeia de ferramentas).

3. **Ligue-o ao monitor.** Use o controle de **virar** para ver a traseira,
   depois clique na entrada **OUT** do IGNITION e na entrada **TAP** do
   MONITOR — um cabo de patch liga os dois. (Arrastar entre as entradas
   também funciona; clicar é mais fácil quando o rack é largo.)

4. **Dispare.** Vire o rack de volta para a frente e aperte o botão **GO**
   do IGNITION. Ele inicia o processo; a saída corre para o MONITOR e os
   LEDs de estado acendem. Se o projeto ainda não for confiável, antes vem
   um aviso único de Confiança no espaço de trabalho — é a proteção que
   impede um repositório clonado de rodar os scripts dele sem o seu aval.

5. **Salve a ligação.** `⌘S` (ou o botão **Salvar patch**) grava o
   `.nmoxrack.json` ao lado do seu projeto. Reabra o projeto depois e a
   ligação — dispositivos, cabos, posições dos botões — volta exatamente
   como estava.

## O que você aprendeu

- **Dispositivos são ferramentas com painel frontal.** Os botões giratórios
  escolhem opções, os botões GO executam, LEDs e visores mostram o estado —
  e todo controle é de verdade (nenhum botão morto; um teste de contrato
  garante isso).
- **Os cabos coordenam as trilhas.** OUT→TAP é a ligação mais simples;
  barreiras de prontidão (`ENABLE`), pontos de encontro (`QUORUM`) e cabos
  de gatilho deixam você compor um pipeline inteiro que reage a si mesmo.
- **Tudo fica salvo.** A ligação é um arquivo que se versiona; o rack até
  ressuscita uma sessão em andamento depois de uma queda.

## Próximos passos

- São 53 dispositivos — navegue por eles em [devices.md](../devices.md) ou
  nos cartões de uso da paleta.
- Peça ao [KVASIR](kvasir.pt.md) para explicar uma execução que falhou.
- Exporte uma ligação para um workflow do GitHub Actions: a **exportação
  para CI** do rack.
