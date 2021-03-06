package com.quorum.tessera.partyinfo;


import com.quorum.tessera.loader.ServiceLoaderUtil;
import com.quorum.tessera.encryption.PublicKey;
import com.quorum.tessera.partyinfo.model.PartyInfo;
import com.quorum.tessera.partyinfo.model.Recipient;

import java.net.URI;

public interface PartyInfoStore {


    static PartyInfoStore create(URI uri) {
        return ServiceLoaderUtil.load(PartyInfoStore.class)
            .orElse(new PartyInfoStoreImpl(uri));
    }

    PartyInfo getPartyInfo();

    void store(PartyInfo incomingInfo);

    Recipient findRecipientByPublicKey(PublicKey from);

    String getAdvertisedUrl();

    PartyInfo removeRecipient(String s);
}


