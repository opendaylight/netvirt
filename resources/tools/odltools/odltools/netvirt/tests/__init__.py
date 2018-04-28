class Args:
    def __init__(self, ip="localhost", port=8181, user="admin", pw="admin", path="/tmp", pretty_print=False):
        self.ip = ip
        self.port = port
        self.user = user
        self.pw = pw
        self.path = path
        self.pretty_print=pretty_print
