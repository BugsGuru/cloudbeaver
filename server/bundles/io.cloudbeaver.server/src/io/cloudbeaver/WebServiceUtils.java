/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2021 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cloudbeaver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import io.cloudbeaver.model.WebConnectionConfig;
import io.cloudbeaver.model.WebConnectionInfo;
import io.cloudbeaver.model.WebNetworkHandlerConfigInput;
import io.cloudbeaver.model.WebPropertyInfo;
import io.cloudbeaver.model.session.WebSession;
import io.cloudbeaver.server.CBApplication;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.model.DBPObject;
import org.jkiss.dbeaver.model.app.DBPDataSourceRegistry;
import org.jkiss.dbeaver.model.auth.AuthProperty;
import org.jkiss.dbeaver.model.auth.DBAAuthCredentials;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.jkiss.dbeaver.model.navigator.DBNBrowseSettings;
import org.jkiss.dbeaver.model.navigator.DBNModel;
import org.jkiss.dbeaver.model.net.DBWHandlerConfiguration;
import org.jkiss.dbeaver.model.preferences.DBPPropertyDescriptor;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.dbeaver.registry.DataSourceNavigatorSettings;
import org.jkiss.dbeaver.registry.DataSourceProviderDescriptor;
import org.jkiss.dbeaver.registry.DataSourceProviderRegistry;
import org.jkiss.dbeaver.registry.driver.DriverDescriptor;
import org.jkiss.dbeaver.registry.network.NetworkHandlerDescriptor;
import org.jkiss.dbeaver.registry.network.NetworkHandlerRegistry;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.runtime.properties.ObjectPropertyDescriptor;
import org.jkiss.dbeaver.runtime.properties.PropertyCollector;
import org.jkiss.utils.CommonUtils;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Various constants
 */
public class WebServiceUtils {

    private static final Log log = Log.getLog(WebServiceUtils.class);

    private static final Gson gson = new GsonBuilder().create();

    public static String makeIconId(@Nullable DBPImage icon) {
        return icon == null ? null : icon.getLocation();
    }

    @NotNull
    public static DBPDriver getDriverById(String id) throws DBWebException {
        int divPos = id.indexOf(':');
        if (divPos < 0) {
            throw new DBWebException("Bad driver id [" + id + "]");
        }
        String dsId = id.substring(0, divPos);
        String driverId = id.substring(divPos + 1);
        DataSourceProviderDescriptor dsProvider = DataSourceProviderRegistry.getInstance().getDataSourceProvider(dsId);
        if (dsProvider == null) {
            throw new DBWebException("Data source provider '" + dsId + "' not found");
        }
        DriverDescriptor driver = dsProvider.getDriver(driverId);
        if (driver == null) {
            throw new DBWebException("Driver '" + driverId + "' not found in provider '" + dsId + "'");
        }
        return driver;
    }

    @NotNull
    public static DBPDataSourceRegistry getGlobalDataSourceRegistry() throws DBWebException {
        DBPDataSourceRegistry registry = DBWorkbench.getPlatform().getWorkspace().getDefaultDataSourceRegistry();
        if (registry == null) {
            throw new DBWebException("No activate data source registry");
        }
        return registry;
    }

    @NotNull
    public static DBNModel getGlobalNavigatorModel() throws DBWebException {
        return DBWorkbench.getPlatform().getNavigatorModel();
    }

    public static InputStream openStaticResource(String path) {
        return WebServiceUtils.class.getClassLoader().getResourceAsStream(path);
    }

    @NotNull
    public static DBPDataSourceContainer createConnectionFromConfig(WebConnectionConfig config, DBPDataSourceRegistry registry) throws DBWebException {
        DBPDataSourceContainer newDataSource;
        if (!CommonUtils.isEmpty(config.getTemplateId())) {
            DBPDataSourceContainer tpl = registry.getDataSource(config.getTemplateId());
            if (tpl == null) {
                throw new DBWebException("Template connection '" + config.getTemplateId() + "' not found");
            }
            newDataSource = registry.createDataSource(tpl);
        } else if (!CommonUtils.isEmpty(config.getDriverId())) {
            String driverId = config.getDriverId();
            if (CommonUtils.isEmpty(driverId)) {
                throw new DBWebException("Driver not specified");
            }
            DBPDriver driver = getDriverById(driverId);

            DBPConnectionConfiguration dsConfig = new DBPConnectionConfiguration();

            setConnectionConfiguration(driver, dsConfig, config);

            newDataSource = registry.createDataSource(driver, dsConfig);
        } else {
            throw new DBWebException("Template connection or driver must be specified");
        }

        newDataSource.setSavePassword(true);
        newDataSource.setName(config.getName());
        newDataSource.setDescription(config.getDescription());
        ((DataSourceDescriptor)newDataSource).setTemplate(config.isTemplate());

        // Set default navigator settings
        DataSourceNavigatorSettings navSettings = new DataSourceNavigatorSettings(
            CBApplication.getInstance().getAppConfiguration().getDefaultNavigatorSettings());
        //navSettings.setShowSystemObjects(false);
        ((DataSourceDescriptor)newDataSource).setNavigatorSettings(navSettings);

        saveAuthProperties(newDataSource, newDataSource.getConnectionConfiguration(), config.getCredentials(), config.isSaveCredentials());


        return newDataSource;
    }

    public static void setConnectionConfiguration(DBPDriver driver, DBPConnectionConfiguration dsConfig, WebConnectionConfig config) {
        if (!CommonUtils.isEmpty(config.getUrl())) {
            dsConfig.setUrl(config.getUrl());
        } else {
            if (!CommonUtils.isEmpty(config.getHost())) {
                dsConfig.setHostName(config.getHost());
            }
            if (!CommonUtils.isEmpty(config.getPort())) {
                dsConfig.setHostPort(config.getPort());
            }
            if (!CommonUtils.isEmpty(config.getDatabaseName())) {
                dsConfig.setDatabaseName(config.getDatabaseName());
            }
            if (!CommonUtils.isEmpty(config.getServerName())) {
                dsConfig.setServerName(config.getServerName());
            }
            dsConfig.setUrl(driver.getDataSourceProvider().getConnectionURL(driver, dsConfig));
        }
        if (config.getProperties() != null) {
            Map<String, String> newProps = new LinkedHashMap<>();
            for (Map.Entry<String, Object> pe : config.getProperties().entrySet()) {
                newProps.put(pe.getKey(), CommonUtils.toString(pe.getValue()));
            }
            dsConfig.setProperties(newProps);
        }
        if (config.getUserName() != null) {
            dsConfig.setUserName(config.getUserName());
        }
        if (config.getUserPassword() != null) {
            dsConfig.setUserPassword(config.getUserPassword());
        }
        if (config.getAuthModelId() != null) {
            dsConfig.setAuthModelId(config.getAuthModelId());
        }
        // Save provider props
        if (config.getProviderProperties() != null) {
            dsConfig.setProviderProperties(new LinkedHashMap<>());
            for (Map.Entry<String, Object> e : config.getProviderProperties().entrySet()) {
                dsConfig.setProviderProperty(e.getKey(), CommonUtils.toString(e.getValue()));
            }
        }
        // Save network handlers
        if (config.getNetworkHandlersConfig() != null) {
            for (WebNetworkHandlerConfigInput nhc : config.getNetworkHandlersConfig()) {
                DBWHandlerConfiguration handlerConfig = dsConfig.getHandler(nhc.getId());
                if (handlerConfig == null) {
                    NetworkHandlerDescriptor handlerDescriptor = NetworkHandlerRegistry.getInstance().getDescriptor(nhc.getId());
                    if (handlerDescriptor == null) {
                        log.warn("Can't find network handler '" + nhc.getId() + "'");
                        continue;
                    } else {
                        handlerConfig = new DBWHandlerConfiguration(handlerDescriptor, null);
                        updateHandlerConfig(handlerConfig, nhc);
                    }
                } else {
                    updateHandlerConfig(handlerConfig, nhc);
                }
                dsConfig.updateHandler(handlerConfig);
            }
        }
    }

    private static void updateHandlerConfig(DBWHandlerConfiguration handlerConfig, WebNetworkHandlerConfigInput cfgInput) {
        if (cfgInput.isEnabled() != null) {
            handlerConfig.setEnabled(cfgInput.isEnabled());
        }
        if (cfgInput.isSavePassword() != null) {
            handlerConfig.setSavePassword(cfgInput.isSavePassword());
        } else {
            handlerConfig.setSavePassword(false);
        }
        if (cfgInput.getUserName() != null) {
            handlerConfig.setUserName(cfgInput.getUserName());
        }
        if (cfgInput.getPassword() != null) {
            handlerConfig.setPassword(cfgInput.getPassword());
        }
        if (cfgInput.getProperties() != null) {
            handlerConfig.setProperties(cfgInput.getProperties());
        }
    }

    public static void saveAuthProperties(
        @NotNull DBPDataSourceContainer dataSourceContainer,
        @NotNull DBPConnectionConfiguration configuration,
        @Nullable Map<String, Object> authProperties,
        boolean saveCredentials)
    {
        dataSourceContainer.setSavePassword(saveCredentials);
        if (!saveCredentials) {
            // Reset credentials
            //authProperties = new LinkedHashMap<>();
        } else {
            if (authProperties == null) {
                // No changes
                return;
            }
        }
        if (!saveCredentials) {
            configuration.setUserPassword(null);
        }
        {
            // Read save credentials
            DBAAuthCredentials credentials = configuration.getAuthModel().loadCredentials(dataSourceContainer, configuration);

            if (!authProperties.isEmpty()) {

                // Make new Gson parser with type adapters to deserialize into existing credentials
                InstanceCreator<DBAAuthCredentials> credTypeAdapter = type -> credentials;
                Gson credGson = new GsonBuilder()
                    .setLenient()
                    .registerTypeAdapter(credentials.getClass(), credTypeAdapter)
                    .create();

                credGson.fromJson(credGson.toJsonTree(authProperties), credentials.getClass());
            }

            configuration.getAuthModel().saveCredentials(dataSourceContainer, configuration, credentials);
        }
    }

    public static void updateConnectionFromConfig(DBPDataSourceContainer dataSource, WebConnectionConfig config) {
        setConnectionConfiguration(dataSource.getDriver(), dataSource.getConnectionConfiguration(), config);
        dataSource.setName(config.getName());
        dataSource.setDescription(config.getDescription());
        saveAuthProperties(dataSource, dataSource.getConnectionConfiguration(), config.getCredentials(), config.isSaveCredentials());
    }

    public static DBNBrowseSettings parseNavigatorSettings(Map<String, Object> settingsMap) {
        return gson.fromJson(
            gson.toJsonTree(settingsMap), DataSourceNavigatorSettings.class);
    }

    public static void checkServerConfigured() throws DBWebException {
        if (CBApplication.getInstance().isConfigurationMode()) {
            throw new DBWebException("Server is in configuration mode");
        }
    }

    @NotNull
    public static WebPropertyInfo[] getObjectProperties(WebSession session, DBPObject details) {
        PropertyCollector propertyCollector = new PropertyCollector(details, false);
        propertyCollector.collectProperties();
        return Arrays.stream(propertyCollector.getProperties())
            .filter(p -> !(p instanceof ObjectPropertyDescriptor && ((ObjectPropertyDescriptor) p).isHidden()))
            .map(p -> new WebPropertyInfo(session, p, propertyCollector)).toArray(WebPropertyInfo[]::new);
    }

    public static boolean isAuthPropertyApplicable(DBPPropertyDescriptor prop, boolean hasContextCredentials) {
        if (hasContextCredentials && prop instanceof ObjectPropertyDescriptor) {
            if (((ObjectPropertyDescriptor) prop).isHidden()) {
                return false;
            }
            AuthProperty authProperty = ((ObjectPropertyDescriptor) prop).getAnnotation(AuthProperty.class);
            if (authProperty != null) return !authProperty.contextProvided();
        }
        return true;
    }

    @Nullable
    public static DBPDataSourceContainer getLocalOrGlobalDataSource(WebSession webSession, String connectionId) throws DBWebException {
        DBPDataSourceContainer dataSource = null;
        if (!CommonUtils.isEmpty(connectionId)) {
            dataSource = webSession.getSingletonProject().getDataSourceRegistry().getDataSource(connectionId);
            if (dataSource == null && (webSession.hasPermission(DBWConstants.PERMISSION_ADMIN) || CBApplication.getInstance().isConfigurationMode())) {
                // If called for new connection in admin mode then this connection may absent in session registry yet
                dataSource = getGlobalDataSourceRegistry().getDataSource(connectionId);
            }
        }
        return dataSource;
    }

    public static void saveCredentialsInDataSource(WebConnectionInfo webConnectionInfo, DBPDataSourceContainer dataSourceContainer, DBPConnectionConfiguration configuration) {
        // Properties passed from web
        // webConnectionInfo may be null in some cases (e.g. connection test when no actual connection exist yet)
        Map<String, Object> authProperties = webConnectionInfo.getSavedAuthProperties();
        if (authProperties != null) {
            authProperties.forEach((s, o) -> configuration.setAuthProperty(s, CommonUtils.toString(o)));
        }
        List<WebNetworkHandlerConfigInput> networkCredentials = webConnectionInfo.getSavedNetworkCredentials();
        if (networkCredentials != null) {
            networkCredentials.forEach(c -> {
                if (c != null) {
                    DBWHandlerConfiguration handlerCfg = configuration.getHandler(c.getId());
                    if (handlerCfg != null) {
                        handlerCfg.setUserName(c.getUserName());
                        handlerCfg.setPassword(c.getPassword());
                    }
                }
            });
        }
    }
}
