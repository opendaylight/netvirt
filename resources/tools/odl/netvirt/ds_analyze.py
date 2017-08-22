import constants as const
import ds_get_data as dsg
import flow_parser as fp
import json
import netvirt_utils as utils


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
    print
    if not ifname:
        print_keys()
        exit(1)
    iface,ifstate,port,tunnel,tunState = by_ifname(ifname)
    print "InterfaceConfig: "
    utils.pretty_print(iface)
    print "InterfaceState: "
    utils.pretty_print(ifstate)
    if port:
        print "NeutronPort: "
        utils.pretty_print(port)
    if tunnel:
        print "Tunnel: "
        utils.pretty_print(tunnel)
    if tunState:
        print "TunState: "
        utils.pretty_print(tunState)
    if ifstate:
        ncId = ifstate.get('lower-layer-if')[0]
        nodeId = ncId[:ncId.rindex(':')]
        #analyze_inventory(nodeId, True, ncId, ifname)
        #analyze_inventory(nodeId, False, ncId, ifname)


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


def get_stale_flows(modules=['ifm']):
    if not modules:
        return 'No modules specified'
    ifaces = {}
    ifindexes = {}
    bindings = {}
    einsts = {}
    table_list = list(set([table for module in modules for table in const.TABLE_MAP[module]]))
    of_nodes = dsg.get_inventory_config()
    if 'ifm' in modules:
        ifaces = dsg.get_config_interfaces()
        bindings = dsg.get_service_bindings()
    elif 'acl' in modules:
        ifaces = ifaces or dsg.get_config_interfaces()
        ifindexes = ifindexes or dsg.get_ifindexes()
    if 'elan' in modules:
        # einsts = einsts or dsg.get_elan_instances()
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
                    flow_dict = fp.stale_ifm_flow(ifaces, flow, flow_info)
                elif 'elan' in modules and table['id'] in const.TABLE_MAP['elan']:
                    flow_dict = fp.stale_elan_flow(ifindexes, flow, flow_info)
                if 'acl' in modules and table['id'] in const.TABLE_MAP['acl']:
                    flow_dict = fp.stale_acl_flow(ifaces, ifindexes, flow, flow_info)
                if flow_dict is not None:
                    stale_flows.append(flow_dict)

    return stale_flows


def show_stale_bindings():
    stale_ids, bindings = get_stale_bindings()
    for iface_id in sorted(stale_ids):
        print json.dumps(bindings[iface_id])


def get_stale_bindings():
    ifaces = dsg.get_config_interfaces()
    bindings = dsg.get_service_bindings()
    return set(bindings.keys()) - set(ifaces.keys()), bindings


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
    for flow in utils.sort(get_stale_flows(['ifm','acl']), sort_by):
        print 'Table:', flow['table'], 'LPORT Tag', flow.get('lport')
        print 'FlowId:', flow['id'], 'FlowName:', flow.get('name')
        print 'Flow:', json.dumps(parse_flow(flow['flow']))


def show_elan_flows():
    for flow in utils.sort(get_stale_flows(['elan']), 'table'):
        print 'Table:', flow['table']
        print 'ELAN Tag', flow.get('elan-tag'), 'LPORT Tag', flow.get('lport')
        print 'FlowId:', flow['id'], 'FlowName:', flow.get('name')
        print 'Flow:', json.dumps(parse_flow(flow['flow']))


def show_elan_instances():
    insts = dsg.get_elan_instances()
    json.dumps(insts)


def parse_flow(flow):
    #parse flow fields
    #hex(int(mask, 16) & int(data, 16))
    if flow['cookie']:
        utils.to_hex(flow, 'cookie')
    # parse instructions
    for instruction in flow['instructions']['instruction']:
        if 'write-metadata' in instruction:
            utils.to_hex(instruction['write-metadata'],'metadata')
            utils.to_hex(instruction['write-metadata'],'metadata-mask')
        if 'apply-actions' in instruction:
            for action in instruction['apply-actions']['action']:
                if 'openflowplugin-extension-nicira-action:nx-reg-load' in action:
                    utils.to_hex(action['openflowplugin-extension-nicira-action:nx-reg-load'], 'value')
    # parse matches
    if 'metadata' in flow['match']:
        metadata = flow['match']['metadata']
        utils.to_hex(metadata,'metadata')
        utils.to_hex(metadata,'metadata-mask')

    if 'openflowplugin-extension-general:extension-list' in flow['match']:
        for ofex in flow['match']['openflowplugin-extension-general:extension-list']:
            if ofex['extension-key'] == 'openflowplugin-extension-nicira-match:nxm-nx-reg6-key':
                utils.to_hex(ofex['extension']['openflowplugin-extension-nicira-match:nxm-nx-reg'], 'value')

    return flow


# Sample method that shows how to use
def show_all_tables():
    of_nodes = dsg.get_inventory_config()
    tables = set()
    for node in of_nodes.itervalues():
        for table in node[const.NODE_TABLE]:
            if table.get('flow'):
                tables.add(table['id'])
    print tables.values()


def main(args=None):
    utils.parse_args()
    options = utils.get_options()
    if options.callMethod:
        eval(options.callMethod)()
        return
    show_all_tables()


if __name__ == '__main__':
    import sys
    main()
