import json
import requests


debug = 0


def set_debug(level):
    global debug
    debug = level


def debug_print(text1, data):
    if debug:
        print "request: {}".format(text1)
        print json.dumps(data)
        print json.dumps(data, indent=4, separators=(',', ': '))


def get(url, user, pw):
    global debug
    resp = requests.get(url, auth=(user, pw))
    # TODO: add error checking of the response
    data = resp.json()
    debug_print(url, data)
    return data


def get_from_file(filename):
    with open(filename) as json_file:
        data = json.load(json_file)
    debug_print(filename, data)
    return data
