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

package de.adorsys.keycloak.config.provider;

import de.adorsys.keycloak.config.AbstractImportTest;
import de.adorsys.keycloak.config.exception.InvalidImportException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class P2_KeycloakImportProviderIT extends AbstractImportTest {

    @Test
    void shouldFailOnP2DataInStandardImport() {
        String location = "classpath:import-files/import/invalid/P2_prefixed_data.json";

        InvalidImportException exception = assertThrows(
                InvalidImportException.class,
                () -> keycloakImportProvider.readFromLocations(location)
        );

        assertThat(exception.getMessage(), containsString("Unable to parse file"));
        assertThat(exception.getMessage(), containsString("P2 data is not supported in standard keycloak-config-cli imports"));
        assertThat(exception.getMessage(), containsString("Place P2 data in a dedicated plugin-only file"));
    }
}
