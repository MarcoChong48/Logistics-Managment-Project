package com.lms.backend.client.account;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.login.SharedCurrentUserReader;

@Service
class ClientAccountService {

    private final SharedCurrentUserReader currentUser;
    private final ClientAccountRepository accounts;

    ClientAccountService(SharedCurrentUserReader currentUser, ClientAccountRepository accounts) {
        this.currentUser = currentUser;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public ClientAccountResponse account() {
        return accounts.findAccount(currentUser.sharedReadCurrentUser().userID())
                .orElseThrow(() -> new SharedRuleException(HttpStatus.UNAUTHORIZED, "SHARED_SESSION_ENDED",
                        "Your session has ended. Please log in again."));
    }

    @Transactional(readOnly = true)
    public ClientDeliveryDefaults deliveryDefaults() {
        return accounts.findDefaults(currentUser.sharedReadCurrentUser().userID())
                .orElse(ClientDeliveryDefaults.EMPTY);
    }

    @Transactional
    public ClientDeliveryDefaults replaceDeliveryDefaults(ClientDeliveryDefaults defaults) {
        return accounts.saveDefaults(currentUser.sharedReadCurrentUser().userID(), defaults);
    }
}
