package net.floodlightcontroller.multicast.multicastdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.multicast.multicastdb.MulticastGroup;
import net.floodlightcontroller.multicast.multicastdb.MulticastGroup.MCHost;

// Class used to manage groups
public class MulticastDb {
	protected static final Logger log = LoggerFactory.getLogger(MulticastDb.class);
	private List<MulticastGroup> db;
	
	public MulticastDb() {
		db = new ArrayList<MulticastGroup>();
	}
	
	public int size() {
		return db.size();
	}

	// returns an object used by rest interface for single group information
	public Map<String, Object> getGroupDescription(String groupAddr) {
		Map<String, Object> map = new HashMap<String, Object>();
		MulticastGroup group = getGroup(groupAddr);
		int i = 0;
		log.info("Searching in group: " + groupAddr);
		if (group != null && group.getHosts() != null) {
			for(MCHost host : group.getHosts()) {
				String ghost = host.addr.toString() + ", " + host.mac.toString() +
						       ", " + host.dpid.toString() + ", " + host.port.toString();  
				map.put("host_" + i, ghost);
				i++;
			}
		}
		return map;
	}

	// returns an object used by rest interface to list all groups
	public Map<String, Object> getGroupsDescription() {
		Map<String, Object> map = new HashMap<String, Object>();
		String gaddrs = "";
		for( MulticastGroup group : db) {
			IPv4Address groupAddr = group.getAddr();
			gaddrs += group.getAddr().toString() + ", ";
		}
		if (gaddrs.length() > 2)
			gaddrs = gaddrs.substring(0, gaddrs.length()-2);
		map.put("groups", gaddrs);
		return map;
	}

	// returns instance of group referenced by addr
	public MulticastGroup getGroup(String groupAddr) {
		for(MulticastGroup g : db) {
			if (g.getAddr().toString().compareTo(groupAddr) == 0) {
				return g;
			}
		}
		return null;
	}

	// returns instance of group referenced by addr
	public MulticastGroup getGroup(IPv4Address groupAddr) {
		return getGroup(groupAddr.toString());
	}

	// called by rest interface, creates a new group
	public boolean createGroup(String groupAddr) {
		if (getGroup(groupAddr) == null) {
			db.add(new MulticastGroup(groupAddr));		
			log.info("Creating a new multicast group: " + groupAddr);
			return true;
		}

		log.warn("Group " + groupAddr  + " already exists");
		return false;
	}
	
	// deletes a group.
	public boolean deleteGroup(String groupAddr) { // TODO not tested 
		for(MulticastGroup g : db) {
			if (g.getAddr().toString().equals(groupAddr)) {
				db.remove(g);
				return true;
			}
		}
		return false;
	}
	
	// called upon IGMP join
	public boolean joinGroup(IPv4Address groupAddr, IPv4Address hostAddr, MacAddress hostMac, DatapathId dpid, OFPort port) {
		MulticastGroup group = getGroup(groupAddr.toString());
		
		if (group == null) {
			log.error("Multicast group " + groupAddr + " does not exist");	
			return false;
		}	

		// if not already in group
		if (!checkHost(groupAddr.toString(), hostAddr.toString())) {
			group.addHost(hostAddr, hostMac, dpid, port);
			log.info("Adding a new host to multicast group: " +
			         hostAddr + " -> " + groupAddr +
			         " | dpid:port -> " + dpid.toString() + ":" + port.toString());			
		    return true;
		}

		log.warn("Host " + hostAddr + " already part of group " + groupAddr);
		return false;
	}
	
	// called upon IGMP leave.
	public boolean leaveGroup(IPv4Address groupAddr, IPv4Address hostAddr) {
		MulticastGroup group = getGroup(groupAddr.toString());
		
		if (group == null) {
			log.error("Multicast group " + groupAddr + " does not exist");	
			return false;
		}
		
		group.deleteHost(hostAddr);
		
		return true;
	}

	// check if host is in group
	public boolean checkHost(IPv4Address groupAddr, IPv4Address hostAddr) {
		return checkHost(groupAddr.toString(), hostAddr.toString());
	}

	// check if host is in group
	public boolean checkHost(String groupAddr, String hostAddr) {
		MulticastGroup group = getGroup(groupAddr);

		if (group != null)	
			for (MCHost host : group.getHosts()) {
				if (host.addr.toString().compareTo(hostAddr) == 0)
					return true;
			}
		return false;
	}
}