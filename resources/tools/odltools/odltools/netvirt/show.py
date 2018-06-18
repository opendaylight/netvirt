# Copyright 2018 Red Hat, Inc. and others. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import json
import logging
from odltools.mdsal.models.neutron import Neutron
from odltools.mdsal.models.opendaylight_inventory import Nodes
from odltools.netvirt import config
from odltools.netvirt import flows
from odltools.netvirt import tables
from odltools.netvirt import utils

logger = logging.getLogger("netvirt.show")


def show_elan_instances(args):
    config.get_models(args, {"elan_elan_instances"})
    instances = config.gmodels.elan_elan_instances.get_clist_by_key()
    for k, v in instances.items():
        print("ElanInstance: {}, {}".format(k, utils.format_json(args, v)))


def get_duplicate_ids(args):
    config.get_models(args, {"id_manager_id_pools"})
    duplicate_ids = {}
    pools = config.gmodels.id_manager_id_pools.get_clist_by_key()
    for k, pool in pools.items():
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


def show_all_idpools(args):
    config.get_models(args, {"id_manager_id_pools"})
    pools = config.gmodels.id_manager_id_pools.get_clist_by_key()
    print("\nid-pools\n")
    if not args.short:
        print(utils.format_json(args, pools))
    else:
        print("pool-name                          ")
        print("-----------------------------------")
        for k, v in sorted(pools.items()):
            print("{:30}".format(v.get("pool-name")))


def show_dup_idpools(args):
    config.get_models(args, {"neutron_neutron"})
    ports = config.gmodels.neutron_neutron.get_objects_by_key(obj=Neutron.PORTS)
    iface_ids = []
    for k, v in get_duplicate_ids(args).iteritems():
        result = "Id:{},Keys:{}".format(k, json.dumps(v.get('id-keys')))
        if v.get('pool-name'):
            result = "{},Pool:{}".format(result, v.get('pool-name'))
            if v.get('pool-name') == 'interfaces':
                iface_ids.extend(v.get('id-keys'))
        if v.get('parent-pool-name'):
            result = "{},ParentPool:{}".format(result, v.get('parent-pool-name'))
        print(result)
    print("\nNeutron Ports")
    print("=============")
    for id in iface_ids:
        port = ports.get(id, {})
        print("Iface={}, NeutronPort={}".format(id, utils.format_json(args, port)))


def show_idpools(args):
    print("args: {}".format(args))
    if args.type == "all":
        show_all_idpools(args)
    elif args.type == "duplicate":
        show_dup_idpools(args)


def show_groups(args):
    config.get_models(args, {"odl_inventory_nodes"})
    of_nodes = config.gmodels.odl_inventory_nodes.get_clist_by_key()
    groups = config.gmodels.odl_inventory_nodes.get_groups(of_nodes)
    for dpn in groups:
        for group_key in groups[dpn]:
            print("Dpn: {}, ID: {}, Group: {}".format(dpn, group_key, utils.format_json(args, groups[dpn][group_key])))


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
        for binding in bindings[iface_id].values():
            # if binding.get('bound-services'):
            path = get_data_path('bindings', binding)
            print(utils.format_json(args, bindings[iface_id]))
            print('http://{}:{}/restconf/config/{}'.format(args.ip, args.port, path))


def show_tables(args):
    config.get_models(args, {"odl_inventory_nodes"})
    of_nodes = config.gmodels.odl_inventory_nodes.get_clist_by_key()

    tableset = set()
    for node in of_nodes.values():
        for table in node[Nodes.NODE_TABLE]:
            if table.get('flow'):
                tableset.add(table['id'])
    result = ''
    for table in (sorted(tableset)):
        result = '{:3}:{} '.format(table, tables.get_table_name(table))
        print(result)


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


def print_neutron_networks(args, obj, data):
    print("uuid                                 type  name")
    print("------------------------------------ ----- --------------------")
    for k, v in data.items():
        ntype = v.get("neutron-provider-ext:network-type").rpartition("-")[2]
        print("{} {:5} {}".format(k, ntype, v.get("name")))


def print_neutron_ports(args, obj, data):
    print("uuid                                 network-id                           mac               "
          "ip              name")
    print("------------------------------------ ------------------------------------ ----------------- "
          "--------------- --------------------")
    for k, v in data.items():
        network_id = v.get("network-id")
        mac = v.get("mac-address")
        fixed_ip = v.get("fixed-ips")
        ip = None
        if fixed_ip is not None:
            ip = fixed_ip[0].get("ip-address")
        name = v.get("name")
        print("{} {} {} {:15} {}".format(k, network_id, mac, ip, name))


def print_neutron(args, obj, data):
    if args.short:
        if obj == Neutron.NETWORKS:
            print_neutron_networks(args, obj, data)
        elif obj == Neutron.PORTS:
            print_neutron_ports(args, obj, data)
        else:
            print(utils.format_json(args, data))
    else:
        print(utils.format_json(args, data))


def show_neutron(args):
    objs = []
    config.get_models(args, {"neutron_neutron"})
    if args.object == "all":
        objs = Neutron.ALL_OBJECTS
    else:
        objs.append(args.object)

    for obj in objs:
        print("\nneutron {}:\n".format(obj))
        data = config.gmodels.neutron_neutron.get_objects_by_key(obj=obj)
        print_neutron(args, obj, data)
