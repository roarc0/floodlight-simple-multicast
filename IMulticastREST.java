package net.floodlightcontroller.multicast;

import java.util.Map;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IMulticastREST extends IFloodlightService {
	public Map<String, Object> getGroupsInfo();
	public Map<String, Object> getGroupInfo(String addr);
	
	public void setLifetime(int value);
	
	public boolean createGroup(String address);
	public boolean deleteGroup(String address);
}
