import json
from collections import defaultdict

import constants as const
import flow_parser as fp
import netvirt_utils as utils
from odltools.mdsal.models.models import Model

# Required
ifaces = None
ifstates = None

#Optional
ports = {}
tunnels = {}
confNodes = {}
operNodes = {}


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
                flow_info['dpnid'] = Model.get_dpn_from_ofnodeid(node['id'])
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
    compute_map = Model.get_dpn_host_mapping()
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
                flow_info['dpnid'] = Model.get_dpn_from_ofnodeid(node['id'])
                flow_dict = fp.get_any_flow(flow, flow_info, groups,
                                        ifaces, ifstates, ifindexes,
                                        fibentries, vpnids, vpninterfaces,
                                        einsts, eifaces)
                if (flow_dict is not None and
                        utils.filter_flow(flow_dict, filter)):
                    flows.append(flow_dict)
    return flows


def show_flows(modules=['ifm'], sort_by='table', filter_by=[]):
    compute_map = Model.get_dpn_host_mapping()
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
    compute_map = Model.get_dpn_host_mapping()
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
    compute_map = Model.get_dpn_host_mapping()
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
    compute_map = Model.get_dpn_host_mapping()
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


