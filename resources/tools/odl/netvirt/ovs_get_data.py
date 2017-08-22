import sys
import re

from datetime import datetime as dt
from datetime import timedelta as td

base_time = '2017-07-31 20:19:00,698'
#base_time = None
TIME_FORMAT = '%Y-%m-%d %H:%M:%S,%f'

def get_ofctl_flows(file_name='ofctl-flows.log'):
    flow_list = []
    with open(file_name, 'rb') as f:
        data = f.read().splitlines()
        for row in data:
            meta = row.split(', ')
            matches, actions = meta.pop().split()
            flow_list.append(get_flow_dict(meta, matches, actions))
    return flow_list


def get_flow_dict(meta, matches, actions):
    actions = actions.strip('actions=')
    flow_dict = {}
    flow_dict['meta'] = get_meta_dict(meta)
    flow_dict['matches'] = get_matches_dict(matches)
    flow_dict['actions'] = get_actions_dict(actions)
    return flow_dict


def get_meta_dict(meta_str):
    meta = {}
    for field in meta_str:
        k, v = field.split('=')
        if base_time and k == 'duration':
            v = get_flow_time(v)
            k = 'installed'
        meta[k] = v
    return meta


def get_matches_dict(matches_str):
    return matches_str
''' Note: This mostly works requires bit of rework for matches like:
    arp,arp_sa=abc etc.
    matches = {}
    for match in matches_str.split(','):
        if '=' in match:
            k, v = match.split('=')
        else:
            k, v = match, ""
        matches[k] = v
    return matches
'''


def get_flow_time(duration):
    duration = duration.strip('s')
    time = dt.strptime(base_time, TIME_FORMAT) + td(0, float(duration))
    time_str = time.strftime(TIME_FORMAT)
    return time_str


def get_actions_dict(action_str):
    return action_str
''' todo: Action parsing is WiP and requires much complex logic
    actions_dict = {}
    actions = action_str.split(',')
    actions = iter(actions)
    for action in actions:
        if ':' in action:
            k, v = action.split(':')
            actions_dict[k] = v
        elif action.startswith('resubmit'):
            k = 'resubmit'
            port = action.strip('resubmit(')
            try:
                next_action = next(actions)
                if next_action.endswith(')'):
                    table = next_action.strip(')')
                else:
                    table = action
            except StopIteration:
                break;
            actions_dict[k] = '('+port+','+table+')'
    return actions_dict
'''


def print_flow_dict(flows):
    for flow in flows:
        print flow['meta'], flow['matches'], flow['actions']