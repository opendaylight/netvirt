import json
import logging

import flows
import utils
from odltools.mdsal.models import elan
from odltools.mdsal.models import id_manager
# from odltools.mdsal.models import ietf_interfaces
# from odltools.mdsal.models import interface_service_bindings
from odltools.mdsal.models import neutron
from odltools.mdsal.models import opendaylight_inventory
from odltools.mdsal.models.models import Model
# from odltools.mdsal.models.models import Models
from odltools.mdsal.models.opendaylight_inventory import Nodes
# from odltools.mdsal.models.models import Models
import config


logger = logging.getLogger("netvirt.show")


def show_elan_instances(args):
    elan_elan_instances = elan.elan_instances(Model.CONFIG, args)
    instances = elan_elan_instances.get_clist_by_key()
    for k, v in instances.items():
        print "ElanInstance: {}, {}".format(k, utils.format_json(args, v))


def get_duplicate_ids(args):
    duplicate_ids = {}
    id_manager_id_pools = id_manager.id_pools(Model.CONFIG, args)
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


def show_idpools(args):
    neutron_neutron = neutron.neutron(Model.CONFIG, args)
    ports = neutron_neutron.get_ports_by_key()
    iface_ids = []
    for k, v in get_duplicate_ids(args).iteritems():
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
        print "Iface={}, NeutronPort={}".format(id, utils.format_json(args, port))


def show_groups(args):
    odl_inventory_nodes_config = opendaylight_inventory.nodes(Model.CONFIG, args)
    of_nodes = odl_inventory_nodes_config.get_clist_by_key()
    groups = odl_inventory_nodes_config.get_groups(of_nodes)
    for dpn in groups:
        for group_key in groups[dpn]:
            print "Dpn: {}, ID: {}, Group: {}".format(dpn, group_key, utils.format_json(args, groups[dpn][group_key]))


def get_data_path(res_type, data):
    if res_type == 'bindings':
        return 'interface-service-bindings:service-bindings/services-info/{}/{}'.format(
            data['interface-name'], data['service-mode'])
    elif res_type == 'flows':
        return 'opendaylight-inventory:nodes/node/openflow:{}/flow-node-inventory:table/{}/flow/{}'.format(
            data['dpnid'], data['table'], data['id'])


def show_stale_bindings(args):
    config.get_models(args, {"ietf_interfaces_interfaces", "interface_service_bindings_service_bindings"})
    stale_ids, bindings = flows.get_stale_bindings(args)
    for iface_id in sorted(stale_ids):
        for binding in bindings[iface_id].itervalues():
            # if binding.get('bound-services'):
            path = get_data_path('bindings', binding)
            print utils.format_json(bindings[iface_id])
            print('http://{}:{}/restconf/config/{}'.format(args.ip, args.port, path))


def show_tables(args):
    odl_inventory_nodes_config = opendaylight_inventory.nodes(Model.CONFIG, args)
    of_nodes = odl_inventory_nodes_config.get_clist_by_key()
    tables = set()
    for node in of_nodes.itervalues():
        for table in node[Nodes.NODE_TABLE]:
            if table.get('flow'):
                tables.add(table['id'])
    print list(tables)


def show_flows(args):
    if args.flowtype == "all":
        flows.show_all_flows(args)
    if args.flowtype == "duplicate":
        flows.show_dup_flows(args)
    if args.flowtype == "learned":
        flows.show_learned_mac_flows(args)
    if args.flowtype == "stale":
        flows.show_stale_flows(args)
    if args.flowtype == "elan":
        flows.show_elan_flows(args)
