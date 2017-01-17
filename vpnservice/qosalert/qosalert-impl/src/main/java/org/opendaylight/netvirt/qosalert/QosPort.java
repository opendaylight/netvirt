/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */


package org.opendaylight.netvirt.qosalert;

import java.math.BigInteger;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.flow.capable.node.connector.statistics.FlowCapableNodeConnectorStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QosPort {

    private String       portName;
    private BigInteger   rxBytes;
    private BigInteger   rxDroppedBytes;

    private static final BigInteger BIG_HUNDRED = new BigInteger("100");
    private static BigInteger   alertThreshold;

    private static final Logger LOG = LoggerFactory.getLogger(QosPort.class);

    public QosPort(String portName, FlowCapableNodeConnectorStatistics initStats) {
        this.portName = portName;
        rxBytes        = initStats.getBytes().getReceived().abs();
        rxDroppedBytes = initStats.getReceiveDrops().abs();
        LOG.info("Port created:{}", portName);
    }


    public  void updatePortStatistics(FlowCapableNodeConnectorStatistics portStats) {
        LOG.debug("updating prot stats:{}", portName);
        calculateAlertCondition(portStats);
        rxBytes = portStats.getBytes().getReceived();
        rxDroppedBytes = portStats.getReceiveDrops();
    }

    public static void setAlertThreshold(int threshold) {
        alertThreshold = BigInteger.valueOf(threshold);
        LOG.info("setAlertThreshold:{}", alertThreshold);
    }

    private void calculateAlertCondition(FlowCapableNodeConnectorStatistics portStats) {

        BigInteger rxDiff = portStats.getBytes().getReceived().subtract(rxBytes);
        BigInteger rxDroppedDiff = portStats.getReceiveDrops().subtract(rxDroppedBytes);

        BigInteger rxTotalDiff = rxDiff.add(rxDroppedDiff);

        LOG.debug("Port {} rxDiff:{} rxDropped diff:{} total diff:{}", portName, rxDiff, rxDroppedDiff, rxTotalDiff);

        if (rxDroppedDiff.multiply(BIG_HUNDRED).compareTo(rxTotalDiff.multiply(alertThreshold)) > 0) {
            LOG.debug("raising alert port:{}", portName);
            QosAlertGenerator.raiseAlert(portName, rxDiff, rxDroppedDiff);
        }

    }
}
