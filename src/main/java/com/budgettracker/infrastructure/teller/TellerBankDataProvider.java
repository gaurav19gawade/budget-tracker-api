package com.budgettracker.infrastructure.teller;

import com.budgettracker.application.port.BankDataProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TellerBankDataProvider implements BankDataProvider {

    private final TellerClient client;

    public TellerBankDataProvider(TellerClient client) {
        this.client = client;
    }

    @Override
    public List<ProviderAccount> fetchAccounts(String accessToken) {
        return client.fetchAccounts(accessToken);
    }

    @Override
    public ProviderBalance fetchBalance(String accessToken, String accountId) {
        return client.fetchBalance(accessToken, accountId);
    }
}
