import json
import logging
import requests


logging.basicConfig(format="%(levelname)-8s [%(module)s:%(lineno)d] %(message)s",
                    level=logging.INFO)
logger = logging.getLogger(__name__)


def set_log_level(level):
    logger.setLevel(level)


def debug_print(text1, data):
    logger.debug("request: %s", text1)
    logger.debug("%s", json.dumps(data))
    logger.debug("%s", json.dumps(data, indent=4, separators=(',', ': ')))


def get(url, user, pw):
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
