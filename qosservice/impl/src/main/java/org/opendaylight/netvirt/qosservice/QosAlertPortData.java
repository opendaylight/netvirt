/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import java.math.BigInteger;
import java.util.function.Supplier;
import javax.annotation.concurrent.NotThreadSafe;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.QosPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.node.connector.statistics.and.port.number.map.NodeConnectorStatisticsAndPortNumberMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NotThreadSafe
public class QosAlertPortData {
    private static final Logger LOG = LoggerFactory.getLogger(QosAlertPortData.class);
    private static final BigInteger BIG_HUNDRED = new BigInteger("100");

    private final Port port;
    private final QosNeutronUtils qosNeutronUtils;
    private final Supplier<BigInteger> alertThreshold;
    private volatile BigInteger rxPackets;
    private volatile BigInteger rxDroppedPackets;
    private volatile boolean statsDataInit;

    public QosAlertPortData(final Port port, final QosNeutronUtils qosNeutronUtils,
            final Supplier<BigInteger> alertThreshold) {
        this.port = port;
        this.qosNeutronUtils = qosNeutronUtils;
        this.alertThreshold = alertThreshold;
    }

    public void initPortData() {
        LOG.trace("Port {} data initialized", port.getUuid().getValue());
        statsDataInit = false;
    }

    public void updatePortStatistics(NodeConnectorStatisticsAndPortNumberMap statsData) {
        LOG.trace("Port {} rx-packets {} tx-packets {} rx-dropped {} tx-dropped {}", port.getUuid().getValue(),
                           statsData.getPackets().getReceived(), statsData.getPackets().getTransmitted(),
                           statsData.getReceiveDrops(), statsData.getTransmitDrops());
        if (statsDataInit) {
            calculateAlertCondition(statsData);
        } else {
            statsDataInit = true;
        }
        rxPackets = statsData.getPackets().getReceived();
        rxDroppedPackets = statsData.getReceiveDrops();
    }

    private void calculateAlertCondition(NodeConnectorStatisticsAndPortNumberMap statsData)  {
        BigInteger rxDiff = statsData.getPackets().getReceived().subtract(rxPackets);
        BigInteger rxDroppedDiff = statsData.getReceiveDrops().subtract(rxDroppedPackets);

        if ((rxDiff.signum() < 0) || (rxDroppedDiff.signum() < 0)) {
            LOG.debug("Port {} counters reset", port.getUuid().getValue());
            initPortData(); // counters wrapped. wait for one more poll.
            return;
        }
        BigInteger rxTotalDiff = rxDiff.add(rxDroppedDiff);
        LOG.trace("Port {} rxDiff:{} rxDropped diff:{} total diff:{}", port.getUuid().getValue(), rxDiff,
                                                                            rxDroppedDiff, rxTotalDiff);
        QosPolicy qosPolicy = qosNeutronUtils.getQosPolicy(port);

        if (qosPolicy == null) {
            return;
        }

        if (rxDroppedDiff.multiply(BIG_HUNDRED).compareTo(rxTotalDiff.multiply(alertThreshold.get())) > 0) {
            LOG.trace(QosConstants.ALERT_MSG_FORMAT, qosPolicy.getName(), qosPolicy.getUuid().getValue(),
                    port.getUuid().getValue(), port.getNetworkId().getValue(), statsData.getPackets().getReceived(),
                                                                                        statsData.getReceiveDrops());

            QosAlertGenerator.raiseAlert(qosPolicy.getName(), qosPolicy.getUuid().getValue(),
                    port.getUuid().getValue(), port.getNetworkId().getValue(), statsData.getPackets().getReceived(),
                                                                                          statsData.getReceiveDrops());
        }

    }
}
