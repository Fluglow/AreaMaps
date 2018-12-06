package me.fluglow;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreationSessionManager implements Listener {
	private Map<UUID, AreaCreationSession> sessions;
	CreationSessionManager()
	{
		sessions = new HashMap<>();
	}

	public boolean addSession(UUID owner, AreaCreationSession session)
	{
		if(!sessions.containsKey(owner))
		{
			sessions.put(owner, session);
			return true;
		}
		return false;
	}

	void removeSession(UUID owner)
	{
		sessions.remove(owner);
	}

	@EventHandler
	public void chatEvent(AsyncPlayerChatEvent event)
	{
		Player player = event.getPlayer();
		AreaCreationSession session = sessions.get(player.getUniqueId());
		if(session != null)
		{
			session.handleSessionChat(event.getMessage(), player);
			event.setCancelled(true);
		}
	}


}
