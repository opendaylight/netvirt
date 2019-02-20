/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;

/**
 * Unit tests for AclDataUtil.
 *
 * @author Thomas Pantelis
 */
public class AclDataUtilTest {
    private static final Uuid ACL1 = new Uuid("85cc3048-abc3-43cc-89b3-377341426ac1");
    private static final Uuid ACL2 = new Uuid("85cc3048-abc3-43cc-89b3-377341426ac2");
    private static final Uuid ACL3 = new Uuid("85cc3048-abc3-43cc-89b3-377341426ac3");
    private static final Uuid ACL4 = new Uuid("85cc3048-abc3-43cc-89b3-377341426ac4");
    private static final Uuid ACL5 = new Uuid("85cc3048-abc3-43cc-89b3-377341426ac5");

    private static final AclInterface PORT1 = newPort("1");
    private static final AclInterface PORT2 = newPort("2");
    private static final AclInterface PORT3 = newPort("3");

    private final AclDataUtil aclDataUtil = new AclDataUtil();

    @Test
    public void testAclInterfaces() {
        assertTrue(aclDataUtil.getInterfaceList(ACL1).isEmpty());

        aclDataUtil.removeAclInterfaceMap(Arrays.asList(ACL1), PORT1);

        final BigInteger dpId = new BigInteger("123");
        assertFalse(aclDataUtil.doesDpnHaveAclInterface(dpId));

        aclDataUtil.addOrUpdateAclInterfaceMap(Arrays.asList(ACL1, ACL2), PORT1);
        assertAclInterfaces(ACL1, PORT1);
        assertAclInterfaces(ACL2, PORT1);

        aclDataUtil.addOrUpdateAclInterfaceMap(Arrays.asList(ACL1), PORT2);
        assertAclInterfaces(ACL1, PORT1, PORT2);
        assertAclInterfaces(ACL2, PORT1);

        assertFalse(aclDataUtil.doesDpnHaveAclInterface(dpId));

        aclDataUtil.addOrUpdateAclInterfaceMap(Arrays.asList(ACL1), PORT2);
        assertAclInterfaces(ACL1, PORT1, PORT2);

        aclDataUtil.addOrUpdateAclInterfaceMap(Arrays.asList(ACL1), PORT3);
        assertAclInterfaces(ACL1, PORT1, PORT2, PORT3);

        AclInterface updatedPort2 = AclInterface.builder().interfaceId(PORT2.getInterfaceId()).dpId(dpId).build();

        aclDataUtil.addOrUpdateAclInterfaceMap(Arrays.asList(ACL1), updatedPort2);
        assertAclInterfaces(ACL1, PORT1, updatedPort2, PORT3);

        assertTrue(aclDataUtil.doesDpnHaveAclInterface(dpId));

        aclDataUtil.removeAclInterfaceMap(Arrays.asList(ACL1, ACL2), PORT1);
        assertAclInterfaces(ACL1, updatedPort2, PORT3);
        assertAclInterfaces(ACL2);

        aclDataUtil.removeAclInterfaceMap(Arrays.asList(ACL1, ACL2), updatedPort2);
        assertAclInterfaces(ACL1, PORT3);
        assertAclInterfaces(ACL2);

        assertFalse(aclDataUtil.doesDpnHaveAclInterface(dpId));

        aclDataUtil.addOrUpdateAclInterfaceMap(Arrays.asList(ACL2), PORT2);
        assertAclInterfaces(ACL2, PORT2);

        aclDataUtil.addOrUpdateAclInterfaceMap(Arrays.asList(ACL3), PORT1);
        assertAclInterfaces(ACL3, PORT1);
    }

    @Test
    public void testIngressRemoteAclInterfaces() {
        Class<? extends DirectionBase> direction = DirectionIngress.class;
        assertNull(aclDataUtil.getRemoteAcl(ACL1, direction));
        assertNull(aclDataUtil.getRemoteAclInterfaces(ACL1, direction));

        aclDataUtil.removeRemoteAclId(ACL1, ACL2, direction);

        assertEquals(0, aclDataUtil.getAllRemoteAclInterfaces(direction).size());

        aclDataUtil.addRemoteAclId(ACL1, ACL2, direction);
        assertRemoteAcls(ACL1, ACL2);

        aclDataUtil.addRemoteAclId(ACL1, ACL3, direction);
        assertRemoteAcls(ACL1, ACL2, ACL3);

        aclDataUtil.addRemoteAclId(ACL4, ACL5, direction);
        assertRemoteAcls(ACL4, ACL5);
        assertRemoteAcls(ACL1, ACL2, ACL3);

        Map<String, Set<AclInterface>> map = aclDataUtil.getRemoteAclInterfaces(ACL1, direction);
        assertNotNull(map);
        assertEquals(0, map.size());

        aclDataUtil.addOrUpdateAclInterfaceMap(Arrays.asList(ACL2), PORT1);
        aclDataUtil.addOrUpdateAclInterfaceMap(Arrays.asList(ACL2), PORT2);
        map = aclDataUtil.getRemoteAclInterfaces(ACL1, direction);
        assertEquals(1, map.size());
        assertAclInterfaces(map.get(ACL2.getValue()), PORT1, PORT2);

        aclDataUtil.addOrUpdateAclInterfaceMap(Arrays.asList(ACL3), PORT3);
        map = aclDataUtil.getRemoteAclInterfaces(ACL1, direction);
        assertEquals(2, map.size());
        assertAclInterfaces(map.get(ACL2.getValue()), PORT1, PORT2);
        assertAclInterfaces(map.get(ACL3.getValue()), PORT3);

        aclDataUtil.removeRemoteAclId(ACL1, ACL2, direction);
        assertRemoteAcls(ACL1, ACL3);
    }

    private static AclInterface newPort(String interfaceId) {
        return AclInterface.builder().interfaceId(interfaceId).dpId(new BigInteger(interfaceId)).build();
    }

    private void assertAclInterfaces(Uuid acl, AclInterface... expPorts) {
        assertAclInterfaces(aclDataUtil.getInterfaceList(acl), expPorts);
    }

    private void assertAclInterfaces(Collection<AclInterface> actualPorts, AclInterface... expPorts) {
        assertNotNull(actualPorts);
        List<AclInterface> actualPortList = new ArrayList<>(actualPorts);
        sortPorts(actualPortList);

        List<AclInterface> expPortsList = Arrays.asList(expPorts);
        sortPorts(expPortsList);

        assertEquals("AclInterfaces", expPortsList, actualPortList);
    }

    private void assertRemoteAcls(Uuid acl, Uuid... expAcls) {
        List<Uuid> actualAclList = getRemoteAcls(acl, DirectionIngress.class);
        sortAcls(actualAclList);

        List<Uuid> expAclList = Arrays.asList(expAcls);
        sortAcls(expAclList);

        assertEquals("Remote Acls", expAclList, actualAclList);
    }

    private List<Uuid> getRemoteAcls(Uuid acl, Class<? extends DirectionBase> direction) {
        Collection<Uuid> acls = aclDataUtil.getRemoteAcl(acl, direction);
        assertNotNull(acls);
        return new ArrayList<>(acls);
    }

    private static void sortPorts(List<AclInterface> ports) {
        Collections.sort(ports, (p1, p2) -> p1.getInterfaceId().compareTo(p2.getInterfaceId()));
    }

    private static void sortAcls(List<Uuid> acls) {
        Collections.sort(acls, (a1, a2) -> a1.getValue().compareTo(a2.getValue()));
    }
}
