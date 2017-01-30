package net.floodlightcontroller.multicast;

import java.util.Map;

import net.floodlightcontroller.core.module.IFloodlightService;

// rest interface
public interface IMulticastREST extends IFloodlightService {
	public Map<String, Object> getGroupsInfo();
	public Map<String, Object> getGroupInfo(String addr);
	public boolean createGroup(String address);
	public boolean deleteGroup(String address);
}
