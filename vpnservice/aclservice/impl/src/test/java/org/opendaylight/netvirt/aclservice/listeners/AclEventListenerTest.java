/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.listeners;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class AclEventListenerTest {

    private AclEventListener aclEventListener;
    private AclServiceManager aclServiceManager;

    private InstanceIdentifier<Acl> mockInstanceId;

    private ArgumentCaptor<Interface> interfaceValueSaver;
    private ArgumentCaptor<Action> actionValueSaver;
    private ArgumentCaptor<Ace> aceValueSaver;
    private String aclName;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {

        mockInstanceId = mock(InstanceIdentifier.class);
        aclServiceManager = mock(AclServiceManager.class);
        aclEventListener = new AclEventListener(aclServiceManager);

        interfaceValueSaver = ArgumentCaptor.forClass(Interface.class);
        actionValueSaver = ArgumentCaptor.forClass(AclServiceManager.Action.class);
        aceValueSaver = ArgumentCaptor.forClass(Ace.class);

        aclName = "00000000-0000-0000-0000-000000000001";
    }

    @Test
    public void testUpdate_singleInterface_addNewAce() {
        prepareAclDataUtil(aclName);

        Acl previousAcl = prepareAcl(aclName, "AllowUDP");
        Acl updatedAcl = prepareAcl(aclName, "AllowICMP", "AllowUDP");

        aclEventListener.update(mockInstanceId, previousAcl, updatedAcl);

        verify(aclServiceManager).notifyAce(interfaceValueSaver.capture(), actionValueSaver.capture(),
                aceValueSaver.capture());

        assertEquals(Action.ADD, actionValueSaver.getValue());
        assertEquals("AllowICMP", aceValueSaver.getValue().getRuleName());
    }

    @Test
    public void testUpdate_singleInterface_removeOldAce() {
        prepareAclDataUtil(aclName);

        Acl previousAcl = prepareAcl(aclName, "AllowICMP", "AllowUDP");
        Acl updatedAcl = prepareAcl(aclName, "AllowUDP");

        aclEventListener.update(mockInstanceId, previousAcl, updatedAcl);

        verify(aclServiceManager).notifyAce(interfaceValueSaver.capture(), actionValueSaver.capture(),
                aceValueSaver.capture());

        assertEquals(Action.REMOVE, actionValueSaver.getValue());
        assertEquals("AllowICMP", aceValueSaver.getValue().getRuleName());
    }

    @Test
    public void testUpdate_singleInterface_addNewAceAndRemoveOldAce() {
        prepareAclDataUtil(aclName);

        Acl previousAcl = prepareAcl(aclName, "AllowICMP", "AllowUDP");
        Acl updatedAcl = prepareAcl(aclName, "AllowTCP", "AllowUDP");

        aclEventListener.update(mockInstanceId, previousAcl, updatedAcl);

        verify(aclServiceManager, times(2)).notifyAce(interfaceValueSaver.capture(), actionValueSaver.capture(),
                aceValueSaver.capture());

        assertEquals(Action.ADD, actionValueSaver.getAllValues().get(0));
        assertEquals("AllowTCP", aceValueSaver.getAllValues().get(0).getRuleName());

        assertEquals(Action.REMOVE, actionValueSaver.getAllValues().get(1));
        assertEquals("AllowICMP", aceValueSaver.getAllValues().get(1).getRuleName());
    }

    private void prepareAclDataUtil(String... updatedAclName) {
        prepareAclDataUtil(mock(Interface.class), updatedAclName);
    }

    private void prepareAclDataUtil(Interface inter, String... updatedAclNames) {
        AclDataUtil.addAclInterfaceMap(aclNames(updatedAclNames), inter);
    }

    private Acl prepareAcl(String aclName, String... aces) {
        AccessListEntries aceEntries = mock(AccessListEntries.class);
        List<Ace> aceList = toAceList(aces);
        when(aceEntries.getAce()).thenReturn(aceList);

        Acl acl = mock(Acl.class);
        when(acl.getAccessListEntries()).thenReturn(aceEntries);
        when(acl.getAclName()).thenReturn(aclName);
        return acl;
    }

    private List<Ace> toAceList(String... aces) {
        List<Ace> aceList = new ArrayList<>();
        for (String aceName : aces) {
            Ace aceMock = mock(Ace.class);
            when(aceMock.getRuleName()).thenReturn(aceName);
            aceList.add(aceMock);
        }
        return aceList;
    }

    private List<Uuid> aclNames(String... names) {
        return Stream.of(names).map(name -> new Uuid(name)).collect(Collectors.toList());
    }
}
