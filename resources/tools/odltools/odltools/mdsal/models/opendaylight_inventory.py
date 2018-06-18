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

import collections
from odltools.mdsal.models.model import Model

MODULE = "opendaylight-inventory"


def nodes(store, args):
    return Nodes(MODULE, store, args)


class Nodes(Model):
    CONTAINER = "nodes"
    CLIST = "node"
    CLIST_KEY = "id"
    NODE_GROUP = 'flow-node-inventory:group'
    NODE_TABLE = 'flow-node-inventory:table'

    def get_groups(self, of_nodes=None):
        key = "group-id"
        group_dict = collections.defaultdict(dict)
        nodez = of_nodes or self.get_clist_by_key()
        for node in nodez.values():
            dpnid = self.get_dpn_from_ofnodeid(node['id'])
            for group in node.get(Nodes.NODE_GROUP, []):
                if group_dict.get(dpnid) and group_dict.get(dpnid).get(group[key]):
                    print("Duplicate: dpn_id: {}, group: {}".format(dpnid, group[key]))
                group_dict[dpnid][group[key]] = group
        return dict(group_dict)

    def get_dpn_host_mapping(self, oper_nodes=None):
        nodes_dict = {}
        nodez = oper_nodes or self.get_clist_by_key()
        for node in nodez.values():
            dpnid = self.get_dpn_from_ofnodeid(node['id'])
            desc = node.get('flow-node-inventory:description', '')
            if desc and desc != 'None':
                nodes_dict[dpnid] = desc.encode('utf8')
        return nodes_dict
