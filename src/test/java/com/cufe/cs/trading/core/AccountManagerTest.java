package com.cufe.cs.trading.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountManagerTest {

    @Test
    void reserveAndReleaseCash() {
        Account account = new Account("a", 1000);
        assertTrue(account.reserveCash(400));
        assertEquals(600, account.getCash(), 0.001);
        assertFalse(account.reserveCash(601));
        account.releaseCash(400);
        assertEquals(1000, account.getCash(), 0.001);
    }

    @Test
    void reserveAndReleaseShares() {
        Account account = new Account("a", 0, Map.of("AAPL", 100));
        assertTrue(account.reserveShares("AAPL", 60));
        assertEquals(40, account.getHolding("AAPL"));
        assertFalse(account.reserveShares("AAPL", 41));
        account.releaseShares("AAPL", 60);
        assertEquals(100, account.getHolding("AAPL"));
    }

    @Test
    void computesEquityAndReturnRate() {
        Account account = new Account("a", 1000, Map.of("AAPL", 10));
        double equity = account.computeEquity(Map.of("AAPL", 175.50));
        assertEquals(2755.0, equity, 0.01);
        double rate = account.computeReturnRate(Map.of("AAPL", 175.50));
        assertEquals(1.755, rate, 0.001);
    }

    @Test
    void accountManagerRequiresUnknownThrows() {
        AccountManager manager = new AccountManager();
        assertThrows(IllegalArgumentException.class, () -> manager.require("nobody"));
    }

    @Test
    void accountManagerRegistersAndFinds() {
        AccountManager manager = new AccountManager();
        Account account = new Account("humanA", 5000);
        manager.register(account);
        assertEquals(account, manager.get("humanA"));
        assertEquals(1, manager.size());
    }
}
