# Tutorial: el Estudio de contratos (Web3)

<!-- languages -->
[English](contract-studio.md) · **Español** · [Français](contract-studio.fr.md) · [Deutsch](contract-studio.de.md) · [Русский](contract-studio.ru.md) · [Українська](contract-studio.uk.md) · [Polski](contract-studio.pl.md) · [Português (Brasil)](contract-studio.pt.md) · [Bahasa Indonesia](contract-studio.id.md) · [Filipino](contract-studio.tl.md) · [Tiếng Việt](contract-studio.vi.md) · [简体中文](contract-studio.zh.md) · [हिन्दी](contract-studio.hi.md) · [עברית](contract-studio.he.md) · [العربية](contract-studio.ar.md)
<!-- /languages -->

El Estudio de contratos es un banco de trabajo completo para contratos
inteligentes: un árbol de artefactos de Foundry/Hardhat, interacción
guiada por la ABI con retornos y reversiones decodificados, un observador
de bloques y eventos en vivo y un panel de supervisión de gas y tamaño,
con una regla inquebrantable: **ninguna clave privada toca nunca el IDE**.

Este es el recorrido rápido. Para un ejemplo completo —escribir un
contrato de depósito en garantía, probarlo y ejecutarlo contra una cadena
local— consulta
[making-a-smart-contract.md](../making-a-smart-contract.md).

![ANVIL corriendo en el rack y el Estudio de contratos conectado a él por sí solo: cadena 31337, el contrato en el árbol de artefactos con su uso del límite de tamaño EIP-170](../images/contract-studio.png)

## Ábrelo

`⌥⌘6`, o la pestaña **Estudio de contratos**. Te conviene tener Foundry
(`anvil`, `forge`) instalado; compruébalo con
`Herramientas ▸ Doctor del entorno…`.

## Pasos

1. **Arranca una cadena local.** En el rack, monta **ANVIL** y pulsa GO:
   levanta una red de desarrollo EVM local con cuentas desbloqueadas y con
   fondos. El Estudio de contratos se conecta a ella solo.

2. **Compila los artefactos.** En un proyecto Foundry, ejecuta
   `forge build` (el dispositivo **FORGE**, o Compilar del IDE). El árbol
   de artefactos del Estudio de contratos se llena con tus contratos
   compilados.

3. **Despliega e interactúa.** Elige un contrato, pulsa **Desplegar** (usa
   una cuenta desbloqueada de anvil: no tienes que introducir ninguna
   clave) y luego usa el panel **Interactuar**: haz `CALL` a una función
   de lectura y ve el retorno decodificado; haz `SEND` de una transacción
   y sigue el recibo. Las reversiones y los errores personalizados se
   decodifican a texto legible.

4. **Observa la cadena.** El panel **Observar** consulta los bloques
   nuevos cada par de segundos y decodifica los registros de eventos con
   tus ABI. El panel **Supervisión** muestra la tabla de gas, los
   veredictos de tamaño EIP-170 y una libreta de direcciones de
   despliegues.

## Lo que acabas de aprender

- Los envíos pasan por las **cuentas desbloqueadas** de una red de
  desarrollo: el IDE no guarda material de claves ni tiene código de
  firma.
- Las URL de RPC secretas viven solo en el llavero y nunca se serializan.
- Una confirmación protege cualquier envío a un extremo **que no sea
  loopback**, así que no puedes emitir por accidente en una cadena real.

## Siguiente

- El recorrido completo del depósito en garantía:
  [making-a-smart-contract.md](../making-a-smart-contract.md).
- GOVERNOR (la compuerta de gas) y el preajuste Banco Web3 están en el
  rack.
