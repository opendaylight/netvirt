package org.opendaylight.netvirt.aclservice.shell;

/**
 * Created by nishchyag on 04-09-2017.
 */

import java.util.*;
import java.util.concurrent.ConcurrentMap;

import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterfaceCacheUtil;
import org.opendaylight.netvirt.aclservice.api.utils.IAclDataUtil;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Command(scope = "aclservice", name = "display-acl-data-cache", description="")
public class DisplayAclDataCaches extends OsgiCommandSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(DisplayAclDataCaches.class);
    private IAclDataUtil aclDataUtil;
    private static final String KEY_TAB = "   %-8s";
    private static final String ACL_INT_TAB = "   %-4s  %-4s  %-4s  %-4s %-4s  %-4s  %-6s  %-20s  %-20s %-4s";
    private static final String ACL_INT_TAB_FOR = KEY_TAB + ACL_INT_TAB;
    private static final String ACL_INT_HEAD = String.format(ACL_INT_TAB_FOR, "UUID", "PortSecurityEnabled",
            "InterfaceId", "LPortTag", "DpId", "ElanId", "VpnId", "SecurityGroups", "AllowedAddressPairs",
            "SubnetIpPrefixes", "MarkedForDelete")
            + "\n   -------------------------------------------------------------------------------------------------";
    private static final String REM_ID_TAB = "   %-20s  ";
    private static final String REM_ID_TAB_FOR = KEY_TAB + REM_ID_TAB;
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

    public void setAclDataUtil(IAclDataUtil aclDataUtil) {
        this.aclDataUtil = aclDataUtil;
    }

    protected Object doExecute() throws Exception {
        if(aclDataUtil == null){
            session.getConsole().println("Failed to handle the command, AclData reference is null at this point");
            return null;
        }

        try {
            if (op == null) {
                session.getConsole().println("Please provide valid option");
                usage();
                session.getConsole().println(op_sel_str);
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
                    session.getConsole().println("invalid operation");
                    usage();
                    session.getConsole().println(op_sel_str);
            }
        } catch (Exception e) {
            log.error("failed to handle the command", e);
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
                exe_cmd_str+"aclInterface --all show | --uuid <uuid>");
    }

    void printRemoteAclIdMapHelp() {
        session.getConsole().println("invalid input");
        usage();
        session.getConsole().println(
                exe_cmd_str+"remoteAclId --all show | --uuid <uuid>");
    }

    void printAclFlowPriorityMapHelp() {
        session.getConsole().println("invalid input");
        usage();
        session.getConsole().println(
                exe_cmd_str+"aclFlowPriority --all show | --key <key>");
    }

    void printAclInterfaceCacheHelp() {
        session.getConsole().println("invalid input");
        usage();
        session.getConsole().println(
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
                session.getConsole().println("Invalid uuid" + e.getMessage());
                log.error("Invalid uuid" +e);
                return;
            }
            List<AclInterface> aclInterfaceList=aclDataUtil.getInterfaceList(_uuid);
            if(aclInterfaceList == null | aclInterfaceList.isEmpty()){
                session.getConsole().println("UUID not matched");
                return;
            }else {
                session.getConsole().println(String.format(ACL_INT_HEAD));
                for(AclInterface aclInterface : aclInterfaceList) {
                    session.getConsole().println(String.format(ACL_INT_TAB_FOR, " ",
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

            if(map == null || map.isEmpty()){
                session.getConsole().println("No data found");
                return;
            }else {
                session.getConsole().println(String.format(ACL_INT_HEAD));
                Iterator<Map.Entry<Uuid, List<AclInterface>>> entries  = map.entrySet().iterator();
                while(entries .hasNext()) {
                    Map.Entry<Uuid, List<AclInterface>> entry = entries.next();
                    Uuid key = entry.getKey();
                    session.getConsole().println(String.format(KEY_TAB, key.toString()));
                    List<AclInterface> aclIntLst = entry.getValue();
                    for (ListIterator<AclInterface> iter = entry.getValue().listIterator(); iter.hasNext(); ) {
                        AclInterface aclInterface = iter.next();
                        session.getConsole().println(String.format(ACL_INT_TAB, " ",
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
                session.getConsole().println("Invalid uuid" + e.getMessage());
                log.error("Invalid uuid" +e);
                return;
            }
            List<Uuid> remoteUuidLst=aclDataUtil.getRemoteAcl(_uuid);
            if(remoteUuidLst == null | remoteUuidLst.isEmpty()){
                session.getConsole().println("UUID not matched");
                return;
            }else {
                session.getConsole().println(String.format(REM_ID_HEAD));
                for(Uuid uuid : remoteUuidLst) {
                    session.getConsole().println(String.format(REM_ID_TAB_FOR, " ", uuid.getValue()));
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

            if(map == null || map.isEmpty()){
                session.getConsole().println("No data found");
                return;
            }else {
                session.getConsole().println(String.format(REM_ID_HEAD));
                Iterator<Map.Entry<Uuid, List<Uuid>>> entries  = map.entrySet().iterator();
                while(entries .hasNext()) {
                    Map.Entry<Uuid, List<Uuid>> entry = entries.next();
                    Uuid key = entry .getKey();
                    session.getConsole().println(String.format(KEY_TAB, key.toString()));
                    for (ListIterator<Uuid> iter = entry.getValue().listIterator(); iter.hasNext(); ) {
                        Uuid uuid = iter.next();
                        session.getConsole().println(String.format(REM_ID_TAB, " ", uuid.getValue()));
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
                session.getConsole().println("No data found");
                return;
            }
            session.getConsole().println(String.format(ACL_DATA_HEAD));
            session.getConsole().println(String.format(ACL_DATA_TAB_FOR, key, val));

            return;
        }

        if(all != null && key ==null){
            if(!validateAll(all)){
                printAclFlowPriorityMapHelp();
                return;
            }
            Map<String, Integer> map =aclDataUtil.getAclFlowPriorityMap();
            if(map == null || map.isEmpty()){
                session.getConsole().println("No data found");
                return;
            }else {
                session.getConsole().println(String.format(ACL_DATA_HEAD));
                for (Map.Entry<String, Integer> entry : map.entrySet()) {
                  session.getConsole().println(String.format(ACL_DATA_TAB_FOR, entry.getKey(), entry.getValue()));
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
                session.getConsole().println("No data found");
                return;
            }
            session.getConsole().println(String.format(ACL_INT_HEAD));
            session.getConsole().println(String.format(ACL_INT_TAB_FOR, key,
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
            if(map == null || map.isEmpty()){
                session.getConsole().println("No data found");
                return;
            }else {
                session.getConsole().println(String.format(ACL_INT_HEAD));
                for (Map.Entry<String, AclInterface> entry : map.entrySet()) {
                    AclInterface aclInterface = entry.getValue();
                    session.getConsole().println(String.format(ACL_INT_TAB_FOR, entry.getKey(),
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



