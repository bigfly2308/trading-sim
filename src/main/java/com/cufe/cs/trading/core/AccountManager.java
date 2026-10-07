package com.cufe.cs.trading.core;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 账户注册表：按 agentId 管理多个交易账户。 */
public class AccountManager {
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public void register(Account account) {
        accounts.put(account.getAgentId(), account);
    }

    public Account get(String agentId) {
        return accounts.get(agentId);
    }

    /** 获取账户；不存在则抛出异常。 */
    public Account require(String agentId) {
        Account account = accounts.get(agentId);
        if (account == null) {
            throw new IllegalArgumentException("未知账户: " + agentId);
        }
        return account;
    }

    public Collection<Account> all() {
        return accounts.values();
    }

    public int size() {
        return accounts.size();
    }
}
