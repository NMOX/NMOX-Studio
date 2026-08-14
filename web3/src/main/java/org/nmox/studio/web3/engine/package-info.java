/**
 * Contract Studio's chain plumbing: {@code JsonRpcClient} (a Transport
 * seam over HTTP JSON-RPC with URL redaction and capped reads),
 * {@code Keccak256} and {@code AbiCodec} (hand-rolled, pinned to the
 * Solidity spec's own test vectors), artifact scanning for
 * Foundry/Hardhat outputs, gas reports, and EIP-170 size checks.
 *
 * <p>The boundary that defines the module: <b>no private keys,
 * ever</b> — sends go through devnet unlocked accounts, secret RPC
 * URLs live in the OS keyring, and the tests pin the boundary
 * structurally. If you extend this package, that invariant is the
 * first thing your review will check.
 */
package org.nmox.studio.web3.engine;
