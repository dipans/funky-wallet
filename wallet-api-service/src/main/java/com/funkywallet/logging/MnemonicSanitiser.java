package com.funkywallet.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

public class MnemonicSanitiser extends ClassicConverter {

    // Matches 12–24 consecutive lowercase words (BIP-39 mnemonic pattern)
    private static final Pattern MNEMONIC = Pattern.compile(
        "\\b([a-z]+(?:\\s+[a-z]+){11,23})\\b"
    );

    @Override
    public String convert(ILoggingEvent event) {
        String msg = event.getFormattedMessage();
        if (msg == null) return "";
        return MNEMONIC.matcher(msg).replaceAll("[MNEMONIC REDACTED]");
    }
}
