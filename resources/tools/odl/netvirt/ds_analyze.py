import collections
import constants as const
import ds_get_data as dsg
import flow_parser as fp
import json
import netvirt_utils as utils
import ovs_get_data as og


# Required
ifaces = None
ifstates = None

#Optional
ports = {}
tunnels = {}
confNodes = {}
operNodes = {}


def by_ifname(ifname):
    ifstate = ifstates.get(ifname)
    iface = ifaces.get(ifname)
    port = None
    tunnel = None
    tunState = None
    if iface.get('type') == const.IFTYPE_VLAN:
        ports = dsg.get_neutron_ports()
        port = ports.get(ifname)
    elif iface.get('type') == const.IFTYPE_TUNNEL:
        tunnels = dsg.get_config_tunnels()
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
    global ifaces,ifstates
    ifaces = dsg.get_config_interfaces()
    ifstates = dsg.get_interface_states()
    if not ifname:
        print_keys()
        exit(1)
    ifname = ifname[0]
    iface,ifstate,port,tunnel,tunState = by_ifname(ifname)
    print "InterfaceConfig: "
    utils.pretty_print(iface)
    print "InterfaceState: "
    utils.pretty_print(ifstate)
    if port:
        print "NeutronPort: "
        utils.pretty_print(port)
        analyze_neutron_port(port, iface, ifstate)
        return
    if tunnel:
        print "Tunnel: "
        utils.pretty_print(tunnel)
    if tunState:
        print "TunState: "
        utils.pretty_print(tunState)
    if ifstate:
        ncId = ifstate.get('lower-layer-if')[0]
        nodeId = ncId[:ncId.rindex(':')]
        analyze_inventory(nodeId, True, ncId, ifname)
        #analyze_inventory(nodeId, False, ncId, ifname)

def analyze_neutron_port(port, iface, ifstate):
    for flow in utils.sort(get_all_flows(['all']), 'table'):
        if ((flow.get('ifname') == port['uuid']) or
            (flow.get('lport') and flow['lport'] == ifstate.get('if-index')) or
            (iface['name'] == flow.get('ifname'))):
                result = 'Table:{},FlowId:{}{}'.format(
                flow['table'], flow['id'],
                utils.show_optionals(flow))
                print result
                print 'Flow:', json.dumps(parse_flow(flow.get('flow')))


def analyze_inventory(nodeId, isConfig=True, ncId=None, ifName=None):
    if isConfig:
        nodes = dsg.get_inventory_config()
        print "Inventory Config:"
    else:
        print "Inventory Operational:"
        nodes = dsg.get_inventory_oper()
    node = nodes.get(nodeId)
    tables = node.get(const.NODE_TABLE)
    groups = node.get(const.NODE_GROUP)
    flow_list = []
    print "Flows:"
    for table in tables:
        for flow in table.get('flow'):
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
        nodes = oper_nodes or dsg.get_inventory_oper()
        for node in nodes.itervalues():
            dpnid = utils.get_dpn_from_ofnodeid(node['id'])
            nodes_dict[dpnid] = node.get('flow-node-inventory:description', '')
        return nodes_dict


def get_groups(ofnodes=None):
    of_nodes = ofnodes or dsg.get_inventory_config()
    key ='group-id'
    group_dict = collections.defaultdict(dict)
    for node in of_nodes.itervalues():
        dpnid = utils.get_dpn_from_ofnodeid(node['id'])
        for group in node[const.NODE_GROUP]:
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
    of_nodes = dsg.get_inventory_config()
    if 'ifm' in modules:
        ifaces = dsg.get_config_interfaces()
        ifstates = dsg.get_interface_states()
    if 'l3vpn' in modules:
        ifaces = ifaces or dsg.get_config_interfaces()
        ifindexes = ifindexes or dsg.get_ifindexes()
        fibentries = fibentries or dsg.get_fibentries_by_label()
        vpnids = vpnids or dsg.get_vpnids()
        vpninterfaces = vpninterfaces or dsg.get_vpninterfaces()
        groups = groups or get_groups(of_nodes)
    if 'acl' in modules:
        ifaces = ifaces or dsg.get_config_interfaces()
        ifindexes = ifindexes or dsg.get_ifindexes()
        einsts = einsts or dsg.get_elan_instances()
        eifaces = eifaces or dsg.get_elan_interfaces()
    if 'elan' in modules:
        ifaces = ifaces or dsg.get_config_interfaces()
        einsts = einsts or dsg.get_elan_instances()
        eifaces = eifaces or dsg.get_elan_interfaces()
        ifindexes = ifindexes or dsg.get_ifindexes()
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
    ifaces = dsg.get_config_interfaces()
    bindings, orphans = dsg.get_service_bindings()
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
    nports = dsg.get_neutron_ports()
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


def get_all_flows(modules=['ifm']):
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
    of_nodes = dsg.get_inventory_config()
    if 'ifm' in modules:
        ifaces = dsg.get_config_interfaces()
        ifstates = dsg.get_interface_states()
    if 'l3vpn' in modules:
        ifaces = ifaces or dsg.get_config_interfaces()
        ifindexes = ifindexes or dsg.get_ifindexes()
        fibentries = fibentries or dsg.get_fibentries_by_label()
        vpnids = vpnids or dsg.get_vpnids()
        vpninterfaces = vpninterfaces or dsg.get_vpninterfaces()
        groups = groups or get_groups(of_nodes)
    if 'acl' in modules:
        ifaces = ifaces or dsg.get_config_interfaces()
        ifindexes = ifindexes or dsg.get_ifindexes()
        einsts = einsts or dsg.get_elan_instances()
        eifaces = eifaces or dsg.get_elan_interfaces()
    if 'elan' in modules:
        ifaces = ifaces or dsg.get_config_interfaces()
        einsts = einsts or dsg.get_elan_instances()
        eifaces = eifaces or dsg.get_elan_interfaces()
        ifindexes = ifindexes or dsg.get_ifindexes()
    if 'all' in modules:
        groups = groups or get_groups(of_nodes)
        ifaces = ifaces or dsg.get_config_interfaces()
        ifstates = ifstates or dsg.get_interface_states()
        ifindexes = ifindexes or dsg.get_ifindexes()
        fibentries = fibentries or dsg.get_fibentries_by_label()
        vpnids = vpnids or dsg.get_vpnids()
        vpninterfaces = vpninterfaces or dsg.get_vpninterfaces()
        einsts = einsts or dsg.get_elan_instances()
        eifaces = eifaces or dsg.get_elan_interfaces()
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
                if flow_dict is not None:
                    flows.append(flow_dict)
    return flows


def show_all_flows():
    compute_map = get_dpn_host_mapping()
    nports = dsg.get_neutron_ports()
    for flow in utils.sort(get_all_flows(['all']), 'table'):
        host = compute_map.get(flow.get('dpnid'), flow.get('dpnid'))
        ip_list = get_ips_for_iface(nports, flow.get('ifname'))
        if ip_list:
            flow['iface-ips'] = ip_list
        result = 'Table:{},Host:{},FlowId:{}{}'.format(
            flow['table'], host, flow['id'],
            utils.show_optionals(flow))
        print result
        #print 'Flow:', json.dumps(parse_flow(flow['flow']))


def show_elan_flows():
    for flow in utils.sort(get_stale_flows(['elan']), 'table'):
        print 'Table:', flow['table'], 'FlowId:', flow['id'], utils.show_optionals(flow)
        print 'Flow:', json.dumps(parse_flow(flow['flow']))


def show_elan_instances():
    insts = dsg.get_elan_instances()
    json.dumps(insts)


def get_duplicate_ids():
    duplicate_ids= {}
    for pool in dsg.get_idpools().itervalues():
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
    for k,v in get_duplicate_ids().iteritems():
        result = "Id:{},Keys:{}".format(k, json.dumps(v.get('id-keys')))
        if v.get('pool-name'):
            result = "{},Pool:{}".format(result, v.get('pool-name'))
        if v.get('parent-pool-name'):
            result = "{},ParentPool:{}".format(result, v.get('parent-pool-name'))
        print result


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
    of_nodes = dsg.get_inventory_config()
    tables = set()
    for node in of_nodes.itervalues():
        for table in node[const.NODE_TABLE]:
            if table.get('flow'):
                tables.add(table['id'])
    print list(tables)


def show_all_groups():
    of_nodes = dsg.get_inventory_config()
    groups = get_groups(of_nodes)
    for dpn in groups:
        for group_key in groups[dpn]:
            print 'Dpn:', dpn, 'ID:', group_key, 'Group:', json.dumps(groups[dpn][group_key])


def main(args=None):
    options, args = utils.parse_args()
    if options.callMethod:
        if args[1:]:
            eval(options.callMethod)(args[1:])
            return
        else:
            eval(options.callMethod)()
            return
    #print json.dumps(dsg.get_vpninterfaces())
    #show_all_tables()
    #analyze_inventory('openflow:165862438671169',ifName='tunf94333cc491')
    #show_stale_flows()
    #show_stale_bindings()
    analyze_interface(args)
    #og.print_flow_dict(og.get_ofctl_flows())


if __name__ == '__main__':
    import sys
    main()
