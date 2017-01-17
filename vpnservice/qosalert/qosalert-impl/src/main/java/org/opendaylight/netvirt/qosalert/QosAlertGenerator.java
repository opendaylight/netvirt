/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosalert;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class QosAlertGenerator {

    private static final String QOS_ALERT_FILE_NAME_DEFAULT = "qosalert/default_qos_alert.log";
    private static final DateTimeFormatter FORMATTER =  DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final  Logger LOG = LoggerFactory.getLogger(QosAlertGenerator.class);
    private static BufferedWriter bufferedWriter;

    private QosAlertGenerator() {
    }

    private static boolean openLogFile(String alertLogFile) {
        File   file;
        File   parent;
        boolean returnValue = false;

        if ((alertLogFile != null) && (!alertLogFile.isEmpty())) {

            file = new File(alertLogFile);
            parent = file.getParentFile();

            if ( parent != null) {
                parent.mkdirs();
            }

            try {
                if (bufferedWriter != null) {
                    bufferedWriter.close();
                    bufferedWriter = null;
                }
                bufferedWriter = new BufferedWriter(new FileWriter(file, true));
            } catch (IOException ex) {
                LOG.error("Error in creating file:" + file.getName());
                LOG.error(ex.toString());
            }

            if (bufferedWriter == null) {
                LOG.info("Failed to Create QosAlert file:" + file.getName());
            } else {
                LOG.info("Created QosAlert file:" + file.getName());
                returnValue = true;
            }

        } else {
            LOG.info("File name not specified in initial config file");
        }

        return (returnValue);
    }

    public static void initAlertLogFile(String alertLogFile) {

        if (openLogFile(alertLogFile) == false) {

            LOG.error("Qos alert file creation failed. Using default log file {}", QOS_ALERT_FILE_NAME_DEFAULT);

            if (openLogFile(QOS_ALERT_FILE_NAME_DEFAULT) == false) {
                LOG.error("Failed to create default log file. Qos alerts not logged");
            }
        }
    }

    public static void close() {
        LOG.info("Closing qoS alert generator");
        try {
            if (bufferedWriter != null) {
                LOG.info("Closing alert file");
                bufferedWriter.close();
                bufferedWriter = null;
            } else {
                LOG.debug("Alert file already null");
            }
        } catch (IOException ex) {
            LOG.error(ex.toString());
        }
    }

    public static void raiseAlert(String portName, BigInteger rxBytes, BigInteger rxDropBytes) {

        String msg = LocalDateTime.now().format(FORMATTER) + " Port " + portName
                + "\t\tRx dropped bytes crossed threshold RxBytes[" + rxBytes + "]\t\tRxDroppedBytes["
                + rxDropBytes + "]\n";

        try {
            if (bufferedWriter != null) {
                LOG.debug("QoS Alert:{}", msg);
                bufferedWriter.write(msg);
            }
        } catch (IOException ex) {
            LOG.error(ex.toString());
        }
    }

    public static void flushAlerts() {
        try {
            if (bufferedWriter != null) {
                LOG.debug("Qos flushing alert file");
                bufferedWriter.flush();
            } else {
                LOG.info("QoS Alert file not initialized");
            }
        } catch (IOException ex) {
            LOG.error(ex.toString());
        }
    }

}
