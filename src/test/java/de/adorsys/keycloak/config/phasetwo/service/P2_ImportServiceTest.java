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

package de.adorsys.keycloak.config.phasetwo.service;

import de.adorsys.keycloak.config.phasetwo.provider.P2ImportProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class P2_ImportServiceTest {

    @Mock
    private P2ImportProvider p2ImportProvider;

    @Mock
    private P2OrganizationImportService p2OrganizationImportService;

    @Test
    void shouldDelegateP2OrganizationsToOrganizationImportService() {
        when(p2ImportProvider.readFromLocations(List.of("classpath:P2_*.json"))).thenReturn(Map.of(
                "classpath:P2_*.json",
            Map.of("P2_organizations.json", List.of(Map.of(
                        "realm", "p2-realm",
            "P2", Map.of("organizations", List.of(Map.of("name", "acme")))
                )))
        ));

        P2ImportService service = new P2ImportService(p2ImportProvider, p2OrganizationImportService);

        service.run(List.of("classpath:P2_*.json"));

        verify(p2OrganizationImportService).importOrganizations("p2-realm", List.of(Map.of("name", "acme")));
    }

    @Test
    void shouldFailOnUnsupportedP2TopLevelField() {
        when(p2ImportProvider.readFromLocations(List.of("classpath:P2_*.json"))).thenReturn(Map.of(
                "classpath:P2_*.json",
                Map.of("P2_config.json", List.of(Map.of(
                        "realm", "p2-realm",
                "P2", Map.of("config", Map.of("enabled", true))
                )))
        ));

        P2ImportService service = new P2ImportService(p2ImportProvider, p2OrganizationImportService);

        assertThrows(IllegalStateException.class, () -> service.run(List.of("classpath:P2_*.json")));
    }
}
