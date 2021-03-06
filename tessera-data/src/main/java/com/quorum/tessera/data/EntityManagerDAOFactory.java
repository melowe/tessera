package com.quorum.tessera.data;

import com.quorum.tessera.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EntityManagerDAOFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityManagerDAOFactory.class);

    private final EntityManagerFactory entityManagerFactory;

    private EntityManagerDAOFactory(
            EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = Objects.requireNonNull(entityManagerFactory);
    }

    public static EntityManagerDAOFactory newFactory(Config config) {
        LOGGER.debug("New EntityManagerDAOFactory from {}", config);
        final String username = config.getJdbcConfig().getUsername();
        final String password = config.getJdbcConfig().getPassword();
        final String url = config.getJdbcConfig().getUrl();

        Map properties = new HashMap();
        properties.put("javax.persistence.jdbc.url", url);
        properties.put("javax.persistence.jdbc.user", username);
        properties.put("javax.persistence.jdbc.password", password);
        properties.put("eclipselink.logging.logger", "org.eclipse.persistence.logging.slf4j.SLF4JLogger");
        properties.put("eclipselink.logging.level", "FINE");
        properties.put("eclipselink.logging.parameters", "true");
        properties.put("eclipselink.logging.level.sql", "FINE");
        properties.put(
                "javax.persistence.schema-generation.database.action",
                config.getJdbcConfig().isAutoCreateTables() ? "create" : "none");

        LOGGER.debug("Creating EntityManagerFactory from {}", properties);
        final EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("tessera", properties);
        LOGGER.debug("Created EntityManagerFactory from {}", properties);

        return new EntityManagerDAOFactory(entityManagerFactory);
    }

    public EncryptedTransactionDAO createEncryptedTransactionDAO() {
        LOGGER.debug("Create EncryptedTransactionDAO");
        return new EncryptedTransactionDAOImpl(entityManagerFactory);
    }

    public EncryptedRawTransactionDAO createEncryptedRawTransactionDAO() {
        LOGGER.debug("Create EncryptedRawTransactionDAO");
        return new EncryptedRawTransactionDAOImpl(entityManagerFactory);
    }

}
