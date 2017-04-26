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

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QosAlertGenerator {

    private static ConfigurationAdmin configurationAdmin;
    private static final Logger LOG = LoggerFactory.getLogger(QosAlertGenerator.class);

    public QosAlertGenerator() {
        BundleContext bundleContext = FrameworkUtil.getBundle(QosAlertGenerator.class).getBundleContext();
        configurationAdmin = (ConfigurationAdmin) bundleContext.getService(bundleContext
                .getServiceReference(ConfigurationAdmin.class.getName()));
        updateQoSAlertLog4jProperties(getPropertyMap(QosConstants.QOS_ALERT_PROPERTIES_PID));
    }

    public void update(Map<String, Object> qosAlertProperties) {
        updateQoSAlertLog4jProperties(qosAlertProperties);
    }

    public static void raiseAlert(final String qosPolicyName, final String qosPolicyUuid, final String portUuid,
                                  final String networkUuid, final BigInteger rxPackets,
                                  final BigInteger rxDroppedPackets) {
        // Log the alert message in a text file using log4j appender qosalertmsg
        LOG.debug(QosConstants.alertMsgFormat, qosPolicyName, qosPolicyUuid, portUuid, networkUuid,
                                                                                        rxPackets, rxDroppedPackets);
    }

    private static Configuration getConfig(String pid) {
        Configuration config = null;
        try {
            config = configurationAdmin.getConfiguration(pid);
        } catch (java.io.IOException ioe) {
            LOG.error("Exception in configuration {}", ioe);
        }
        return (config);
    }

    private static Map<String, Object> getPropertyMap(String pid) {
        Map<String, Object> propertyMap = null;
        Configuration configurationInit = getConfig(pid);
        Dictionary<String, Object> config = configurationInit.getProperties();
        propertyMap = new HashMap<>(config.size());
        Enumeration<String> keys = config.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            propertyMap.put(key, config.get(key));
        }
        return (propertyMap);
    }

    private static Map<String, Object> removeNonLog4jProperties(Map<String, Object> qosAlertLog4jProperties) {
        qosAlertLog4jProperties.remove(QosConstants.FELIX_FILEINSTALL_FILENAME);
        qosAlertLog4jProperties.remove(QosConstants.SERVICE_PID);
        return (qosAlertLog4jProperties);
    }

    private static void updateQoSAlertLog4jProperties(Map<String, Object> qosAlertLog4jProperties) {
        Map<String, Object> log4jProperties = getPropertyMap(QosConstants.ORG_OPS4J_PAX_LOGGING);
        Hashtable<String, Object> updateLog4jProperties = new Hashtable<>();
        updateLog4jProperties.putAll(log4jProperties);
        updateLog4jProperties.putAll(removeNonLog4jProperties(qosAlertLog4jProperties));

        Configuration log4jConfig = getConfig(QosConstants.ORG_OPS4J_PAX_LOGGING);
        try {
            log4jConfig.update(updateLog4jProperties);
            log4jConfig.update();
        } catch (IOException ioe) {
            LOG.error("Exception in configuration {}", ioe);
        }
    }
}
