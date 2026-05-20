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
import de.adorsys.keycloak.config.util.CloneUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "p2.import", name = "enabled", havingValue = "true")
@ConditionalOnBean(P2OrganizationRepository.class)
public class P2OrganizationImportService {

    private static final Logger logger = LoggerFactory.getLogger(P2OrganizationImportService.class);

    private final P2OrganizationRepository p2OrganizationRepository;
    private final P2ManagementConfigProperties p2ManagementConfigProperties;

    public P2OrganizationImportService(
            P2OrganizationRepository p2OrganizationRepository,
            P2ManagementConfigProperties p2ManagementConfigProperties
    ) {
        this.p2OrganizationRepository = p2OrganizationRepository;
        this.p2ManagementConfigProperties = p2ManagementConfigProperties;
    }

    public void importOrganizations(String realmName, List<?> p2OrganizationsRaw) {
        List<P2OrganizationRepresentation> p2Organizations = toOrganizations(p2OrganizationsRaw);
        List<P2OrganizationRepresentation> existingOrganizations = p2OrganizationRepository.getAll(realmName);

        if (p2ManagementConfigProperties.getOrganization() == P2ManagementConfigProperties.P2ManagedValue.FULL) {
            deleteOrganizationsMissingInImport(realmName, p2Organizations, existingOrganizations);
        }

        for (P2OrganizationRepresentation p2Organization : p2Organizations) {
            createOrUpdateOrganization(realmName, p2Organization);
        }
    }

    private List<P2OrganizationRepresentation> toOrganizations(List<?> p2OrganizationsRaw) {
        return p2OrganizationsRaw.stream()
                .map(raw -> CloneUtil.deepClone(raw, P2OrganizationRepresentation.class))
                .peek(this::validateOrganization)
                .collect(Collectors.toList());
    }

    private void validateOrganization(P2OrganizationRepresentation organization) {
        if (organization == null || organization.getName() == null || organization.getName().isBlank()) {
            throw new IllegalStateException("P2.organizations entries require a non-empty 'name'.");
        }
    }

    private void deleteOrganizationsMissingInImport(
            String realmName,
            List<P2OrganizationRepresentation> organizations,
            List<P2OrganizationRepresentation> existingOrganizations
    ) {
        for (P2OrganizationRepresentation existingOrganization : existingOrganizations) {
            if (!hasOrganizationWithName(organizations, existingOrganization.getName())) {
                logger.debug("Delete P2 organization '{}' in realm '{}'", existingOrganization.getName(), realmName);
                p2OrganizationRepository.delete(realmName, existingOrganization);
            }
        }
    }

    private void createOrUpdateOrganization(String realmName, P2OrganizationRepresentation organization) {
        String organizationName = organization.getName();
        Optional<P2OrganizationRepresentation> maybeOrganization = p2OrganizationRepository.search(realmName, organizationName);

        if (maybeOrganization.isPresent()) {
            updateOrganizationIfNecessary(realmName, organization, maybeOrganization.get());
            return;
        }

        logger.debug("Create P2 organization '{}' in realm '{}'", organizationName, realmName);
        p2OrganizationRepository.create(realmName, organization);
    }

    private void updateOrganizationIfNecessary(
            String realmName,
            P2OrganizationRepresentation organization,
            P2OrganizationRepresentation existingOrganization
    ) {
        P2OrganizationRepresentation patched = CloneUtil.patch(existingOrganization, organization, "id");
        patched.setId(existingOrganization.getId());

        if (CloneUtil.deepEquals(existingOrganization, patched)) {
            logger.debug("No need to update P2 organization '{}' in realm '{}'", existingOrganization.getName(), realmName);
            return;
        }

        logger.debug("Update P2 organization '{}' in realm '{}'", existingOrganization.getName(), realmName);
        p2OrganizationRepository.update(realmName, patched);
    }

    private boolean hasOrganizationWithName(List<P2OrganizationRepresentation> organizations, String name) {
        return organizations.stream().anyMatch(org -> Objects.equals(org.getName(), name));
    }
}
