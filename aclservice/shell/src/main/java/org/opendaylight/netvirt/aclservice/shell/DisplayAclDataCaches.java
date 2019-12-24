/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.shell;

import static java.util.stream.Collectors.joining;

import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.stream.Collectors;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.aclservice.api.AclInterfaceCache;
import org.opendaylight.netvirt.aclservice.api.utils.AclDataCache;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIp;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.AceIpVersion;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv6;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yangtools.yang.common.Uint8;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "aclservice", name = "display-acl-data-cache", description = " ")
public class DisplayAclDataCaches extends OsgiCommandSupport {
    private static final Logger LOG = LoggerFactory.getLogger(DisplayAclDataCaches.class);
    private AclDataCache aclDataCache;
    private AclInterfaceCache aclInterfaceCache;

    private static final String UUID_TAB = "%-40s";
    private static final String DELIMITER = String.format("%-40s", "");
    private  static final String ACL_TAG_HEADER_LINE = "-----------------------------------------------";
    private static final String ACL_TAG_HEADERS = String.format("%-40s%-40s", "ACL Id", "ACL Tag");
    private static final String ACL_TAG_DATA_FORMAT_STRING = "%-40s%-40s";
    private static final String HEADER_LINE =
            "----------------------------------------------------------------------------------";
    private static final String REMOTE_ACL_ID_HEADERS = String.format("%-40s%-40s", "Remote ACL Id", "ACL Id");
    private static final String ACL_INTERFACE_MAP_HEADERS = String.format("%-40s%-40s", "ACL Id", "Interface Id");
    private static final String ACL_HEADER = String.format("%-8s", "ACL Id: ");
    private static final String ACE_DATA_FOR = "%-37s%-10s%-6s%-7s%-25s";
    private static final String ACL_ENTRIES_HEADERS = String.format(ACE_DATA_FOR, "ACE ID", "Direction", "Proto",
            "IP Ver", "IP Prefix/RemoteGroupId");
    private static final String ACL_INTERFACE_FORMAT_STRING = "%-37s %-12s %-10s %-15s %-7s %-6s %-6s";
    private static final String ACL_ENTRIES_HEADER_LINE =
            "--------------------------------------------------------------------------------------";
    private static final String ACE_ENTRIES_FORMAT_STRING = "%-37s%-10s%-6s%-7s%-40s";

    private final String exeCmdStr = "display-acl-data-cache -op ";
    private final String opSelections =
            "[ aclInterface | ingressRemoteAclId | egressRemoteAclId | aclTag | aclInterfaceCache | acl ]";
    private final String opSelStr = exeCmdStr + opSelections;

    @Option(name = "-op", aliases = {"--option",
            "--op"}, description = opSelections, required = false, multiValued = false)
    private String op;

    @Option(name = "--all", description = "display the complete selected map", required = false, multiValued = false)
    private String all;

    @Option(name = "--key", description = "key for aclTag/aclInterfaceCache/acl", required = false,
            multiValued = false)
    private String key;

    public void setAclDataCache(AclDataCache aclDataCache) {
        this.aclDataCache = aclDataCache;
    }

    public void setAclInterfaceCache(AclInterfaceCache aclInterfaceCache) {
        this.aclInterfaceCache = aclInterfaceCache;
    }

    private Map<String, String> protoMap = new HashMap<String, String>() {
        {
            put("1", "ICMP");
            put("6", "TCP");
            put("17", "UDP");
        }
    };

    private Map<String, String> opToKeyIdMap = new HashMap<String, String>() {
        {
            put("aclInterface", "aclInterfaceUuid");
            put("ingressRemoteAclId", "remoteAclUuid");
            put("egressRemoteAclId", "remoteAclUuid");
            put("aclTag", "aclUuid");
            put("aclInterfaceCache", "aclInterfaceUuid");
            put("acl", "aclUuid");
        }
    };

    @Override
    protected Object doExecute() {
        if (aclDataCache == null) {
            session.getConsole().println("Failed to handle the command, AclData reference is null at this point");
            return null;
        }

        if (op == null) {
            session.getConsole().println("Please provide valid option");
            session.getConsole().println();
            session.getConsole().println("Usage: " + opSelStr);
            return null;
        }
        switch (op) {
            case "aclInterface":
                getAclInterfaceMap();
                break;
            case "ingressRemoteAclId":
                getRemoteAclIdMap(DirectionIngress.class);
                break;
            case "egressRemoteAclId":
                getRemoteAclIdMap(DirectionEgress.class);
                break;
            case "aclTag":
                getAclTagMap();
                break;
            case "aclInterfaceCache":
                getAclInterfaceCache();
                break;
            case "acl":
                getAclMap();
                break;
            default:
                session.getConsole().println("Invalid operation");
                session.getConsole().println();
                session.getConsole().println("Usage: " + opSelStr);
        }
        return null;
    }

    private void printHelp() {
        session.getConsole().println("Invalid input");
        session.getConsole().println();
        session.getConsole().println("Usage: " + exeCmdStr + op + " --all show | --key <"
                                            + opToKeyIdMap.get(op) + ">");
    }

    private boolean validateAll() {
        return "show".equalsIgnoreCase(all);
    }

    private void printHeader(String headerString, String headerLine) {
        session.getConsole().println();
        session.getConsole().println(headerString);
        session.getConsole().println(headerLine);
    }

    protected void getAclInterfaceMap() {
        if (all == null && key == null) {
            printHelp();
        } else if (all == null) {
            Uuid uuid;
            try {
                uuid = Uuid.getDefaultInstance(key);
            } catch (IllegalArgumentException e) {
                session.getConsole().println("Invalid uuid. " + e.getMessage());
                LOG.error("Invalid uuid", e);
                return;
            }
            Collection<AclInterface> aclInterfaceList = aclDataCache.getInterfaceList(uuid);
            printHeader(ACL_INTERFACE_MAP_HEADERS, HEADER_LINE);
            session.getConsole().print(String.format(UUID_TAB, uuid.getValue()));
            printAclInterfaceMap(aclInterfaceList);
        } else if (key == null) {
            if (!validateAll()) {
                printHelp();
                return;
            }
            Map<Uuid, Collection<AclInterface>> aclInterfaceMap = aclDataCache.getAclInterfaceMap();
            if (aclInterfaceMap.isEmpty()) {
                session.getConsole().println("No data found");
            } else {
                printHeader(ACL_INTERFACE_MAP_HEADERS, HEADER_LINE);
                aclInterfaceMap.forEach((uuid, aclInterfaceList) -> {
                    session.getConsole().print(String.format(UUID_TAB, uuid.getValue()));
                    printAclInterfaceMap(aclInterfaceList);
                });
            }
        }
    }

    protected void getRemoteAclIdMap(Class<? extends DirectionBase> direction) {
        if (all == null && key == null) {
            printHelp();
        } else if (all == null) {
            Uuid uuidRef;
            try {
                uuidRef = Uuid.getDefaultInstance(key);
            } catch (IllegalArgumentException e) {
                session.getConsole().println("Invalid uuid" + e.getMessage());
                LOG.error("Invalid uuid", e);
                return;
            }
            Collection<Uuid> remoteUuidLst = aclDataCache.getRemoteAcl(uuidRef, direction);
            printHeader(REMOTE_ACL_ID_HEADERS, HEADER_LINE);
            session.getConsole().print(String.format(UUID_TAB, uuidRef.getValue()));
            printRemoteAcl(remoteUuidLst);
        } else if (key == null) {
            if (!validateAll()) {
                printHelp();
                return;
            }

            Map<Uuid, Collection<Uuid>> map = DirectionEgress.class.equals(direction)
                    ? aclDataCache.getEgressRemoteAclIdMap() : aclDataCache.getIngressRemoteAclIdMap();
            if (map.isEmpty()) {
                session.getConsole().println("No data found");
            } else {
                printHeader(REMOTE_ACL_ID_HEADERS, HEADER_LINE);
                map.forEach((uuid, remoteUuidList) -> {
                    session.getConsole().print(String.format(UUID_TAB, uuid.getValue()));
                    printRemoteAcl(remoteUuidList);
                    session.getConsole().println();
                });
            }
        }
    }

    private void printRemoteAcl(Collection<Uuid> remoteUuidLst) {
        if (remoteUuidLst == null || remoteUuidLst.isEmpty()) {
            session.getConsole().println("No data found ");
        } else {
            List<String> uuids = remoteUuidLst.stream().map(Uuid::getValue).collect(Collectors.toList());
            String joined = uuids.stream().collect(joining("\n" + DELIMITER, "", ""));
            session.getConsole().println(joined);
        }
    }

    protected void getAclTagMap() {
        if (all == null && key == null) {
            printHelp();
        } else if (all == null) {
            Integer val = aclDataCache.getAclTag(key);
            if (val == null) {
                session.getConsole().println("No data found");
                return;
            }
            printHeader(ACL_TAG_HEADERS, ACL_TAG_HEADER_LINE);
            session.getConsole().println(String.format(ACL_TAG_DATA_FORMAT_STRING, key, val));
        } else if (key == null) {
            if (!validateAll()) {
                printHelp();
                return;
            }
            Map<String, Integer> map = aclDataCache.getAclTagMap();
            if (map.isEmpty()) {
                session.getConsole().println("No data found");
            } else {
                printHeader(ACL_TAG_HEADERS, ACL_TAG_HEADER_LINE);
                map.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry -> session.getConsole()
                        .println(String.format(ACL_TAG_DATA_FORMAT_STRING, entry.getKey(), entry.getValue())));
            }
        }
    }


    protected void getAclInterfaceCache() {
        if (all == null && key == null) {
            printHelp();
            return;
        }
        if (all == null && key != null) {
            AclInterface aclInterface = aclInterfaceCache.get(key);
            if (aclInterface == null) {
                session.getConsole().println("No data found");
                return;
            }
            printAclInterfaceHeader();
            printAclInterface(aclInterface);
        } else if (key == null) {
            if (!validateAll()) {
                printHelp();
                return;
            }
            Collection<Entry<String, AclInterface>> entries = aclInterfaceCache.entries();
            if (entries.isEmpty()) {
                session.getConsole().println("No data found");
                return;
            }
            printAclInterfaceCache(entries);
        }
    }

    private void printAclInterfaceMap(Collection<AclInterface> aclInterfaceList) {
        if (aclInterfaceList == null || aclInterfaceList.isEmpty()) {
            session.getConsole().println("No data found");
        } else {
            List<String> uuids = aclInterfaceList.stream().map(AclInterface::getInterfaceId)
                    .collect(Collectors.toList());
            String joined = uuids.stream().collect(joining("\n" + DELIMITER, "", ""));
            session.getConsole().println(joined);
            session.getConsole().println();
        }
    }

    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    private void printAclInterfaceCache(Collection<Entry<String, AclInterface>> entries) {
        printAclInterfaceHeader();

        for (Map.Entry<String, AclInterface> entry : entries) {
            AclInterface aclInterface = entry.getValue();
            printAclInterface(aclInterface);
        }
    }

    private void printAclInterface(AclInterface aclInterface) {
        session.getConsole().println(String.format(ACL_INTERFACE_FORMAT_STRING, aclInterface.getInterfaceId(),
                aclInterface.getInterfaceType(), aclInterface.isPortSecurityEnabled(), aclInterface.getDpId(),
                aclInterface.getLPortTag(), aclInterface.getElanId(), aclInterface.isMarkedForDelete()));
        List<AllowedAddressPairs> aaps = aclInterface.getAllowedAddressPairs();
        if (aaps == null || aaps.isEmpty()) {
            session.getConsole().println("--");
        } else {
            for (AllowedAddressPairs aap : aaps) {
                IpPrefixOrAddress ipPrefixOrAddress = aap.getIpAddress();
                IpPrefix ipPrefix = ipPrefixOrAddress.getIpPrefix();
                String ipAddrStr = "";
                if (ipPrefix != null) {
                    if (ipPrefix.getIpv4Prefix() != null) {
                        ipAddrStr = ipPrefix.getIpv4Prefix().getValue();
                    } else {
                        ipAddrStr = ipPrefix.getIpv6Prefix().getValue();
                    }
                } else {
                    IpAddress ipAddress = ipPrefixOrAddress.getIpAddress();
                    if (ipAddress != null) {
                        if (ipAddress.getIpv4Address() != null) {
                            ipAddrStr = ipAddress.getIpv4Address().getValue();
                        } else {
                            ipAddrStr = ipAddress.getIpv6Address().getValue();
                        }
                    }
                }
                String macAddrStr = aap.getMacAddress().getValue();
                session.getConsole().println(ipAddrStr + ", " + macAddrStr);
            }
        }

        List<Uuid> sgsUuid = aclInterface.getSecurityGroups();
        if (sgsUuid == null || sgsUuid.isEmpty()) {
            session.getConsole().println("--");
        } else {
            for (Uuid sgUuid : sgsUuid) {
                session.getConsole().println(sgUuid.getValue());
            }
        }
        SortedSet<Integer> ingressRemoteAclTags = aclInterface.getIngressRemoteAclTags();
        if (ingressRemoteAclTags == null || ingressRemoteAclTags.isEmpty()) {
            session.getConsole().println("--");
        } else {
            session.getConsole().println(ingressRemoteAclTags);
        }
        SortedSet<Integer> egressRemoteAclTags = aclInterface.getEgressRemoteAclTags();
        if (egressRemoteAclTags == null || egressRemoteAclTags.isEmpty()) {
            session.getConsole().println("--");
        } else {
            session.getConsole().println(egressRemoteAclTags);
        }
        session.getConsole().println();
    }

    private void printAclInterfaceHeader() {
        session.getConsole().println();
        StringBuilder sb = new StringBuilder();
        Formatter fmt = new Formatter(sb);
        session.getConsole().println(fmt.format(ACL_INTERFACE_FORMAT_STRING, "InterfaceId", "Type",
                "SGEnabled", "DpId", "LPort", "ElanId", "Marked"));
        sb.setLength(0);
        session.getConsole().println(fmt.format("%-55s", "AllowedAddressPairs"));
        sb.setLength(0);
        session.getConsole().println(fmt.format("%-55s", "SecurityGroups"));
        sb.setLength(0);
        session.getConsole().println(fmt.format("%-55s", "IngressRemoteAclTags"));
        sb.setLength(0);
        session.getConsole().println(fmt.format("%-55s", "EgressRemoteAclTags"));
        sb.setLength(0);
        session.getConsole().println(fmt
            .format("----------------------------------------------------------------------------------------------"));
        sb.setLength(0);
        fmt.close();
    }

    protected void getAclMap() {
        if (all == null && key == null) {
            printHelp();
        } else if (all == null) {
            Acl acl = aclDataCache.getAcl(key);
            if (acl == null) {
                session.getConsole().println("No data found");
                return;
            }
            printAcl(key, acl);
        } else if (key == null) {
            if (!validateAll()) {
                printHelp();
                return;
            }
            Map<String, Acl> map = aclDataCache.getAclMap();
            if (map.isEmpty()) {
                session.getConsole().println("No data found");
            } else {
                map.forEach(this::printAcl);
            }
        }
    }

    private void printAcl(String aclId, Acl acl) {
        session.getConsole().println();
        session.getConsole().println(ACL_HEADER + String.format("%-32s  ", aclId));
        if (null != acl.getAccessListEntries() && null != acl.getAccessListEntries().getAce()) {
            printHeader(ACL_ENTRIES_HEADERS, ACL_ENTRIES_HEADER_LINE);
            List<Ace> aceList = acl.getAccessListEntries().getAce();
            for (Ace ace : aceList) {
                LOG.info("ace data: {}", ace);
                SecurityRuleAttr aceAttr = getAccessListAttributes(ace);
                Class<? extends DirectionBase> aceAttrDirection = aceAttr.getDirection();
                AceIp aceIp = (AceIp) ace.getMatches().getAceType();
                AceIpVersion ipVersion = aceIp.getAceIpVersion();
                Uint8 protoNum = aceIp.getProtocol();
                String protocol = "Any";
                if (null != protoNum) {
                    protocol = protoMap.get(protoNum.toString());
                    protocol = (protocol == null) ? protoNum.toString() : protocol;
                }
                String ipVer = "";
                String direction = DirectionEgress.class.equals(aceAttrDirection) ? "Egress" : "Ingress";
                String ipPrefix = " -- ";
                if (null != ipVersion && ipVersion instanceof AceIpv4) {
                    ipVer = "IPv4";
                    Ipv4Prefix srcNetwork = ((AceIpv4) ipVersion).getSourceIpv4Network();
                    if (null != srcNetwork) {
                        ipPrefix = srcNetwork.getValue();
                    }
                } else if (null != ipVersion && ipVersion instanceof AceIpv6) {
                    ipVer = "IPv6";
                    Ipv6Prefix srcNetwork = ((AceIpv6) ipVersion).getSourceIpv6Network();
                    if (null != srcNetwork) {
                        ipPrefix = srcNetwork.getValue();
                    }
                }
                String remoteGroupId = "-";
                if (aceAttr.getRemoteGroupId() != null) {
                    remoteGroupId = aceAttr.getRemoteGroupId().getValue();
                    ipPrefix = "-";
                }
                String prefixAndRemoteId = ipPrefix + " / " + remoteGroupId;
                session.getConsole().print(String.format(ACE_ENTRIES_FORMAT_STRING, ace.key().getRuleName(),
                        direction, protocol, ipVer, prefixAndRemoteId));
            }
        }
        session.getConsole().println();
    }

    public SecurityRuleAttr getAccessListAttributes(Ace ace) {
        if (ace == null) {
            LOG.error("Ace is Null");
            return null;
        }
        SecurityRuleAttr aceAttributes = ace.augmentation(SecurityRuleAttr.class);
        if (aceAttributes == null) {
            LOG.error("Ace is null");
            return null;
        }
        return aceAttributes;
    }
}
