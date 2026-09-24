# Início rápido: cinco minutos até o seu projeto rodar

<!-- languages -->
[English](quickstart.md) · [Español](quickstart.es.md) · [Français](quickstart.fr.md) · [Deutsch](quickstart.de.md) · [Русский](quickstart.ru.md) · [Українська](quickstart.uk.md) · [Polski](quickstart.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](quickstart.id.md) · [Filipino](quickstart.tl.md) · [Tiếng Việt](quickstart.vi.md) · [简体中文](quickstart.zh.md) · [हिन्दी](quickstart.hi.md) · [עברית](quickstart.he.md) · [العربية](quickstart.ar.md)
<!-- /languages -->

Esta página põe um projeto seu para rodar dentro do NMOX Studio. Ela
cobre só o que você precisa para isso. [O guia do usuário](user-guide.pt.md)
é o manual completo. Se você usa o VS Code, leia
[Vindo do VS Code](coming-from-vscode.pt.md) em seguida.

<a id="1-install-one-minute"></a>
## 1. Instalar (um minuto)

**macOS, com o Homebrew:**

```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

O Homebrew pede que você rode `brew trust` uma única vez para qualquer tap
de terceiros. Ele não pergunta de novo quando você atualiza.

**macOS, Windows e Linux, sem o Homebrew:** baixe a última versão para o
seu sistema na
[página de versões](https://github.com/NMOX/NMOX-Studio/releases/latest):

| Sistema | Arquivo | Depois |
|---|---|---|
| macOS | `NMOX-Studio-<version>-macos.dmg` | Arraste o aplicativo para Aplicativos. |
| Windows | `NMOX-Studio-<version>-windows-setup.exe` | Rode o instalador. |
| Debian, Ubuntu | `nmox-studio_<version>_amd64.deb` | `sudo apt install ./nmox-studio_<version>_amd64.deb` |
| Outros Linux | `NMOX-Studio-<version>-linux.tar.gz` | Descompacte e rode `bin/nmoxstudio`. |

Cada um desses arquivos traz o próprio ambiente de execução do Java, então
não há mais nada para instalar. Só o zip portátil precisa de Java 21 ou
mais novo já instalado na máquina.

No macOS, o aplicativo é notarizado pela Apple. Na primeira vez que você o
abre, o macOS pergunta se deve abrir um aplicativo baixado da internet:
clique em **Abrir**.

<a id="2-open-your-project-one-minute"></a>
## 2. Abrir o seu projeto (um minuto)

Abra o **NMOX Studio**. Ele abre três abas: **Bem-vindo**, **Rack de
tarefas** e **Navegador web**.

Para abrir o seu projeto, escolha **Arquivo ▸ Abrir pasta…** (⌥⌘O no
macOS, Ctrl+Alt+O no Windows e no Linux) e selecione a pasta dele. Você
também pode fazer isso pelo terminal, do jeito que faria com `code .`:

```bash
cd ~/code/my-app
nmox .
```

O comando volta na hora. Se o NMOX Studio já está rodando, ele recebe a
pasta; se não, ele inicia. O Homebrew, o instalador do Windows e os pacotes
do Linux põem o `nmox` no seu PATH. Para uma instalação pelo DMG, veja
[como pôr o `nmox` no seu PATH](user-guide.pt.md#2-first-launch).

Uma pasta conta como projeto se tiver um `package.json`, `Cargo.toml`,
`go.mod`, `pom.xml`, `composer.json`, `pyproject.toml` ou um dos outros 57
arquivos de projeto. Uma pasta com arquivos HTML simples também conta.

Três coisas acontecem quando você abre um projeto:

- O **Estúdio de projeto**, à esquerda, mostra os seus arquivos.
- A barra de status, embaixo, mostra o seu ramo do git e o número de
  arquivos alterados.
- O **Rack de tarefas** é montado para o tipo de projeto que ele é. Um
  projeto Vite ganha um console Vite, um projeto Cargo ganha trilhas de
  executar, depurar e testar, e assim por diante.

<a id="3-run-it-one-minute"></a>
## 3. Executar (um minuto)

Aperte **▶** na barra de ferramentas, ou F6. Ele executa o seu projeto do
jeito que as ferramentas dele executam: o script `dev`, `start` ou `serve`
do `package.json`, `cargo run`, `go run`. Ele usa o gerenciador de pacotes
do próprio projeto: npm, pnpm ou yarn, ou bun num projeto Bun.

Na primeira vez que você executa qualquer coisa num projeto, o NMOX Studio
pergunta se você confia na pasta. Um projeto em que você não confiou não
roda nada do próprio código: nem scripts, nem builds, nem testes. Clique em
**Confiar no espaço de trabalho** para o seu próprio código.

Se o seu projeto é um servidor de desenvolvimento, o endereço dele aparece
na barra de status ao lado de um símbolo **⇄**, e a página abre na aba
**Navegador web**. Edite um arquivo e salve, e a página recarrega.

Para parar tudo que está rodando, aperte **■** ao lado do ▶, ou ⌥⌘. (Option,
Command e ponto).

Se nada acontecer, olhe a aba **Output** embaixo. Ela explica por que a
execução não conseguiu começar, por exemplo porque uma ferramenta não está
instalada ou as dependências ainda não foram instaladas, e se oferece para
resolver. **Ferramentas ▸ Doutor do ambiente…** lista todas as ferramentas
que o NMOX Studio sabe usar e mostra quais estão instaladas.

<a id="4-find-anything-thirty-seconds"></a>
## 4. Encontrar qualquer coisa (trinta segundos)

Aperte **⌘I** (Ctrl+I no Windows e no Linux) e digite. A Pesquisa rápida
encontra arquivos, ações de menu, símbolos, dispositivos do rack,
servidores e comandos em execução, e os scripts do seu `package.json`.
Aperte Enter para abrir ou executar o resultado.

Aperte **⌘P** para abrir um arquivo pelo nome.

<a id="5-test-it-thirty-seconds"></a>
## 5. Testar (trinta segundos)

Aperte **⌃F6** (Ctrl+F6) para rodar os testes do seu projeto. Para ver
todos os testes do projeto antes de rodar qualquer um, abra a janela
**Testes** com ⌥⌘2.

<a id="if-you-have-no-project-handy"></a>
## Se você não tem um projeto à mão

- **Arquivo ▸ Novo projeto…** cria um projeto de verdade a partir de um
  modelo (Angular, Vue, Svelte, React com Vite, JavaScript puro, PHP,
  Phoenix e outros). Ele cria os arquivos, prepara o git e instala as
  dependências.
- **Arquivo ▸ Novo espaço de aprendizado…** abre um tutorial guiado. *Sua
  primeira página web* é o primeiro da lista.

<a id="where-to-go-next"></a>
## Para onde ir depois

- **[O Rack de tarefas](user-guide.pt.md#4-the-task-rack)**. Cada ferramenta
  que você roda é um dispositivo no rack, e cabos entre os dispositivos os
  encadeiam: por exemplo, rodar os testes sempre que o build passa.
- **[O editor](user-guide.pt.md#5-the-editor)**. Inclui Emmet, amostras de
  cor, depuração com pontos de interrupção para Node e Chrome, e modelos do
  Angular.
- **[Os estúdios](user-guide.pt.md#6-the-studios)**. Os estúdios de API,
  de banco de dados, de contratos e de blocos, e o Quadro de tarefas.
- **[O glossário](glossary.pt.md)** explica as palavras próprias do
  produto: rack, patch, jack, trilha, apontar, KVASIR.
