/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2021 adorsys GmbH & Co. KG @ https://adorsys.com
 * ---
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---license-end
 */

package de.adorsys.keycloak.config;

import de.adorsys.keycloak.config.model.KeycloakImport;
import de.adorsys.keycloak.config.model.RealmImport;
// FAST project custom extend about P2-Inc org plugin: Start
import de.adorsys.keycloak.config.phasetwo.properties.P2ImportConfigProperties;
import de.adorsys.keycloak.config.phasetwo.service.P2ImportService;
// FAST project custom extend: End
import de.adorsys.keycloak.config.properties.ImportConfigProperties;
import de.adorsys.keycloak.config.properties.KeycloakConfigProperties;
import de.adorsys.keycloak.config.provider.KeycloakImportProvider;
import de.adorsys.keycloak.config.service.RealmImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// FAST project custom extend about P2-Inc org plugin: Start
import org.springframework.beans.factory.ObjectProvider;
// FAST project custom extend: End
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
/*
 * Spring only considers actual properties set, not default values of @ConfigurationProperties classes.
 * Therefore, we enable matchIfMissing here, so if there is *no* property set, we consider it an import
 * for backwards compatibility
 */
@ConditionalOnProperty(prefix = "run", name = "operation", havingValue = "IMPORT", matchIfMissing = true)
// FAST project custom extend about P2-Inc org plugin: Start
@EnableConfigurationProperties({
        ImportConfigProperties.class,
        KeycloakConfigProperties.class,
        P2ImportConfigProperties.class
})
public class KeycloakConfigRunner implements CommandLineRunner, ExitCodeGenerator {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakConfigRunner.class);
    private static final long START_TIME = System.currentTimeMillis();

    private final KeycloakImportProvider keycloakImportProvider;
    private final RealmImportService realmImportService;
    private final ImportConfigProperties importConfigProperties;
    private final P2ImportConfigProperties p2ImportConfigProperties;
    private final ObjectProvider<P2ImportService> p2ImportServiceProvider;

    private int exitCode = 0;

    @Autowired
    public KeycloakConfigRunner(
            KeycloakImportProvider keycloakImportProvider,
            RealmImportService realmImportService,
            ImportConfigProperties importConfigProperties,
            P2ImportConfigProperties p2ImportConfigProperties,
            ObjectProvider<P2ImportService> p2ImportServiceProvider) {
        this.keycloakImportProvider = keycloakImportProvider;
        this.realmImportService = realmImportService;
        this.importConfigProperties = importConfigProperties;
        this.p2ImportConfigProperties = p2ImportConfigProperties;
        this.p2ImportServiceProvider = p2ImportServiceProvider;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    @Override
    public void run(String... args) {
        try {
            Collection<String> importLocations = importConfigProperties.getFiles().getLocations();

            if (p2ImportConfigProperties.isEnabled()) {
                P2ImportService p2ImportService = p2ImportServiceProvider.getIfAvailable();
                if (p2ImportService == null) {
                    throw new IllegalStateException("P2 import is enabled but P2 services are not available in this runtime context.");
                }
                p2ImportService.run(importLocations);
                return;
            }
            // FAST project custom extend: End

            KeycloakImport keycloakImport = keycloakImportProvider.readFromLocations(importLocations);

            Map<String, Map<String, List<RealmImport>>> realmImports = keycloakImport.getRealmImports();

            for (Map<String, List<RealmImport>> realmImportLocations : realmImports.values()) {
                for (Map.Entry<String, List<RealmImport>> realmImport : realmImportLocations.entrySet()) {
                    logger.info("Importing file '{}'", realmImport.getKey());
                    for (RealmImport realmImportParts : realmImport.getValue()) {
                        realmImportService.doImport(realmImportParts);
                    }
                }
            }
        } catch (NullPointerException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error during Keycloak import: {}", e.getMessage(), e);
            if (e.getCause() instanceof Exception cause) {
                try {
                    String responseBody = cause.toString();
                    logger.error("Error Response: {}", responseBody);
                } catch (Exception ex) {
                    logger.error("Failed to read error response", ex);
                }
            }

            exitCode = 1;

            if (logger.isDebugEnabled()) {
                throw e;
            }
        } finally {
            long totalTime = System.currentTimeMillis() - START_TIME;
            String formattedTime = new SimpleDateFormat("mm:ss.SSS").format(new Date(totalTime));
            logger.info("keycloak-config-cli ran in {}.", formattedTime);
        }
    }
}
