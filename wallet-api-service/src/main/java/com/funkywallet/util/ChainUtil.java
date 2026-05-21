package com.funkywallet.util;

import java.util.Set;

public final class ChainUtil {

    private static final Set<Integer> EVM_MAINNET   = Set.of(1, 137, 56, 42161, 10, 8453);
    private static final Set<Integer> EVM_TESTNET   = Set.of(560048, 11155111, 80001, 421613);
    private static final Set<Integer> EVM_LOCAL      = Set.of(1337, 31337);

    private ChainUtil() {}

    public static String deriveEnvironment(String networkType, int chainId) {
        return switch (networkType) {
            case "EVM" -> {
                if (EVM_MAINNET.contains(chainId)) yield "MAINNET";
                if (EVM_TESTNET.contains(chainId)) yield "TESTNET";
                if (EVM_LOCAL.contains(chainId))   yield "LOCAL";
                yield "DEVNET";
            }
            case "SOLANA" -> chainId == 1 ? "MAINNET" : "DEVNET";
            case "BITCOIN" -> chainId == 1 ? "MAINNET" : "TESTNET";
            default -> "LOCAL";
        };
    }
}
