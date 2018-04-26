import errno
import json
import logging
import os
import requests


logger = logging.getLogger("mdsal.request")


def debug_print(fname, text1, data):
    logger.debug("%s: request: %s: processed %d lines", fname, text1, len(data))
    logger.debug("%s:\n%s", fname, json.dumps(data))
    logger.debug("%s:\n%s", fname, json.dumps(data, indent=4, separators=(',', ': ')))


def get(url, user, pw):
    try:
        resp = requests.get(url, auth=(user, pw))
    except requests.exceptions.RequestException:
        logger.exception("Failed to get url")
        return None

    try:
        data = resp.json()
    except ValueError:
        logger.exception("Failed to get url")
        return None

    if logger.isEnabledFor(logging.DEBUG):
        debug_print("get", url, data)
    return data


def read_file(filename):
    if os.path.isfile(filename) is False:
        return None

    with open(filename) as json_file:
        data = json.load(json_file)
    if logger.isEnabledFor(logging.DEBUG):
        debug_print("read_file", filename, data)
    return data


def write_file(filename, data, pretty_print=False):
    try:
        os.makedirs(os.path.dirname(filename))
    except OSError as exception:
        if exception.errno != errno.EEXIST:
            raise

    with open(filename, 'w') as fp:
        if pretty_print:
            json.dump(data, fp, indent=4, separators=(',', ': '))
        else:
            json.dump(data, fp)
    if logger.isEnabledFor(logging.DEBUG):
        debug_print("write_file", filename, data)
