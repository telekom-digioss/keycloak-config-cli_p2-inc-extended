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

package de.adorsys.keycloak.config.phasetwo.provider;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.adorsys.keycloak.config.exception.InvalidImportException;
import de.adorsys.keycloak.config.model.ImportResource;
import de.adorsys.keycloak.config.properties.ImportConfigProperties;
import de.adorsys.keycloak.config.service.script.JavaScriptEvaluator;
import de.adorsys.keycloak.config.service.script.ScriptEvaluator;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.apache.commons.text.lookup.StringLookupFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.PathMatcher;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class P2ImportProvider {

    private static final Path CWD = Paths.get(System.getProperty("user.dir"));
    private static final Logger logger = LoggerFactory.getLogger(P2ImportProvider.class);
    private static final Pattern JS_PATTERN = Pattern.compile("\\$\\$\\{javascript:(.*?)\\}", Pattern.DOTALL);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final PathMatchingResourcePatternResolver patternResolver;
    private final ImportConfigProperties importConfigProperties;
    private StringSubstitutor interpolator = null;
    private ScriptEvaluator scriptEvaluator = null;

    public P2ImportProvider(
            Environment environment,
            PathMatchingResourcePatternResolver patternResolver,
            ImportConfigProperties importConfigProperties
    ) {
        this.patternResolver = patternResolver;
        this.importConfigProperties = importConfigProperties;

        if (importConfigProperties.getVarSubstitution().isEnabled()) {
            setupVariableSubstitution(environment);
        }

        if (importConfigProperties.getVarSubstitution().isScriptEvaluationEnabled()) {
            this.scriptEvaluator = new JavaScriptEvaluator();
        }
    }

    public Map<String, Map<String, List<Map<String, Object>>>> readFromLocations(Collection<String> locations) {
        Map<String, Map<String, List<Map<String, Object>>>> rawImports = new LinkedHashMap<>();

        for (String location : locations) {
            logger.debug("Loading P2 file location '{}'", location);
            String resourceLocation = prepareResourceLocation(location);

            Resource[] resources;
            try {
                resources = this.patternResolver.getResources(resourceLocation);
            } catch (IOException e) {
                throw new InvalidImportException("Unable to proceed location '" + location + "': " + e.getMessage(), e);
            }

            resources = Arrays.stream(resources).filter(this::filterExcludedResources).toArray(Resource[]::new);

            if (resources.length == 0) {
                throw new InvalidImportException("No files matching '" + location + "'!");
            }

            Map<String, List<Map<String, Object>>> rawImport = Arrays.stream(resources)
                    .map(this::readResource)
                    .filter(this::filterEmptyResources)
                    .sorted(Map.Entry.comparingByKey())
                    .map(this::substituteImportResource)
                    .map(this::readRawImportFromImportResource)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (oldValue, newValue) -> oldValue, LinkedHashMap::new));

            rawImports.put(location, rawImport);
        }

        return rawImports;
    }

    private void setupVariableSubstitution(Environment environment) {
        StringLookup variableResolver = StringLookupFactory.INSTANCE.interpolatorStringLookup(
                StringLookupFactory.INSTANCE.functionStringLookup(environment::getProperty)
        );

        this.interpolator = StringSubstitutor.createInterpolator()
                .setVariableResolver(variableResolver)
                .setVariablePrefix(importConfigProperties.getVarSubstitution().getPrefix())
                .setVariableSuffix(importConfigProperties.getVarSubstitution().getSuffix())
                .setEnableSubstitutionInVariables(importConfigProperties.getVarSubstitution().isNested())
                .setEnableUndefinedVariableException(importConfigProperties.getVarSubstitution().isUndefinedIsError());
    }

    private boolean filterExcludedResources(Resource resource) {
        if (!resource.isFile()) {
            return true;
        }

        File file;
        try {
            file = resource.getFile();
        } catch (IOException ignored) {
            return true;
        }

        if (file.isDirectory()) {
            return false;
        }

        if (!this.importConfigProperties.getFiles().isIncludeHiddenFiles()
                && (file.isHidden() || hasHiddenAncestorDirectory(file))) {
            return false;
        }

        PathMatcher pathMatcher = patternResolver.getPathMatcher();
        return importConfigProperties.getFiles().getExcludes()
                .stream()
                .map(pattern -> pattern.startsWith("**") ? "/" + pattern : pattern)
                .map(pattern -> !pattern.startsWith("/**") ? "/**" + pattern : pattern)
                .map(pattern -> !pattern.startsWith("/") ? "/" + pattern : pattern)
                .noneMatch(pattern -> pathMatcher.match(pattern, file.getPath()));
    }

    private ImportResource readResource(Resource resource) {
        logger.debug("Loading file '{}'", resource.getFilename());

        try {
            resource = setupAuthentication(resource);
            try (InputStream inputStream = resource.getInputStream()) {
                return new ImportResource(resource.getURI().toString(), new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new InvalidImportException("Unable to proceed resource '" + resource + "': " + e.getMessage(), e);
        } finally {
            Authenticator.setDefault(null);
        }
    }

    private boolean hasHiddenAncestorDirectory(File file) {
        File absoluteFile;

        try {
            absoluteFile = file.getAbsoluteFile().toPath().toAbsolutePath().normalize().toFile();
        } catch (NullPointerException ignored) {
            return false;
        }

        File relativeFile = relativize(absoluteFile);
        while (relativeFile != null) {
            if (relativeFile.isHidden()) {
                return true;
            }
            relativeFile = relativeFile.getParentFile();
        }

        return false;
    }

    private File relativize(File file) {
        Path absolutePath = file.toPath();
        if (absolutePath.startsWith(CWD)) {
            return CWD.relativize(absolutePath).toFile();
        }
        return absolutePath.toFile();
    }

    private boolean filterEmptyResources(ImportResource resource) {
        return !resource.getValue().isEmpty();
    }

    private ImportResource substituteImportResource(ImportResource importResource) {
        if (importConfigProperties.getVarSubstitution().isEnabled()) {
            importResource.setValue(interpolator.replace(importResource.getValue()));
        }

        if (JS_PATTERN.matcher(importResource.getValue()).find()) {
            if (!importConfigProperties.getVarSubstitution().isScriptEvaluationEnabled()) {
                throw new IllegalStateException("Script evaluation used but --import.var-substitution.script-evaluation-enabled not set");
            }
            importResource.setValue(evaluateScripts(importResource.getValue()));
        }

        return importResource;
    }

    private String evaluateScripts(String content) {
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, String> env = new LinkedHashMap<>();
        System.getenv().forEach(env::put);
        System.getProperties().forEach((key, value) -> env.put(key.toString(), value.toString()));
        context.put("env", env);

        Matcher matcher = JS_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String expression = matcher.group(1);
            Object result = scriptEvaluator.evaluate(expression, context);
            String replacement;
            try {
                replacement = result instanceof String ? (String) result : OBJECT_MAPPER.writeValueAsString(result);
            } catch (Exception e) {
                throw new InvalidImportException("Failed to serialize script result: " + e.getMessage(), e);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private Pair<String, List<Map<String, Object>>> readRawImportFromImportResource(ImportResource resource) {
        String location = resource.getFilename();
        String content = resource.getValue();

        if (logger.isTraceEnabled()) {
            logger.trace(content);
        }

        try {
            return new ImmutablePair<>(location, readRawContent(content));
        } catch (Exception e) {
            throw new InvalidImportException("Unable to parse file '" + location + "': " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> readRawContent(String content) {
        List<Map<String, Object>> rawDocuments = new ArrayList<>();
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(importConfigProperties.getFiles().getCodePointLimit());

        Yaml yaml = new Yaml(loaderOptions);
        Iterable<Object> yamlDocuments = yaml.loadAll(content);

        for (Object yamlDocument : yamlDocuments) {
            Object converted = OBJECT_MAPPER.convertValue(yamlDocument, Map.class);
            if (!(converted instanceof Map<?, ?> rawDocument)) {
                throw new InvalidImportException("Import document must be an object.");
            }

            Map<String, Object> typedDocument = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawDocument.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new InvalidImportException("Import document contains a non-string top-level key.");
                }
                typedDocument.put(key, entry.getValue());
            }
            rawDocuments.add(typedDocument);
        }

        return rawDocuments;
    }

    private String prepareResourceLocation(String location) {
        String importLocation = location.replaceFirst("^zip:", "jar:");
        if (!importLocation.contains(":")) {
            importLocation = "file:" + importLocation;
        }
        return importLocation;
    }

    private Resource setupAuthentication(Resource resource) throws IOException {
        String userInfo;

        try {
            userInfo = resource.getURL().getUserInfo();
        } catch (IOException e) {
            return resource;
        }

        if (userInfo == null) {
            return resource;
        }

        String[] userInfoSplit = userInfo.split(":");
        if (userInfoSplit.length != 2) {
            return resource;
        }

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(userInfoSplit[0], userInfoSplit[1].toCharArray());
            }
        });

        String location = resource.getURI().toString().replace(userInfo + "@", "***@");
        return new UrlResource(location);
    }
}
