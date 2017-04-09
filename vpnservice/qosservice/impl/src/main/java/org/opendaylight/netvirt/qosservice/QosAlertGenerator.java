/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QosAlertGenerator {

    private static final  Logger LOG = LoggerFactory.getLogger(QosAlertGenerator.class);

    private QosAlertGenerator() {
       // Hide implicit constructor
    }

    public static void raiseAlert(final String qosPolicyName, final String qosPolicyUuid, final String portUuid,
                                  final String networkUuid, final BigInteger rxPackets,
                                  final BigInteger rxDroppedPackets) {
        // Log the alert message in a text file using log4j appender qosalertmsg
        LOG.debug(QosConstants.alertMsgFormat, qosPolicyName, qosPolicyUuid, portUuid, networkUuid,
                                                                                   rxPackets, rxDroppedPackets);
    }

}
