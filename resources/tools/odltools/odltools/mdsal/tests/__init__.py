import os


class Args:
    def __init__(self, transport="http", ip="localhost", port=8181, user="admin", pw="admin", path="/tmp",
                 pretty_print=False):
        self.transport = transport
        self.ip = ip
        self.port = port
        self.user = user
        self.pw = pw
        self.path = path
        self.pretty_print = pretty_print


def get_resources_path():
    return os.path.join(os.path.dirname(__file__), '../../tests/resources')
