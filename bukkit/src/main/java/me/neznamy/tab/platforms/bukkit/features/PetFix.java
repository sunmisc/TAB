package me.neznamy.tab.platforms.bukkit.features;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.neznamy.tab.api.TabFeature;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.platforms.bukkit.nms.NMSStorage;
import me.neznamy.tab.platforms.bukkit.nms.datawatcher.DataWatcher;
import me.neznamy.tab.platforms.bukkit.nms.datawatcher.DataWatcherItem;
import me.neznamy.tab.shared.TAB;

/**
 * A feature to disable minecraft 1.9+ feature making tamed animals with custom names copy nametag properties of their owner
 * This is achieved by listening to entity spawn (<1.15) / entity metadata packets and removing owner field from the datawatcher list
 * Since 1.16 this results in client sending entity use packet twice, so we must cancel the 2nd one to prevent double toggle
 */
public class PetFix extends TabFeature {

	//datawatcher position of pet owner field
	private int petOwnerPosition;

	//logger of last interacts to prevent feature not working on 1.16
	private Map<String, Long> lastInteractFix = new HashMap<>();
	
	//nms storage
	private NMSStorage nms;
	
	/**
	 * Constructs new instance with given parameter
	 * @param nms
	 */
	public PetFix(NMSStorage nms) {
		super("Pet name fix");
		this.nms = nms;
		petOwnerPosition = getPetOwnerPosition();
		TAB.getInstance().debug("Loaded PetFix feature");
	}
	
	/**
	 * Returns position of pet owner field based on server version
	 * @return position of pet owner field based on server version
	 */
	private int getPetOwnerPosition() {
		if (nms.getMinorVersion() >= 17) {
			//1.17.x
			return 18;
		} else if (nms.getMinorVersion() >= 15) {
			//1.15.x, 1.16.x
			return 17;
		} else if (nms.getMinorVersion() >= 14) {
			//1.14.x
			return 16;
		} else if (nms.getMinorVersion() >= 10) {
			//1.10.x - 1.13.x
			return 14;
		} else {
			//1.9.x
			return 13;
		}
	}
	
	/**
	 * Cancels a packet if previous one arrived with no delay to prevent double toggle on 1.16
	 * @throws IllegalAccessException 
	 */
	@Override
	public boolean onPacketReceive(TabPlayer sender, Object packet) throws IllegalAccessException {
		if (nms.PacketPlayInUseEntity.isInstance(packet)) {
			if (lastInteractFix.containsKey(sender.getName()) && (System.currentTimeMillis() - lastInteractFix.get(sender.getName()) < 5)) {
				//last interact packet was sent right now, cancelling to prevent double-toggle due to this feature enabled
				return true;
			}
			if (isInteract(nms.PacketPlayInUseEntity_ACTION.get(packet))) {
				//this is the first packet, saving player so the next packet can be cancelled
				lastInteractFix.put(sender.getName(), System.currentTimeMillis());
			}
		}
		return false;
	}
	
	private boolean isInteract(Object action) {
		if (nms.getMinorVersion() >= 17) {
			return nms.PacketPlayInUseEntity$d.isInstance(action);
		} else {
			return action.toString().equals("INTERACT");
		}
	}
	
	/**
	 * Removes pet owner field from datawatcher
	 * @throws IllegalAccessException 
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 * @throws InstantiationException 
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void onPacketSend(TabPlayer receiver, Object packet) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, SecurityException, InstantiationException {
		if (nms.PacketPlayOutEntityMetadata.isInstance(packet)) {
			Object removedEntry = null;
			List<Object> items = (List<Object>) nms.PacketPlayOutEntityMetadata_LIST.get(packet);
			if (items == null) return;
			for (Object item : items) {
				if (nms.DataWatcherObject_SLOT.getInt(nms.DataWatcherItem_TYPE.get(item)) == petOwnerPosition) {
					Object value = nms.DataWatcherItem_VALUE.get(item);
					if (value instanceof java.util.Optional || value instanceof com.google.common.base.Optional) {
						removedEntry = item;
						break;
					}
				}
			}
			if (removedEntry != null) items.remove(removedEntry);
		}
		//<1.15
		if (nms.PacketPlayOutSpawnEntityLiving.isInstance(packet) && nms.PacketPlayOutSpawnEntityLiving_DATAWATCHER != null) {
			DataWatcher watcher = DataWatcher.fromNMS(nms.PacketPlayOutSpawnEntityLiving_DATAWATCHER.get(packet));
			DataWatcherItem petOwner = watcher.getItem(petOwnerPosition);
			if (petOwner != null && (petOwner.getValue() instanceof java.util.Optional || petOwner.getValue() instanceof com.google.common.base.Optional)) {
				watcher.removeValue(petOwnerPosition);
				nms.setField(packet, nms.PacketPlayOutSpawnEntityLiving_DATAWATCHER, watcher.toNMS());
			}
		}
	}

	/**
	 * Removing player data to not have a memory leak
	 */
	@Override
	public void onQuit(TabPlayer disconnectedPlayer) {
		lastInteractFix.remove(disconnectedPlayer.getName());
	}
}