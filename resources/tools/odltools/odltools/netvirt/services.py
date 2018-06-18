services = {
    0: None,
    1: 'SFC',
    2: 'ACL',
    3: 'IN_CTRS',
    4: 'SFC_CLASS',
    5: 'DHCP',
    6: 'QoS',
    7: 'IPv6',
    8: 'COE',
    9: 'L3VPN',
    10: 'ELAN',
    11: 'L3VPN6'
}


def get_service_name(service_id):
    if service_id and service_id in services:
        return services[service_id]
    else:
        return None
