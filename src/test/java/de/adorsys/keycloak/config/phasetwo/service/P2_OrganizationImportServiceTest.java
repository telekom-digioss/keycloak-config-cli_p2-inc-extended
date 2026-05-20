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

import de.adorsys.keycloak.config.phasetwo.model.P2OrganizationRepresentation;
import de.adorsys.keycloak.config.phasetwo.properties.P2ManagementConfigProperties;
import de.adorsys.keycloak.config.phasetwo.repository.P2OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.adorsys.keycloak.config.phasetwo.properties.P2ManagementConfigProperties.P2ManagedValue.FULL;
import static de.adorsys.keycloak.config.phasetwo.properties.P2ManagementConfigProperties.P2ManagedValue.NO_DELETE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class P2_OrganizationImportServiceTest {

    @Mock
    private P2OrganizationRepository p2OrganizationRepository;

    @Mock
    private P2ManagementConfigProperties p2ManagementConfigProperties;

    @Test
    void shouldCreateOrganizationWhenMissing() {
        when(p2ManagementConfigProperties.getOrganization()).thenReturn(FULL);
        when(p2OrganizationRepository.getAll("p2-realm")).thenReturn(List.of());
        when(p2OrganizationRepository.search("p2-realm", "acme")).thenReturn(Optional.empty());

        P2OrganizationImportService service = new P2OrganizationImportService(p2OrganizationRepository, p2ManagementConfigProperties);

        service.importOrganizations("p2-realm", List.of(Map.of("name", "acme", "displayName", "ACME")));

        ArgumentCaptor<P2OrganizationRepresentation> captor = ArgumentCaptor.forClass(P2OrganizationRepresentation.class);
        verify(p2OrganizationRepository).create(org.mockito.ArgumentMatchers.eq("p2-realm"), captor.capture());
        assertEquals("acme", captor.getValue().getName());
    }

    @Test
    void shouldUpdateOrganizationWhenChanged() {
        when(p2ManagementConfigProperties.getOrganization()).thenReturn(FULL);

        P2OrganizationRepresentation existing = new P2OrganizationRepresentation();
        existing.setId("org-1");
        existing.setName("acme");
        existing.setDisplayName("ACME old");

        when(p2OrganizationRepository.getAll("p2-realm")).thenReturn(List.of(existing));
        when(p2OrganizationRepository.search("p2-realm", "acme")).thenReturn(Optional.of(existing));

        P2OrganizationImportService service = new P2OrganizationImportService(p2OrganizationRepository, p2ManagementConfigProperties);

        service.importOrganizations("p2-realm", List.of(Map.of("name", "acme", "displayName", "ACME new")));

        ArgumentCaptor<P2OrganizationRepresentation> captor = ArgumentCaptor.forClass(P2OrganizationRepresentation.class);
        verify(p2OrganizationRepository).update(org.mockito.ArgumentMatchers.eq("p2-realm"), captor.capture());
        assertEquals("org-1", captor.getValue().getId());
        assertEquals("ACME new", captor.getValue().getDisplayName());
    }

    @Test
    void shouldDeleteOrganizationsMissingInImportWhenManagedFull() {
        when(p2ManagementConfigProperties.getOrganization()).thenReturn(FULL);

        P2OrganizationRepresentation existing = new P2OrganizationRepresentation();
        existing.setId("org-1");
        existing.setName("old-org");

        when(p2OrganizationRepository.getAll("p2-realm")).thenReturn(List.of(existing));
        when(p2OrganizationRepository.search("p2-realm", "new-org")).thenReturn(Optional.empty());

        P2OrganizationImportService service = new P2OrganizationImportService(p2OrganizationRepository, p2ManagementConfigProperties);

        service.importOrganizations("p2-realm", List.of(Map.of("name", "new-org", "displayName", "New Org")));

        verify(p2OrganizationRepository).delete("p2-realm", existing);
    }

    @Test
    void shouldDeleteAllOrganizationsWhenImportListEmptyAndManagedFull() {
        when(p2ManagementConfigProperties.getOrganization()).thenReturn(FULL);

        P2OrganizationRepresentation existing = new P2OrganizationRepresentation();
        existing.setId("org-1");
        existing.setName("old-org");

        when(p2OrganizationRepository.getAll("p2-realm")).thenReturn(List.of(existing));

        P2OrganizationImportService service = new P2OrganizationImportService(p2OrganizationRepository, p2ManagementConfigProperties);

        service.importOrganizations("p2-realm", List.of());

        verify(p2OrganizationRepository).delete("p2-realm", existing);
        verify(p2OrganizationRepository, never()).create(org.mockito.ArgumentMatchers.eq("p2-realm"), org.mockito.ArgumentMatchers.any());
        verify(p2OrganizationRepository, never()).update(org.mockito.ArgumentMatchers.eq("p2-realm"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNotDeleteOrganizationsMissingInImportWhenManagedNoDelete() {
        when(p2ManagementConfigProperties.getOrganization()).thenReturn(NO_DELETE);

        P2OrganizationRepresentation existing = new P2OrganizationRepresentation();
        existing.setId("org-1");
        existing.setName("old-org");

        when(p2OrganizationRepository.getAll("p2-realm")).thenReturn(List.of(existing));
        when(p2OrganizationRepository.search("p2-realm", "new-org")).thenReturn(Optional.empty());

        P2OrganizationImportService service = new P2OrganizationImportService(p2OrganizationRepository, p2ManagementConfigProperties);

        service.importOrganizations("p2-realm", List.of(Map.of("name", "new-org", "displayName", "New Org")));

        verify(p2OrganizationRepository, never()).delete("p2-realm", existing);
    }

    @Test
    void shouldFailWhenNameMissing() {
        P2OrganizationImportService service = new P2OrganizationImportService(p2OrganizationRepository, p2ManagementConfigProperties);

        assertThrows(IllegalStateException.class, () -> service.importOrganizations("p2-realm", List.of(Map.of("displayName", "No Name"))));
    }
}
