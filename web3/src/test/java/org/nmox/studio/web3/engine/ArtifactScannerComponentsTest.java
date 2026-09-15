package org.nmox.studio.web3.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A struct's members arrive in the ABI JSON's {@code components}; the
 * scanner must carry them, recursively, or the selector is wrong and the
 * codec cannot lay the value out.
 */
class ArtifactScannerComponentsTest {

    /** Uniswap V3 SwapRouter's exactInputSingle, as a block explorer serves it. */
    private static final String EXACT_INPUT_SINGLE = """
            [{"type": "function", "name": "exactInputSingle", "stateMutability": "payable",
              "inputs": [{"name": "params", "type": "tuple",
                "internalType": "struct ISwapRouter.ExactInputSingleParams",
                "components": [
                  {"name": "tokenIn", "type": "address"},
                  {"name": "tokenOut", "type": "address"},
                  {"name": "fee", "type": "uint24"},
                  {"name": "recipient", "type": "address"},
                  {"name": "deadline", "type": "uint256"},
                  {"name": "amountIn", "type": "uint256"},
                  {"name": "amountOutMinimum", "type": "uint256"},
                  {"name": "sqrtPriceLimitX96", "type": "uint160"}]}],
              "outputs": [{"name": "amountOut", "type": "uint256"}]}]
            """;

    @Test
    @DisplayName("components parse into the param and the selector matches the deployed router")
    void componentsParsed() {
        List<AbiEntry> abi = ArtifactScanner.parseAbiJson(EXACT_INPUT_SINGLE);
        AbiParam params = abi.get(0).inputs().get(0);
        assertThat(params.components()).extracting(AbiParam::name).containsExactly(
                "tokenIn", "tokenOut", "fee", "recipient", "deadline", "amountIn",
                "amountOutMinimum", "sqrtPriceLimitX96");
        assertThat(Hex.toHex(Keccak256.selector(abi.get(0).signature()))).isEqualTo("414bf389");
    }

    @Test
    @DisplayName("nested tuple arrays keep their components at every level")
    void nestedComponents() {
        String json = """
                [{"type": "event", "name": "Batch", "inputs": [
                  {"name": "b", "type": "tuple[]", "indexed": false, "components": [
                    {"name": "id", "type": "uint256"},
                    {"name": "legs", "type": "tuple[2]", "components": [
                      {"name": "to", "type": "address"}]}]}]}]
                """;
        AbiEntry batch = ArtifactScanner.parseAbiJson(json).get(0);
        assertThat(batch.signature()).isEqualTo("Batch((uint256,(address)[2])[])");
    }

    @Test
    @DisplayName("components nested past the depth cap are dropped, and the codec refuses by name")
    void depthCapped() {
        StringBuilder json = new StringBuilder();
        int levels = AbiLiteral.MAX_DEPTH + 5;
        for (int i = 0; i < levels; i++) {
            json.append("{\"name\": \"l").append(i).append("\", \"type\": \"tuple\", \"components\": [");
        }
        json.append("{\"name\": \"leaf\", \"type\": \"uint256\"}");
        json.append("]}".repeat(levels));
        List<AbiEntry> abi = ArtifactScanner.parseAbiJson(
                "[{\"type\": \"function\", \"name\": \"deep\", \"inputs\": [" + json + "]}]");
        AbiEntry deep = abi.get(0);
        assertThatThrownBy(() -> AbiCodec.encodeCall(deep, List.of("[]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".l32#1 is a tuple, but this ABI lists none of its components");
    }
}
