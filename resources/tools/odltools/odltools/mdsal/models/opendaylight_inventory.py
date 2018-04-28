import collections
from odltools.mdsal.models.model import Model


NAME = "opendaylight-inventory"


def nodes(store, args):
    return Nodes(NAME, Nodes.CONTAINER, store, args)


class Nodes(Model):
    CONTAINER = "nodes"
    NODE = "node"
    NODE_GROUP = 'flow-node-inventory:group'
    NODE_TABLE = 'flow-node-inventory:table'

    def get_nodes(self):
        return self.data[self.CONTAINER][self.NODE]

    def get_nodes_by_key(self, key="id"):
        d = {}
        nodez = self.get_nodes()
        if nodez is None:
            return None
        for node in nodez:
            d[node[key]] = node
        return d

    def get_groups(self, of_nodes=None):
        key = "group-id"
        group_dict = collections.defaultdict(dict)
        for node in of_nodes.itervalues():
            dpnid = self.get_dpn_from_ofnodeid(node['id'])
            for group in node.get(Nodes.NODE_GROUP, []):
                if group_dict.get(dpnid) and group_dict.get(dpnid).get(group[key]):
                    print 'Duplicate:', dpnid, group[key]
                group_dict[dpnid][group[key]] = group
        return dict(group_dict)

    def get_dpn_host_mapping(self, oper_nodes=None):
        nodes_dict = {}
        nodes = oper_nodes or self.get_nodes_by_key()
        for node in nodes.itervalues():
            dpnid = self.get_dpn_from_ofnodeid(node['id'])
            nodes_dict[dpnid] = node.get('flow-node-inventory:description', '')
        return nodes_dict

