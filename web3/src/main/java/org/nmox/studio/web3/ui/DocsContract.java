package org.nmox.studio.web3.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;
import org.nmox.studio.core.process.ProcessSupport;
import org.nmox.studio.core.spi.DocsScene;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Stages the Contract Studio picture (v2.164.0): a Foundry project built
 * for real, and the studio connected to a local chain.
 *
 * <p>The project is the storefront's escrow — its own directory beside the
 * demo shop, because a {@code foundry.toml} makes a project a Foundry
 * project and the other scenes' shop is a web one. It imports nothing, so
 * {@code forge build} compiles offline with whatever solc Foundry already
 * holds; a machine without {@code forge} stages no artifacts and the scene
 * never becomes ready, which skips the picture rather than painting an
 * empty tree. The chain is the forge script's: {@code anvil} on 8545 for
 * the length of the run.
 *
 * <p>Solidity identifiers are code and stay English in every language;
 * the window's own words come from the reader's bundle.
 */
@ServiceProvider(service = DocsScene.class)
public final class DocsContract implements DocsScene {

    /** The scene's name, as its picture is named. */
    public static final String ID = "contract-studio";

    /** The escrow project's directory under the forge home. */
    public static final String PROJECT = "storefront-escrow";

    static final String FOUNDRY_TOML = """
            [profile.default]
            src = "src"
            out = "out"
            offline = true
            """;

    static final String ESCROW = """
            // SPDX-License-Identifier: MIT
            pragma solidity ^0.8.20;

            import {IRefundPolicy} from "./IRefundPolicy.sol";

            /// Holds a customer's payment until the order is delivered.
            contract StorefrontEscrow {
                enum State { Funded, Released, Refunded }

                struct Order { address buyer; uint256 amount; State state; }

                address public immutable seller;
                IRefundPolicy public immutable policy;
                mapping(uint256 => Order) public orders;

                event Funded(uint256 indexed orderId, address indexed buyer, uint256 amount);
                event Released(uint256 indexed orderId, uint256 amount);
                event Refunded(uint256 indexed orderId, uint256 amount);

                error NotSeller();
                error NotFunded(uint256 orderId);

                constructor(address seller_, IRefundPolicy policy_) {
                    seller = seller_;
                    policy = policy_;
                }

                function fund(uint256 orderId) external payable {
                    orders[orderId] = Order(msg.sender, msg.value, State.Funded);
                    emit Funded(orderId, msg.sender, msg.value);
                }

                function release(uint256 orderId) external {
                    if (msg.sender != seller) revert NotSeller();
                    Order storage o = orders[orderId];
                    if (o.state != State.Funded) revert NotFunded(orderId);
                    o.state = State.Released;
                    payable(seller).transfer(o.amount);
                    emit Released(orderId, o.amount);
                }

                function refund(uint256 orderId) external {
                    Order storage o = orders[orderId];
                    if (o.state != State.Funded) revert NotFunded(orderId);
                    require(policy.refundable(orderId, block.timestamp), "outside the refund window");
                    o.state = State.Refunded;
                    payable(o.buyer).transfer(o.amount);
                    emit Refunded(orderId, o.amount);
                }
            }
            """;

    static final String POLICY = """
            // SPDX-License-Identifier: MIT
            pragma solidity ^0.8.20;

            interface IRefundPolicy {
                function refundable(uint256 orderId, uint256 at) external view returns (bool);
            }

            /// Refunds are open for fourteen days after funding.
            contract FourteenDayPolicy is IRefundPolicy {
                mapping(uint256 => uint256) public fundedAt;

                function record(uint256 orderId) external {
                    fundedAt[orderId] = block.timestamp;
                }

                function refundable(uint256 orderId, uint256 at) external view returns (bool) {
                    return at <= fundedAt[orderId] + 14 days;
                }
            }
            """;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public File stage(File home, String fixtures, String lang) throws IOException {
        Path dir = home.toPath().resolve(PROJECT);
        Files.createDirectories(dir.resolve("src"));
        write(dir.resolve("foundry.toml"), FOUNDRY_TOML);
        write(dir.resolve("src/StorefrontEscrow.sol"), ESCROW);
        write(dir.resolve("src/IRefundPolicy.sol"), POLICY);
        if (!Files.isDirectory(dir.resolve("out"))) {
            try {
                ProcessSupport.BoundedResult built = ProcessSupport.runBounded(
                        List.of("forge", "build"), dir.toFile(), Duration.ofSeconds(120));
                if (!built.ok()) {
                    Logger.getLogger(DocsContract.class.getName()).warning(
                            "forge build did not succeed (exit " + built.exitCode() + "): " + built.stderr().strip());
                }
            } catch (IOException ex) {
                Logger.getLogger(DocsContract.class.getName()).warning("forge build could not run: " + ex);
            }
        }
        return dir.toFile();
    }

    @Override
    public void arrange() {
        Web3StudioTopComponent studio = studio();
        if (studio != null) {
            if (!studio.isOpened()) {
                studio.open();
            }
            studio.requestActive();
            studio.docsConnectAndScan();
        }
    }

    @Override
    public boolean ready() {
        Web3StudioTopComponent studio = studio();
        return studio != null && studio.docsReady();
    }

    private static Web3StudioTopComponent studio() {
        TopComponent tc = WindowManager.getDefault().findTopComponent("Web3StudioTopComponent");
        return tc instanceof Web3StudioTopComponent s ? s : null;
    }

    private static void write(Path file, String text) throws IOException {
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
