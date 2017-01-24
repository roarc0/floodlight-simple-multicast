package net.floodlightcontroller.multicast.multicastdb;

import java.util.ArrayList;
import java.util.List;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastGroup {
	protected static final Logger log = LoggerFactory.getLogger(MulticastGroup.class);

	private IPv4Address addr;
	
	public class MCHost {
		public IPv4Address addr;
		// ADD socket port ?
		public MacAddress  mac;
		public DatapathId  dpid;
		public OFPort port;
		
		MCHost(IPv4Address addr, MacAddress mac, DatapathId  dpid, OFPort port) {
			this.addr = addr;
			this.mac  = mac;
			this.dpid = dpid;
			this.port = port;
		}
	};
	
	private List<MCHost> hosts;
	private int lifetime;

	public int getLifetime() {
		return lifetime;
	}

	public MulticastGroup setLifetime(int lifetime) {
		this.lifetime = lifetime;
		return this;
	}

	public MulticastGroup(String group_addr) {
		addr = IPv4Address.of(group_addr);
		hosts = new ArrayList<MCHost>();
		lifetime = 60;
	}

	public IPv4Address getAddr() {
		return addr;
	}

	public MulticastGroup setAddr(IPv4Address addr) {
		this.addr = addr;
		return this;
	}

	public List<MCHost> getHosts() {
		return hosts;
	}

	public MulticastGroup addHost(IPv4Address addr, MacAddress mac, DatapathId dpid, OFPort port) {
		hosts.add(new MCHost(addr, mac, dpid, port));
		return this;
	}

	public MulticastGroup deleteHost(IPv4Address addr) {
		MCHost h_del = null;
		for(MCHost h : hosts) {
			if (h.addr.toString().equals(addr.toString())) {
				h_del = h;
				break;
			}
		}
		if (h_del != null)
			hosts.remove(h_del);	

		return this;
	}
	
	public MulticastGroup clearHosts() {
		hosts.clear();
		return this;
	}
}
