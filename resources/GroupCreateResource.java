package net.floodlightcontroller.multicast.resources;

import java.io.IOException;

import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.floodlightcontroller.multicast.IMulticastREST;

public class GroupCreateResource extends ServerResource {
	@Post
	public String Create(String json) {
		boolean ret;
		ObjectMapper mapper = new ObjectMapper();
			try {
				JsonNode root = mapper.readTree(json);

				String addr = root.get("group_addr").asText();
				IMulticastREST mc = (IMulticastREST)getContext().getAttributes().get(IMulticastREST.class.getCanonicalName());
				ret = mc.createGroup(addr);
			} 
			catch (IOException e) {
				e.printStackTrace();
				return new String("error");
			}

			if (ret)
				return new String("group created");
			else
				return new String("group already existing");
	}
}