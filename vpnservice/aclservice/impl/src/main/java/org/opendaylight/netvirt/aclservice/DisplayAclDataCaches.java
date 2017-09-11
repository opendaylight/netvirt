package org.opendaylight.netvirt.aclservice;

/**
 * Created by nishchyag on 04-09-2017.
 */

import java.util.*;
import java.util.concurrent.ConcurrentMap;

import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterfaceCacheUtil;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.Commands;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "aclservice", name = "display-acl-data-cache", description="")
public class DisplayAclDataCaches extends OsgiCommandSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisplayAclDataCaches.class);
    private AclDataUtil aclDataUtil;
    private static final String ACL_INT_TAB_FOR = "   %-8s %-4s  %-4s  %-4s  %-4s %-4s  %-4s  %-6s  %-20s  %-20s %-4s";
    private static final String ACL_INT_HEAD = String.format(ACL_INT_TAB_FOR, "UUID", "PortSecurityEnabled",
            "InterfaceId", "LPortTag", "DpId", "ElanId", "VpnId", "SecurityGroups", "AllowedAddressPairs",
            "SubnetIpPrefixes", "MarkedForDelete")
            + "\n   -------------------------------------------------------------------------------------------------";
    private static final String REM_ID_TAB_FOR = "   %-8s %-20s  ";
    private static final String REM_ID_HEAD = String.format(REM_ID_TAB_FOR, "UUID", "Values")
            + "\n   -------------------------------------------------------------------------";
    private static final String ACL_DATA_TAB_FOR = "   %-8s %-8s  ";
    private static final String ACL_DATA_HEAD = String.format(ACL_DATA_TAB_FOR, "Key", "Value")
            + "\n   -------------------------------------------------------------------------";
    private final String exe_cmd_str = "exec display-acl-data-cache -op ";
    private final String op_selections = "[ aclInterface | remoteAclId | aclFlowPriority | aclInterfaceCache ]";
    private String op_sel_str = exe_cmd_str + op_selections;


    @Option(name = "-op", aliases = { "--option",
            "--op" }, description = op_selections, required = false, multiValued = false)
    private String op;

    @Option(name = "--uuid", description = "uuid for the aclInterface or remoteAclId", required = false, multiValued = false)
    private String uuid_str;


    @Option(name = "--all", description = "display the complete selected map", required = false, multiValued = false)
    private String all ;

    @Option(name = "--key", description = "key for aclFlowPriority or aclInterfaceCache", required = false, multiValued = false)
    private String key;



    protected Object doExecute() throws Exception {
        aclDataUtil = Commands.getAclDataUtil();
        if(aclDataUtil == null){
            System.out.println("failed to handle the command, AclData reference is null at this point");
            return null;
        }

        try {
            if (op == null) {
                System.out.println("Please provide valid option");
                usage();
                System.out.println(op_sel_str);
            }
            switch (op) {
                case "aclInterface":
                    getAclInterfaceMap();
                    break;
                case "remoteAclId":
                    getRemoteAclIdMap();
                    break;
                case "aclFlowPriority":
                    getAclFlowPriorityMap();
                    break;
                case "aclInterfaceCache":
                    getAclInterfaceCache();
                    break;
                default:
                    System.out.println("invalid operation");
                    usage();
                    System.out.println(op_sel_str);
            }
        } catch (Exception e) {
            log.error("failed to handle the command", e);
        }
        return null;
    }

    void usage() {
        System.out.println("usage:");
    }

    void printAclInterfaceMapHelp() {
        System.out.println("invalid input");
        usage();
        System.out.println(
                exe_cmd_str+"aclInterface --all show | --uuid <uuid>");
    }

    void printRemoteAclIdMapHelp() {
        System.out.println("invalid input");
        usage();
        System.out.println(
                exe_cmd_str+"remoteAclId --all show | --uuid <uuid>");
    }

    void printAclFlowPriorityMapHelp() {
        System.out.println("invalid input");
        usage();
        System.out.println(
                exe_cmd_str+"aclFlowPriority --all show | --key <key>");
    }

    void printAclInterfaceCacheHelp() {
        System.out.println("invalid input");
        usage();
        System.out.println(
                exe_cmd_str+"aclInterfaceCache --all show | --key <key>");
    }

    private boolean validateAll(String all) {
        if(all.equalsIgnoreCase("show")){
            return true;
        }
        return false;
    }

    protected void getAclInterfaceMap() throws Exception {
        if(all == null && uuid_str == null){
            printAclInterfaceMapHelp();
            return;
        }
        if (all == null && uuid_str !=null) {
            Uuid _uuid;
            try{
                _uuid = Uuid.getDefaultInstance(uuid_str);
            }catch(IllegalArgumentException e){
                System.out.println("Invalid uuid" + e.getMessage());
                log.error("Invalid uuid" +e);
                return;
            }
            List<AclInterface> aclInterfaceList=aclDataUtil.getInterfaceList(_uuid);
            if(aclInterfaceList == null | aclInterfaceList.isEmpty()){
                System.out.println("UUID not matched");
                return;
            }else {
                System.out.println(String.format(ACL_INT_HEAD));
                for(AclInterface aclInterface : aclInterfaceList) {
                    System.out.println(String.format(ACL_INT_TAB_FOR, _uuid.toString(),
                            aclInterface.isPortSecurityEnabled(), aclInterface.getInterfaceId(),
                            aclInterface.getLPortTag(), aclInterface.getDpId(), aclInterface.getElanId(),
                            aclInterface.getVpnId(), aclInterface.getSecurityGroups(),
                            aclInterface.getAllowedAddressPairs(), aclInterface.getSubnetIpPrefixes(),
                            aclInterface.isMarkedForDelete()));
                }
                return;
            }
        }
        if(all != null && uuid_str ==null){
            if(!validateAll(all)){
                printAclInterfaceMapHelp();
                return;
            }
            Map<Uuid, List<AclInterface>> map =aclDataUtil.getAclInterfaceMap();
            if(map == null | map.isEmpty()){
                System.out.println("No data found");
                return;
            }else {
                System.out.println(String.format(ACL_INT_HEAD));
                for (Map.Entry<Uuid, List<AclInterface>> entry : map.entrySet()) {
                    Uuid key = entry.getKey();
                    System.out.println(String.format(ACL_INT_TAB_FOR, key.toString()));
                    List<AclInterface> aclIntLst = entry.getValue();
                    for(AclInterface aclInterface : aclIntLst) {
                        System.out.println(String.format(ACL_INT_TAB_FOR, " ",
                                aclInterface.isPortSecurityEnabled(), aclInterface.getInterfaceId(),
                                aclInterface.getLPortTag(), aclInterface.getDpId(), aclInterface.getElanId(),
                                aclInterface.getVpnId(), aclInterface.getSecurityGroups(),
                                aclInterface.getAllowedAddressPairs(), aclInterface.getSubnetIpPrefixes(),
                                aclInterface.isMarkedForDelete()));
                    }
                }
                return;
            }
        }
    }

    protected void getRemoteAclIdMap() throws Exception{
        if(all == null && uuid_str == null){
            printRemoteAclIdMapHelp();
            return;
        }
        if (all == null && uuid_str !=null) {
            Uuid _uuid;
            try{
                _uuid = Uuid.getDefaultInstance(uuid_str);
            }catch(IllegalArgumentException e){
                System.out.println("Invalid uuid" + e.getMessage());
                log.error("Invalid uuid" +e);
                return;
            }
            List<Uuid> remoteUuidLst=aclDataUtil.getRemoteAcl(_uuid);
            if(remoteUuidLst == null | remoteUuidLst.isEmpty()){
                System.out.println("UUID not matched");
                return;
            }else {
                System.out.println(String.format(REM_ID_HEAD));
                for(Uuid uuid : remoteUuidLst) {
                    System.out.println(String.format(REM_ID_TAB_FOR, " ", uuid.getValue()));
                }
                return;
            }
        }
        if(all != null && uuid_str ==null){
            if(!validateAll(all)){
                printRemoteAclIdMapHelp();
                return;
            }
            Map<Uuid, List<Uuid>> map =aclDataUtil.getRemoteAclIdMap();
            if(map == null | map.isEmpty()){
                System.out.println("No data found");
                return;
            }else {
                System.out.println(String.format(REM_ID_HEAD));
                for (Map.Entry<Uuid, List<Uuid>> entry : map.entrySet()) {
                    Uuid key = entry.getKey();
                    System.out.println(String.format(REM_ID_TAB_FOR, key.toString()));
                    List<Uuid> uuidLst = entry.getValue();
                    for(Uuid uuid : uuidLst) {
                        System.out.println(String.format(REM_ID_TAB_FOR, " ", uuid.getValue()));
                    }
                }
                return;
            }
        }
    }

    protected void getAclFlowPriorityMap() throws Exception{
        if(all == null && key == null){
            printAclFlowPriorityMapHelp();
            return;
        }
        if (all == null && key !=null) {
            Integer val = aclDataUtil.getAclFlowPriority(key);
            if(val == null){
                System.out.println("No data found");
                return;
            }
            System.out.println(String.format(ACL_DATA_HEAD));
            System.out.println(String.format(ACL_DATA_TAB_FOR, key, val));

            return;
        }

        if(all != null && key ==null){
            if(!validateAll(all)){
                printAclFlowPriorityMapHelp();
                return;
            }
            Map<String, Integer> map =aclDataUtil.getAclFlowPriorityMap();
            if(map == null | map.isEmpty()){
                System.out.println("No data found");
                return;
            }else {
                System.out.println(String.format(ACL_DATA_HEAD));
                for (Map.Entry<String, Integer> entry : map.entrySet()) {
                    System.out.println(String.format(ACL_DATA_TAB_FOR, entry.getKey(), entry.getValue()));
                }
                return;
            }
        }
    }

    protected void getAclInterfaceCache() throws Exception{
        if(all == null && key == null){
            printAclInterfaceCacheHelp();
            return;
        }
        if (all == null && key !=null) {
            AclInterface aclInterface = AclInterfaceCacheUtil.getAclInterfaceFromCache(key);
            if(aclInterface == null){
                System.out.println("No data found");
                return;
            }
            System.out.println(String.format(ACL_INT_HEAD));
            System.out.println(String.format(ACL_INT_TAB_FOR, key,
                    aclInterface.isPortSecurityEnabled(), aclInterface.getInterfaceId(),
                    aclInterface.getLPortTag(), aclInterface.getDpId(), aclInterface.getElanId(),
                    aclInterface.getVpnId(), aclInterface.getSecurityGroups(),
                    aclInterface.getAllowedAddressPairs(), aclInterface.getSubnetIpPrefixes(),
                    aclInterface.isMarkedForDelete()));

            return;
        }

        if(all != null && key ==null){
            if(!validateAll(all)){
                printAclInterfaceCacheHelp();
                return;
            }
            ConcurrentMap<String, AclInterface> map =AclInterfaceCacheUtil.getAclInterfaceCache();
            if(map == null | map.isEmpty()){
                System.out.println("No data found");
                return;
            }else {
                System.out.println(String.format(ACL_INT_HEAD));
                for (Map.Entry<String, AclInterface> entry : map.entrySet()) {
                    AclInterface aclInterface = entry.getValue();
                    System.out.println(String.format(ACL_INT_TAB_FOR, entry.getKey(),
                            aclInterface.isPortSecurityEnabled(), aclInterface.getInterfaceId(),
                            aclInterface.getLPortTag(), aclInterface.getDpId(), aclInterface.getElanId(),
                            aclInterface.getVpnId(), aclInterface.getSecurityGroups(),
                            aclInterface.getAllowedAddressPairs(), aclInterface.getSubnetIpPrefixes(),
                            aclInterface.isMarkedForDelete()));
                }
            }
            return;
        }
    }
}



