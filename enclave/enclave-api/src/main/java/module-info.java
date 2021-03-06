module tessera.enclave.enclave.api.main {
    requires org.slf4j;
    requires tessera.config.main;
    requires tessera.encryption.encryption.api.main;
    requires tessera.key.vault.key.vault.api.main;
    requires tessera.shared.main;

    exports com.quorum.tessera.enclave;
}
