package com.cufe.cs.trading.io;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigLoaderTest {

    @Test
    void loadsDefaultClasspathConfig() {
        ConfigLoader config = new ConfigLoader();
        assertEquals(List.of("AAPL", "GOOGL", "TSLA"), config.getStockSymbols());
        assertEquals(175.50, config.getInitialPrices().get("AAPL"), 0.001);
        assertEquals(2, config.getHumanCount());
        assertEquals(3, config.getAiCount());
    }

    @Test
    void loadsFromCustomFile() throws Exception {
        Path path = Files.createTempFile("config", ".properties");
        Files.writeString(path, "stocks=IBM,MSFT\nIBM=100.0\nMSFT=200.0\nagent.human.count=5\n");
        try {
            ConfigLoader config = new ConfigLoader(path);
            assertEquals(List.of("IBM", "MSFT"), config.getStockSymbols());
            assertEquals(100.0, config.getInitialPrices().get("IBM"), 0.001);
            assertEquals(5, config.getHumanCount());
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void resolveEnvReplacesKnownAndDropsUnknown() {
        assertEquals("hello", ConfigLoader.resolveEnv("hello"));
        assertEquals("", ConfigLoader.resolveEnv("${THIS_ENV_SHOULD_NOT_EXIST_12345}"));
    }
}
