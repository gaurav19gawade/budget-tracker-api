package com.budgettracker.infrastructure.simplefin;

import com.budgettracker.application.port.BankDataProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SimpleFinBankDataProvider implements BankDataProvider {

    private final SimpleFinClient client;

    public SimpleFinBankDataProvider(SimpleFinClient client) {
        this.client = client;
    }

    @Override
    public String claim(String setupToken) {
        return client.claimAccessUrl(setupToken);
    }

    @Override
    public List<ProviderAccount> fetchAccounts(String accessCredential) {
        return client.fetchAccounts(accessCredential);
    }
}
