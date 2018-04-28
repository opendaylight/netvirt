import json

import odltools.mdsal.request


class Model:
    CONFIG = "config"
    OPERATIONAL = "operational"
    USER = "admin"
    PW = "admin"

    def __init__(self, name, container, store, args, mid=None):
        self.name = name
        self.container = container
        self.store = store
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

    def make_filename(self):
        return "{}/{}___{}__{}.json".format(self.path, self.store, self.name, self.container)

    def make_filename_type(self, mid):
        fmid = mid.replace(":", "__")
        return "{}/{}___{}__{}___topology___{}.json".format(self.path, self.store, self.name, self.container, fmid)

    def make_url(self):
        return "http://{}:{}/restconf/{}/{}:{}".format(self.ip, self.port, self.store,
                                                       self.name, self.container)

    def make_url_type(self, mid):
        return "http://{}:{}/restconf/{}/{}:{}/topology/{}".format(self.ip, self.port, self.store,
                                                                   self.name, self.container, mid)

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

    def get_dpn_from_ofnodeid(self, node_id):
        return node_id.split(':')[1] if node_id else 'none'

    def get_ofport_from_ncid(self, ncid):
        return ncid.split(':')[2] if ncid and ncid.startswith('openflow') else 0
