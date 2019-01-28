/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.shell;

import static java.util.stream.Collectors.joining;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "aclservice", name = "display-acl-data-cache", description = " ")
public class DisplayAclDataCaches extends OsgiCommandSupport {
    private static final Logger LOG = LoggerFactory.getLogger(DisplayAclDataCaches.class);
    private AclDataCache aclDataCache;
    private AclInterfaceCache aclInterfaceCache;
    private static final String KEY_TAB = "   %-8s";
    private static final String ACL_INT_HEAD_FOR = "            %-25s%-8s   %-10s  %-6s  %-17s%-20s          %-35s";
    private static final String ACL_INT_DATA_FOR = "%-32s   %-6s%-1s  %-6s%-9s";
    private static final String ACL_INT_HEAD = String.format(ACL_INT_HEAD_FOR, "InterfaceId", "LPort",
            "DpId", "ElanId", "Marked", "SecurityGroups", "AllowedAddressPairs");
    private static final String UUID_TAB = "   %-41s  ";
    private static final String DELIMITER = String.format("\n%46s", "");
    private static final String ACL_INT_DATA_FOR_KEY = "\n%-20s : %-36s\n%-20s : %-36s\n%-20s : %-36s\n%-20s : %-36s\n"
            + "%-20s : %-36s\n%-20s : %-36s\n%-20s : %-36s\n%-20s : %-36s\n";
    private static final String ACL_DATA_TAB_FOR = "\n         %s   %28s ";
    private  static final String ACL_LINE_LIMIT = "\n ----------------------------------------------";
    private static final String ACL_DATA_HEAD = String.format(ACL_LINE_LIMIT
            + ACL_DATA_TAB_FOR, "ACL-ID", "ACL-TAG" + ACL_LINE_LIMIT);
    private static final String REM_ID_LINE_LIMIT =
            "\n----------------------------------------------------------------------------------";
    private static final String REM_ID_HEAD = String.format(REM_ID_LINE_LIMIT
            + ACL_DATA_TAB_FOR, "Remote-ACL-ID", "ACL-ID" + REM_ID_LINE_LIMIT);

    private static final String ACL_INTERFACE_HEAD = String.format(REM_ID_LINE_LIMIT
            + "\n           %10s     %115s ", "ACL-ID", "InterfaceId " + REM_ID_LINE_LIMIT);
    private static final String ACL_HEAD = String.format("\n  %s", "ACL-ID : ");
    private static final String ACE_DATA_FOR = "\n         %12s   %23s  %-8s  %-8s  %8s  %-10s";
    private static final String ACL_ENTRIES_HEAD = String.format(ACE_DATA_FOR, "ACE-ID", "Direction", "Protocol",
            "IP-Version", "IP-Prefix", "RemoteGroupId");
    private static final String LINE_DELIMITER =
            "\n------------------------------------------------------------------------------------------------"
                    + "---------------------------\n";
    private static final String LINE_DELIMITER_XTEND =
            "\n------------------------------------------------------------------------------------------------"
                    + "-------------------------------------------------------\n";
    private static final String ACE_ENTRIES_FOR = "%-32s  %-10s  %-8s  %-8s  %10s  %-10s";
    private final String exeCmdStr = "exec display-acl-data-cache -op ";
    private final String opSelections =
            "[ aclInterface | ingressRemoteAclId | egressRemoteAclId | aclTag | aclInterfaceCache | acl ]";
    private final String opSelStr = exeCmdStr + opSelections;

    private static final String KEY_TAB_DATA = String.format(" %-5s", "SGEnabled : ");


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

    private Map<Integer, String> protoString = Collections.unmodifiableMap(Stream.of(
            new AbstractMap.SimpleEntry<>(1, "ICMP"),
            new AbstractMap.SimpleEntry<>(6, "TCP"),
            new AbstractMap.SimpleEntry<>(17, "UDP"))
            .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)));

    @Override
    protected Object doExecute() {
        if (aclDataCache == null) {
            session.getConsole().println("Failed to handle the command, AclData reference is null at this point");
            return null;
        }

        if (op == null) {
            session.getConsole().println("Please provide valid option");
            usage();
            session.getConsole().println(opSelStr);
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
                session.getConsole().println("invalid operation");
                usage();
                session.getConsole().println(opSelStr);
        }
        return null;
    }

    void usage() {
        session.getConsole().println("usage:");
    }

    void printAclInterfaceMapHelp() {
        session.getConsole().println("invalid input");
        usage();
        session.getConsole().println(
                exeCmdStr + "aclInterface --all show | --uuid <uuid>");
    }

    void printRemoteAclIdMapHelp() {
        session.getConsole().println("invalid input");
        usage();
        session.getConsole().println(
                exeCmdStr + "remoteAclId --all show | --uuid <uuid>");
    }

    void printAclTagMapHelp() {
        session.getConsole().println("invalid input");
        usage();
        session.getConsole().println(
                exeCmdStr + "aclTag --all show | --key <ACL-ID>");
    }

    void printAclInterfaceCacheHelp() {
        session.getConsole().println("invalid input");
        usage();
        session.getConsole().println(
                exeCmdStr + "aclInterfaceCache --all show | --key <key>");
    }

    void printAclMapHelp() {
        session.getConsole().println("invalid input");
        usage();
        session.getConsole().println(exeCmdStr + "acl --all show | --key <key>");
    }

    private boolean validateAll() {
        return "show".equalsIgnoreCase(all);
    }

    protected void getAclInterfaceMap() {
        if (all == null && key == null) {
            printAclInterfaceMapHelp();
        } else if (all == null) {
            Uuid uuid;
            try {
                uuid = Uuid.getDefaultInstance(key);
            } catch (IllegalArgumentException e) {
                session.getConsole().println("Invalid uuid" + e.getMessage());
                log.error("Invalid uuid", e);
                return;
            }
            Collection<AclInterface> aclInterfaceList = aclDataCache.getInterfaceList(uuid);
            session.getConsole().println(ACL_INTERFACE_HEAD);
            session.getConsole().print(String.format(UUID_TAB, uuid.getValue()));
            printAclInterface(aclInterfaceList);
        } else if (key == null) {
            if (!validateAll()) {
                printAclInterfaceMapHelp();
                return;
            }
            Map<Uuid, Collection<AclInterface>> aclInterfaceMap = aclDataCache.getAclInterfaceMap();

            if (aclInterfaceMap.isEmpty()) {
                session.getConsole().println("No data found");
            } else {
                session.getConsole().println(ACL_INTERFACE_HEAD);
                aclInterfaceMap.forEach((uuid1, aclInterfaceList) -> {
                    session.getConsole().print(String.format(UUID_TAB, uuid1.getValue()));
                    printAclInterface(aclInterfaceList);
                });
            }
        }
    }

    protected void getRemoteAclIdMap(Class<? extends DirectionBase> direction) {
        if (all == null && key == null) {
            printRemoteAclIdMapHelp();
        } else if (all == null) {
            Uuid uuidRef;
            try {
                uuidRef = Uuid.getDefaultInstance(key);
            } catch (IllegalArgumentException e) {
                session.getConsole().println("Invalid uuid" + e.getMessage());
                log.error("Invalid uuid", e);
                return;
            }
            Collection<Uuid> remoteUuidLst = aclDataCache.getRemoteAcl(uuidRef, direction);
            session.getConsole().println(REM_ID_HEAD);
            session.getConsole().print(String.format(UUID_TAB, uuidRef.getValue()));
            printRemoteAcl(remoteUuidLst);
        } else if (key == null) {
            if (!validateAll()) {
                printRemoteAclIdMapHelp();
                return;
            }

            Map<Uuid, Collection<Uuid>> map =
                    DirectionEgress.class.equals(direction) ? aclDataCache.getEgressRemoteAclIdMap()
                            : aclDataCache.getIngressRemoteAclIdMap();
            if (map.isEmpty()) {
                session.getConsole().println("No data found");
            } else {
                session.getConsole().println(REM_ID_HEAD);
                map.forEach((uuid, remoteUuidList) -> {
                    session.getConsole().print(String.format(UUID_TAB, uuid.getValue()));
                    printRemoteAcl(remoteUuidList);
                });
            }
        }
    }

    private void printRemoteAcl(Collection<Uuid> remoteUuidLst) {
        if (remoteUuidLst == null || remoteUuidLst.isEmpty()) {
            session.getConsole().println("No data found ");
        } else {
            List<String> uuids = remoteUuidLst.stream().map(Uuid::getValue).collect(Collectors.toList());
            String joined = uuids.stream().collect(joining(DELIMITER, "", ""));
            session.getConsole().printf("%s \n", joined);
        }
    }

    protected void getAclTagMap() {
        if (all == null && key == null) {
            printAclTagMapHelp();
        } else if (all == null) {
            Integer val = aclDataCache.getAclTag(key);
            if (val == null) {
                session.getConsole().println("No data found");
                return;
            }
            session.getConsole().println(ACL_DATA_HEAD);
            session.getConsole().println(String.format("  %s %5s", key, val));
        } else if (key == null) {
            if (!validateAll()) {
                printAclTagMapHelp();
                return;
            }
            Map<String, Integer> map = aclDataCache.getAclTagMap();
            if (map.isEmpty()) {
                session.getConsole().println("No data found");
            } else {
                session.getConsole().println(ACL_DATA_HEAD);
                map.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry -> session.getConsole()
                        .println(String.format(" %s  %5s", entry.getKey(), entry.getValue())));
            }
        }
    }


    protected void getAclInterfaceCache() {
        if (all == null && key == null) {
            printAclInterfaceCacheHelp();
            return;
        }
        if (all == null && key != null) {
            AclInterface aclInterface = aclInterfaceCache.get(key);
            if (aclInterface == null) {
                session.getConsole().println("No data found");
                return;
            }
            printAclInterfaceKey(aclInterface);

        } else if (key == null) {
            if (!validateAll()) {
                printAclInterfaceCacheHelp();
                return;
            }
            Collection<Entry<String, AclInterface>> entries = aclInterfaceCache.entries();
            if (entries.isEmpty()) {
                session.getConsole().println("No data found");
            } else {
                printAclInterfaceCache();
            }
        }
    }

    private void printAclInterface(Collection<AclInterface> aclInterfaceList) {
        if (aclInterfaceList == null || aclInterfaceList.isEmpty()) {
            session.getConsole().println("No data found");
        } else {
            List<String> uuids = aclInterfaceList.stream().map(AclInterface::getInterfaceId)
                    .collect(Collectors.toList());
            String joined = uuids.stream().collect(joining(DELIMITER, "", ""));
            session.getConsole().printf("%s \n", joined);
        }
    }

    private void printAclInterfaceKey(AclInterface aclInterface) {
        session.getConsole().println(REM_ID_LINE_LIMIT
                + String.format(ACL_INT_DATA_FOR_KEY, "InterfaceId", aclInterface.getInterfaceId(),
                "SGEnabled", aclInterface.isPortSecurityEnabled(), "LPort", aclInterface.getLPortTag(),
                "DpId", aclInterface.getDpId(), "ElanId", aclInterface.getElanId(),
                "Marked", aclInterface.isMarkedForDelete(), "SecurityGroups", getSgString(aclInterface),
                "AllowedAddressPairs", getAAPString(aclInterface)) + REM_ID_LINE_LIMIT);
    }

    private void printAclInterfaceCache() {
        List<AclInterface> portEnabledList = new ArrayList<>();
        List<AclInterface> portDisabledList = new ArrayList<>();
        Collection<Entry<String, AclInterface>> entries = aclInterfaceCache.entries();

        for (Map.Entry<String, AclInterface> entry : entries) {
            AclInterface aclInterface = entry.getValue();
            if (aclInterface.isPortSecurityEnabled()) {
                portEnabledList.add(aclInterface);
            } else {
                portDisabledList.add(aclInterface);
            }
        }
        printAclInterfaceList(portEnabledList);
        printAclInterfaceList(portDisabledList);
    }

    private void printAclInterfaceList(List<AclInterface> portList) {
        if (portList.iterator().hasNext()) {
            session.getConsole().println(LINE_DELIMITER_XTEND + KEY_TAB_DATA
                    + portList.iterator().next().isPortSecurityEnabled());
            session.getConsole().print(ACL_INT_HEAD + LINE_DELIMITER_XTEND);
            for (AclInterface value : portList) {
                session.getConsole().print(String.format(ACL_INT_DATA_FOR, value.getInterfaceId(), value.getLPortTag(),
                        value.getDpId(), value.getElanId(), value.isMarkedForDelete()));
                session.getConsole().println(sgAAPString(value));
            }
            session.getConsole().print(LINE_DELIMITER_XTEND);
        }
    }

    protected void getAclMap() {
        if (all == null && key == null) {
            printAclMapHelp();
        } else if (all == null) {
            Acl acl = aclDataCache.getAcl(key);
            if (acl == null) {
                session.getConsole().println("No data found");
                return;
            }
            session.getConsole().print(LINE_DELIMITER);
            printAcl(key, acl);
        } else if (key == null) {
            if (!validateAll()) {
                printAclMapHelp();
                return;
            }
            Map<String, Acl> map = aclDataCache.getAclMap();
            if (map.isEmpty()) {
                session.getConsole().println("No data found");
            } else {
                session.getConsole().print(LINE_DELIMITER);
                map.forEach(this::printAcl);
            }
        }
    }

    private void printAcl(String aclId, Acl acl) {
        session.getConsole().println(ACL_HEAD + String.format("%-32s  ", aclId));
        if (null != acl.getAccessListEntries() && null != acl.getAccessListEntries().getAce()) {
            session.getConsole().print(ACL_ENTRIES_HEAD + LINE_DELIMITER);
            List<Ace> aceList = acl.getAccessListEntries().getAce();
            for (Ace ace : aceList) {
                LOG.info("ace data: {}", ace);
                SecurityRuleAttr aceAttr = getAccessListAttributes(ace);
                Class<? extends DirectionBase> aceAttrDirection = aceAttr.getDirection();
                AceIp aceIp = (AceIp) ace.getMatches().getAceType();
                AceIpVersion ipVersion = aceIp.getAceIpVersion();
                Short protoNum = aceIp.getProtocol();
                String protocol = "Any";
                if (null != protoNum) {
                    protocol = protoString.get(Integer.valueOf(protoNum));
                    protocol = protocol == null ? protoNum.toString() : protocol;
                }
                String ipVer = "";
                String direction = DirectionEgress.class.equals(aceAttrDirection) ? "Egress" : "Ingress";
                String ipPrefix = "None";
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
                String remoteGroupId = "None";
                if (aceAttr.getRemoteGroupId() != null) {
                    remoteGroupId = aceAttr.getRemoteGroupId().getValue();
                }
                session.getConsole().println(String.format(ACE_ENTRIES_FOR, ace.key().getRuleName(),
                        direction, protocol, ipVer, ipPrefix,
                        remoteGroupId));
            }
            session.getConsole().print(LINE_DELIMITER);
        }
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

    private String sgAAPString(AclInterface value) {
        List<Uuid> securityGroups = value.getSecurityGroups();
        List<AllowedAddressPairs> aaps = value.getAllowedAddressPairs();
        if (null != securityGroups && null != aaps) {
            Iterator<AllowedAddressPairs> aapIt = aaps.iterator();
            Iterator<Uuid> sgIt = securityGroups.iterator();
            StringBuilder sb = new StringBuilder();
            while (sgIt.hasNext() && aapIt.hasNext()) {
                AllowedAddressPairs aap = aapIt.next();
                String ipAddrStr = aap.getIpAddress().getIpAddress().stringValue();
                String macAddrStr = aap.getMacAddress().getValue();
                sb.append(sgIt.next().getValue()).append("  ").append(ipAddrStr).append("[").append(macAddrStr)
                        .append("]").append("\n").append(String.format("%-79s", " "));
            }
            if (sgIt.hasNext()) {
                do {
                    sb.append(sgIt.next().getValue()).append("\n").append(String.format("%-95s", " "));
                } while (sgIt.hasNext());
            }
            if (aapIt.hasNext()) {
                do {
                    AllowedAddressPairs aap = aapIt.next();
                    String ipAddrStr = aap.getIpAddress().getIpAddress().stringValue();
                    String macAddrStr = aap.getMacAddress().getValue();
                    sb.append(String.format("%-36s", " ")).append(ipAddrStr).append("[").append(macAddrStr)
                            .append("]").append("\n").append(String.format("%-95s", " "));
                } while (aapIt.hasNext());
            }
            return sb.toString();
        }
        return "";
    }

    private static String getSgString(AclInterface value) {
        List<Uuid> securityGroups = value.getSecurityGroups();
        if (null != securityGroups) {
            Iterator<Uuid> sgIt = securityGroups.iterator();
            StringBuilder sb = new StringBuilder();
            if (sgIt.hasNext()) {
                do {
                    sb.append(sgIt.next().getValue()).append(String.format("%-22s", " "));
                } while (sgIt.hasNext());
            }
            return sb.toString();
        }
        return "";
    }

    private static String getAAPString(AclInterface value) {
        List<AllowedAddressPairs> aaps = value.getAllowedAddressPairs();
        if (null != aaps) {
            Iterator<AllowedAddressPairs> aapIt = aaps.iterator();
            StringBuilder sb = new StringBuilder();
            if (aapIt.hasNext()) {
                do {
                    AllowedAddressPairs aap = aapIt.next();
                    String ipAddrStr = aap.getIpAddress().getIpAddress().stringValue();
                    String macAddrStr = aap.getMacAddress().getValue();
                    sb.append(String.format("%s", " ")).append(ipAddrStr).append("[").append(macAddrStr).append("]")
                            .append("\n").append(String.format("%-23s", " "));
                } while (aapIt.hasNext());
            }
            return sb.toString();
        }
        return "";
    }
}
