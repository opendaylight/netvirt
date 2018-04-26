import json

import request


class Model:
    CONFIG = "config"
    OPERATIONAL = "operational"
    USER = "admin"
    PW = "admin"

    def __init__(self, name, container, store, ip=None, port=None, path="/tmp", mid=None):
        self.name = name
        self.container = container
        self.store = store
        self.ip = ip
        self.port = port
        self.url = self.make_url()
        self.path = path
        self.filename = self.make_filename()
        if mid is not None:
            self.url = self.make_url_type(mid)
            self.filename = self.make_filename_type(mid)
        self.data = None
        self.data = self.get_model_data()

    def make_filename(self):
        return "{}/{}_{}:{}.json".format(self.path, self.store, self.name, self.container)

    def make_filename_type(self, mid):
        return "{}/{}_{}:{}_topology_{}.json".format(self.path, self.store, self.name, self.container, mid)

    def make_url(self):
        return "http://{}:{}/restconf/{}/{}:{}".format(self.ip, self.port, self.store,
                                                       self.name, self.container)

    def make_url_type(self, mid):
        return "http://{}:{}/restconf/{}/{}:{}/topology/{}".format(self.ip, self.port, self.store,
                                                                   self.name, self.container, mid)

    def get_from_odl(self):
        return request.get(self.url, self.USER, self.PW)

    def read_file(self, filename):
        return request.read_file(filename)

    def get_model_data(self):
        if self.data is not None:
            return self.data

        self.data = self.read_file(self.filename)
        if self.data is not None:
            return self.data

        self.data = self.get_from_odl()
        if self.data is not None:
            request.write_file(self.filename, self.data)
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
