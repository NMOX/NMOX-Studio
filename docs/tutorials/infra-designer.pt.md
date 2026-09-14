# Tutorial: Designer de infraestrutura

<!-- languages -->
[English](infra-designer.md) · [Español](infra-designer.es.md) · [Français](infra-designer.fr.md) · [Deutsch](infra-designer.de.md) · [Русский](infra-designer.ru.md) · [Українська](infra-designer.uk.md) · [Polski](infra-designer.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](infra-designer.id.md) · [Filipino](infra-designer.tl.md) · [Tiếng Việt](infra-designer.vi.md) · [简体中文](infra-designer.zh.md) · [हिन्दी](infra-designer.hi.md) · [עברית](infra-designer.he.md) · [العربية](infra-designer.ar.md)
<!-- /languages -->

O Designer de infraestrutura é uma tela no estilo do Node-RED para
infraestrutura de nuvem. Você arrasta nós (droplets, firewalls, registros
DNS…), liga uns aos outros e implanta na DigitalOcean, na Hetzner ou na
Cloudflare — vendo o custo antes de gastar qualquer coisa. Este tutorial
monta um plano e o simula, então nenhum dinheiro sai do lugar.

![Uma pilha tomando forma — DNS, balanceador de carga, droplet e um volume com a folha de propriedades; a barra de ferramentas calcula o preço do desenho ao vivo e deixa claro que está em modo de simulação](../images/infra-designer.png)

## Abrir

`⌥⌘9`, ou a aba **Designer de infraestrutura**.

## Passos

1. **Solte um servidor.** Arraste um nó **Droplet** da paleta para a tela.
   A folha de propriedades à direita permite escolher região, tamanho e
   imagem. Uma estimativa de custo se atualiza conforme você escolhe.

2. **Acrescente um firewall.** Arraste um nó **Firewall** e ligue-o ao
   droplet arrastando entre as portas dos dois. Defina uma regra de entrada
   (por exemplo, liberar 22 e 443).

3. **Acrescente cloud-init (opcional).** No campo `user_data` do droplet,
   cole um script cloud-init curto — ele roda na primeira inicialização.

4. **Simule a implantação.** Aperte o botão vermelho **IMPLANTAR**. Sem um
   token de nuvem tudo fica numa **simulação**: você vê o plano exato e
   ordenado de chamadas de API (criar firewall, criar droplet, anexar…) e o
   custo, mas nada é criado. O registro da implantação mostra cada passo.

5. **Vá para valer (quando estiver pronto).** Acrescente o token de um
   provedor com **Tokens…** (ou em Opções ▸ Rack e nuvem; guardado no
   chaveiro do sistema operacional), e o
   IMPLANTAR executa o plano de verdade, resolvendo as referências entre nós
   (o IP de um droplet chega ao registro DNS) à medida que os recursos
   sobem.

## O que você aprendeu

- A tela é um grafo de dependências de verdade; o planejador ordena as
  chamadas de API e passa ids e IPs de um passo para o outro.
- Os diálogos destrutivos (Destruir a pilha, Destruir recurso, Implantar)
  deixam o Enter no botão **seguro** — um toque de reflexo não apaga um
  recurso cobrado.
- Os recursos vivos podem ser **sincronizados** de volta e ter a deriva
  atualizada; o plano fica salvo em `.nmoxinfra.json`.

## Próximos passos

- Copie o comando SSH de um nó direto da tela.
- Várias nuvens: a mesma tela comanda DO, Hetzner e Cloudflare.
