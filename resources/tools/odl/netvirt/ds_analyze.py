import constants as const
import ds_get_data as dsg
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


def show_stale_flows(sort_by='table'):
    for flow in utils.sort(get_stale_flows(), sort_by):
        print 'Table:', flow['table']
        print 'FlowId:', flow['id'], 'FlowName:', flow.get('name')
        print 'Flow:', json.dumps(parse_flow(flow['flow']))


def get_stale_flows():
    ifaces = dsg.get_config_interfaces()
    bindings = dsg.get_service_bindings()
    of_nodes = dsg.get_inventory_config()
    #stale_bindings = set(bindings.keys()) - set(ifaces.keys())
    stale_flows = []
    for node in of_nodes.itervalues():
        tables = [x for x in node[const.NODE_TABLE] if x['id'] in [0, 17, 220]]
        for table in tables:
            for flow in table.get('flow'):
                flow_ifname = get_ifname_from_flowid(flow['id'], table['id'])
                if flow_ifname is not None and not ifaces.get(flow_ifname):
                    flow_dict = {}
                    flow_dict['table'] = table['id']
                    flow_dict['id'] = flow['id']
                    flow_dict['name'] = flow.get('flow-name')
                    flow_dict['flow'] = flow
                    flow_dict['ifname'] = flow_ifname
                    stale_flows.append(flow_dict)
    return stale_flows


def show_stale_bindings():
    stale_ids, bindings = get_stale_bindings()
    for iface_id in stale_ids:
        print json.dumps(bindings[iface_id])


def get_stale_bindings():
    ifaces = dsg.get_config_interfaces()
    bindings = dsg.get_service_bindings()
    return set(bindings.keys()) - set(ifaces.keys()), bindings


def get_ifname_from_flowid(flow_id, table):
    splitter = ':' if table == 0 else '.'
    i = 2 if table == 0 else 1
    ifname = None
    try:
        ifname = flow_id.split(splitter)[i]
    except IndexError:
        tun_index = flow_id.find('tun')
        if tun_index > -1:
            ifname = flow_id[tun_index:]
    return ifname


def parse_flow(flow):
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


def main(args=None):
    utils.parse_args()
    ifaces = dsg.get_config_interfaces()
    utils.pretty_print(ifaces)
    '''
    ifstates = dsg.get_interface_states()
    '''


if __name__ == '__main__':
    import sys
    main()
