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

package de.adorsys.keycloak.config.phasetwo.resource;

import de.adorsys.keycloak.config.phasetwo.model.P2OrganizationRepresentation;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public interface P2OrganizationsResource {

    @GET
    @Path("/admin/realms/{realm}/orgs")
    @Produces(MediaType.APPLICATION_JSON)
    List<P2OrganizationRepresentation> listOrganizations(
            @PathParam("realm") String realm,
            @QueryParam("search") String search,
            @QueryParam("first") Integer first,
            @QueryParam("max") Integer max,
            @QueryParam("q") String query);

    @POST
    @Path("/admin/realms/{realm}/orgs")
    @Consumes(MediaType.APPLICATION_JSON)
    Response createOrganization(@PathParam("realm") String realm, P2OrganizationRepresentation organization);

    @PUT
    @Path("/admin/realms/{realm}/orgs/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    Response updateOrganization(
            @PathParam("realm") String realm,
            @PathParam("id") String id,
            P2OrganizationRepresentation organization);

    @DELETE
    @Path("/admin/realms/{realm}/orgs/{id}")
    Response deleteOrganization(@PathParam("realm") String realm, @PathParam("id") String id);
}