/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

//import java.util.Map;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AclServiceImplFactory implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AclServiceImplFactory.class);
    //private static final String SECURITY_GROUP_MODE = "security-group-mode";

    private DataBroker dataBroker;
    private IMdsalApiManager mdsalManager;
    private SecurityGroupMode securityGroupMode;

    public AclServiceImplFactory(DataBroker dataBroker, IMdsalApiManager mdsalManager, AclserviceConfig config) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        this.securityGroupMode = config.getSecurityGroupMode();
        LOG.info("AclserviceConfig: {}", config);
    }

    /* alternate use for ConfigAdmin
    public void setSecurityGroupMode(String securityGroupMode) {
        LOG.info("setSecurityGroupMode: {}", securityGroupMode);
    }

    public void updateConfigParameter(Map<String, Object> configParameters) {
        LOG.info("Config parameters received : {}", configParameters.entrySet());
        if (configParameters != null && !configParameters.isEmpty()) {
            for (Map.Entry<String, Object> paramEntry : configParameters.entrySet()) {
                if (paramEntry.getKey().equalsIgnoreCase(SECURITY_GROUP_MODE)) {
                    LOG.info("setSecurityGroupMode: {}", paramEntry.getValue());

                    //Please remove the break if you add more config nobs.
                    break;
                }
                if (paramEntry.getKey().equalsIgnoreCase("teststring")) {
                    LOG.info("testString: {}", paramEntry.getValue());

                    //Please remove the break if you add more config nobs.
                    break;
                }
                if (paramEntry.getKey().equalsIgnoreCase("testint")) {
                    LOG.info("testInt: {}", Integer.parseInt((String)paramEntry.getValue()));

                    //Please remove the break if you add more config nobs.
                    break;
                }
            }
        }
    }*/

    protected InstanceIdentifier<AclserviceConfig> getWildCardPath() {
        return InstanceIdentifier
                .create(AclserviceConfig.class);
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public void close() throws Exception {
        LOG.info("{} close", getClass().getSimpleName());
    }

    public IngressAclServiceImpl createIngressAclServiceImpl() {
        LOG.info("creating ingress acl service using mode {}", securityGroupMode);
        if (securityGroupMode == null || securityGroupMode == SecurityGroupMode.Stateful) {
            return new IngressAclServiceImpl(dataBroker, mdsalManager);
        } else if (securityGroupMode == SecurityGroupMode.Stateless) {
            return new StatelessIngressAclServiceImpl(dataBroker, mdsalManager);
        } else {
            return new TransparentIngressAclServiceImpl(dataBroker, mdsalManager);
        }
    }

    public EgressAclServiceImpl createEgressAclServiceImpl() {
        LOG.info("creating egress acl service using mode {}", securityGroupMode);
        if (securityGroupMode == null || securityGroupMode == SecurityGroupMode.Stateful) {
            return new EgressAclServiceImpl(dataBroker, mdsalManager);
        } else if (securityGroupMode == SecurityGroupMode.Stateless) {
            return new StatelessEgressAclServiceImpl(dataBroker, mdsalManager);
        } else {
            return new TransparentEgressAclServiceImpl(dataBroker, mdsalManager);
        }
    }
}
