import constants as const
import flow_parser as fp
import json
import netvirt_utils as utils
from collections import defaultdict
from mdsal.model import Model
from mdsal import elan
from mdsal import id_manager
from mdsal import ietf_interfaces
from mdsal import interface_service_bindings
from mdsal import itm_state
from mdsal import l3vpn
from mdsal import mip
from mdsal import network_topology
from mdsal import neutron
from mdsal import odl_fib
from mdsal import odl_interface_meta
from mdsal import odl_l3vpn
from mdsal import opendaylight_inventory


# Required
ifaces = None
ifstates = None

#Optional
ports = {}
tunnels = {}
confNodes = {}
operNodes = {}


modelpath = "/tmp/robotjob/s1-t1_Create_VLAN_Network_net_1/models"
elan_elan_instances = None
elan_elan_interfaces = None
id_manager_id_pools = None
ietf_interfaces_interfaces = None
ietf_interfaces_interfaces_state = None
interface_service_bindings_service_bindings = None
itm_state_tunnels_state = None
l3vpn_vpn_interfaces = None
mip_mac = None
network_topology_network_topology_config = None
network_topology_network_topology_operational = None
neutron_neutron = None
odl_fib_fib_entries = None
odl_interface_meta_if_index_interface_map = None
odl_inventory_nodes_config = None
odl_inventory_nodes_operational = None
odl_l3vpn_vpn_instance_to_vpn_id = None


def by_ifname(ifname):
    ifstate = ifstates.get(ifname)
    iface = ifaces.get(ifname)
    port = None
    tunnel = None
    tunState = None
    if iface and iface.get('type') == const.IFTYPE_VLAN:
        ports = neutron_neutron.get_ports_by_key()
        port = ports.get(ifname)
    elif iface and iface.get('type') == const.IFTYPE_TUNNEL:
        tunnels = itm_state_tunnels_state.get_tunnels_by_key()
        tunnel = tunnels.get(ifname)
        tunStates = dsg.get_tunnel_states()
        tunState = tunStates.get(ifname)
    else:
        print "UNSUPPORTED IfType"
    return iface,ifstate,port,tunnel,tunState


def print_keys():
    print "InterfaceNames: ", ifaces.keys()
    print
    print "IfStateNames: ", ifstates.keys()


def analyze_interface(ifname=None):
    global ifaces, ifstates
    if not ifname:
        print_keys()
        exit(1)
    ifname = ifname[0]
    ifaces = ietf_interfaces_interfaces.get_interfaces_by_key()
    ifstates = ietf_interfaces_interfaces_state.get_interfaces_by_key()
    iface,ifstate,port,tunnel,tunState = by_ifname(ifname)
    print "InterfaceConfig: "
    json.dumps(iface, indent=2)
    print "InterfaceState: "
    json.dumps(ifstate, indent=2)
    if port:
        print "NeutronPort: "
        json.dumps(port, indent=2)
        analyze_neutron_port(port, iface, ifstate)
        return
    if tunnel:
        print "Tunnel: "
        json.dumps(tunnel, indent=2)
    if tunState:
        print "TunState: "
        json.dumps(tunState, indent=2)
    if ifstate:
        ncId = ifstate.get('lower-layer-if')[0]
        nodeId = ncId[:ncId.rindex(':')]
        analyze_inventory(nodeId, True, ncId, ifname)
        #analyze_inventory(nodeId, False, ncId, ifname)

def analyze_neutron_port(port, iface, ifstate):
    for flow in utils.sort(get_all_flows(['all']), 'table'):
        if ((flow.get('ifname') == port['uuid']) or
            (flow.get('lport') and ifstate and flow['lport'] == ifstate.get('if-index')) or
            (iface['name'] == flow.get('ifname'))):
                result = 'Table:{},FlowId:{}{}'.format(
                flow['table'], flow['id'],
                utils.show_optionals(flow))
                print result
                print 'Flow:', json.dumps(parse_flow(flow.get('flow')))


def analyze_inventory(nodeId, isConfig=True, ncId=None, ifName=None):
    if isConfig:
        nodes = odl_inventory_nodes_config.get_nodes_by_key()
        print "Inventory Config:"
    else:
        print "Inventory Operational:"
        nodes = odl_inventory_nodes_operational.get_nodes_by_key()
    node = nodes.get(nodeId)
    tables = node.get(const.NODE_TABLE)
    groups = node.get(const.NODE_GROUP)
    flow_list = []
    print "Flows:"
    for table in tables:
        for flow in table.get('flow', []):
            if not ifName or ifName in utils.nstr(flow.get('flow-name')):
                flow_dict = {}
                flow_dict['table'] = table['id']
                flow_dict['id'] = flow['id']
                flow_dict['name'] = flow.get('flow-name')
                flow_dict['flow'] = flow
                flow_list.append(flow_dict)
    flows = sorted(flow_list, key=lambda x: x['table'])
    for flow in flows:
        print 'Table:', flow['table']
        print 'FlowId:', flow['id'], 'FlowName:', flow.get('name')


def get_dpn_host_mapping(oper_nodes=None):
        nodes_dict = {}
        nodes = oper_nodes or odl_inventory_nodes_operational.get_nodes_by_key()
        for node in nodes.itervalues():
            dpnid = utils.get_dpn_from_ofnodeid(node['id'])
            nodes_dict[dpnid] = node.get('flow-node-inventory:description', '')
        return nodes_dict


def get_groups(ofnodes=None):
    of_nodes = ofnodes or odl_inventory_nodes_config.get_nodes_by_key()
    key ='group-id'
    group_dict = defaultdict(dict)
    for node in of_nodes.itervalues():
        dpnid = utils.get_dpn_from_ofnodeid(node['id'])
        for group in node.get(const.NODE_GROUP, []):
            if group_dict.get(dpnid) and group_dict.get(dpnid).get(group[key]):
                print 'Duplicate:', dpnid, group[key]
            group_dict[dpnid][group[key]] = group
    return dict(group_dict)


def get_stale_flows(modules=['ifm']):
    if not modules:
        return 'No modules specified'
    ifaces = {}
    ifstates = {}
    ifindexes = {}
    bindings = {}
    einsts = {}
    eifaces = {}
    fibentries = {}
    vpnids = {}
    vpninterfaces = {}
    groups = {}
    table_list = list(set([table for module in modules for table in const.TABLE_MAP[module]]))
    ##table_list = [214, 244]
    of_nodes = odl_inventory_nodes_config.get_nodes_by_key()
    if 'ifm' in modules:
        ifaces = ietf_interfaces_interfaces.get_interfaces_by_key()
        ifstates = ietf_interfaces_interfaces_state.get_interfaces_by_key()
    if 'l3vpn' in modules:
        ifaces = ifaces or ietf_interfaces_interfaces.get_interfaces_by_key()
        ifindexes = ifindexes or odl_interface_meta_if_index_interface_map.get_if_index_interfaces_by_key()
        fibentries = fibentries or odl_fib_fib_entries.get_vrf_entries_by_key()
        vpnids = vpnids or odl_l3vpn_vpn_instance_to_vpn_id.get_vpn_ids_by_key()
        vpninterfaces = vpninterfaces or l3vpn_vpn_interfaces.get_vpn_ids_by_key()
        groups = groups or get_groups(of_nodes)
    if 'acl' in modules:
        ifaces = ifaces or ietf_interfaces_interfaces.get_interfaces_by_key()
        ifindexes = ifindexes or odl_interface_meta_if_index_interface_map.get_if_index_interfaces_by_key()
        einsts = einsts or elan_elan_instances.get_elan_instances_by_key()
        eifaces = eifaces or elan_elan_interfaces.get_elan_interfaces()
    if 'elan' in modules:
        ifaces = ifaces or ietf_interfaces_interfaces.get_interfaces_by_key()
        einsts = einsts or elan_elan_instances.get_elan_instances_by_key()
        eifaces = eifaces or elan_elan_interfaces.get_elan_interfaces()
        ifindexes = ifindexes or odl_interface_meta_if_index_interface_map.get_if_index_interfaces_by_key()
    stale_flows = []
    for node in of_nodes.itervalues():
        tables = [x for x in node[const.NODE_TABLE] if x['id'] in table_list]
        for table in tables:
            for flow in table.get('flow', []):
                flow_dict = None
                flow_info = {}
                flow_info['dpnid'] = utils.get_dpn_from_ofnodeid(node['id'])
                if 'ifm' in modules and table['id'] in const.TABLE_MAP['ifm']:
                    flow_dict = fp.stale_ifm_flow(flow, flow_info, ifaces, ifstates)
                if 'l3vpn' in modules and table['id'] in const.TABLE_MAP['l3vpn']:
                    flow_dict = fp.stale_l3vpn_flow(flow, flow_info, groups, ifaces, ifindexes, vpnids, vpninterfaces, fibentries)
                if 'elan' in modules and table['id'] in const.TABLE_MAP['elan']:
                    flow_dict = fp.stale_elan_flow(flow, flow_info, ifaces, ifindexes, einsts, eifaces)
                if 'acl' in modules and table['id'] in const.TABLE_MAP['acl']:
                    flow_dict = fp.stale_acl_flow(flow, flow_info, ifaces, ifindexes, einsts, eifaces)
                if flow_dict is not None:
                    stale_flows.append(flow_dict)

    return stale_flows


def show_stale_bindings():
    stale_ids, bindings = get_stale_bindings()
    for iface_id in sorted(stale_ids):
        for binding in bindings[iface_id].itervalues():
            #if binding.get('bound-services'):
            path = get_data_path('bindings', binding)
            print json.dumps(bindings[iface_id])
            print('http://192.168.2.32:8383/restconf/config/{}'.format(path))


def get_stale_bindings():
    ifaces = ietf_interfaces_interfaces.get_interfaces_by_key()
    bindings, orphans = interface_service_bindings_service_bindings.get_service_bindings()
    return set(bindings.keys()) - set(ifaces.keys()), bindings


def get_ips_for_iface(nports, ifname):
    ips = []
    port = nports.get(ifname) if ifname else None
    fixed_ips = port.get('fixed-ips', []) if port else []
    for fixed_ip in fixed_ips:
        ips.append(fixed_ip['ip-address'])
    return ips


def show_link_flow_binding():
    stale_ids, bindings = get_stale_bindings()
    flows = get_stale_flows()
    print len(stale_ids), len(flows)
    for flow in flows:
        if flow['ifname'] in stale_ids and 'bound-services' in bindings[flow['ifname']]:
            print 'Flow with binding: ', flow['ifname']
        else:
            print 'Flow without binding: ', flow['ifname']


def show_stale_flows(sort_by='table'):
    compute_map = get_dpn_host_mapping()
    nports = neutron_neutron.get_ports_by_key()
    for flow in utils.sort(get_stale_flows(['ifm', 'acl', 'elan', 'l3vpn']), sort_by):
        host = compute_map.get(flow.get('dpnid'), flow.get('dpnid'))
        ip_list = get_ips_for_iface(nports, flow.get('ifname'))
        if ip_list:
            flow['iface-ips'] = ip_list
        result = 'Table:{},Host:{},FlowId:{}{}'.format(
            flow['table'], host, flow['id'],
            utils.show_optionals(flow))
        print result
        ##path = get_data_path('flows', flow)
        #print('http://192.168.2.32:8383/restconf/config/{}'.format(path))
        #print 'Flow:', json.dumps(parse_flow(flow['flow']))


def get_all_flows(modules=['ifm'], filter=[]):
    if not modules:
        return 'No modules specified'
    ifaces = {}
    ifstates = {}
    ifindexes = {}
    bindings = {}
    einsts = {}
    eifaces = {}
    fibentries = {}
    vpnids = {}
    vpninterfaces = {}
    groups = {}
    if 'all' in modules:
        table_list = list(range(0, 255))
    else:
        table_list = list(set([table for module in modules for table in const.TABLE_MAP[module]]))
    ##table_list = [214, 244]
    of_nodes = odl_inventory_nodes_config.get_nodes_by_key()
    if 'ifm' in modules:
        ifaces = ietf_interfaces_interfaces.get_interfaces_by_key()
        ifstates = ietf_interfaces_interfaces_state.get_interfaces_by_key()
    if 'l3vpn' in modules:
        ifaces = ifaces or ietf_interfaces_interfaces.get_interfaces_by_key()
        ifindexes = ifindexes or odl_interface_meta_if_index_interface_map.get_if_index_interfaces_by_key()
        fibentries = fibentries or dsg.get_fibentries_by_label()
        vpnids = vpnids or odl_l3vpn_vpn_instance_to_vpn_id.get_vpn_ids_by_key()
        vpninterfaces = vpninterfaces or l3vpn_vpn_interfaces.get_vpn_ids_by_key()
        groups = groups or get_groups(of_nodes)
    if 'acl' in modules:
        ifaces = ifaces or ietf_interfaces_interfaces.get_interfaces_by_key()
        ifindexes = ifindexes or odl_interface_meta_if_index_interface_map.get_if_index_interfaces_by_key()
        einsts = einsts or elan_elan_instances.get_elan_instances_by_key()
        eifaces = eifaces or elan_elan_interfaces.get_elan_interfaces()
    if 'elan' in modules:
        ifaces = ifaces or ietf_interfaces_interfaces.get_interfaces_by_key()
        einsts = einsts or elan_elan_instances.get_elan_instances_by_key()
        eifaces = eifaces or elan_elan_interfaces.get_elan_interfaces()
        ifindexes = ifindexes or odl_interface_meta_if_index_interface_map.get_if_index_interfaces_by_key()
    if 'all' in modules:
        groups = groups or get_groups(of_nodes)
        ifaces = ifaces or ietf_interfaces_interfaces.get_interfaces_by_key()
        ifstates = ifstates or ietf_interfaces_interfaces_state.get_interfaces_by_key()
        ifindexes = ifindexes or odl_interface_meta_if_index_interface_map.get_if_index_interfaces_by_key()
        fibentries = fibentries or dsg.get_fibentries_by_label()
        vpnids = vpnids or odl_l3vpn_vpn_instance_to_vpn_id.get_vpn_ids_by_key()
        vpninterfaces = vpninterfaces or l3vpn_vpn_interfaces.get_vpn_ids_by_key()
        einsts = einsts or elan_elan_instances.get_elan_instances_by_key()
        eifaces = eifaces or elan_elan_interfaces.get_elan_interfaces()
    flows = []
    for node in of_nodes.itervalues():
        tables = [x for x in node[const.NODE_TABLE] if x['id'] in table_list]
        for table in tables:
            for flow in table.get('flow', []):
                flow_dict = None
                flow_info = {}
                flow_info['dpnid'] = utils.get_dpn_from_ofnodeid(node['id'])
                flow_dict = fp.get_any_flow(flow, flow_info, groups,
                                        ifaces, ifstates, ifindexes,
                                        fibentries, vpnids, vpninterfaces,
                                        einsts, eifaces)
                if (flow_dict is not None and
                        utils.filter_flow(flow_dict, filter)):
                    flows.append(flow_dict)
    return flows


def show_flows(modules=['ifm'], sort_by='table', filter_by=[]):
    compute_map = get_dpn_host_mapping()
    nports = neutron_neutron.get_ports_by_key()
    for flow in utils.sort(get_all_flows(modules, filter_by), sort_by):
        host = compute_map.get(flow.get('dpnid'), flow.get('dpnid'))
        ip_list = get_ips_for_iface(nports, flow.get('ifname'))
        if ip_list:
            flow['iface-ips'] = ip_list
        result = 'Table:{},Host:{},FlowId:{}{}'.format(
            flow['table'], host, flow['id'],
            utils.show_optionals(flow))
        print result
        print 'Flow:', json.dumps(parse_flow(flow['flow']))


def show_all_flows():
    show_flows(modules=['all'])


def show_elan_flows():
    compute_map = get_dpn_host_mapping()
    for flow in utils.sort(get_all_flows(['elan']), 'id'):
        host = compute_map.get(flow.get('dpnid'), flow.get('dpnid'))
        result = 'MacHost:{}{},Table:{},FlowId:{},{},Flow:{}'.format(flow['id'][-17:],host,flow['table'],flow['id'],utils.show_optionals(flow),json.dumps(parse_flow(flow['flow'])))
        print result
        #print 'Flow:', json.dumps(parse_flow(flow['flow']))


def get_matchstr(flow):
    if flow and flow.get('flow') and flow.get('flow').get('match'):
        return json.dumps(flow.get('flow').get('match', None))


def get_key_for_dup_detect(flow):
    result = '{}:{}:{}'.format(flow.get('dpnid'), flow.get('table'), get_matchstr(flow))
    return result


def show_dup_flows():
    mmac = mip_mac.get_entries_by_key()
    einsts = elan_elan_instances.get_elan_instances_by_key()
    flows = utils.sort(get_all_flows(['elan']), 'table')
    matches = defaultdict(list)
    compute_map = get_dpn_host_mapping()
    for flow in flows:
        dup_key = get_key_for_dup_detect(flow)
        if dup_key:
            if matches and matches.get(dup_key):
                matches[dup_key].append(flow)
            else:
                matches[dup_key].append(flow)
    for k, v in matches.iteritems():
        if len(v) > 1:
            dpnid = k.split(':')[0]
            host = compute_map.get(dpnid, dpnid)
            result = 'Host:{},FlowCount:{},MatchKey:{},ElanTag:{}'.format(host, len(v), k,v[0].get('elan-tag'))
            print result
            for idx, flow in enumerate(v):
                result = "Duplicate"
                mac_addr = flow.get('dst-mac')
                if mac_addr and mmac.get(mac_addr):
                    result = fp.is_correct_elan_flow(flow, mmac.get(mac_addr), einsts, host)
                print '    {}Flow-{}:{}'.format(result, idx, json.dumps(parse_flow(flow.get('flow'))))


def show_learned_mac_flows():
    nports = neutron_neutron.get_ports_by_key(key_field='mac-address')
    flows = utils.sort(get_all_flows(['elan']), 'table')
    compute_map = get_dpn_host_mapping()
    for flow_info in flows:
        flow = flow_info.get('flow')
        dpnid = flow_info.get('dpnid')
        host = compute_map.get(dpnid, dpnid)
        if ((flow_info.get('table') == 50 and
                flow.get('idle-timeout') == 300 and not
                nports.get(flow_info.get('src-mac'))) or
                (flow_info.get('table') == 51 and
                 not nports.get(flow_info.get('dst-mac')))):
            result = 'Table:{},Host:{},FlowId:{}{}'.format(
                flow_info.get('table'), host, flow.get('id'),
                utils.show_optionals(flow_info))
            print result
            print 'Flow:{}'.format(json.dumps(parse_flow(flow)))


def show_elan_instances():
    insts = elan_elan_instances.get_elan_instances_by_key()
    json.dumps(insts)


def get_duplicate_ids():
    duplicate_ids= {}
    for pool in id_manager_id_pools.get_id_pools_by_key().itervalues():
        id_values = {}
        for id_entry in pool.get('id-entries', []):
            id_info = {}
            id_value = id_entry.get('id-value')[0]
            id_key = id_entry.get('id-key')
            if id_values and id_values.get(id_value, None):
                key_list = id_values.get(id_value)
                key_list.append(id_key)
                id_info['id-value'] = id_value
                id_info['id-keys'] = key_list
                id_info['pool-name'] = pool.get('pool-name')
                id_info['parent-pool-name'] = pool.get('parent-pool-name')
                duplicate_ids[id_value] = id_info
            else:
                id_values[id_value] = [id_key]
    return duplicate_ids



def show_idpools():
    ports = neutron_neutron.get_ports_by_key()
    iface_ids = []
    for k,v in get_duplicate_ids().iteritems():
        result = "Id:{},Keys:{}".format(k, json.dumps(v.get('id-keys')))
        if v.get('pool-name'):
            result = "{},Pool:{}".format(result, v.get('pool-name'))
            if v.get('pool-name') == 'interfaces':
                iface_ids.extend(v.get('id-keys'))
        if v.get('parent-pool-name'):
            result = "{},ParentPool:{}".format(result, v.get('parent-pool-name'))
        print result
    print "\nNeutron Ports"
    print "============="
    for id in iface_ids:
        port = ports.get(id, {})
        print "Iface={}, NeutronPort={}".format(id, json.dumps(port))


def parse_flow(flow):
    #parse flow fields
    #hex(int(mask, 16) & int(data, 16))
    if flow['cookie']:
        utils.to_hex(flow, 'cookie')
    # parse instructions
    for instruction in flow['instructions'].get('instruction', []):
        if 'write-metadata' in instruction:
            utils.to_hex(instruction['write-metadata'],'metadata')
            utils.to_hex(instruction['write-metadata'],'metadata-mask')
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions'].get('action', []):
                if 'openflowplugin-extension-nicira-action:nx-reg-load' in action:
                    utils.to_hex(action['openflowplugin-extension-nicira-action:nx-reg-load'], 'value')
    # parse matches
    if 'metadata' in flow['match']:
        metadata = flow['match']['metadata']
        utils.to_hex(metadata,'metadata')
        utils.to_hex(metadata,'metadata-mask')

    for ofex in flow['match'].get('openflowplugin-extension-general:extension-list', []):
        if ofex['extension-key'] == 'openflowplugin-extension-nicira-match:nxm-nx-reg6-key':
            utils.to_hex(ofex['extension']['openflowplugin-extension-nicira-match:nxm-nx-reg'], 'value')

    return flow


def get_data_path(res_type, data):
    if res_type == 'bindings':
        return 'interface-service-bindings:service-bindings/services-info/{}/{}'.format(data['interface-name'],data['service-mode'])
    elif res_type == 'flows':
        return 'opendaylight-inventory:nodes/node/openflow:{}/flow-node-inventory:table/{}/flow/{}'.format(data['dpnid'],data['table'],data['id'])


# Sample method that shows how to use
def show_all_tables():
    of_nodes = odl_inventory_nodes_config.get_nodes_by_key()
    tables = set()
    for node in of_nodes.itervalues():
        for table in node[const.NODE_TABLE]:
            if table.get('flow'):
                tables.add(table['id'])
    print list(tables)


def show_all_groups():
    of_nodes = odl_inventory_nodes_config.get_nodes_by_key()
    groups = get_groups(of_nodes)
    for dpn in groups:
        for group_key in groups[dpn]:
            print 'Dpn:', dpn, 'ID:', group_key, 'Group:', json.dumps(groups[dpn][group_key])


def analyze_trunks():
    nports = neutron_neutron.get_ports_by_key()
    ntrunks = neutron_neutron.get_trunks_by_key()
    vpninterfaces = l3vpn_vpn_interfaces.get_vpn_ids_by_key()
    ifaces = ietf_interfaces_interfaces.get_interfaces_by_key()
    ifstates = ietf_interfaces_interfaces_state.get_interfaces_by_key()
    subport_dict = {}
    for v in ntrunks.itervalues():
        nport = nports.get(v.get('port-id'))
        s_subports = []
        for subport in v.get('sub-ports'):
            sport_id = subport.get('port-id')
            snport = nports.get(sport_id)
            svpniface = vpninterfaces.get(sport_id)
            siface = ifaces.get(sport_id)
            sifstate = ifstates.get(sport_id)
            subport['SubNeutronPort'] = 'Correct' if snport else 'Wrong'
            subport['SubVpnInterface'] = 'Correct' if svpniface else 'Wrong'
            subport['ofport'] = utils.get_ofport_from_ncid(sifstate.get('lower-layer-if')[0]) 
            if siface:
                vlan_mode = siface.get('odl-interface:l2vlan-mode')
                parent_iface_id = siface.get('odl-interface:parent-interface')
                if vlan_mode !='trunk-member':
                    subport['SubIface'] = 'WrongMode'
                elif parent_iface_id !=v.get('port-id'):
                    subport['SubIface'] = 'WrongParent'
                elif siface.get('odl-interface:vlan-id') !=subport.get('segmentation-id'):
                    subport['SubIface'] = 'WrongVlanId'
                else:
                    subport['SubIface'] = 'Correct'
            else:
                subport['SubIface'] = 'Wrong'
            s_subport = 'SegId:{},PortId:{},SubNeutronPort:{},SubIface:{},SubVpnIface:{}'.format(
                    subport.get('segmentation-id'),subport.get('port-id'),
                    subport.get('SubNeutronPort'),
                    subport.get('SubIface'),
                    subport.get('SubVpnInterface'))
            s_subports.append(subport)
            subport_dict[subport['port-id']] = subport
        s_trunk = 'TrunkName:{},TrunkId:{},PortId:{},NeutronPort:{},SubPorts:{}'.format(
                v.get('name'), v.get('uuid'), v.get('port-id'),
                'Correct' if nport else 'Wrong', json.dumps(s_subports))
        print s_trunk
    print '\n------------------------------------'
    print   'Analyzing Flow status for SubPorts'
    print   '------------------------------------'
    for flow in utils.sort(get_all_flows(['ifm'], ['vlanid']), 'ifname'):
        subport = subport_dict.get(flow.get('ifname')) or None
        vlanid = subport.get('segmentation-id') if subport else None
        ofport = subport.get('ofport') if subport else None
        flow_status = 'Okay'
        if flow.get('ofport') and flow.get('ofport') != ofport:
            flow_status = 'OfPort mismatch for SubPort:{} and Flow:{}'.format(subport, flow.get('flow'))
        if flow.get('vlanid') and flow.get('vlanid') != vlanid:
            flow_status = 'VlanId mismatch for SubPort:{} and Flow:{}'.format(subport, flow.get('flow'))
        if subport:
            print 'SubPort:{},Table:{},FlowStatus:{}'.format(
                    subport.get('port-id'), flow.get('table'), flow_status)

def get_all_dumps():
    dsg.get_all_dumps()


def main(args=None):
    global elan_elan_instances
    global elan_elan_interfaces
    global id_manager_id_pools
    global ietf_interfaces_interfaces
    global ietf_interfaces_interfaces_state
    global interface_service_bindings_service_bindings
    global itm_state_tunnels_state
    global l3vpn_vpn_interfaces
    global mip_mac
    global network_topology_network_topology_config
    global network_topology_network_topology_operational
    global neutron_neutron
    global odl_fib_fib_entries
    global odl_interface_meta_if_index_interface_map
    global odl_inventory_nodes_config
    global odl_inventory_nodes_operational
    global odl_l3vpn_vpn_instance_to_vpn_id
    options, args = utils.parse_args()

    elan_elan_instances = elan.elan_instances(Model.CONFIG, modelpath)
    elan_elan_interfaces = elan.elan_interfaces(Model.CONFIG, modelpath)
    id_manager_id_pools = id_manager.id_pools(Model.CONFIG, modelpath)
    ietf_interfaces_interfaces = ietf_interfaces.interfaces(Model.CONFIG, modelpath)
    ietf_interfaces_interfaces_state = ietf_interfaces.interfaces_state(Model.OPERATIONAL, modelpath)
    interface_service_bindings_service_bindings = interface_service_bindings.service_bindings(Model.CONFIG, modelpath)
    itm_state_tunnels_state = itm_state.tunnels_state(Model.OPERATIONAL, modelpath)
    l3vpn_vpn_interfaces = l3vpn.vpn_instance_to_vpn_id(Model.CONFIG, modelpath)
    mip_mac = mip.mac(Model.CONFIG, modelpath)
    neutron_neutron = neutron.neutron(Model.CONFIG, modelpath)
    network_topology_network_topology_config = network_topology.network_topology(Model.CONFIG, modelpath)
    network_topology_network_topology_operational = network_topology.network_topology(Model.CONFIG, modelpath)
    neutron_neutron = neutron.neutron(Model.CONFIG, modelpath)
    odl_fib_fib_entries = odl_fib.fib_entries(Model.CONFIG, modelpath)
    odl_interface_meta_if_index_interface_map = odl_interface_meta.if_indexes_interface_map(Model.OPERATIONAL, modelpath)
    odl_inventory_nodes_config = opendaylight_inventory.nodes(Model.CONFIG, modelpath)
    odl_inventory_nodes_operational = opendaylight_inventory.nodes(Model.OPERATIONAL, modelpath)
    odl_l3vpn_vpn_instance_to_vpn_id = odl_l3vpn.vpn_instance_to_vpn_id(Model.CONFIG, modelpath)

    if options.callMethod:
        if args[1:]:
            eval(options.callMethod)(args[1:])
            return
        else:
            eval(options.callMethod)()
            return
    #print json.dumps(l3vpn_vpn_interfaces.get_vpn_ids_by_key())
    #show_all_tables()
    #analyze_inventory('openflow:165862438671169',ifName='tunf94333cc491')
    #show_stale_flows()
    #show_stale_bindings()
    analyze_interface([args[1]])
    #og.print_flow_dict(og.get_ofctl_flows())


if __name__ == '__main__':
    import sys
    main()
