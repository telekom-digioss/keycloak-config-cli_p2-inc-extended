/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2026 Deutsche Telekom IT GmbH & https://www.telekom.de
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

import de.adorsys.keycloak.config.phasetwo.properties.P2ImportConfigProperties;
import de.adorsys.keycloak.config.phasetwo.service.P2ImportService;
import de.adorsys.keycloak.config.properties.ImportConfigProperties;
import de.adorsys.keycloak.config.provider.KeycloakImportProvider;
import de.adorsys.keycloak.config.service.RealmImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class P2_KeycloakConfigRunnerTest {

    @Mock
    private KeycloakImportProvider keycloakImportProvider;

    @Mock
    private RealmImportService realmImportService;

    @Mock
    private ImportConfigProperties importConfigProperties;

    @Mock
    private ImportConfigProperties.ImportFilesProperties importFilesProperties;

    @Mock
    private P2ImportService p2ImportService;

    @Mock
    private ObjectProvider<P2ImportService> p2ImportServiceProvider;

    @Test
    void shouldDelegateToP2ImportServiceInP2ImportMode() {
        P2ImportConfigProperties p2ImportConfigProperties = new P2ImportConfigProperties(true);

        when(importConfigProperties.getFiles()).thenReturn(importFilesProperties);
        when(importFilesProperties.getLocations()).thenReturn(List.of("classpath:P2_*.json"));
        when(p2ImportServiceProvider.getIfAvailable()).thenReturn(p2ImportService);

        KeycloakConfigRunner runner = new KeycloakConfigRunner(
                keycloakImportProvider,
                realmImportService,
                importConfigProperties,
                p2ImportConfigProperties,
            p2ImportServiceProvider
        );

        runner.run();

        verify(p2ImportService).run(List.of("classpath:P2_*.json"));
        verify(keycloakImportProvider, never()).readFromLocations(org.mockito.ArgumentMatchers.<java.util.Collection<String>>any());
        verify(realmImportService, never()).doImport(any());
        assertEquals(0, runner.getExitCode());
    }

    @Test
    void shouldRunCoreImportWhenP2ImportModeDisabled() {
        P2ImportConfigProperties p2ImportConfigProperties = new P2ImportConfigProperties(false);

        when(importConfigProperties.getFiles()).thenReturn(importFilesProperties);
        when(importFilesProperties.getLocations()).thenReturn(List.of("classpath:P2_*.json"));
        when(keycloakImportProvider.readFromLocations((java.util.Collection<String>) List.of("classpath:P2_*.json")))
            .thenReturn(new de.adorsys.keycloak.config.model.KeycloakImport(java.util.Map.of()));

        KeycloakConfigRunner runner = new KeycloakConfigRunner(
                keycloakImportProvider,
                realmImportService,
                importConfigProperties,
                p2ImportConfigProperties,
                p2ImportServiceProvider
        );

        runner.run();

        verify(p2ImportService, never()).run(any());
        verify(keycloakImportProvider).readFromLocations(List.of("classpath:P2_*.json"));
        assertEquals(0, runner.getExitCode());
    }
}
