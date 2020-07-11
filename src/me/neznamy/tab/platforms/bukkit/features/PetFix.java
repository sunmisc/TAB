package me.neznamy.tab.platforms.bukkit.features;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import me.neznamy.tab.platforms.bukkit.packets.DataWatcher;
import me.neznamy.tab.platforms.bukkit.packets.PacketPlayOut;
import me.neznamy.tab.platforms.bukkit.packets.PacketPlayOutSpawnEntityLiving;
import me.neznamy.tab.platforms.bukkit.packets.DataWatcher.Item;
import me.neznamy.tab.platforms.bukkit.packets.method.MethodAPI;
import me.neznamy.tab.shared.ITabPlayer;
import me.neznamy.tab.shared.ProtocolVersion;
import me.neznamy.tab.shared.cpu.CPUFeature;
import me.neznamy.tab.shared.features.interfaces.RawPacketFeature;

public class PetFix implements RawPacketFeature{

	private final int PET_OWNER_POSITION = getPetOwnerPosition();
	private Field PacketPlayOutEntityMetadata_LIST;
	
	private int getPetOwnerPosition() {
		if (ProtocolVersion.SERVER_VERSION.getMinorVersion() >= 15) {
			//1.15.x, 1.16.x
			return 17;
		} else if (ProtocolVersion.SERVER_VERSION.getMinorVersion() >= 14) {
			//1.14.x
			return 16;
		} else if (ProtocolVersion.SERVER_VERSION.getMinorVersion() >= 10) {
			//1.10.x - 1.13.x
			return 14;
		} else {
			//1.9.x
			return 13;
		}
	}
	
	public PetFix() {
		PacketPlayOutEntityMetadata_LIST = PacketPlayOut.getFields(MethodAPI.PacketPlayOutEntityMetadata).get("b");
	}
	
	@Override
	public Object onPacketReceive(ITabPlayer sender, Object packet) throws Throwable {
		return packet;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Object onPacketSend(ITabPlayer receiver, Object packet) throws Throwable{
		if (MethodAPI.PacketPlayOutEntityMetadata.isInstance(packet)) {
			List<Object> items = (List<Object>) PacketPlayOutEntityMetadata_LIST.get(packet);
			if (items == null) return packet;
			List<Object> newList = new ArrayList<Object>();
			for (Object item : items) {
				Item i = Item.fromNMS(item);
				if (i.type.position == PET_OWNER_POSITION) {
					modifyDataWatcherItem(i);
				}
				newList.add(i.toNMS());
			}
			PacketPlayOutEntityMetadata_LIST.set(packet, newList);
		}
		if (MethodAPI.PacketPlayOutSpawnEntityLiving.isInstance(packet) && PacketPlayOutSpawnEntityLiving.DATAWATCHER != null) {
			DataWatcher watcher = DataWatcher.fromNMS(PacketPlayOutSpawnEntityLiving.DATAWATCHER.get(packet));
			Item petOwner = watcher.getItem(PET_OWNER_POSITION);
			if (petOwner != null) modifyDataWatcherItem(petOwner);
			PacketPlayOutSpawnEntityLiving.DATAWATCHER.set(packet, watcher.toNMS());
		}
		return packet;
	}
	
	@SuppressWarnings({ "rawtypes" })
	private void modifyDataWatcherItem(Item petOwner) {
		//1.12-
		if (petOwner.value instanceof com.google.common.base.Optional) {
			com.google.common.base.Optional o = (com.google.common.base.Optional) petOwner.value;
			if (o.isPresent() && o.get() instanceof UUID) {
				petOwner.value = com.google.common.base.Optional.of(UUID.randomUUID());
			}
		}
		//1.13+
		if (petOwner.value instanceof java.util.Optional) {
			java.util.Optional o = (java.util.Optional) petOwner.value;
			if (o.isPresent() && o.get() instanceof UUID) {
				petOwner.value = java.util.Optional.of(UUID.randomUUID());
			}
		}
	}
	
	@Override
	public CPUFeature getCPUName() {
		return CPUFeature.PET_NAME_FIX;
	}
}