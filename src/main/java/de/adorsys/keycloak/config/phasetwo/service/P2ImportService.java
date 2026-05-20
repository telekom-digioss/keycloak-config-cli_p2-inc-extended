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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "p2.import", name = "enabled", havingValue = "true")
@ConditionalOnBean(P2OrganizationImportService.class)
public class P2ImportService {

    private static final String P2_FIELD = "P2";
    private static final String P2_ORGANIZATIONS_FIELD = "organizations";
    private static final Logger logger = LoggerFactory.getLogger(P2ImportService.class);

    private final P2ImportProvider p2ImportProvider;
    private final P2OrganizationImportService p2OrganizationImportService;

    public P2ImportService(
            P2ImportProvider p2ImportProvider,
            P2OrganizationImportService p2OrganizationImportService
    ) {
        this.p2ImportProvider = p2ImportProvider;
        this.p2OrganizationImportService = p2OrganizationImportService;
    }

    public void run(Collection<String> importLocations) {
        Map<String, Map<String, List<Map<String, Object>>>> rawImports = p2ImportProvider.readFromLocations(importLocations);

        for (Map<String, List<Map<String, Object>>> rawImportLocations : rawImports.values()) {
            for (Map.Entry<String, List<Map<String, Object>>> rawImport : rawImportLocations.entrySet()) {
                logger.info("Importing P2 plugin file '{}'", rawImport.getKey());
                for (Map<String, Object> rawDocument : rawImport.getValue()) {
                    executeP2OnlyDocument(rawImport.getKey(), rawDocument);
                }
            }
        }
    }

    private void executeP2OnlyDocument(String source, Map<String, Object> rawDocument) {
        String realmName = validateP2OnlyDocument(source, rawDocument);
        Map<String, Object> p2PluginData = extractPluginData(rawDocument);

        executeValidatedP2Import(source, realmName, p2PluginData);
    }

    private void executeValidatedP2Import(String source, String realmName, Map<String, Object> p2PluginData) {
        logger.info("Validated P2 import document '{}' for realm '{}'", source, realmName);

        if (!p2PluginData.containsKey(P2_ORGANIZATIONS_FIELD)) {
            throw new IllegalStateException(String.format(
                    "P2 import in '%s' for realm '%s' currently supports only '%s'. Found keys: %s",
                    source,
                    realmName,
                    P2_ORGANIZATIONS_FIELD,
                    p2PluginData.keySet()
            ));
        }

        if (p2PluginData.size() != 1) {
            throw new IllegalStateException(String.format(
                    "P2 import in '%s' for realm '%s' currently supports only '%s' inside '%s'. Found keys: %s",
                    source,
                    realmName,
                    P2_ORGANIZATIONS_FIELD,
                    P2_FIELD,
                    p2PluginData.keySet()
            ));
        }

        Object p2OrganizationsValue = p2PluginData.get(P2_ORGANIZATIONS_FIELD);
        if (!(p2OrganizationsValue instanceof List<?> p2OrganizationsRaw)) {
            throw new IllegalStateException(String.format(
                    "P2 import field '%s.%s' in '%s' for realm '%s' must be a list.",
                    P2_FIELD,
                    P2_ORGANIZATIONS_FIELD,
                    source,
                    realmName
            ));
        }

        p2OrganizationImportService.importOrganizations(realmName, p2OrganizationsRaw);
    }

    private Map<String, Object> extractPluginData(Map<String, Object> rawDocument) {
        Object p2PluginData = rawDocument.get(P2_FIELD);
        if (!(p2PluginData instanceof Map<?, ?> rawP2PluginData)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> typedP2PluginData = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawP2PluginData.entrySet()) {
            if (entry.getKey() instanceof String key) {
                typedP2PluginData.put(key, entry.getValue());
            }
        }

        return typedP2PluginData;
    }

    private String validateP2OnlyDocument(String source, Map<String, Object> rawDocument) {
        Object realmValue = rawDocument.get("realm");
        if (!(realmValue instanceof String realmName) || realmName.isBlank()) {
            throw new IllegalStateException(String.format(
                    "P2 plugin import requires a non-empty 'realm' field in '%s'.",
                    source
            ));
        }

        for (Map.Entry<String, Object> entry : rawDocument.entrySet()) {
            String key = entry.getKey();
            if ("realm".equals(key)) {
                continue;
            }

            if (!P2_FIELD.equals(key)) {
                throw new IllegalStateException(String.format(
                        "P2 plugin import supports only 'realm' and '%s' as top-level fields. Found '%s' in '%s'.",
                        P2_FIELD,
                        key,
                        source
                ));
            }
        }

        Object p2FieldValue = rawDocument.get(P2_FIELD);
        if (p2FieldValue == null) {
            throw new IllegalStateException(String.format(
                    "P2 plugin import requires a '%s' object in '%s'.",
                    P2_FIELD,
                    source
            ));
        }

        if (!(p2FieldValue instanceof Map<?, ?> p2DataMap)) {
            throw new IllegalStateException(String.format(
                    "P2 plugin import field '%s' in '%s' must be an object.",
                    P2_FIELD,
                    source
            ));
        }

        if (!p2DataMap.containsKey(P2_ORGANIZATIONS_FIELD)) {
            throw new IllegalStateException(String.format(
                    "P2 plugin import requires '%s.%s' in '%s'.",
                    P2_FIELD,
                    P2_ORGANIZATIONS_FIELD,
                    source
            ));
        }

        for (Map.Entry<?, ?> entry : p2DataMap.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String keyString)) {
                throw new IllegalStateException(String.format(
                        "P2 plugin import field '%s' in '%s' contains a non-string key.",
                        P2_FIELD,
                        source
                ));
            }

            if (!P2_ORGANIZATIONS_FIELD.equals(keyString)) {
                throw new IllegalStateException(String.format(
                        "P2 plugin import currently supports only '%s' inside '%s'. Found '%s' in '%s'.",
                        P2_ORGANIZATIONS_FIELD,
                        P2_FIELD,
                        keyString,
                        source
                ));
            }
        }

        return realmName;
    }
}
