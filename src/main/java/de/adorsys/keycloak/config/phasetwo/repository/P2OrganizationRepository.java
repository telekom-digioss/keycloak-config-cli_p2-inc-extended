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

package de.adorsys.keycloak.config.phasetwo.repository;

import de.adorsys.keycloak.config.exception.ImportProcessingException;
import de.adorsys.keycloak.config.phasetwo.model.P2OrganizationRepresentation;
import de.adorsys.keycloak.config.phasetwo.resource.P2OrganizationsResource;
import de.adorsys.keycloak.config.provider.KeycloakProvider;
import de.adorsys.keycloak.config.util.ResponseUtil;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@Service
@ConditionalOnProperty(prefix = "p2.import", name = "enabled", havingValue = "true")
public class P2OrganizationRepository {

    private static final Logger logger = LoggerFactory.getLogger(P2OrganizationRepository.class);

    private final KeycloakProvider keycloakProvider;

    public P2OrganizationRepository(KeycloakProvider keycloakProvider) {
        this.keycloakProvider = keycloakProvider;
    }

    public List<P2OrganizationRepresentation> getAll(String realmName) {
        return getResource().listOrganizations(realmName, null, 0, Integer.MAX_VALUE, null);
    }

    public Optional<P2OrganizationRepresentation> search(String realmName, String organizationName) {
        return getAll(realmName).stream()
                .filter(organization -> Objects.equals(organizationName, organization.getName()))
                .findFirst();
    }

    public void create(String realmName, P2OrganizationRepresentation organization) {
        try (Response response = getResource().createOrganization(realmName, organization)) {
            CreatedResponseUtil.getCreatedId(response);
            logger.debug("Created P2 organization '{}' in realm '{}'", organization.getName(), realmName);
        } catch (WebApplicationException error) {
            throw new ImportProcessingException(String.format(
                    "Cannot create P2 organization '%s' in realm '%s': %s",
                    organization.getName(),
                    realmName,
                    ResponseUtil.getErrorMessage(error)
            ), error);
        }
    }

    public void update(String realmName, P2OrganizationRepresentation organization) {
        try (Response ignored = getResource().updateOrganization(realmName, organization.getId(), organization)) {
            logger.debug("Updated P2 organization '{}' in realm '{}'", organization.getName(), realmName);
        } catch (WebApplicationException error) {
            throw new ImportProcessingException(String.format(
                    "Cannot update P2 organization '%s' in realm '%s': %s",
                    organization.getName(),
                    realmName,
                    ResponseUtil.getErrorMessage(error)
            ), error);
        }
    }

    public void delete(String realmName, P2OrganizationRepresentation organization) {
        try (Response ignored = getResource().deleteOrganization(realmName, organization.getId())) {
            logger.debug("Deleted P2 organization '{}' in realm '{}'", organization.getName(), realmName);
        } catch (WebApplicationException error) {
            throw new ImportProcessingException(String.format(
                    "Cannot delete P2 organization '%s' in realm '%s': %s",
                    organization.getName(),
                    realmName,
                    ResponseUtil.getErrorMessage(error)
            ), error);
        }
    }

    private P2OrganizationsResource getResource() {
        return keycloakProvider.getCustomApiProxy(P2OrganizationsResource.class);
    }
}