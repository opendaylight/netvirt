import json
import logging
import requests


logger = logging.getLogger("mdsal.request")


def debug_print(text1, data):
    logger.debug("request: %s: processed %d lines", text1, len(data))
    logger.debug("request: %s", text1)
    logger.debug("%s", json.dumps(data))
    logger.debug("%s", json.dumps(data, indent=4, separators=(',', ': ')))


def get(url, user, pw):
    resp = requests.get(url, auth=(user, pw))
    # TODO: add error checking of the response
    data = resp.json()
    debug_print(url, data)
    return data


def read_file(filename):
    with open(filename) as json_file:
        data = json.load(json_file)
    debug_print(filename, data)
    return data


def write_file(filename, data):
    with open(filename, 'w') as fp:
        json.dump(data, fp)
    logger.debug("write_file: File: %s", filename)
