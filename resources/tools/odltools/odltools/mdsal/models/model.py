import json
import logging

import odltools.mdsal.request

logger = logging.getLogger("mdsal.model")


class Model:
    CONFIG = "config"
    OPERATIONAL = "operational"
    USER = "admin"
    PW = "admin"
    CONTAINER = "container"
    CLIST = "clist"
    CLIST_KEY = "key"

    def __init__(self, modul, store, args, mid=None):
        self.modul = modul
        self.container = self.CONTAINER  # container
        self.clist = self.CLIST  # clist
        self.clist_key = self.CLIST_KEY  # clist_key
        self.store = store
        self.transport = args.transport
        self.ip = args.ip
        self.port = args.port
        self.url = self.make_url()
        self.path = args.path
        self.filename = self.make_filename()
        if mid is not None:
            self.url = self.make_url_type(mid)
            self.filename = self.make_filename_type(mid)
        self.data = None
        self.data = self.get_model_data()
        if self.data is None:
            logger.warning("Model data was not imported")
        elif self.get_clist() is []:
            logger.warning("Model data is wrong")
            self.data = None

    def get_list(self, data, container_key, lst):
        c = data and data.get(container_key, {})
        lst = c.get(lst, [])
        return lst

    def get_clist(self):
        return self.get_list(self.data, self.container, self.clist)

    def get_clist_by_key(self, key=None):
        d = {}
        key = key or self.clist_key
        cl = self.get_clist()
        for l in cl:
            d[l[key]] = l
        return d

    def make_filename(self):
        return "{}/{}___{}__{}.json".format(self.path, self.store, self.modul, self.container)

    def make_filename_type(self, mid):
        fmid = mid.replace(":", "__")
        return "{}/{}___{}__{}___topology___{}.json".format(self.path, self.store, self.modul, self.container, fmid)

    def make_url(self):
        return "{}://{}:{}/restconf/{}/{}:{}".format(self.transport, self.ip, self.port,
                                                     self.store, self.modul,
                                                     self.container)

    def make_url_type(self, mid):
        return "{}://{}:{}/restconf/{}/{}:{}/topology/{}".format(self.transport, self.ip, self.port,
                                                                 self.store, self.modul,
                                                                 self.container, mid)

    def get_from_odl(self):
        return odltools.mdsal.request.get(self.url, self.USER, self.PW)

    def read_file(self, filename):
        return odltools.mdsal.request.read_file(filename)

    def get_model_data(self):
        if self.data is not None:
            return self.data

        self.data = self.read_file(self.filename)
        if self.data is not None:
            return self.data

        self.data = self.get_from_odl()
        if self.data is not None:
            odltools.mdsal.request.write_file(self.filename, self.data)
            return self.data

    def pretty_format(self, data=None):
        if data is None:
            data = self.data
        return json.dumps(data, indent=4, separators=(',', ': '))

    def get_kv(self, k, v, values):
        """
        Return a list of values for the given key
        :param k:
        :param v:
        :param values:
        :return:
        """
        if type(v) is dict:
            for jsonkey in v:
                if jsonkey == k:
                    values.append(v[jsonkey])
                elif type(v[jsonkey]) in (list, dict):
                    self.get_kv(k, v[jsonkey], values)
        elif type(v) is list:
            for item in v:
                if type(item) in (list, dict):
                    self.get_kv(k, item, values)
        return values

    @staticmethod
    def get_dpn_from_ofnodeid(node_id):
        return node_id.split(':')[1] if node_id else 'none'

    @staticmethod
    def get_ofport_from_ncid(ncid):
        return ncid.split(':')[2] if ncid and ncid.startswith('openflow') else 0
