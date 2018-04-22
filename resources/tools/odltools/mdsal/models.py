import json
import request


class Model:
    CONFIG = "config"
    OPERATIONAL = "operational"
    USER = "admin"
    PW = "admin"

    def __init__(self, name, container, store, ip, port):
        self.name = name
        self.CONTAINER = container
        self.store = store
        self.ip = ip
        self.port = port
        self.data = None
        self.url = self.make_url()

    def set_odl_address(self, ip, port):
        self.ip = ip
        self.port = port

    def make_url(self):
        url = "http://{}:{}/restconf/{}/{}:{}".format(self.ip, self.port, self.store,
                                                      self.name, self.CONTAINER)
        return url

    def get_from_odl(self):
        self.data = request.get(self.url, self.USER, self.PW)
        return self.data

    def read_file(self, filename):
        self.data = request.read_file(filename)
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
