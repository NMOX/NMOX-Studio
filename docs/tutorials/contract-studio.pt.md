# Tutorial: Estúdio de contratos (Web3)

<!-- languages -->
[English](contract-studio.md) · [Español](contract-studio.es.md) · [Français](contract-studio.fr.md) · [Deutsch](contract-studio.de.md) · [Русский](contract-studio.ru.md) · [Українська](contract-studio.uk.md) · [Polski](contract-studio.pl.md) · **Português (Brasil)** · [Bahasa Indonesia](contract-studio.id.md) · [Filipino](contract-studio.tl.md) · [Tiếng Việt](contract-studio.vi.md) · [简体中文](contract-studio.zh.md) · [हिन्दी](contract-studio.hi.md) · [עברית](contract-studio.he.md) · [العربية](contract-studio.ar.md)
<!-- /languages -->

O Estúdio de contratos é uma bancada completa de smart contracts: uma
árvore de artefatos do Foundry/Hardhat, interação guiada pela ABI com
retornos e reversões decodificados, um observador ao vivo de blocos e
eventos, e um painel de supervisão de gás e tamanho — com uma regra firme:
**nenhuma chave privada jamais toca a IDE**.

Este é o passeio rápido. Para um exemplo completo — escrever um contrato de
escrow, testá-lo e rodá-lo contra uma cadeia local — veja
[making-a-smart-contract.md](../making-a-smart-contract.md).

![ANVIL rodando no rack e o Estúdio de contratos conectado a ele sozinho — chain 31337, o contrato na árvore de artefatos com o uso de tamanho EIP-170](../images/contract-studio.png)

## Abrir

`⌥⌘6`, ou a aba **Estúdio de contratos**. Você vai querer o Foundry
(`anvil`, `forge`) instalado; confira com
`Ferramentas ▸ Doutor do ambiente…`.

## Passos

1. **Suba uma cadeia local.** No rack, monte o **ANVIL** e aperte GO — ele
   roda uma devnet EVM local com contas destravadas e com saldo. O Estúdio
   de contratos se conecta a ela sozinho.

2. **Compile os artefatos.** Num projeto Foundry, rode `forge build` (o
   dispositivo **FORGE**, ou o Compilar da IDE). A árvore de artefatos do
   Estúdio de contratos se enche com os seus contratos compilados.

3. **Implante e interaja.** Escolha um contrato, aperte **Implantar** (ele
   usa uma conta destravada do anvil — nenhuma chave digitada) e depois use
   o painel **Interagir**: `CALL` numa função de leitura mostra o retorno
   decodificado; `SEND` numa transação mostra o recibo. Reversões e erros
   personalizados são decodificados em texto legível.

4. **Observe a cadeia.** O painel **Observar** consulta novos blocos a cada
   par de segundos e decodifica os logs de eventos com as suas ABIs. O
   painel **Supervisão** mostra a tabela de gás, os veredictos de tamanho
   EIP-170 e um catálogo de endereços das implantações.

## O que você aprendeu

- Os envios passam pelas **contas destravadas** de uma devnet — a IDE não
  guarda material de chave nem tem código de assinatura.
- As URLs de RPC secretas ficam só no chaveiro e nunca são serializadas.
- Uma confirmação protege qualquer envio para um endpoint **fora da
  interface local**, então você não transmite para uma cadeia real por
  acidente.

## Próximos passos

- O passo a passo completo do escrow:
  [making-a-smart-contract.md](../making-a-smart-contract.md).
- O GOVERNOR (limite de gás) e a predefinição Web3 Bench ficam no rack.
