/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.qosservice;

import java.util.function.Supplier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.rev160613.qos.attributes.qos.policies.QosPolicy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.node.connector.statistics.and.port.number.map.NodeConnectorStatisticsAndPortNumberMap;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is ThreadSafe.
 */
public class QosAlertPortData {
    private static final Logger LOG = LoggerFactory.getLogger(QosAlertPortData.class);
    private static final Uint64 BIG_HUNDRED = Uint64.valueOf("100").intern();

    private final Port port;
    private final QosNeutronUtils qosNeutronUtils;
    private final Supplier<Uint64> alertThreshold;
    private volatile Uint64 rxPackets;
    private volatile Uint64 rxDroppedPackets;
    private volatile boolean statsDataInit;

    public QosAlertPortData(final Port port, final QosNeutronUtils qosNeutronUtils,
            final Supplier<Uint64> alertThreshold) {
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
        Uint64 rxDiff = Uint64.valueOf(statsData.getPackets().getReceived().toJava().subtract(rxPackets.toJava()));
        Uint64 rxDroppedDiff = Uint64.valueOf(statsData.getReceiveDrops().toJava().subtract(rxDroppedPackets.toJava()));

        if ((rxDiff.toJava().signum() < 0) || (rxDroppedDiff.toJava().signum() < 0)) {
            LOG.debug("Port {} counters reset", port.getUuid().getValue());
            initPortData(); // counters wrapped. wait for one more poll.
            return;
        }
        Uint64 rxTotalDiff = Uint64.valueOf(rxDiff.toJava().add(rxDroppedDiff.toJava()));
        LOG.trace("Port {} rxDiff:{} rxDropped diff:{} total diff:{}", port.getUuid().getValue(), rxDiff,
                                                                            rxDroppedDiff, rxTotalDiff);
        QosPolicy qosPolicy = qosNeutronUtils.getQosPolicy(port);

        if (qosPolicy == null) {
            return;
        }

        if (rxDroppedDiff.toJava().multiply(BIG_HUNDRED.toJava())
                .compareTo(rxTotalDiff.toJava().multiply(alertThreshold.get().toJava())) > 0) {
            LOG.trace(QosConstants.ALERT_MSG_FORMAT, qosPolicy.getName(), qosPolicy.getUuid().getValue(),
                    port.getUuid().getValue(), port.getNetworkId().getValue(), statsData.getPackets().getReceived(),
                                                                                        statsData.getReceiveDrops());

            QosAlertGenerator.raiseAlert(qosPolicy.getName(), qosPolicy.getUuid().getValue(),
                    port.getUuid().getValue(), port.getNetworkId().getValue(),
                    statsData.getPackets().getReceived(), statsData.getReceiveDrops());
        }

    }
}
