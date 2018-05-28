/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QosAlertGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(QosAlertGenerator.class);

    private final ConfigurationAdmin configurationAdmin;

    public QosAlertGenerator(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
        try {
            updateQoSAlertLog4jProperties(getPropertyMap(QosConstants.QOS_ALERT_PROPERTIES_PID));
        } catch (IOException e) {
            LOG.warn("Qos Alert properties could not be initialised");
            LOG.debug("Error initialising log4j properties ", e);
        }
    }

    public void update(Map<String, Object> qosAlertProperties) {
        try {
            updateQoSAlertLog4jProperties(qosAlertProperties);
        } catch (IOException e) {
            LOG.warn("Qos Alert properties update failed");
            LOG.debug("Error updating log4j properties ", e);
        }
    }

    public static void raiseAlert(final String qosPolicyName, final String qosPolicyUuid, final String portUuid,
                                  final String networkUuid, final BigInteger rxPackets,
                                  final BigInteger rxDroppedPackets) {
        // Log the alert message in a text file using log4j appender qosalertmsg
        LOG.debug(QosConstants.ALERT_MSG_FORMAT, qosPolicyName, qosPolicyUuid, portUuid, networkUuid,
                                                                                        rxPackets, rxDroppedPackets);
    }

    private Configuration getConfig(String pid) throws IOException {
        return configurationAdmin.getConfiguration(pid);
    }

    private Map<String, Object> getPropertyMap(String pid) throws IOException {
        Map<String, Object> propertyMap;
        Configuration configurationInit = getConfig(pid);
        Dictionary<String, Object> config = configurationInit.getProperties();
        propertyMap = new HashMap<>(config.size());
        Enumeration<String> keys = config.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            propertyMap.put(key, config.get(key));
        }
        return propertyMap;
    }

    private static Map<String, Object> removeNonLog4jProperties(Map<String, Object> qosAlertLog4jProperties) {
        qosAlertLog4jProperties.remove(QosConstants.FELIX_FILEINSTALL_FILENAME);
        qosAlertLog4jProperties.remove(QosConstants.SERVICE_PID);
        return qosAlertLog4jProperties;
    }

    private void updateQoSAlertLog4jProperties(Map<String, Object> qosAlertLog4jProperties) throws IOException {
        Map<String, Object> log4jProperties = getPropertyMap(QosConstants.ORG_OPS4J_PAX_LOGGING);
        Hashtable<String, Object> updateLog4jProperties = new Hashtable<>();
        updateLog4jProperties.putAll(log4jProperties);
        updateLog4jProperties.putAll(removeNonLog4jProperties(qosAlertLog4jProperties));

        Configuration log4jConfig = getConfig(QosConstants.ORG_OPS4J_PAX_LOGGING);
        try {
            log4jConfig.update(updateLog4jProperties);
            log4jConfig.update();
        } catch (IOException ioe) {
            LOG.warn("Could not update configuration in Config log");
            LOG.debug("Exception in configuration ", ioe);
        }
    }
}
